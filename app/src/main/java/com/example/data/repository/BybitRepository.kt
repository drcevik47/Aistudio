package com.example.data.repository
import androidx.room.withTransaction

import android.util.Log
import com.example.bot.RebalanceEngine
import com.example.data.local.BotPreferences
import com.example.data.local.dao.ExchangeTradeDao
import com.example.data.local.dao.LogDao
import com.example.data.local.dao.OrderDao
import com.example.data.local.entity.ExchangeTradeEntity
import com.example.data.local.entity.LogEntity
import com.example.data.local.entity.LogLevel
import com.example.data.local.entity.OrderEntity
import com.example.data.remote.BybitApiService
import com.example.data.remote.BybitSigner
import com.example.data.remote.model.BybitExecutionDto
import com.example.data.remote.model.BybitOrderDto
import com.example.data.remote.model.SpotTicker
import com.example.data.remote.model.TradeAnalysisResult
import com.example.data.remote.model.TradeSyncResult
import com.example.data.remote.model.KlineResult
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.Locale
import com.example.ui.AssetBalance
import java.util.concurrent.TimeUnit
import kotlin.math.floor

data class ReconciliationResult(
    val executedOrderFound: Boolean = false,
    val executedSide: String? = null,
    val executedPrice: Double = 0.0,
    val executedQty: Double = 0.0,
    val executedOrderId: String = "",
    val newBasePrice: Double = 0.0,
    val message: String = ""
)

data class LastFilledTradeInfo(
    val price: Double,
    val side: String = "",
    val qty: Double = 0.0,
    val timestamp: Long = 0L,
    val source: String = ""
)

class BybitRepository(
    private val preferences: BotPreferences,
    private val database: com.example.data.local.AppDatabase,
    private val orderDao: OrderDao,
    private val logDao: LogDao,
    private val exchangeTradeDao: ExchangeTradeDao
) {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        scope.launch {
            try {
                exchangeTradeDao.clearSyntheticTrades()
            } catch (e: Exception) {
                // Non-fatal
            }
        }
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = if (com.example.BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
            redactHeader("X-BAPI-API-KEY")
            redactHeader("X-BAPI-SIGN")
            redactHeader("X-BAPI-TIMESTAMP")
            redactHeader("Authorization")
        })
        .build()

    private val orderFillMutex = kotlinx.coroutines.sync.Mutex()
    private val tradeSyncMutex = kotlinx.coroutines.sync.Mutex()

    // Clock offset between device time and Bybit server time
    private var serverTimeOffsetMs: Long = 0L

    // Concurrency lock and cooldown for grid order reconciliation
    private val reconcileMutex = Mutex()
    @Volatile
    var lastGridOrderPlacedTimeMs: Long = 0L

    private fun getBaseUrl(isTestnet: Boolean): String {
        return if (isTestnet) "https://api-testnet.bybit.com"
        else "https://api.bybit.com"
    }

    private fun createApiService(isTestnet: Boolean): BybitApiService {
        return Retrofit.Builder()
            .baseUrl(getBaseUrl(isTestnet))
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(BybitApiService::class.java)
    }

    private fun getSyncedTimestamp(): Long {
        return System.currentTimeMillis() + serverTimeOffsetMs
    }

    suspend fun syncServerTime(isTestnet: Boolean = preferences.isTestnet): Long = withContext(Dispatchers.IO) {
        try {
            val api = createApiService(isTestnet)
            val response = api.getServerTime()
            if (response.isSuccessful && response.body()?.isSuccess == true) {
                val serverTime = response.body()?.time ?: response.body()?.result?.timeSecond?.toLongOrNull()?.times(1000)
                if (serverTime != null && serverTime > 0) {
                    serverTimeOffsetMs = serverTime - System.currentTimeMillis()
                    Log.d("BybitRepo", "Synced server time offset: $serverTimeOffsetMs ms")
                }
            }
        } catch (e: Exception) {
            Log.w("BybitRepo", "Server time sync failed: ${e.message}")
        }
        serverTimeOffsetMs
    }

    private fun createAuthHeaders(
        apiKey: String,
        apiSecret: String,
        paramStr: String
    ): Map<String, String> {
        val timestamp = getSyncedTimestamp()
        val recvWindow = "20000" // 20 seconds tolerance for mobile network & clock variance
        val signature = BybitSigner.signRest(timestamp, apiKey, recvWindow, paramStr, apiSecret)

        return mapOf(
            "X-BAPI-API-KEY" to apiKey,
            "X-BAPI-TIMESTAMP" to timestamp.toString(),
            "X-BAPI-SIGN" to signature,
            "X-BAPI-RECV-WINDOW" to recvWindow,
            "Content-Type" to "application/json"
        )
    }

    private fun parseBybitErrorMessage(retCode: Int, retMsg: String): String {
        val hint = when (retCode) {
            10002 -> "Zaman aşımı: Cihaz saati Bybit sunucusu ile senkronize değil"
            10003 -> "API Key veya Secret hatalı / geçersiz"
            10004 -> "API İmza doğrulaması başarısız (Parametreler veya secret uyuşmuyor)"
            10005, 33004 -> "Yetki hatası: API anahtarınızda 'Spot: Trade' (Alım-Satım) izni açık olmalıdır"
            10006 -> "İstek sınırı aşıldı (Rate Limit): Borsa hız sınırı devrede, lütfen bekleyin"
            10010 -> "IP adresi yetkilendirilmemiş (Bybit IP kısıtlaması)"
            170131 -> "Yetersiz bakiye: Emir için Unified cüzdanınızda yeterli USDT veya MNT yok"
            170140 -> "Emir tutarı çok küçük: Bybit MNT/USDT için minimum işlem tutarı 5 USDT'dir"
            170193 -> "Emir miktarı veya fiyatı Bybit sınırlarını aşıyor"
            else -> ""
        }
        return if (hint.isNotBlank()) "Hata $retCode ($hint): $retMsg" else "Bybit Hata [$retCode]: $retMsg"
    }

    private val instrumentInfoCache = java.util.concurrent.ConcurrentHashMap<String, com.example.data.remote.model.SpotInstrumentInfo>()

    suspend fun getInstrumentInfo(symbol: String = preferences.bybitSymbol, isTestnet: Boolean = preferences.isTestnet): com.example.data.remote.model.SpotInstrumentInfo? = withContext(Dispatchers.IO) {
        instrumentInfoCache[symbol]?.let { return@withContext it }
        try {
            val api = createApiService(isTestnet)
            val response = api.getInstrumentsInfo(category = "spot", symbol = symbol)
            if (response.isSuccessful && response.body()?.isSuccess == true) {
                val info = response.body()?.result?.list?.firstOrNull { it.symbol.equals(symbol, ignoreCase = true) }
                if (info != null) {
                    instrumentInfoCache[symbol] = info
                    return@withContext info
                }
            }
        } catch (e: Exception) {
            Log.w("BybitRepo", "Failed to fetch instruments info: ${e.message}")
        }
        null
    }

    fun getCachedInstrumentInfo(symbol: String = preferences.bybitSymbol): com.example.data.remote.model.SpotInstrumentInfo? {
        return instrumentInfoCache[symbol]
    }

    fun getPrecisionForSymbol(symbol: String = preferences.bybitSymbol): Pair<Int?, Int?> {
        val info = instrumentInfoCache[symbol]
        val qtyDecimals = info?.lotSizeFilter?.basePrecision?.let { com.example.bot.RebalanceEngine.stepToDecimals(it) }
        val priceDecimals = info?.priceFilter?.tickSize?.let { com.example.bot.RebalanceEngine.stepToDecimals(it) }
        return Pair(qtyDecimals, priceDecimals)
    }

    /**
     * Dynamically format order quantity based on exchange lotSizeFilter and precision so we never attempt to trade more than available balance.
     */
    fun formatMntQty(qty: Double, price: Double? = null, symbol: String = preferences.bybitSymbol): String {
        val info = instrumentInfoCache[symbol]
        val stepStr = info?.lotSizeFilter?.basePrecision
        if (!stepStr.isNullOrBlank()) {
            return com.example.bot.RebalanceEngine.formatWithStep(qty, stepStr, java.math.RoundingMode.FLOOR)
        }
        val decimals = run {
            val refPrice = price ?: preferences.lastRebalancePrice
            when {
                refPrice >= 10000.0 -> 6
                refPrice >= 1000.0 -> 5
                refPrice >= 100.0 -> 4
                refPrice >= 10.0 -> 3
                refPrice >= 1.0 -> 2
                else -> 1
            }
        }
        val factor = Math.pow(10.0, decimals.toDouble())
        val truncated = floor(qty * factor) / factor
        return String.format(Locale.US, "%.${decimals}f", truncated).trimEnd('0').let {
            if (it.endsWith(".")) it + "0" else it
        }
    }

    /**
     * Dynamically format price based on exchange priceFilter (tickSize).
     */
    fun formatPrice(price: Double, symbol: String = preferences.bybitSymbol): String {
        val info = instrumentInfoCache[symbol]
        val tickStr = info?.priceFilter?.tickSize
        if (!tickStr.isNullOrBlank()) {
            return com.example.bot.RebalanceEngine.formatWithStep(price, tickStr, java.math.RoundingMode.HALF_UP)
        }
        val decimals = when {
            price >= 1000.0 -> 2
            price >= 1.0 -> 4
            price >= 0.01 -> 6
            else -> 8
        }
        return String.format(Locale.US, "%.${decimals}f", price).trimEnd('0').trimEnd('.')
    }

    private val logInsertCounter = java.util.concurrent.atomic.AtomicInteger(0)

    suspend fun pruneLogs(
        maxAgeMillis: Long = 24 * 60 * 60 * 1000L, // 24 hours
        maxLogsToKeep: Int = 1000
    ) = withContext(Dispatchers.IO) {
        try {
            val cutoff = System.currentTimeMillis() - maxAgeMillis
            val deletedByAge = logDao.pruneOldLogs(cutoff)
            val deletedByCount = logDao.pruneExcessLogs(maxLogsToKeep)
            if (deletedByAge > 0 || deletedByCount > 0) {
                Log.d("BybitRepository", "Log pruning: $deletedByAge old logs deleted, $deletedByCount excess logs pruned")
            }
        } catch (e: Exception) {
            Log.e("BybitRepository", "Failed to prune logs: ${e.message}")
        }
    }

    suspend fun log(level: LogLevel, tag: String, message: String, details: String = "") {
        try {
            logDao.insertLog(
                LogEntity(
                    level = level.name,
                    tag = tag,
                    message = message,
                    details = details
                )
            )
            Log.d(tag, "[${level.name}] $message $details")

            // Periodically prune logs older than 24 hours or exceeding 1000 items
            if (logInsertCounter.incrementAndGet() % 50 == 0) {
                pruneLogs()
            }
        } catch (e: Exception) {
            Log.e("Repository", "Failed to insert log", e)
        }
    }

    suspend fun getKlines(symbol: String, interval: String, start: Long? = null, end: Long? = null, limit: Int = 1000): Result<KlineResult> {
        return withContext(Dispatchers.IO) {
            try {
                val api = createApiService(preferences.isTestnet)
                val response = api.getKlines(
                    symbol = symbol,
                    interval = interval,
                    start = start,
                    end = end,
                    limit = limit
                )
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null && body.isSuccess && body.result != null) {
                        Result.success(body.result)
                    } else {
                        Result.failure(Exception(body?.retMsg ?: "Kline alınamadı"))
                    }
                } else {
                    Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getWalletBalance(
        apiKey: String = preferences.apiKey,
        apiSecret: String = preferences.apiSecret,
        isTestnet: Boolean = preferences.isTestnet
    ): Result<Map<String, AssetBalance>> = withContext(Dispatchers.IO) {
        try {
            if (apiKey.isBlank() || apiSecret.isBlank()) {
                return@withContext Result.failure(Exception("API Key veya Secret eksik"))
            }

            val api = createApiService(isTestnet)
            val queryString = "accountType=UNIFIED"
            val headers = createAuthHeaders(apiKey, apiSecret, queryString)

            val response = api.getWalletBalance(headers, "UNIFIED")
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.isSuccess) {
                    val balanceMap = mutableMapOf<String, AssetBalance>()
                    body.result?.list?.firstOrNull()?.coin?.forEach { coin ->
                        val totalQty = coin.balanceValue
                        val availQty = coin.availableValue
                        val totalUsd = coin.fiatValue
                        val availUsd = if (totalQty > 0.0) totalUsd * (availQty / totalQty) else 0.0

                        balanceMap[coin.coin.uppercase()] = AssetBalance(
                            quantity = availQty,
                            fiatValue = availUsd
                        )
                    }
                    Result.success(balanceMap)
                } else {
                    val code = body?.retCode ?: -1
                    val msg = body?.retMsg ?: "Bilinmeyen API hatası"
                    val formatted = parseBybitErrorMessage(code, msg)
                    log(LogLevel.ERROR, "WalletBalance", "Bakiye hatası: $formatted")
                    Result.failure(Exception(formatted))
                }
            } else {
                val errBody = response.errorBody()?.string() ?: ""
                val err = "HTTP ${response.code()}: ${response.message()} $errBody".trim()
                log(LogLevel.ERROR, "WalletBalance", err)
                Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            log(LogLevel.ERROR, "WalletBalance", "Bağlantı hatası: ${e.localizedMessage}")
            Result.failure(e)
        }
    }

    suspend fun getTicker(isTestnet: Boolean = preferences.isTestnet): Result<SpotTicker> =
        withContext(Dispatchers.IO) {
            try {
                val api = createApiService(isTestnet)
                val response = api.getTickers("spot", preferences.bybitSymbol)
                if (response.isSuccessful) {
                    val body = response.body()
                    val ticker = body?.result?.list?.firstOrNull()
                    if (body != null && body.isSuccess && ticker != null) {
                        Result.success(ticker)
                    } else {
                        val errMsg = body?.retMsg ?: "Ticker bilgisi alınamadı"
                        Result.failure(Exception(errMsg))
                    }
                } else {
                    Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun createOrder(
        side: String, // "Buy" or "Sell"
        orderType: String, // "Limit" or "Market"
        qty: Double,
        price: Double? = null,
        triggerReason: String = "Manual",
        customOrderLinkId: String? = null,
        apiKey: String = preferences.apiKey,
        apiSecret: String = preferences.apiSecret,
        isTestnet: Boolean = preferences.isTestnet
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (apiKey.isBlank() || apiSecret.isBlank()) {
                return@withContext Result.failure(Exception("API Key veya Secret eksik"))
            }

            val formattedQty = formatMntQty(qty, price)
            if (formattedQty.toDoubleOrNull() == null || formattedQty.toDouble() <= 0.0) {
                return@withContext Result.failure(Exception("Geçersiz miktar: $formattedQty ${preferences.bybitBaseCoin}"))
            }

            val formattedPrice = price?.let { formatPrice(it) }
            val orderLinkId = customOrderLinkId?.takeIf { it.isNotBlank() }
                ?: "bot_${System.currentTimeMillis()}_${(100..999).random()}"

            // Construct exact JSON payload
            val jsonObject = JSONObject().apply {
                put("category", "spot")
                put("symbol", preferences.bybitSymbol)
                put("side", side)
                put("orderType", orderType)
                put("qty", formattedQty)
                if (orderType.equals("Limit", ignoreCase = true) && formattedPrice != null) {
                    put("price", formattedPrice)
                    put("timeInForce", "GTC")
                } else if (orderType.equals("Market", ignoreCase = true)) {
                    put("marketUnit", "baseCoin")
                }
                put("orderLinkId", orderLinkId)
            }
            val jsonString = jsonObject.toString()

            // Calculate HMAC SHA256 over this exact jsonString
            val headers = createAuthHeaders(apiKey, apiSecret, jsonString)
            val requestBody = jsonString.toRequestBody("application/json; charset=utf-8".toMediaType())

            val api = createApiService(isTestnet)
            try {
                val response = api.createOrder(headers, requestBody)
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null && body.isSuccess && body.result != null) {
                        val orderId = body.result.orderId
                        lastGridOrderPlacedTimeMs = System.currentTimeMillis()
                        val orderEntity = OrderEntity(
                            orderId = orderId,
                            orderLinkId = orderLinkId,
                            symbol = preferences.bybitSymbol,
                            side = side,
                            orderType = orderType,
                            price = price ?: 0.0,
                            qty = formattedQty.toDouble(),
                            status = "New",
                            filledQty = 0.0,
                            avgPrice = price ?: 0.0,
                            triggerReason = triggerReason
                        )
                        orderDao.insertOrder(orderEntity)
                        log(
                            LogLevel.SUCCESS,
                            "OrderCreate",
                            "Emir iletildi: $side $formattedQty MNT @ ${formattedPrice ?: "Market"}",
                            "OrderId: $orderId | Neden: $triggerReason"
                        )
                        return@withContext Result.success(orderId)
                    } else {
                        val code = body?.retCode ?: -1
                        val msg = body?.retMsg ?: "Emir oluşturulamadı"
                        // Duplicate orderLinkId detection (codes 10006, 10007, 10024, 110012, 170140)
                        if (code in listOf(10006, 10007, 10024, 110012, 170140)) {
                            log(LogLevel.WARN, "OrderCreate", "Bybit orderLinkId ($orderLinkId) zaten mevcut döndü ($code: $msg). Borsa sorgulanıyor...")
                            val existing = findOrderByLinkId(orderLinkId, apiKey = apiKey, apiSecret = apiSecret, isTestnet = isTestnet)
                            if (existing != null) {
                                val orderId = existing.orderId
                                val orderEntity = OrderEntity(
                                    orderId = orderId,
                                    orderLinkId = orderLinkId,
                                    symbol = preferences.bybitSymbol,
                                    side = side,
                                    orderType = orderType,
                                    price = price ?: (existing.price.toDoubleOrNull() ?: 0.0),
                                    qty = formattedQty.toDouble(),
                                    status = existing.orderStatus,
                                    filledQty = existing.filledQtyValue,
                                    avgPrice = existing.avgPriceValue,
                                    triggerReason = triggerReason
                                )
                                orderDao.insertOrder(orderEntity)
                                log(LogLevel.SUCCESS, "OrderCreate", "Çifte emir engellendi: Mevcut emir tespit edildi ($orderId)")
                                return@withContext Result.success(orderId)
                            }
                        }
                        val formattedErr = parseBybitErrorMessage(code, msg)
                        log(LogLevel.ERROR, "OrderCreate", formattedErr, "İstek gövdesi: $jsonString")
                        return@withContext Result.failure(Exception(formattedErr))
                    }
                } else {
                    val httpCode = response.code()
                    val errBody = response.errorBody()?.string() ?: ""
                    if (httpCode in 500..599 || httpCode == 408) {
                        log(LogLevel.WARN, "OrderCreate", "HTTP $httpCode alındı. Emir borsaya ulaşmış olabilir. Idempotency kontrolü yapılıyor ($orderLinkId)...")
                        delay(600)
                        val existing = findOrderByLinkId(orderLinkId, apiKey = apiKey, apiSecret = apiSecret, isTestnet = isTestnet)
                        if (existing != null) {
                            val orderId = existing.orderId
                            val orderEntity = OrderEntity(
                                orderId = orderId,
                                orderLinkId = orderLinkId,
                                symbol = preferences.bybitSymbol,
                                side = side,
                                orderType = orderType,
                                price = price ?: (existing.price.toDoubleOrNull() ?: 0.0),
                                qty = formattedQty.toDouble(),
                                status = existing.orderStatus,
                                filledQty = existing.filledQtyValue,
                                avgPrice = existing.avgPriceValue,
                                triggerReason = triggerReason
                            )
                            orderDao.insertOrder(orderEntity)
                            log(LogLevel.SUCCESS, "OrderCreate", "HTTP $httpCode sonrası emir borsada bulundu ($orderId). Çifte emir engellendi.")
                            return@withContext Result.success(orderId)
                        }
                    }
                    val err = "HTTP ${response.code()}: ${response.message()} $errBody".trim()
                    log(LogLevel.ERROR, "OrderCreate", "HTTP Hatası: $err", "İstek: $jsonString")
                    return@withContext Result.failure(Exception(err))
                }
            } catch (networkEx: Exception) {
                if (networkEx is CancellationException) throw networkEx
                log(LogLevel.WARN, "OrderCreate", "Ağ zaman aşımı/hatası (${networkEx.message}). Emir borsaya ulaşmış olabilir, $orderLinkId sorgulanıyor...")
                delay(800)
                val existing = findOrderByLinkId(orderLinkId, apiKey = apiKey, apiSecret = apiSecret, isTestnet = isTestnet)
                if (existing != null) {
                    val orderId = existing.orderId
                    val orderEntity = OrderEntity(
                        orderId = orderId,
                        orderLinkId = orderLinkId,
                        symbol = preferences.bybitSymbol,
                        side = side,
                        orderType = orderType,
                        price = price ?: (existing.price.toDoubleOrNull() ?: 0.0),
                        qty = formattedQty.toDouble(),
                        status = existing.orderStatus,
                        filledQty = existing.filledQtyValue,
                        avgPrice = existing.avgPriceValue,
                        triggerReason = triggerReason
                    )
                    orderDao.insertOrder(orderEntity)
                    log(LogLevel.SUCCESS, "OrderCreate", "Zaman aşımı sonrası emir borsada kurtarıldı ($orderId). Çifte emir engellendi.")
                    return@withContext Result.success(orderId)
                }
                throw networkEx
            }
        } catch (e: Exception) {
            log(LogLevel.ERROR, "OrderCreate", "İstek hatası: ${e.localizedMessage}")
            Result.failure(e)
        }
    }

    suspend fun cancelOrder(
        orderId: String,
        apiKey: String = preferences.apiKey,
        apiSecret: String = preferences.apiSecret,
        isTestnet: Boolean = preferences.isTestnet
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (apiKey.isBlank() || apiSecret.isBlank() || orderId.isBlank()) {
                return@withContext Result.success(true)
            }

            val jsonObject = JSONObject().apply {
                put("category", "spot")
                put("symbol", preferences.bybitSymbol)
                put("orderId", orderId)
            }
            val jsonString = jsonObject.toString()
            val headers = createAuthHeaders(apiKey, apiSecret, jsonString)
            val requestBody = jsonString.toRequestBody("application/json; charset=utf-8".toMediaType())

            val api = createApiService(isTestnet)
            val response = api.cancelOrder(headers, requestBody)
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.isSuccess) {
                    orderDao.deleteOrder(orderId)
                    log(LogLevel.INFO, "OrderCancel", "Emir iptal edildi: $orderId")
                    Result.success(true)
                } else {
                    val code = body?.retCode ?: -1
                    val msg = body?.retMsg ?: "İptal başarısız"
                    if (code == 170213 || code == 170170 || code == 110001) {
                        // Order already cancelled or filled or doesn't exist on active book.
                        // Check if it was actually FILLED on Bybit so we don't miss a fill!
                        try {
                            val histRes = getOrderHistory(orderId = orderId, apiKey = apiKey, apiSecret = apiSecret, isTestnet = isTestnet)
                            val histOrder = histRes.getOrNull()
                            if (histOrder != null && (histOrder.isFilled || histOrder.filledQtyValue > 0.0)) {
                                log(LogLevel.WARN, "OrderCancel", "İptal edilmek istenen emir ($orderId) borsada DOLMUŞ! Dolum kaydı işleniyor.")
                                val p = histOrder.avgPriceValue.takeIf { it > 0.0 } ?: histOrder.priceValue
                                val q = histOrder.filledQtyValue
                                recordOrderFilled(
                                    orderId = orderId,
                                    side = histOrder.side,
                                    price = p,
                                    qty = q,
                                    triggerReason = "CancelCheckFill"
                                )
                            }
                        } catch (ex: Exception) {
                            Log.w("BybitRepository", "Cancel fill check hatası: ${ex.message}")
                        }
                        orderDao.deleteOrder(orderId)
                        Result.success(true)
                    } else {
                        val formatted = parseBybitErrorMessage(code, msg)
                        log(LogLevel.WARN, "OrderCancel", "İptal cevabı: $formatted ($orderId)")
                        Result.failure(Exception(formatted))
                    }
                }
            } else {
                val errBody = response.errorBody()?.string() ?: ""
                val err = "HTTP ${response.code()}: ${response.message()} $errBody".trim()
                Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            log(LogLevel.ERROR, "OrderCancel", "İptal isteği hatası: ${e.localizedMessage}")
            Result.failure(e)
        }
    }

    sealed class RollbackResult {
        object Cancelled : RollbackResult()
        data class Filled(val order: BybitOrderDto) : RollbackResult()
        data class StillOpen(val order: BybitOrderDto) : RollbackResult()
        data class Unknown(val error: Throwable) : RollbackResult()
    }

    suspend fun safeRollbackOrder(
        orderId: String,
        side: String,
        callerTag: String = "Rollback"
    ): RollbackResult = withContext(Dispatchers.IO) {
        if (orderId.isBlank()) return@withContext RollbackResult.Cancelled

        log(LogLevel.WARN, callerTag, "Asimetrik emir geri alınıyor (iptal isteği): $side ($orderId)...")
        val cancelRes = cancelOrder(orderId)
        if (cancelRes.isSuccess) {
            log(LogLevel.INFO, callerTag, "Emir ($orderId) başarıyla iptal edildi.")
            orderDao.deleteOrder(orderId)
            return@withContext RollbackResult.Cancelled
        }

        // İptal başarısız olduysa doğrudan borsadan emir geçmişini sorgula
        log(LogLevel.WARN, callerTag, "Emir ($orderId) doğrudan iptal edilemedi (${cancelRes.exceptionOrNull()?.message}). Borsa durumu sorgulanıyor...")
        val histRes = getOrderHistory(orderId = orderId)
        val order = histRes.getOrNull()

        if (order != null) {
            when {
                order.isFilled || order.orderStatus.equals("Filled", ignoreCase = true) || order.filledQtyValue > 0.0 -> {
                    log(LogLevel.WARN, callerTag, "Rollback sırasındaki $side emri ($orderId) borsada DOLMUŞ! Dolum kaydı işleniyor.")
                    val p = order.avgPriceValue.takeIf { it > 0.0 } ?: order.priceValue
                    val q = order.filledQtyValue.takeIf { it > 0.0 } ?: order.qtyValue
                    recordOrderFilled(
                        orderId = orderId,
                        side = order.side,
                        price = p,
                        qty = q,
                        triggerReason = "RollbackDetectedFill"
                    )
                    return@withContext RollbackResult.Filled(order)
                }
                order.isCancelled || order.orderStatus.equals("Cancelled", ignoreCase = true) || order.orderStatus.equals("Deactivated", ignoreCase = true) -> {
                    log(LogLevel.INFO, callerTag, "Emir ($orderId) borsada zaten iptal edilmiş.")
                    orderDao.deleteOrder(orderId)
                    return@withContext RollbackResult.Cancelled
                }
                order.isActive || order.orderStatus.equals("New", ignoreCase = true) || order.orderStatus.equals("PartiallyFilled", ignoreCase = true) -> {
                    log(LogLevel.ERROR, callerTag, "KRİTİK: Emir ($orderId) borsada HÂLÂ AÇIK (${order.orderStatus})! Silinmedi, aktif tutuluyor.")
                    return@withContext RollbackResult.StillOpen(order)
                }
                else -> {
                    log(LogLevel.ERROR, callerTag, "Emir ($orderId) durumu belirsiz: ${order.orderStatus}. Güvenlik için aktif tutuluyor.")
                    return@withContext RollbackResult.StillOpen(order)
                }
            }
        } else {
            val err = histRes.exceptionOrNull() ?: Exception("Borsadan emir durumu doğrulanamadı")
            log(LogLevel.ERROR, callerTag, "Borsa ile iletişim kurulamadı ($orderId). Emir güvenliği için aktif sipariş silinmedi: ${err.message}")
            return@withContext RollbackResult.Unknown(err)
        }
    }

    suspend fun findOrderByLinkId(
        orderLinkId: String,
        category: String = "spot",
        symbol: String = preferences.bybitSymbol,
        apiKey: String = preferences.apiKey,
        apiSecret: String = preferences.apiSecret,
        isTestnet: Boolean = preferences.isTestnet
    ): BybitOrderDto? = withContext(Dispatchers.IO) {
        if (orderLinkId.isBlank() || apiKey.isBlank() || apiSecret.isBlank()) return@withContext null
        try {
            val api = createApiService(isTestnet)
            // 1. Check open orders first
            val qOpen = "category=$category&symbol=$symbol&orderLinkId=$orderLinkId"
            val hOpen = createAuthHeaders(apiKey, apiSecret, qOpen)
            val openRes = api.getOpenOrders(hOpen, category, symbol, null, orderLinkId)
            if (openRes.isSuccessful && openRes.body()?.isSuccess == true) {
                val found = openRes.body()?.result?.list?.firstOrNull()
                if (found != null) return@withContext found
            }
            // 2. Check order history
            val qHist = "category=$category&symbol=$symbol&orderLinkId=$orderLinkId"
            val hHist = createAuthHeaders(apiKey, apiSecret, qHist)
            val histRes = api.getOrderHistory(hHist, category, symbol, null, orderLinkId)
            if (histRes.isSuccessful && histRes.body()?.isSuccess == true) {
                return@withContext histRes.body()?.result?.list?.firstOrNull()
            }
            null
        } catch (e: Exception) {
            Log.w("BybitRepository", "findOrderByLinkId hatası: ${e.message}")
            null
        }
    }

    suspend fun cancelAllBaseOrders(
        apiKey: String = preferences.apiKey,
        apiSecret: String = preferences.apiSecret,
        isTestnet: Boolean = preferences.isTestnet
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (apiKey.isBlank() || apiSecret.isBlank()) {
                return@withContext Result.failure(Exception("API Key eksik"))
            }

            val jsonObject = JSONObject().apply {
                put("category", "spot")
                put("symbol", preferences.bybitSymbol)
            }
            val jsonString = jsonObject.toString()
            val headers = createAuthHeaders(apiKey, apiSecret, jsonString)
            val requestBody = jsonString.toRequestBody("application/json; charset=utf-8".toMediaType())

            val api = createApiService(isTestnet)
            val response = api.cancelAllOrders(headers, requestBody)
            if (response.isSuccessful && response.body()?.isSuccess == true) {
                orderDao.deleteUnfilledOrders()
                preferences.activeBuyOrderId = ""
                preferences.activeSellOrderId = ""
                log(LogLevel.INFO, "CancelAll", "Tüm açık symbol emirleri iptal edildi ve geçmişten temizlendi")
                Result.success(true)
            } else {
                val code = response.body()?.retCode ?: -1
                val msg = response.body()?.retMsg ?: "Tüm emirler iptal edilemedi"
                if (code == 170213 || code == 170170 || code == 110001) {
                    orderDao.deleteUnfilledOrders()
                    preferences.activeBuyOrderId = ""
                    preferences.activeSellOrderId = ""
                    Result.success(true)
                } else {
                    Result.failure(Exception(parseBybitErrorMessage(code, msg)))
                }
            }
        } catch (e: Exception) {
            log(LogLevel.ERROR, "CancelAll", "Toplu iptal hatası: ${e.localizedMessage}")
            Result.failure(e)
        }
    }

    suspend fun getOrderHistory(
        orderId: String? = null,
        orderLinkId: String? = null,
        category: String = "spot",
        symbol: String = preferences.bybitSymbol,
        apiKey: String = preferences.apiKey,
        apiSecret: String = preferences.apiSecret,
        isTestnet: Boolean = preferences.isTestnet
    ): Result<BybitOrderDto?> = withContext(Dispatchers.IO) {
        try {
            if (apiKey.isBlank() || apiSecret.isBlank()) {
                return@withContext Result.failure(Exception("API Key eksik"))
            }

            val api = createApiService(isTestnet)
            val queryParts = mutableListOf("category=$category", "symbol=$symbol")
            if (!orderId.isNullOrBlank()) queryParts.add("orderId=$orderId")
            if (!orderLinkId.isNullOrBlank()) queryParts.add("orderLinkId=$orderLinkId")
            val queryString = queryParts.joinToString("&")
            val headers = createAuthHeaders(apiKey, apiSecret, queryString)

            val response = api.getOrderHistory(headers, category, symbol, orderId, orderLinkId)
            if (response.isSuccessful && response.body()?.isSuccess == true) {
                val order = response.body()?.result?.list?.firstOrNull()
                Result.success(order)
            } else {
                val code = response.body()?.retCode ?: -1
                val msg = response.body()?.retMsg ?: "Geçmiş çekilemedi"
                Result.failure(Exception(parseBybitErrorMessage(code, msg)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getOpenOrders(
        apiKey: String = preferences.apiKey,
        apiSecret: String = preferences.apiSecret,
        isTestnet: Boolean = preferences.isTestnet
    ): Result<List<BybitOrderDto>> = withContext(Dispatchers.IO) {
        try {
            if (apiKey.isBlank() || apiSecret.isBlank()) {
                return@withContext Result.failure(Exception("API Key eksik"))
            }

            val api = createApiService(isTestnet)
            val queryString = "category=spot&symbol=${preferences.bybitSymbol}"
            val headers = createAuthHeaders(apiKey, apiSecret, queryString)

            val response = api.getOpenOrders(headers, "spot", preferences.bybitSymbol)
            if (response.isSuccessful && response.body()?.isSuccess == true) {
                Result.success(response.body()?.result?.list ?: emptyList())
            } else {
                val code = response.body()?.retCode ?: -1
                val msg = response.body()?.retMsg ?: "Açık emirler çekilemedi"
                Result.failure(Exception(parseBybitErrorMessage(code, msg)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getRecentOrdersList(
        category: String = "spot",
        symbol: String = preferences.bybitSymbol,
        limit: Int = 20,
        apiKey: String = preferences.apiKey,
        apiSecret: String = preferences.apiSecret,
        isTestnet: Boolean = preferences.isTestnet
    ): Result<List<BybitOrderDto>> = withContext(Dispatchers.IO) {
        try {
            if (apiKey.isBlank() || apiSecret.isBlank()) {
                return@withContext Result.failure(Exception("API Key eksik"))
            }

            val api = createApiService(isTestnet)
            val queryString = buildOrderHistoryQueryString(
                category = category,
                symbol = symbol,
                limit = limit
            )
            val headers = createAuthHeaders(apiKey, apiSecret, queryString)

            val response = api.getOrderHistoryList(headers, category, symbol, null, null, limit, null)
            if (response.isSuccessful && response.body()?.isSuccess == true) {
                Result.success(response.body()?.result?.list ?: emptyList())
            } else {
                val code = response.body()?.retCode ?: -1
                val msg = response.body()?.retMsg ?: "Sipariş geçmişi alınamadı"
                Result.failure(Exception(parseBybitErrorMessage(code, msg)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getRecentExecutionsList(
        category: String = "spot",
        symbol: String = preferences.bybitSymbol,
        limit: Int = 20,
        apiKey: String = preferences.apiKey,
        apiSecret: String = preferences.apiSecret,
        isTestnet: Boolean = preferences.isTestnet
    ): Result<List<BybitExecutionDto>> = withContext(Dispatchers.IO) {
        try {
            if (apiKey.isBlank() || apiSecret.isBlank()) {
                return@withContext Result.failure(Exception("API Key eksik"))
            }

            val api = createApiService(isTestnet)
            val queryString = buildExecutionQueryString(
                category = category,
                symbol = symbol,
                limit = limit
            )
            val headers = createAuthHeaders(apiKey, apiSecret, queryString)

            val response = api.getExecutionList(headers, category, symbol, null, null, limit, null)
            if (response.isSuccessful && response.body()?.isSuccess == true) {
                Result.success(response.body()?.result?.list ?: emptyList())
            } else {
                val code = response.body()?.retCode ?: -1
                val msg = response.body()?.retMsg ?: "İşlem geçmişi alınamadı"
                Result.failure(Exception(parseBybitErrorMessage(code, msg)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Actively reconciles the exchange state against the local grid state.
     * Detects filled orders (even if missed by WebSockets or background killing),
     * cancels stale counter-orders, updates the Room database, adjusts the base price,
     * and places new symmetric grid orders.
     */
    suspend fun reconcileGridOrders(
        callerTag: String = "Reconcile",
        triggeringFilledOrder: BybitOrderDto? = null
    ): Result<ReconciliationResult> = withContext(Dispatchers.IO) {
        if (!preferences.isConfigured || !preferences.isBotActive) {
            return@withContext Result.success(ReconciliationResult(message = "Bot aktif değil"))
        }

        // Concurrency Guard: Prevent overlapping executions from Watchdog, LiveSync, and Manual triggers
        if (!reconcileMutex.tryLock()) {
            return@withContext Result.success(ReconciliationResult(message = "Başka bir mutabakat devam ediyor"))
        }

        try {
            val apiKey = preferences.apiKey
            val apiSecret = preferences.apiSecret
            val isTestnet = preferences.isTestnet
            if (apiKey.isBlank() || apiSecret.isBlank()) {
                return@withContext Result.failure(Exception("API Key eksik"))
            }

            // 1. Sync server time only if not synced recently
            if (serverTimeOffsetMs == 0L) {
                syncServerTime(isTestnet)
            }

            // 2. Fetch current Open Orders from Bybit
            val openOrdersRes = getOpenOrders()
            if (openOrdersRes.isFailure) {
                val err = openOrdersRes.exceptionOrNull()?.message ?: "Açık emirler alınamadı"
                log(LogLevel.WARN, callerTag, "Açık emir kontrolü başarısız: $err")
                return@withContext Result.failure(Exception(err))
            }

            val openOrders = openOrdersRes.getOrDefault(emptyList())
            val openBuyOrder = openOrders.firstOrNull { it.side.equals("Buy", ignoreCase = true) }
            val openSellOrder = openOrders.firstOrNull { it.side.equals("Sell", ignoreCase = true) }

            val activeBuyId = preferences.activeBuyOrderId
            val activeSellId = preferences.activeSellOrderId

            // CASE 1: Both Buy and Sell orders are open on Bybit
            if (openBuyOrder != null && openSellOrder != null) {
                // Ensure tracked IDs match
                if (activeBuyId != openBuyOrder.orderId) preferences.activeBuyOrderId = openBuyOrder.orderId
                if (activeSellId != openSellOrder.orderId) preferences.activeSellOrderId = openSellOrder.orderId

                return@withContext Result.success(
                    ReconciliationResult(
                        executedOrderFound = false,
                        newBasePrice = preferences.lastRebalancePrice,
                        message = "Her iki ızgara emri borsada aktif (Alış: ${openBuyOrder.price}, Satış: ${openSellOrder.price})"
                    )
                )
            }

            // Only fetch recent history when at least one grid order has executed or disappeared
            val recentOrdersRes = getRecentOrdersList(limit = 15)
            val recentOrders = recentOrdersRes.getOrDefault(emptyList())
            val recentExecutionsRes = getRecentExecutionsList(limit = 15)
            val recentExecutions = recentExecutionsRes.getOrDefault(emptyList())

            // CASE 2: Exactly ONE order is open on Bybit (The classic fill case: Sell filled while Buy remained, or vice versa)
            if ((openBuyOrder != null && openSellOrder == null) || (openBuyOrder == null && openSellOrder != null)) {
                val missingSide = if (openBuyOrder == null) "Buy" else "Sell"
                val remainingOrder = openBuyOrder ?: openSellOrder!!
                val missingOrderId = if (triggeringFilledOrder != null && triggeringFilledOrder.orderId.isNotBlank()) {
                    triggeringFilledOrder.orderId
                } else if (missingSide.equals("Sell", ignoreCase = true)) {
                    activeSellId
                } else {
                    activeBuyId
                }

                // Prioritize matching the exact tracked missingOrderId first or use direct triggering event
                val exactTrackedOrder = if (triggeringFilledOrder != null) {
                    triggeringFilledOrder
                } else if (missingOrderId.isNotBlank()) {
                    recentOrders.firstOrNull { it.orderId == missingOrderId }
                } else null

                // State Machine Check: If the missing order is STILL LIVE and only partially filled,
                // do NOT reconcile and do NOT cancel the opposite order! Let it continue executing.
                if (exactTrackedOrder != null && exactTrackedOrder.isPartiallyFilledAndLive) {
                    log(
                        LogLevel.INFO,
                        callerTag,
                        "Mutabakat bekletildi: $missingSide emri (${exactTrackedOrder.orderId}) borsada kısmi doldu (${exactTrackedOrder.cumExecQty}/${exactTrackedOrder.qty}) ancak halen açık/canlı. Tam dolum veya sonlanma bekleniyor."
                    )
                    return@withContext Result.success(
                        ReconciliationResult(
                            executedOrderFound = false,
                            newBasePrice = preferences.lastRebalancePrice,
                            message = "$missingSide emri kısmi doldu ve halen aktif, bekleniyor."
                        )
                    )
                }

                val isCancelledWithoutFill = exactTrackedOrder != null && exactTrackedOrder.isCancelledWithoutFill

                // Find the filled order details if genuinely filled or terminal partial fill (cancelled after partial fill).
                // CRITICAL SAFETY SHIELD 1: Never match an order that is already marked as FILLED in Room DB!
                // CRITICAL SAFETY SHIELD 2: Reject orders older than last grid placement time or older than 5 minutes.
                val minAllowedCreatedTime = maxOf(lastGridOrderPlacedTimeMs - 10_000L, System.currentTimeMillis() - 300_000L)
                val filledOrder = if (exactTrackedOrder != null && exactTrackedOrder.isTerminalFilled) {
                    val isAlreadyProcessed = orderDao.getOrderByOrderId(exactTrackedOrder.orderId)?.status.equals("Filled", ignoreCase = true)
                    if (isAlreadyProcessed && triggeringFilledOrder == null) null else exactTrackedOrder
                } else {
                    recentOrders.firstOrNull { order ->
                        order.side.equals(missingSide, ignoreCase = true) &&
                        order.isTerminalFilled &&
                        order.orderId != remainingOrder.orderId &&
                        (order.createdTime.toLongOrNull() ?: 0L) >= minAllowedCreatedTime &&
                        !orderDao.getOrderByOrderId(order.orderId)?.status.equals("Filled", ignoreCase = true)
                    }
                }

                val filledExec = if (exactTrackedOrder != null) {
                    null
                } else (if (missingOrderId.isNotBlank()) {
                    recentExecutions.firstOrNull { it.orderId == missingOrderId }
                } else {
                    null
                }) ?: recentExecutions.firstOrNull { exec ->
                    exec.side.equals(missingSide, ignoreCase = true) &&
                    exec.orderId != remainingOrder.orderId &&
                    (exec.execTime.toLongOrNull() ?: 0L) >= minAllowedCreatedTime
                }

                val lastBase = preferences.lastRebalancePrice
                val step = preferences.stepPercent
                val currentTickerPrice = getTicker().getOrNull()?.currentPrice ?: 0.0

                // CRITICAL SAFETY SHIELD: Positive Proof of Fill or Explicit Cancellation
                var confirmedBybitOrder: BybitOrderDto? = filledOrder
                var confirmedBybitExec: BybitExecutionDto? = filledExec
                var isExplicitlyCancelled = isCancelledWithoutFill

                if (confirmedBybitOrder == null && confirmedBybitExec == null && !isExplicitlyCancelled && missingOrderId.isNotBlank()) {
                    try {
                        val histRes = getOrderHistory(orderId = missingOrderId)
                        if (histRes.isSuccess) {
                            val histOrder = histRes.getOrNull()
                            if (histOrder != null) {
                                if (histOrder.isPartiallyFilledAndLive) {
                                    log(
                                        LogLevel.INFO,
                                        callerTag,
                                        "Mutabakat bekletildi: $missingSide geçmiş emri (${histOrder.orderId}) borsada kısmi doldu ancak açık/canlı. Bekleniyor."
                                    )
                                    return@withContext Result.success(
                                        ReconciliationResult(
                                            executedOrderFound = false,
                                            newBasePrice = preferences.lastRebalancePrice,
                                            message = "$missingSide emri kısmi doldu ve halen aktif, bekleniyor."
                                        )
                                    )
                                } else if (histOrder.isTerminalFilled) {
                                    confirmedBybitOrder = histOrder
                                } else if (histOrder.isCancelledWithoutFill) {
                                    isExplicitlyCancelled = true
                                }
                            }
                        }
                    } catch (e: Exception) {
                        log(LogLevel.WARN, callerTag, "Bybit Emir geçmişi sorgulama uyarısı: ${e.message}")
                    }
                }

                // If neither positive fill nor explicit cancellation is proven: SAFE WAIT!
                if (confirmedBybitOrder == null && confirmedBybitExec == null && !isExplicitlyCancelled) {
                    log(
                        LogLevel.WARN,
                        callerTag,
                        "Mutabakat: $missingSide emri ($missingOrderId) açık değil ancak borsada henüz dolum veya iptal kanıtı bulunamadı. Sahte işlem açılmaması için bekleniyor."
                    )
                    return@withContext Result.failure(Exception("Bybit $missingSide emri için borsadan kesin dolum veya iptal kanıtı henüz alınamadı (işlem beklemede)."))
                }

                if (isExplicitlyCancelled) {
                    log(
                        LogLevel.WARN,
                        callerTag,
                        "Mutabakat: $missingSide emri borsada iptal edilmiş (Gerçekleşme yok). Karşı açık emir temizlenip yeni ızgara açılıyor..."
                    )
                } else {
                    log(LogLevel.INFO, callerTag, "Izgarada tek taraf açık (${remainingOrder.side} ${remainingOrder.orderId}). Karşı taraf ($missingSide) doğrulanmış dolum olarak tespit edildi!")
                }

                // Calculate the theoretical grid execution price based on the previous base price and step ratio
                val expectedGridPrice = if (missingSide.equals("Sell", ignoreCase = true)) {
                    if (lastBase > 0.0) lastBase * (1.0 + step / 100.0) else 0.0
                } else {
                    if (lastBase > 0.0) lastBase * (1.0 - step / 100.0) else 0.0
                }

                // CRITICAL SAFETY SHIELD 3: Geometric Boundary Check
                // A genuine fill price must be within ±2x of step percent from expectedGridPrice.
                // If an ancient or erroneous order price leaks in, fallback to expectedGridPrice!
                val rawExecPrice = confirmedBybitOrder?.avgPriceValue?.takeIf { it > 0.0 }
                    ?: confirmedBybitExec?.priceValue?.takeIf { it > 0.0 }
                    ?: expectedGridPrice

                val isPriceWithinGridBounds = if (expectedGridPrice > 0.0 && rawExecPrice > 0.0) {
                    val maxAllowedDeviation = (step / 100.0) * 2.0
                    val deviation = Math.abs(rawExecPrice - expectedGridPrice) / expectedGridPrice
                    deviation <= maxAllowedDeviation
                } else true

                val safeExecPrice = if (isPriceWithinGridBounds && rawExecPrice > 0.0) {
                    rawExecPrice
                } else {
                    log(LogLevel.WARN, callerTag, "Fiyat sapması engellendi (Tespit: $rawExecPrice, Beklenen: $expectedGridPrice). Güvenli ızgara fiyatı kullanılıyor.")
                    if (expectedGridPrice > 0.0) expectedGridPrice else lastBase
                }

                val finalExecPrice = if (!isExplicitlyCancelled && safeExecPrice > 0.0) {
                    safeExecPrice
                } else {
                    if (lastBase > 0.0) lastBase else if (currentTickerPrice > 0.0) currentTickerPrice else 0.5
                }

                val verifiedExecQty = confirmedBybitOrder?.filledQtyValue?.takeIf { it > 0.0 }
                    ?: confirmedBybitExec?.qtyValue?.takeIf { it > 0.0 }
                    ?: 0.0

                val execOrderId = confirmedBybitOrder?.orderId
                    ?: confirmedBybitExec?.orderId
                    ?: missingOrderId.ifBlank { "exec_${System.currentTimeMillis()}" }

                if (!isExplicitlyCancelled) {
                    if (verifiedExecQty <= 0.0) {
                        log(LogLevel.ERROR, callerTag, "Mutabakat: $missingSide emri için borsa dolum miktarı 0 veya geçersiz ($verifiedExecQty). Yeni ızgara açılması durduruldu.")
                        return@withContext Result.failure(Exception("Bybit Geçersiz dolum miktarı ($verifiedExecQty)"))
                    }
                    log(
                        LogLevel.SUCCESS,
                        callerTag,
                        "Mutabakat: $missingSide emri GERÇEKLEŞMİŞ! Fiyat: $finalExecPrice, Miktar: $verifiedExecQty ($execOrderId)"
                    )
                }

                // 1. Cancel opposite remaining order
                log(LogLevel.INFO, callerTag, "Karşı açık emir (${remainingOrder.side} ${remainingOrder.orderId}) iptal ediliyor...")
                val cancelRes = cancelOrder(remainingOrder.orderId)
                if (cancelRes.isFailure) {
                    val cancelErr = cancelRes.exceptionOrNull()?.message ?: "İptal başarısız"
                    log(LogLevel.ERROR, callerTag, "Karşı açık emir (${remainingOrder.orderId}) iptal EDİLEMEDİ: $cancelErr. Çifte emir yığılmasını önlemek için yeni ızgara açılması durduruldu.")
                    return@withContext Result.failure(Exception("Karşı emir (${remainingOrder.orderId}) iptal edilemediği için yeni ızgara açılamaz: $cancelErr"))
                }
                orderDao.deleteOrder(remainingOrder.orderId)

                // 2. Clear old active IDs
                preferences.activeBuyOrderId = ""
                preferences.activeSellOrderId = ""

                // 3. Record filled trade in Room database ONLY if genuinely filled with positive qty
                if (!isExplicitlyCancelled && verifiedExecQty > 0.0) {
                    val fillTime = confirmedBybitOrder?.updatedTimeMillis?.takeIf { it > 0L }
                        ?: confirmedBybitExec?.timeMillis?.takeIf { it > 0L }
                        ?: System.currentTimeMillis()
                    recordOrderFilled(
                        orderId = execOrderId,
                        side = missingSide,
                        price = finalExecPrice,
                        qty = verifiedExecQty,
                        triggerReason = if (missingSide.equals("Buy", ignoreCase = true)) "GridStepDownBuy" else "GridStepUpSell",
                        fillTime = fillTime
                    )
                }

                // 4. Update anchor base price
                preferences.lastRebalancePrice = finalExecPrice

                // 5. Allow Bybit account balances to settle
                delay(1000)

                // 6. Fetch fresh balances
                var usdt = 0.0
                var mnt = 0.0
                val balanceRes = getWalletBalance()
                balanceRes.onSuccess { map ->
                    usdt = map["USDT"]?.quantity ?: 0.0
                    mnt = map["${preferences.bybitBaseCoin}"]?.quantity ?: 0.0
                }

                if (usdt > 0.0 && mnt > 0.0 && finalExecPrice > 0.0) {
                    val info = getInstrumentInfo(preferences.bybitSymbol)
                    val (qtyPrec, pricePrec) = getPrecisionForSymbol(preferences.bybitSymbol)
                    val minAmt = info?.lotSizeFilter?.minOrderAmt?.toDoubleOrNull() ?: 5.0
                    val minQty = info?.lotSizeFilter?.minOrderQty?.toDoubleOrNull()
                    val maxQty = info?.lotSizeFilter?.maxOrderQty?.toDoubleOrNull()
                    val plan = RebalanceEngine.calculateGridOrders(
                        usdtBalance = usdt,
                        baseCoinBalance = mnt,
                        basePrice = finalExecPrice,
                        stepPercent = step,
                        qtyPrecision = qtyPrec,
                        pricePrecision = pricePrec,
                        tickSize = info?.priceFilter?.tickSize,
                        lotStep = info?.lotSizeFilter?.basePrecision,
                        minOrderAmt = minAmt,
                        minOrderQty = minQty,
                        maxOrderQty = maxQty
                    )

                    if (plan.isValid) {
                        val cycleNow = System.currentTimeMillis()
                        // Place new Limit Sell
                        val sellRes = createOrder(
                            side = "Sell",
                            orderType = "Limit",
                            qty = plan.sellBaseQty,
                            price = plan.sellLimitPrice,
                            triggerReason = "GridStepUpSell",
                            customOrderLinkId = "grid_${cycleNow}_s"
                        )

                        // Place new Limit Buy
                        val buyRes = createOrder(
                            side = "Buy",
                            orderType = "Limit",
                            qty = plan.buyBaseQty,
                            price = plan.buyLimitPrice,
                            triggerReason = "GridStepDownBuy",
                            customOrderLinkId = "grid_${cycleNow}_b"
                        )

                        if (sellRes.isSuccess && buyRes.isSuccess) {
                            val sid = sellRes.getOrNull().orEmpty()
                            val bid = buyRes.getOrNull().orEmpty()
                            preferences.activeSellOrderId = sid
                            preferences.activeBuyOrderId = bid
                            lastGridOrderPlacedTimeMs = System.currentTimeMillis()

                            log(LogLevel.INFO, callerTag, "Yeni Satış Limit Emri: $sid @ ${plan.sellLimitPrice}")
                            log(LogLevel.INFO, callerTag, "Yeni Alış Limit Emri: $bid @ ${plan.buyLimitPrice}")
                            log(
                                LogLevel.SUCCESS,
                                callerTag,
                                "Yeni ızgara limit emirleri açıldı. Yeni Baz: $${RebalanceEngine.format4(finalExecPrice)}"
                            )

                            return@withContext Result.success(
                                ReconciliationResult(
                                    executedOrderFound = !isExplicitlyCancelled,
                                    executedSide = if (!isExplicitlyCancelled) missingSide else null,
                                    executedPrice = finalExecPrice,
                                    executedQty = verifiedExecQty,
                                    executedOrderId = if (!isExplicitlyCancelled) execOrderId else "",
                                    newBasePrice = finalExecPrice,
                                    message = if (!isExplicitlyCancelled) "$missingSide emri gerçekleşti! Karşı emir iptal edilip yeni ızgara kuruldu."
                                              else "İptal edilen emir sonrası yeni ızgara kuruldu."
                                )
                            )
                        } else {
                            // ASYMMETRIC FAILURE GUARD / SAFE ROLLBACK:
                            // Never leave a single open order stranded, otherwise next cycle assumes the other side was filled!
                            val sid = sellRes.getOrNull().orEmpty()
                            val bid = buyRes.getOrNull().orEmpty()
                            if (sellRes.isSuccess && sid.isNotBlank()) {
                                log(LogLevel.ERROR, callerTag, "Alış emri açılamadı (${buyRes.exceptionOrNull()?.message}). Açılan satış emri ($sid) güvenli geri alınıyor...")
                                val rb = safeRollbackOrder(sid, "Sell", callerTag)
                                when (rb) {
                                    is RollbackResult.Cancelled -> {
                                        preferences.activeSellOrderId = ""
                                    }
                                    is RollbackResult.Filled -> {
                                        preferences.activeSellOrderId = ""
                                        preferences.lastRebalancePrice = rb.order.avgPriceValue.takeIf { it > 0.0 } ?: rb.order.priceValue
                                        log(LogLevel.INFO, callerTag, "Satış emri rollback anında dolduğu için baz fiyat güncellendi: ${preferences.lastRebalancePrice}")
                                    }
                                    is RollbackResult.StillOpen, is RollbackResult.Unknown -> {
                                        log(LogLevel.ERROR, callerTag, "KRİTİK: Satış emri ($sid) iptal edilemedi veya açık kaldı. Hayalet emir oluşmaması için aktif olarak izleniyor.")
                                    }
                                }
                            }
                            if (buyRes.isSuccess && bid.isNotBlank()) {
                                log(LogLevel.ERROR, callerTag, "Satış emri açılamadı (${sellRes.exceptionOrNull()?.message}). Açılan alış emri ($bid) güvenli geri alınıyor...")
                                val rb = safeRollbackOrder(bid, "Buy", callerTag)
                                when (rb) {
                                    is RollbackResult.Cancelled -> {
                                        preferences.activeBuyOrderId = ""
                                    }
                                    is RollbackResult.Filled -> {
                                        preferences.activeBuyOrderId = ""
                                        preferences.lastRebalancePrice = rb.order.avgPriceValue.takeIf { it > 0.0 } ?: rb.order.priceValue
                                        log(LogLevel.INFO, callerTag, "Alış emri rollback anında dolduğu için baz fiyat güncellendi: ${preferences.lastRebalancePrice}")
                                    }
                                    is RollbackResult.StillOpen, is RollbackResult.Unknown -> {
                                        log(LogLevel.ERROR, callerTag, "KRİTİK: Alış emri ($bid) iptal edilemedi veya açık kaldı. Hayalet emir oluşmaması için aktif olarak izleniyor.")
                                    }
                                }
                            }
                            val errMsg = "Izgara tam açılamadı. Satış: ${sellRes.exceptionOrNull()?.message ?: "OK"}, Alış: ${buyRes.exceptionOrNull()?.message ?: "OK"}"
                            log(LogLevel.ERROR, callerTag, errMsg)
                            return@withContext Result.failure(Exception(errMsg))
                        }
                    }
                }

                return@withContext Result.success(
                    ReconciliationResult(
                        executedOrderFound = !isExplicitlyCancelled,
                        executedSide = if (!isExplicitlyCancelled) missingSide else null,
                        executedPrice = finalExecPrice,
                        executedQty = verifiedExecQty,
                        executedOrderId = if (!isExplicitlyCancelled) execOrderId else "",
                        newBasePrice = finalExecPrice,
                        message = if (!isExplicitlyCancelled) "$missingSide emri gerçekleşti." else "İptal tespit edildi."
                    )
                )
            }

            // CASE 3: ZERO orders are open on Bybit (Both are gone, but bot is active)
            if (openOrders.isEmpty()) {
                val timeSinceLastPlacement = System.currentTimeMillis() - lastGridOrderPlacedTimeMs
                if (timeSinceLastPlacement < 1000L) {
                    log(LogLevel.INFO, callerTag, "Emirler yeni iletildi (${timeSinceLastPlacement}ms önce), Bybit borsa mutabakatı bekleniyor...")
                    return@withContext Result.success(ReconciliationResult(message = "Emir iletimi onay bekleniyor"))
                }

                log(LogLevel.INFO, callerTag, "Borsada hiç açık emir bulunamadı. Durum kontrol ediliyor...")

                // Check if all recent orders were cancelled on exchange rather than filled
                val isCancelledOnly = recentOrders.isNotEmpty() &&
                    recentOrders.take(2).all {
                        (it.orderStatus.equals("Cancelled", ignoreCase = true) || it.orderStatus.equals("Deactivated", ignoreCase = true)) &&
                        it.filledQtyValue == 0.0
                    }

                val minAllowedCreatedTime = maxOf(lastGridOrderPlacedTimeMs - 10_000L, System.currentTimeMillis() - 300_000L)
                val latestFilled = if (isCancelledOnly) null else recentOrders.firstOrNull { order ->
                    order.isTerminalFilled &&
                    (order.createdTime.toLongOrNull() ?: 0L) >= minAllowedCreatedTime &&
                    !orderDao.getOrderByOrderId(order.orderId)?.status.equals("Filled", ignoreCase = true)
                }
                val latestExec = if (isCancelledOnly) null else recentExecutions.firstOrNull { exec ->
                    (exec.execTime.toLongOrNull() ?: 0L) >= minAllowedCreatedTime
                }

                val tickerPrice = getTicker().getOrNull()?.currentPrice ?: 0.0

                // Use stored base price first to prevent overwriting user-configured base prices
                val basePrice = if (preferences.lastRebalancePrice > 0.0) {
                    preferences.lastRebalancePrice
                } else if (latestFilled != null && latestFilled.avgPriceValue > 0.0) {
                    latestFilled.avgPriceValue
                } else if (tickerPrice > 0.0) {
                    tickerPrice
                } else {
                    0.5
                }

                val latestSide = latestFilled?.side ?: latestExec?.side ?: ""
                val latestQty = latestFilled?.filledQtyValue ?: latestExec?.qtyValue ?: 0.0
                val latestId = latestFilled?.orderId ?: latestExec?.orderId ?: ""

                if (!isCancelledOnly && latestId.isNotBlank() && (latestFilled?.avgPriceValue ?: 0.0) > 0.0) {
                    recordOrderFilled(
                        orderId = latestId,
                        side = latestSide,
                        price = latestFilled!!.avgPriceValue,
                        qty = latestQty,
                        triggerReason = "RecentFilledRecovery"
                    )
                } else if (isCancelledOnly) {
                    log(LogLevel.INFO, callerTag, "Önceki emirler borsada iptal edilmiş. Baz fiyattan ($$basePrice) yeni ızgara kuruluyor...")
                }

                if (preferences.lastRebalancePrice <= 0.0 && basePrice > 0.0) {
                    preferences.lastRebalancePrice = basePrice
                }
                preferences.activeBuyOrderId = ""
                preferences.activeSellOrderId = ""

                delay(1000)
                var usdt = 0.0
                var mnt = 0.0
                getWalletBalance().onSuccess { map ->
                    usdt = map["USDT"]?.quantity ?: 0.0
                    mnt = map["${preferences.bybitBaseCoin}"]?.quantity ?: 0.0
                }

                if (usdt > 0.0 && mnt > 0.0) {
                    val info = getInstrumentInfo(preferences.bybitSymbol)
                    val (qtyPrec, pricePrec) = getPrecisionForSymbol(preferences.bybitSymbol)
                    val minAmt = info?.lotSizeFilter?.minOrderAmt?.toDoubleOrNull() ?: 5.0
                    val minQty = info?.lotSizeFilter?.minOrderQty?.toDoubleOrNull()
                    val maxQty = info?.lotSizeFilter?.maxOrderQty?.toDoubleOrNull()
                    val plan = RebalanceEngine.calculateGridOrders(
                            usdtBalance = usdt,
                            baseCoinBalance = mnt,
                            basePrice = basePrice,
                            stepPercent = preferences.stepPercent,
                            qtyPrecision = qtyPrec,
                            pricePrecision = pricePrec,
                            tickSize = info?.priceFilter?.tickSize,
                            lotStep = info?.lotSizeFilter?.basePrecision,
                            minOrderAmt = minAmt,
                            minOrderQty = minQty,
                            maxOrderQty = maxQty
                        )
                        if (plan.isValid) {
                            val cycleNow = System.currentTimeMillis()
                            val sellRes = createOrder("Sell", "Limit", plan.sellBaseQty, plan.sellLimitPrice, "GridStepUpSell", customOrderLinkId = "grid_${cycleNow}_s")
                            val buyRes = createOrder("Buy", "Limit", plan.buyBaseQty, plan.buyLimitPrice, "GridStepDownBuy", customOrderLinkId = "grid_${cycleNow}_b")

                            if (sellRes.isSuccess && buyRes.isSuccess) {
                                val sid = sellRes.getOrNull().orEmpty()
                                val bid = buyRes.getOrNull().orEmpty()
                                preferences.activeSellOrderId = sid
                                preferences.activeBuyOrderId = bid
                                lastGridOrderPlacedTimeMs = System.currentTimeMillis()

                                log(LogLevel.SUCCESS, callerTag, "Açık emir yoktu, yeni ızgara kuruldu (Baz: $${RebalanceEngine.format4(basePrice)})")

                                return@withContext Result.success(
                                    ReconciliationResult(
                                        executedOrderFound = (latestFilled != null),
                                        executedSide = latestSide.ifBlank { null },
                                        executedPrice = basePrice,
                                        executedQty = latestQty,
                                        executedOrderId = latestId,
                                        newBasePrice = basePrice,
                                        message = if (latestFilled != null) "Açık emirler tamamlanmıştı, yeni ızgara açıldı."
                                                  else "Yeni ızgara açıldı."
                                    )
                                )
                            } else {
                                // ASYMMETRIC FAILURE GUARD / SAFE ROLLBACK
                                val sid = sellRes.getOrNull().orEmpty()
                                val bid = buyRes.getOrNull().orEmpty()
                                if (sellRes.isSuccess && sid.isNotBlank()) {
                                    log(LogLevel.ERROR, callerTag, "Alış emri açılamadı (${buyRes.exceptionOrNull()?.message}). Açılan satış emri ($sid) güvenli geri alınıyor...")
                                    val rb = safeRollbackOrder(sid, "Sell", callerTag)
                                    when (rb) {
                                        is RollbackResult.Cancelled -> {
                                            preferences.activeSellOrderId = ""
                                        }
                                        is RollbackResult.Filled -> {
                                            preferences.activeSellOrderId = ""
                                            preferences.lastRebalancePrice = rb.order.avgPriceValue.takeIf { it > 0.0 } ?: rb.order.priceValue
                                            log(LogLevel.INFO, callerTag, "Satış emri rollback anında dolduğu için baz fiyat güncellendi: ${preferences.lastRebalancePrice}")
                                        }
                                        is RollbackResult.StillOpen, is RollbackResult.Unknown -> {
                                            log(LogLevel.ERROR, callerTag, "KRİTİK: Satış emri ($sid) iptal edilemedi veya açık kaldı. Hayalet emir oluşmaması için aktif olarak izleniyor.")
                                        }
                                    }
                                }
                                if (buyRes.isSuccess && bid.isNotBlank()) {
                                    log(LogLevel.ERROR, callerTag, "Satış emri açılamadı (${sellRes.exceptionOrNull()?.message}). Açılan alış emri ($bid) güvenli geri alınıyor...")
                                    val rb = safeRollbackOrder(bid, "Buy", callerTag)
                                    when (rb) {
                                        is RollbackResult.Cancelled -> {
                                            preferences.activeBuyOrderId = ""
                                        }
                                        is RollbackResult.Filled -> {
                                            preferences.activeBuyOrderId = ""
                                            preferences.lastRebalancePrice = rb.order.avgPriceValue.takeIf { it > 0.0 } ?: rb.order.priceValue
                                            log(LogLevel.INFO, callerTag, "Alış emri rollback anında dolduğu için baz fiyat güncellendi: ${preferences.lastRebalancePrice}")
                                        }
                                        is RollbackResult.StillOpen, is RollbackResult.Unknown -> {
                                            log(LogLevel.ERROR, callerTag, "KRİTİK: Alış emri ($bid) iptal edilemedi veya açık kaldı. Hayalet emir oluşmaması için aktif olarak izleniyor.")
                                        }
                                    }
                                }
                                val errMsg = "Izgara tam açılamadı. Satış: ${sellRes.exceptionOrNull()?.message ?: "OK"}, Alış: ${buyRes.exceptionOrNull()?.message ?: "OK"}"
                                log(LogLevel.ERROR, callerTag, errMsg)
                                return@withContext Result.failure(Exception(errMsg))
                            }
                        }
                    }
                }

            Result.success(ReconciliationResult(message = "Mutabakat tamamlandı."))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            log(LogLevel.ERROR, callerTag, "Mutabakat hatası: ${e.message}")
            Result.failure(e)
        } finally {
            reconcileMutex.unlock()
        }
    }

    suspend fun getLastRealFilledTradeInfo(): LastFilledTradeInfo? = withContext(Dispatchers.IO) {
        try {
            val candidates = mutableListOf<LastFilledTradeInfo>()

            // 1. Direct query to Bybit Executions (v5/execution/list) - Highest fidelity real trade fills
            val execsRes = getRecentExecutionsList(limit = 10)
            val exec = execsRes.getOrNull()?.firstOrNull { it.priceValue > 0.0 }
            if (exec != null) {
                candidates.add(
                    LastFilledTradeInfo(
                        price = exec.priceValue,
                        side = exec.side,
                        qty = exec.qtyValue,
                        timestamp = exec.timeMillis,
                        source = "Bybit Borsa İşlemi"
                    )
                )
            }

            // 2. Direct query to Bybit Order History (v5/order/history)
            val ordersRes = getRecentOrdersList(limit = 10)
            val filledOrder = ordersRes.getOrNull()?.firstOrNull {
                (it.isFilled || it.filledQtyValue > 0.0) && it.avgPriceValue > 0.0
            }
            if (filledOrder != null) {
                val orderTime = filledOrder.updatedTime.toLongOrNull()
                    ?: filledOrder.createdTime.toLongOrNull()
                    ?: 0L
                candidates.add(
                    LastFilledTradeInfo(
                        price = filledOrder.avgPriceValue,
                        side = filledOrder.side,
                        qty = filledOrder.filledQtyValue,
                        timestamp = orderTime,
                        source = "Bybit Sipariş Geçmişi"
                    )
                )
            }

            // 3. Local Room Database
            val dbOrder = orderDao.getLastFilledOrder()
            if (dbOrder != null && dbOrder.avgPrice > 0.0) {
                candidates.add(
                    LastFilledTradeInfo(
                        price = dbOrder.avgPrice,
                        side = dbOrder.side,
                        qty = dbOrder.filledQty,
                        timestamp = dbOrder.timestamp,
                        source = "Kayıtlı İşlem Geçmişi"
                    )
                )
            }

            // Return the most recent genuine trade fill
            candidates.maxByOrNull { it.timestamp }
        } catch (e: Exception) {
            log(LogLevel.WARN, "BasePrice", "Son işlem sorgulama hatası: ${e.message}")
            null
        }
    }

    suspend fun getLastRealFilledPrice(): Double? = withContext(Dispatchers.IO) {
        getLastRealFilledTradeInfo()?.price
    }

    suspend fun applyCustomBasePrice(newPrice: Double): Result<Unit> = withContext(Dispatchers.IO) {
        if (newPrice <= 0.0) return@withContext Result.failure(Exception("Geçersiz baz fiyat: $newPrice"))

        reconcileMutex.withLock {
            try {
                log(LogLevel.INFO, "BasePrice", "Kullanıcı baz fiyatı $${RebalanceEngine.format4(newPrice)} olarak güncelliyor...")
                preferences.lastRebalancePrice = newPrice

                if (preferences.isBotActive) {
                    // 1. Cancel all open MNT orders on Bybit
                    log(LogLevel.INFO, "BasePrice", "Eski açık emirler iptal ediliyor...")
                    cancelAllBaseOrders()
                    preferences.activeBuyOrderId = ""
                    preferences.activeSellOrderId = ""

                    delay(1000)

                    // 2. Fetch fresh balances
                    var usdt = 0.0
                    var mnt = 0.0
                    getWalletBalance().onSuccess { map ->
                        usdt = map["USDT"]?.quantity ?: 0.0
                        mnt = map["${preferences.bybitBaseCoin}"]?.quantity ?: 0.0
                    }

                    if (usdt <= 0.0 && mnt <= 0.0) {
                        return@withContext Result.failure(Exception("Cüzdan bakiyesi okunamadı"))
                    }

                    // 3. Calculate grid with the EXACT new base price
                    val info = getInstrumentInfo(preferences.bybitSymbol)
                    val (qtyPrec, pricePrec) = getPrecisionForSymbol(preferences.bybitSymbol)
                    val minAmt = info?.lotSizeFilter?.minOrderAmt?.toDoubleOrNull() ?: 5.0
                    val minQty = info?.lotSizeFilter?.minOrderQty?.toDoubleOrNull()
                    val maxQty = info?.lotSizeFilter?.maxOrderQty?.toDoubleOrNull()
                    val plan = RebalanceEngine.calculateGridOrders(
                        usdtBalance = usdt,
                        baseCoinBalance = mnt,
                        basePrice = newPrice,
                        stepPercent = preferences.stepPercent,
                        qtyPrecision = qtyPrec,
                        pricePrecision = pricePrec,
                        tickSize = info?.priceFilter?.tickSize,
                        lotStep = info?.lotSizeFilter?.basePrecision,
                        minOrderAmt = minAmt,
                        minOrderQty = minQty,
                        maxOrderQty = maxQty
                    )

                    if (!plan.isValid) {
                        log(LogLevel.WARN, "BasePrice", "Izgara planı geçersiz: ${plan.validationMessage}")
                        return@withContext Result.failure(Exception(plan.validationMessage))
                    }

                    // 4. Place new orders on Bybit
                    val sellRes = createOrder("Sell", "Limit", plan.sellBaseQty, plan.sellLimitPrice, "ManualBasePriceUpdate")
                    val buyRes = createOrder("Buy", "Limit", plan.buyBaseQty, plan.buyLimitPrice, "ManualBasePriceUpdate")

                    if (sellRes.isSuccess && buyRes.isSuccess) {
                        preferences.activeSellOrderId = sellRes.getOrNull().orEmpty()
                        preferences.activeBuyOrderId = buyRes.getOrNull().orEmpty()
                        lastGridOrderPlacedTimeMs = System.currentTimeMillis()
                        log(
                            LogLevel.SUCCESS,
                            "BasePrice",
                            "Yeni baz fiyatla ($${RebalanceEngine.format4(newPrice)}) ızgara emirleri kuruldu. Alış: $${RebalanceEngine.format4(plan.buyLimitPrice)}, Satış: $${RebalanceEngine.format4(plan.sellLimitPrice)}"
                        )
                    } else {
                        // Asymmetric failure rollback
                        val sid = sellRes.getOrNull().orEmpty()
                        val bid = buyRes.getOrNull().orEmpty()
                        if (sellRes.isSuccess && sid.isNotBlank()) {
                            safeRollbackOrder(sid, "Sell", "BasePrice")
                        }
                        if (buyRes.isSuccess && bid.isNotBlank()) {
                            safeRollbackOrder(bid, "Buy", "BasePrice")
                        }
                        val errMsg = "Izgara tam açılamadı. Satış: ${sellRes.exceptionOrNull()?.message ?: "OK"}, Alış: ${buyRes.exceptionOrNull()?.message ?: "OK"}"
                        log(LogLevel.ERROR, "BasePrice", errMsg)
                        return@withContext Result.failure(Exception(errMsg))
                    }
                }

                orderDao.deleteUnfilledOrders()
                Result.success(Unit)
            } catch (e: Exception) {
                log(LogLevel.ERROR, "BasePrice", "Baz fiyat güncelleme hatası: ${e.message}")
                Result.failure(e)
            }
        }
    }

    private fun buildExecutionQueryString(
        category: String = "spot",
        symbol: String? = null,
        startTime: Long? = null,
        endTime: Long? = null,
        limit: Int = 100,
        cursor: String? = null
    ): String {
        val parts = mutableListOf<String>()
        parts.add("category=$category")
        if (!symbol.isNullOrBlank()) {
            parts.add("symbol=$symbol")
        }
        if (startTime != null) {
            parts.add("startTime=$startTime")
        }
        if (endTime != null) {
            parts.add("endTime=$endTime")
        }
        parts.add("limit=$limit")
        if (!cursor.isNullOrBlank()) {
            parts.add("cursor=$cursor")
        }
        return parts.joinToString("&")
    }

    private fun buildOrderHistoryQueryString(
        category: String = "spot",
        symbol: String? = null,
        startTime: Long? = null,
        endTime: Long? = null,
        limit: Int = 50,
        cursor: String? = null
    ): String {
        val parts = mutableListOf<String>()
        parts.add("category=$category")
        if (!symbol.isNullOrBlank()) {
            parts.add("symbol=$symbol")
        }
        if (startTime != null) {
            parts.add("startTime=$startTime")
        }
        if (endTime != null) {
            parts.add("endTime=$endTime")
        }
        parts.add("limit=$limit")
        if (!cursor.isNullOrBlank()) {
            parts.add("cursor=$cursor")
        }
        return parts.joinToString("&")
    }

    suspend fun fetchAllExecutions(
        symbol: String? = preferences.bybitSymbol,
        daysBack: Int = 730,
        startTimestamp: Long? = null,
        apiKey: String = preferences.apiKey,
        apiSecret: String = preferences.apiSecret,
        isTestnet: Boolean = preferences.isTestnet,
        onProgress: ((currentWindow: Int, totalWindows: Int, fetchedCount: Int) -> Unit)? = null
    ): Result<List<BybitExecutionDto>> = withContext(Dispatchers.IO) {
        try {
            if (apiKey.isBlank() || apiSecret.isBlank()) {
                return@withContext Result.failure(Exception("API Key veya Secret eksik"))
            }

            syncServerTime(isTestnet)
            val api = createApiService(isTestnet)
            val allExecutions = mutableListOf<BybitExecutionDto>()
            val effectiveSymbol = if (symbol.isNullOrBlank() || symbol.equals("ALL", ignoreCase = true)) null else symbol.trim().uppercase()

            val nowMs = System.currentTimeMillis()
            // Clamp daysBack to 720 days (~24 months) so startTime never touches Bybit's strict 2-year (730-day) API limit
            val minAllowedMs = nowMs - (720L * 24L * 60L * 60L * 1000L)
            val targetStartMs = if (startTimestamp != null && startTimestamp > 0L) {
                maxOf(startTimestamp, minAllowedMs)
            } else {
                val safeDaysBack = daysBack.coerceIn(1, 720)
                nowMs - (safeDaysBack.toLong() * 24L * 60L * 60L * 1000L)
            }
            val effectiveDays = ((nowMs - targetStartMs) / (24L * 60L * 60L * 1000L)).toInt().coerceAtLeast(1)
            // Bybit V5 requires (endTime - startTime) <= 7 days (604,800,000 ms).
            // We use 604,700,000 ms to stay safely inside the limit.
            val sevenDaysMs = 7L * 24L * 60L * 60L * 1000L - 100_000L

            var windowEndMs = nowMs
            var windowIndex = 0
            val totalEstimatedWindows = ((effectiveDays + 6) / 7).coerceAtLeast(1)
            var reachedHistoryLimit = false

            while (windowEndMs > targetStartMs && windowIndex < (totalEstimatedWindows + 3) && !reachedHistoryLimit) {
                val windowStartMs = maxOf(windowEndMs - sevenDaysMs, targetStartMs)
                var cursor: String? = null
                var pageInWindow = 0
                val maxPagesInWindow = 50 // Allows full historical depth without premature 5-page cutoff

                while (pageInWindow < maxPagesInWindow && !reachedHistoryLimit) {
                    val category = "spot"
                    val limit = 100
                    val queryString = buildExecutionQueryString(
                        category = category,
                        symbol = effectiveSymbol,
                        startTime = windowStartMs,
                        endTime = windowEndMs,
                        limit = limit,
                        cursor = cursor
                    )

                    var retryCount = 0
                    var success = false
                    var responseList: List<BybitExecutionDto>? = null
                    var nextCursor: String? = null

                    while (retryCount < 4 && !success && !reachedHistoryLimit) {
                        try {
                            val headers = createAuthHeaders(apiKey, apiSecret, queryString)
                            val response = api.getExecutionList(
                                headers = headers,
                                category = category,
                                symbol = effectiveSymbol,
                                startTime = windowStartMs,
                                endTime = windowEndMs,
                                limit = limit,
                                cursor = cursor
                            )

                            if (response.isSuccessful && response.body()?.isSuccess == true) {
                                val result = response.body()?.result
                                responseList = result?.list ?: emptyList()
                                nextCursor = result?.nextPageCursor
                                success = true
                            } else {
                                val code = response.body()?.retCode ?: -1
                                val msg = response.body()?.retMsg ?: response.message()
                                if (code == 10006 || response.code() == 429) {
                                    // Rate limit encountered: Exponential backoff instead of skipping window!
                                    retryCount++
                                    val waitTime = 1200L * retryCount
                                    log(LogLevel.WARN, "TradeAnalysis", "Bybit hız sınırı (10006), $waitTime ms bekleniyor... (Deneme $retryCount/3)")
                                    delay(waitTime)
                                } else if (code == 10001) {
                                    // Hit Bybit's 2-year history boundary limit
                                    log(LogLevel.INFO, "TradeAnalysis", "Bybit 2 yıllık maksimum geçmiş sınırına ulaşıldı ($windowIndex pencere tarandı).")
                                    reachedHistoryLimit = true
                                    break
                                } else if (response.code() in 500..599) {
                                    retryCount++
                                    delay(1000L * retryCount)
                                } else {
                                    log(LogLevel.WARN, "TradeAnalysis", "Pencere $windowIndex sayfa $pageInWindow sorgu hatası: $code - $msg")
                                    break
                                }
                            }
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            retryCount++
                            log(LogLevel.WARN, "TradeAnalysis", "Ağ zaman aşımı, tekrar deneniyor ($retryCount/3): ${e.message}")
                            delay(1000L * retryCount)
                        }
                    }

                    if (success && responseList != null) {
                        allExecutions.addAll(responseList)
                        cursor = nextCursor
                        pageInWindow++
                        if (cursor.isNullOrBlank() || responseList.isEmpty()) {
                            break
                        }
                        // Gentle pacing between pages to stay well below 10 req/s
                        delay(120)
                    } else {
                        if (windowIndex == 0 && pageInWindow == 0 && allExecutions.isEmpty() && !reachedHistoryLimit) {
                            return@withContext Result.failure(Exception("Bybit işlem geçmişine bağlanılamadı. Lütfen API anahtarlarınızı kontrol edin."))
                        }
                        break
                    }
                }

                // If this window reached or passed targetStartMs, or 2-year limit was reached, stop!
                if (windowStartMs <= targetStartMs || reachedHistoryLimit) {
                    windowIndex++
                    onProgress?.invoke(windowIndex, totalEstimatedWindows, allExecutions.size)
                    break
                }

                // 5-second overlap between windows ensures no executions are dropped at boundary
                windowEndMs = windowStartMs + 5000L
                windowIndex++
                onProgress?.invoke(windowIndex, totalEstimatedWindows, allExecutions.size)

                // Safe delay between windows (120ms keeps overall rate comfortably below 8 req/s)
                delay(120)
            }

            // Deduplicate executions across window boundaries & overlaps
            val uniqueExecutions = allExecutions.distinctBy {
                it.execId.ifBlank { "${it.orderId}_${it.execTime}" }
            }

            // Filter for genuine executed trades with valid price and volume and inside targetStartMs
            val validExecutions = uniqueExecutions.filter {
                it.qtyValue > 0.0 && it.priceValue > 0.0 &&
                        (it.execType.isBlank() || it.execType.equals("Trade", ignoreCase = true) || it.execType.equals("BlockTrade", ignoreCase = true)) &&
                        it.timeMillis >= targetStartMs
            }

            Result.success(validExecutions)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchFilledOrderHistory(
        symbol: String? = preferences.bybitSymbol,
        daysBack: Int = 730,
        startTimestamp: Long? = null,
        apiKey: String = preferences.apiKey,
        apiSecret: String = preferences.apiSecret,
        isTestnet: Boolean = preferences.isTestnet
    ): Result<List<BybitOrderDto>> = withContext(Dispatchers.IO) {
        try {
            if (apiKey.isBlank() || apiSecret.isBlank()) {
                return@withContext Result.failure(Exception("API Key veya Secret eksik"))
            }

            syncServerTime(isTestnet)
            val api = createApiService(isTestnet)
            val allOrders = mutableListOf<BybitOrderDto>()
            val effectiveSymbol = if (symbol.isNullOrBlank() || symbol.equals("ALL", ignoreCase = true)) null else symbol.trim().uppercase()

            val nowMs = System.currentTimeMillis()
            val minAllowedMs = nowMs - (720L * 24L * 60L * 60L * 1000L)
            val targetStartMs = if (startTimestamp != null && startTimestamp > 0L) {
                maxOf(startTimestamp, minAllowedMs)
            } else {
                val safeDaysBack = daysBack.coerceIn(1, 720)
                nowMs - (safeDaysBack.toLong() * 24L * 60L * 60L * 1000L)
            }
            val effectiveDays = ((nowMs - targetStartMs) / (24L * 60L * 60L * 1000L)).toInt().coerceAtLeast(1)
            val sevenDaysMs = 7L * 24L * 60L * 60L * 1000L - 100_000L

            var windowEndMs = nowMs
            var windowIndex = 0
            val maxWindows = ((effectiveDays / 7) + 2).coerceAtLeast(1)
            var reachedHistoryLimit = false

            while (windowEndMs > targetStartMs && windowIndex < maxWindows && !reachedHistoryLimit) {
                val windowStartMs = maxOf(windowEndMs - sevenDaysMs, targetStartMs)
                var cursor: String? = null
                var pageInWindow = 0

                while (pageInWindow < 50 && !reachedHistoryLimit) {
                    val category = "spot"
                    val limit = 50
                    val queryString = buildOrderHistoryQueryString(
                        category = category,
                        symbol = effectiveSymbol,
                        startTime = windowStartMs,
                        endTime = windowEndMs,
                        limit = limit,
                        cursor = cursor
                    )
                    val headers = createAuthHeaders(apiKey, apiSecret, queryString)
                    val response = api.getOrderHistoryList(
                        headers = headers,
                        category = category,
                        symbol = effectiveSymbol,
                        startTime = windowStartMs,
                        endTime = windowEndMs,
                        limit = limit,
                        cursor = cursor
                    )

                    if (response.isSuccessful && response.body()?.isSuccess == true) {
                        val result = response.body()?.result
                        val list = result?.list ?: emptyList()
                        val filledList = list.filter { it.filledQtyValue > 0.0 }
                        allOrders.addAll(filledList)
                        cursor = result?.nextPageCursor
                        if (cursor.isNullOrBlank() || list.isEmpty()) {
                            break
                        }
                        pageInWindow++
                        delay(120)
                    } else {
                        val code = response.body()?.retCode ?: -1
                        if (code == 10001) {
                            reachedHistoryLimit = true
                        }
                        break
                    }
                }

                if (windowStartMs <= targetStartMs || reachedHistoryLimit) {
                    break
                }

                windowEndMs = windowStartMs + 5000L
                windowIndex++
                delay(120)
            }

            val uniqueOrders = allOrders.distinctBy { it.orderId }
            Result.success(uniqueOrders)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchTradeAnalysis(
        symbol: String? = preferences.bybitSymbol,
        daysBack: Int = 730,
        startTimestamp: Long? = null,
        apiKey: String = preferences.apiKey,
        apiSecret: String = preferences.apiSecret,
        isTestnet: Boolean = preferences.isTestnet,
        onProgress: ((currentWindow: Int, totalWindows: Int, fetchedCount: Int) -> Unit)? = null
    ): Result<TradeAnalysisResult> = withContext(Dispatchers.IO) {
        try {
            val execRes = fetchAllExecutions(symbol, daysBack, startTimestamp, apiKey, apiSecret, isTestnet, onProgress)
            val executions = (execRes.getOrNull() ?: emptyList()).toMutableList()

            // If executions endpoint returned nothing or we want to ensure full coverage,
            // also query filled order history as fallback/complement
            if (executions.isEmpty()) {
                val ordersRes = fetchFilledOrderHistory(symbol, daysBack, startTimestamp, apiKey, apiSecret, isTestnet)
                val filledOrders = ordersRes.getOrNull() ?: emptyList()
                executions.addAll(filledOrders.map { order ->
                    BybitExecutionDto(
                        symbol = order.symbol,
                        orderId = order.orderId,
                        orderLinkId = order.orderLinkId,
                        side = order.side,
                        orderPrice = order.price,
                        orderQty = order.qty,
                        orderType = order.orderType,
                        execId = order.orderId,
                        execPrice = if (order.avgPriceValue > 0) order.avgPrice else order.price,
                        execQty = order.cumExecQty,
                        execType = "Trade",
                        execValue = order.cumExecValue,
                        execTime = order.updatedTime.ifBlank { order.createdTime }
                    )
                })
            }

            if (executions.isEmpty() && execRes.isFailure) {
                return@withContext Result.failure(execRes.exceptionOrNull() ?: Exception("İşlem geçmişi çekilemedi"))
            }

            val effectiveDays = if (startTimestamp != null && startTimestamp > 0L) {
                val nowMs = System.currentTimeMillis()
                ((nowMs - startTimestamp) / (24L * 60L * 60L * 1000L)).toInt().coerceAtLeast(1)
            } else {
                daysBack
            }
            val rangeLabel = if (startTimestamp != null && startTimestamp > 0L) {
                val dateStr = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault()).format(java.util.Date(startTimestamp))
                "$dateStr Tarihinden İtibaren"
            } else {
                "Son $daysBack Gün"
            }

            val analysis = com.example.bot.RebalanceEngine.calculateTradeAnalysis(
                symbol = symbol,
                executions = executions,
                daysRange = effectiveDays,
                dateRangeLabel = rangeLabel
            )

            log(
                LogLevel.SUCCESS,
                "TradeAnalysis",
                "Borsa işlem geçmişi çekildi ($daysBack gün): ${executions.size} işlem. Semboller: ${analysis.symbolBreakdown.keys.joinToString()}"
            )

            Result.success(analysis)
        } catch (e: Exception) {
            log(LogLevel.ERROR, "TradeAnalysis", "Geçmiş analizi hatası: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun recordOrderFilled(
        orderId: String,
        side: String,
        price: Double,
        qty: Double = 0.0,
        triggerReason: String = "",
        fillTime: Long = System.currentTimeMillis()
    ) = withContext(Dispatchers.IO) {
        if (price <= 0.0 || qty <= 0.0) {
            log(LogLevel.WARN, "BybitOrderFill", "Dolum kaydı reddedildi: Geçersiz fiyat ($price) veya miktar ($qty) ($orderId)")
            return@withContext
        }
        orderFillMutex.withLock {
            try {
                val effectiveTime = if (fillTime > 0L) fillTime else System.currentTimeMillis()
                var shouldTriggerSync = false
                database.withTransaction {
                    val existing = orderDao.getOrderByOrderId(orderId)
                    if (existing != null) {
                        if (existing.status != "Filled" || (qty > 0.0 && existing.filledQty < qty)) {
                            orderDao.updateOrderStatus(
                                orderId = orderId,
                                status = "Filled",
                                filledQty = if (qty > 0.0) qty else existing.qty,
                                avgPrice = if (price > 0.0) price else existing.price,
                                fillTime = effectiveTime
                            )
                            shouldTriggerSync = true
                        }
                    } else {
                        orderDao.insertOrder(
                            OrderEntity(
                                exchange = "BYBIT",
                                orderId = orderId,
                                side = side,
                                orderType = "Limit",
                                price = price,
                                qty = qty,
                                status = "Filled",
                                filledQty = qty,
                                avgPrice = price,
                                timestamp = effectiveTime,
                                triggerReason = triggerReason
                            )
                        )
                        shouldTriggerSync = true
                    }
                } // Close withTransaction

                if (shouldTriggerSync) {
                    log(LogLevel.SUCCESS, "OrderHistory", "İşlem Room Veritabanına kaydedildi: $side $orderId @ $price")
                    // Trigger sync of genuine execution record from exchange in background to capture exact fee, maker/taker, execId
                    scope.launch {
                        try {
                            syncTradesFromExchange(
                                symbol = preferences.bybitSymbol,
                                daysBack = 1
                            )
                        } catch (e: Exception) {
                            // Non-blocking background sync
                        }
                    }
                }
            } catch (e: Exception) {
                log(LogLevel.WARN, "OrderHistory", "Veritabanı kayıt hatası: ${e.message}")
            }
        }
    }

    suspend fun syncUnfilledOrdersWithExchange(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!preferences.isConfigured) return@withContext Result.success(Unit)

            // 1. Get current active open orders directly from Bybit
            val liveOpenOrdersRes = getOpenOrders()
            val liveOpenOrders = liveOpenOrdersRes.getOrDefault(emptyList())
            val liveOpenOrderIds = liveOpenOrders.map { it.orderId }.toSet()

            // 2. Fetch recent orders from Bybit (limit = 50)
            val recentOrdersRes = getRecentOrdersList(limit = 50)
            val recentOrders = recentOrdersRes.getOrDefault(emptyList()).associateBy { it.orderId }

            // 3. Clean up any ghost filled orders (orders marked as Filled locally but actually Cancelled on exchange)
            cleanupGhostFilledOrders(recentOrders)

            val openOrdersInDb = orderDao.getOpenOrdersFromDb()
            if (openOrdersInDb.isEmpty()) return@withContext Result.success(Unit)

            for (dbOrder in openOrdersInDb) {
                // If it is currently open on exchange, leave it as New/PartiallyFilled
                if (liveOpenOrderIds.contains(dbOrder.orderId)) {
                    continue
                }

                // It is not in Bybit's open orders. Check what happened to it
                var exOrder = recentOrders[dbOrder.orderId]
                if (exOrder == null && dbOrder.orderId.isNotBlank()) {
                    // Query single order status from Bybit directly
                    exOrder = getOrderHistory(dbOrder.orderId).getOrNull()
                }

                if (exOrder != null) {
                    if (exOrder.isFilled || exOrder.filledQtyValue > 0.0) {
                        val fillPrice = if (exOrder.avgPriceValue > 0.0) exOrder.avgPriceValue else dbOrder.price
                        val fillQty = if (exOrder.filledQtyValue > 0.0) exOrder.filledQtyValue else dbOrder.qty
                        val fillTime = if (exOrder.updatedTimeMillis > 0L) exOrder.updatedTimeMillis else System.currentTimeMillis()
                        log(
                            LogLevel.SUCCESS,
                            "OrderSync",
                            "Açık görünen emir borsada GERÇEKLEŞMİŞ olarak güncellendi: ${dbOrder.side} ${dbOrder.orderId} @ $fillPrice"
                        )
                        recordOrderFilled(
                            orderId = dbOrder.orderId,
                            side = dbOrder.side,
                            price = fillPrice,
                            qty = fillQty,
                            triggerReason = dbOrder.triggerReason,
                            fillTime = fillTime
                        )
                    } else if (exOrder.orderStatus.equals("Cancelled", ignoreCase = true) ||
                        exOrder.orderStatus.equals("Deactivated", ignoreCase = true)) {
                        log(LogLevel.INFO, "OrderSync", "Borsada iptal edilmiş emir veritabanından kaldırıldı: ${dbOrder.orderId}")
                        orderDao.deleteOrder(dbOrder.orderId)
                    }
                } else {
                    // Not found in open orders AND not found in order history.
                    // This means the order was cancelled, deactivated, or no longer exists on exchange.
                    // CRITICAL: NEVER mark an order as Filled without proof from exchange!
                    val isNotActiveGrid = dbOrder.orderId != preferences.activeBuyOrderId &&
                            dbOrder.orderId != preferences.activeSellOrderId
                    if (isNotActiveGrid) {
                        log(LogLevel.INFO, "OrderSync", "Borsada bulunmayan eski emir temizlendi: ${dbOrder.side} ${dbOrder.orderId}")
                        orderDao.deleteOrder(dbOrder.orderId)
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            log(LogLevel.WARN, "OrderSync", "Açık emir senkronizasyon hatası: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Cross-checks locally marked 'Filled' orders against exchange recent orders.
     * If an order was locally marked 'Filled' (e.g. by legacy sync assumption) but was actually
     * Cancelled/Deactivated on Bybit with 0 filled quantity, it is safely deleted from local DB.
     * Also updates fill timestamps of genuine filled orders to match exchange execution times.
     */
    private suspend fun cleanupGhostFilledOrders(recentOrders: Map<String, BybitOrderDto>) {
        try {
            val localFilled = orderDao.getRecentFilledOrdersList(limit = 50)
            for (order in localFilled) {
                val exOrder = recentOrders[order.orderId]
                if (exOrder != null) {
                    if ((exOrder.isCancelled || exOrder.orderStatus.equals("Deactivated", ignoreCase = true)) &&
                        exOrder.filledQtyValue == 0.0) {
                        log(
                            LogLevel.WARN,
                            "OrderSync",
                            "Borsada gerçekleşmemiş (İptal edilmiş) sahte işlem geçmişten kaldırıldı: ${order.side} ${order.orderId}"
                        )
                        orderDao.deleteOrder(order.orderId)
                        exchangeTradeDao.deleteTradeByOrderId(order.orderId)
                    } else if (exOrder.isFilled && exOrder.updatedTimeMillis > 0L &&
                        Math.abs(order.timestamp - exOrder.updatedTimeMillis) > 60_000L) {
                        // Align local timestamp with actual fill time on exchange so sorting is accurate
                        orderDao.updateOrderStatus(
                            orderId = order.orderId,
                            status = "Filled",
                            filledQty = if (exOrder.filledQtyValue > 0.0) exOrder.filledQtyValue else order.filledQty,
                            avgPrice = if (exOrder.avgPriceValue > 0.0) exOrder.avgPriceValue else order.avgPrice,
                            fillTime = exOrder.updatedTimeMillis
                        )
                    }
                }
            }
        } catch (e: Exception) {
            // Non-blocking cleanup
        }
    }

    suspend fun getLastFilledOrder(): OrderEntity? = withContext(Dispatchers.IO) {
        orderDao.getLastFilledOrder()
    }

    suspend fun getOrderByOrderId(orderId: String): OrderEntity? = withContext(Dispatchers.IO) {
        orderDao.getOrderByOrderId(orderId)
    }

    /**
     * Borsadan işlemleri çeker, mevcut Room veritabanındaki kayıtlarla karşılaştırır
     * ve SADECE yeni olan işlemleri veritabanına ekler (deduplication / mükerrer engelleme).
     * Ardından güncel tüm veriler üzerinden analiz ve senkronizasyon raporu döndürür.
     */
    suspend fun syncTradesFromExchange(
        symbol: String? = preferences.bybitSymbol,
        daysBack: Int = 730,
        startTimestamp: Long? = null,
        apiKey: String = preferences.apiKey,
        apiSecret: String = preferences.apiSecret,
        isTestnet: Boolean = preferences.isTestnet,
        onProgress: ((currentWindow: Int, totalWindows: Int, fetchedCount: Int) -> Unit)? = null
    ): Result<TradeSyncResult> = withContext(Dispatchers.IO) {
        tradeSyncMutex.withLock {
            try {
                log(LogLevel.INFO, "TradeSync", "Borsadan işlemler çekilip veritabanı kontrol ediliyor...")

            // 1. Borsadan seçilen zaman aralığındaki tüm işlemleri çek
            val fetchResult = fetchTradeAnalysis(
                symbol = symbol,
                daysBack = daysBack,
                startTimestamp = startTimestamp,
                apiKey = apiKey,
                apiSecret = apiSecret,
                isTestnet = isTestnet,
                onProgress = onProgress
            )

            val remoteAnalysis = fetchResult.getOrElse { error ->
                log(LogLevel.ERROR, "TradeSync", "Borsadan işlem çekilemedi: ${error.message}")
                return@withContext Result.failure(error)
            }

            val remoteExecutions = remoteAnalysis.executions
            val totalFetched = remoteExecutions.size

            // 2. Yerel veritabanındaki mevcut kayıtlı işlem ID'lerini al
            val existingExecIds = exchangeTradeDao.getAllExecIds().toHashSet()
            val existingInDbCount = existingExecIds.size

            // 3. Karşılaştır: Sadece veritabanında henüz bulunmayan yeni işlemleri filtrele
            val newExecutions = remoteExecutions.filter { exec ->
                val uniqueKey = exec.execId.ifBlank { "${exec.orderId}_${exec.execTime}" }
                !existingExecIds.contains(uniqueKey) && !existingExecIds.contains(exec.execId)
            }

            // 4. Yeni işlemleri ExchangeTradeEntity formatına dönüştürüp veritabanına kaydet
            if (newExecutions.isNotEmpty()) {
                val entitiesToInsert = newExecutions.map { exec ->
                    val uniqueKey = exec.execId.ifBlank { "${exec.orderId}_${exec.execTime}" }
                    val effectiveSymbol = if (exec.symbol.isNotBlank()) {
                        exec.symbol.trim().uppercase()
                    } else {
                        (symbol?.takeIf { !it.equals("ALL", ignoreCase = true) && !it.equals("TÜM", ignoreCase = true) } ?: preferences.bybitSymbol).trim().uppercase()
                    }
                    ExchangeTradeEntity(
                        execId = uniqueKey,
                        orderId = exec.orderId,
                        orderLinkId = exec.orderLinkId,
                        symbol = effectiveSymbol,
                        side = exec.side,
                        orderPrice = exec.orderPrice.toDoubleOrNull() ?: 0.0,
                        orderQty = exec.orderQty.toDoubleOrNull() ?: 0.0,
                        orderType = exec.orderType,
                        execPrice = exec.priceValue,
                        execQty = exec.qtyValue,
                        execValue = exec.totalValue,
                        execFee = exec.feeValue,
                        feeRate = exec.feeRate.toDoubleOrNull() ?: 0.0,
                        feeCurrency = exec.feeCurrency,
                        timeMillis = if (exec.timeMillis > 0) exec.timeMillis else System.currentTimeMillis(),
                        isMaker = exec.isMaker
                    )
                }

                exchangeTradeDao.insertTrades(entitiesToInsert)

                // 5. Bybit resmi /v5/order/history (Emir Geçmişi) listesini çekip haritala
                // Bu liste emrin gerçek toplam miktarını (ör. 25.77 MNT) ve gerçek açılış anını (createdTime: 17:11:33) verir
                val recentOrdersRes = try {
                    fetchFilledOrderHistory(
                        symbol = symbol,
                        daysBack = minOf(daysBack, 30),
                        startTimestamp = startTimestamp,
                        apiKey = apiKey,
                        apiSecret = apiSecret,
                        isTestnet = isTestnet
                    )
                } catch (e: Exception) {
                    Result.failure(e)
                }
                val bybitOrdersMap: Map<String, BybitOrderDto> = (recentOrdersRes.getOrNull() ?: emptyList())
                    .filter { it.orderId.isNotBlank() }
                    .associateBy { it.orderId }

                // 6. Çekilen dolumları (remoteExecutions) orderId bazında konsolide et (parçalı dolumları birleştir)
                // Böylece 14.16 MNT gibi tek parçalar değil, emrin gerçek toplamı (25.77 MNT) kaydedilir
                val executionsToGroup = if (newExecutions.isNotEmpty()) remoteExecutions else emptyList()
                val groupedByOrderId = executionsToGroup
                    .filter { it.orderId.isNotBlank() }
                    .groupBy { it.orderId }

                val ordersToUpsert = mutableListOf<OrderEntity>()

                for ((orderId, tradeGroup) in groupedByOrderId) {
                    val officialOrder = bybitOrdersMap[orderId]
                    val firstTrade = tradeGroup.first()
                    val totalFilledQty = tradeGroup.sumOf { it.qtyValue }
                    val totalValue = tradeGroup.sumOf { it.totalValue }
                    val weightedAvgPrice = if (totalFilledQty > 0.0) totalValue / totalFilledQty else firstTrade.priceValue
                    val maxOrderQty = tradeGroup.firstNotNullOfOrNull { it.orderQty.toDoubleOrNull()?.takeIf { q -> q > 0.0 } } ?: 0.0

                    val effectiveQty = when {
                        officialOrder != null && officialOrder.qtyValue > 0.0 -> officialOrder.qtyValue
                        maxOrderQty > 0.0 -> maxOf(maxOrderQty, totalFilledQty)
                        else -> totalFilledQty
                    }
                    val effectiveFilledQty = when {
                        officialOrder != null && officialOrder.filledQtyValue > 0.0 -> officialOrder.filledQtyValue
                        else -> totalFilledQty
                    }
                    val effectivePrice = when {
                        officialOrder != null && officialOrder.priceValue > 0.0 -> officialOrder.priceValue
                        firstTrade.orderPrice.toDoubleOrNull() != null && (firstTrade.orderPrice.toDoubleOrNull() ?: 0.0) > 0.0 -> firstTrade.orderPrice.toDoubleOrNull() ?: weightedAvgPrice
                        else -> weightedAvgPrice
                    }
                    val effectiveAvgPrice = when {
                        officialOrder != null && officialOrder.avgPriceValue > 0.0 -> officialOrder.avgPriceValue
                        else -> weightedAvgPrice
                    }
                    val effectiveTimestamp = when {
                        officialOrder != null && officialOrder.createdTimeMillis > 0L -> officialOrder.createdTimeMillis
                        else -> tradeGroup.minOf { it.timeMillis }
                    }
                    val effectiveSide = officialOrder?.side?.ifBlank { firstTrade.side } ?: firstTrade.side
                    val effectiveOrderType = officialOrder?.orderType?.ifBlank { firstTrade.orderType.ifBlank { "Limit" } } ?: firstTrade.orderType.ifBlank { "Limit" }
                    val effectiveSymbol = officialOrder?.symbol?.ifBlank { firstTrade.symbol } ?: firstTrade.symbol

                    ordersToUpsert.add(
                        OrderEntity(
                            exchange = "BYBIT",
                            orderId = orderId,
                            orderLinkId = officialOrder?.orderLinkId?.ifBlank { firstTrade.orderLinkId } ?: firstTrade.orderLinkId,
                            symbol = effectiveSymbol.ifBlank { preferences.bybitSymbol },
                            side = effectiveSide,
                            orderType = effectiveOrderType,
                            price = if (effectiveOrderType.equals("Market", ignoreCase = true)) effectiveAvgPrice else effectivePrice,
                            qty = effectiveQty,
                            status = "Filled",
                            filledQty = effectiveFilledQty,
                            avgPrice = effectiveAvgPrice,
                            timestamp = effectiveTimestamp,
                            triggerReason = "BorsaSenkronizasyonu"
                        )
                    )
                }

                // Bybit /v5/order/history listesindeki diğer resmi emirleri de ekle
                for ((orderId, officialOrder) in bybitOrdersMap) {
                    if (groupedByOrderId.containsKey(orderId)) continue
                    val effectiveSymbol = if (officialOrder.symbol.isNotBlank()) {
                        officialOrder.symbol.trim().uppercase()
                    } else {
                        (symbol?.takeIf { !it.equals("ALL", ignoreCase = true) && !it.equals("TÜM", ignoreCase = true) } ?: preferences.bybitSymbol).trim().uppercase()
                    }
                    ordersToUpsert.add(
                        OrderEntity(
                            exchange = "BYBIT",
                            orderId = orderId,
                            orderLinkId = officialOrder.orderLinkId,
                            symbol = effectiveSymbol,
                            side = officialOrder.side,
                            orderType = officialOrder.orderType.ifBlank { "Limit" },
                            price = if (officialOrder.priceValue > 0.0) officialOrder.priceValue else officialOrder.avgPriceValue,
                            qty = if (officialOrder.qtyValue > 0.0) officialOrder.qtyValue else officialOrder.filledQtyValue,
                            status = "Filled",
                            filledQty = officialOrder.filledQtyValue,
                            avgPrice = officialOrder.avgPriceValue,
                            timestamp = if (officialOrder.createdTimeMillis > 0L) officialOrder.createdTimeMillis else (officialOrder.updatedTimeMillis.takeIf { it > 0L } ?: System.currentTimeMillis()),
                            triggerReason = "BorsaSenkronizasyonu"
                        )
                    )
                }

                // Veritabanına kaydet/güncelle (Eksik veya parçalı kayıtları tamamlama)
                for (order in ordersToUpsert) {
                    val existing = orderDao.getOrderByOrderId(order.orderId)
                    if (existing == null) {
                        orderDao.insertOrder(order)
                    } else if (existing.triggerReason == "BorsaSenkronizasyonu") {
                        if (existing.filledQty < order.filledQty || existing.qty < order.qty || (order.timestamp < existing.timestamp && order.timestamp > 0L)) {
                            orderDao.insertOrder(order.copy(id = existing.id))
                        }
                    }
                }

                log(
                    LogLevel.SUCCESS,
                    "TradeSync",
                    "Senkronizasyon tamamlandı: Borsadan $totalFetched işlem çekildi. " +
                            "$existingInDbCount işlem zaten veritabanındaydı. ${newExecutions.size} YENİ işlem eklendi!"
                )
            } else {
                log(
                    LogLevel.INFO,
                    "TradeSync",
                    "Senkronizasyon kontrolü: Borsadan $totalFetched işlem çekildi. Tüm işlemler zaten veritabanında mevcut, yeni işlem yok."
                )
            }

            val totalInDb = exchangeTradeDao.getTradeCountSync()

            // Borsa Analizi için SADECE çekilen işlemlerin analizi (remoteAnalysis) döndürülür.
            // Böylece Borsa Analiz ekranı kullanıcının belirlediği zaman aralığını/seçilen tarihi temel alır.
            val syncResult = TradeSyncResult(
                totalFetched = totalFetched,
                existingInDb = existingInDbCount,
                newlyAddedCount = newExecutions.size,
                totalInDb = totalInDb,
                newlyAddedTrades = newExecutions,
                analysis = remoteAnalysis
            )

            Result.success(syncResult)
            } catch (e: Exception) {
                log(LogLevel.ERROR, "TradeSync", "Senkronizasyon hatası: ${e.message}")
                Result.failure(e)
            }
        }
    }

    suspend fun getLocalTradesCount(): Int = withContext(Dispatchers.IO) {
        exchangeTradeDao.getTradeCountSync()
    }

    suspend fun clearLocalExchangeTrades() = withContext(Dispatchers.IO) {
        exchangeTradeDao.clearAllTrades()
        orderDao.deleteFilledOrders()
        log(LogLevel.INFO, "TradeSync", "Yerel borsa işlem geçmişi ve dolmuş emir kayıtları temizlendi")
    }

    fun getAllExchangeTradesFlow() = exchangeTradeDao.getAllTrades()
}
