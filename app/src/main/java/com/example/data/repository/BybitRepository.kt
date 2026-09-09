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
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

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
            10003 -> "API Key veya Secret hatalı / geçersiz"
            10004 -> "API İmza doğrulaması başarısız (Parametreler veya secret uyuşmuyor)"
            10005, 33004 -> "Yetki hatası: API anahtarınızda 'Spot: Trade' (Alım-Satım) izni açık olmalıdır"
            170131 -> "Yetersiz bakiye: Emir için Unified cüzdanınızda yeterli USDT veya MNT yok"
            170140 -> "Emir tutarı çok küçük: Bybit MNT/USDT için minimum işlem tutarı 5 USDT'dir"
            170193 -> "Emir miktarı veya fiyatı Bybit sınırlarını aşıyor"
            10002 -> "Zaman aşımı: Cihaz saati Bybit sunucusu ile senkronize değil"
            else -> ""
        }
        return if (hint.isNotBlank()) "Hata $retCode ($hint): $retMsg" else "Bybit Hata [$retCode]: $retMsg"
    }

    /**
     * MNT Spot basePrecision is 0.01 (2 decimal places).
     * We floor truncate so we never attempt to trade more than available balance.
     */
    fun formatMntQty(qty: Double): String {
        val truncated = floor(qty * 100.0) / 100.0
        return String.format(Locale.US, "%.2f", truncated)
    }

    /**
     * MNT Spot tickSize is 0.0001 (4 decimal places).
     */
    fun formatPrice(price: Double): String {
        return String.format(Locale.US, "%.4f", price)
    }

    private val logInsertCounter = java.util.concurrent.atomic.AtomicInteger(0)

    suspend fun pruneLogs(
        maxAgeMillis: Long = 24 * 60 * 60 * 1000L, // 24 hours
        maxLogsToKeep: Int = 10000
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

    suspend fun getWalletBalance(
        apiKey: String = preferences.apiKey,
        apiSecret: String = preferences.apiSecret,
        isTestnet: Boolean = preferences.isTestnet
    ): Result<Map<String, Double>> = withContext(Dispatchers.IO) {
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
                    val balanceMap = mutableMapOf<String, Double>()
                    body.result?.list?.firstOrNull()?.coin?.forEach { coin ->
                        balanceMap[coin.coin.uppercase()] = coin.balanceValue
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

    suspend fun getMntTicker(isTestnet: Boolean = preferences.isTestnet): Result<SpotTicker> =
        withContext(Dispatchers.IO) {
            try {
                val api = createApiService(isTestnet)
                val response = api.getTickers("spot", "MNTUSDT")
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
        apiKey: String = preferences.apiKey,
        apiSecret: String = preferences.apiSecret,
        isTestnet: Boolean = preferences.isTestnet
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (apiKey.isBlank() || apiSecret.isBlank()) {
                return@withContext Result.failure(Exception("API Key veya Secret eksik"))
            }

            val formattedQty = formatMntQty(qty)
            if (formattedQty.toDoubleOrNull() == null || formattedQty.toDouble() <= 0.0) {
                return@withContext Result.failure(Exception("Geçersiz miktar: $formattedQty MNT (Min: 0.01 MNT)"))
            }

            val formattedPrice = price?.let { formatPrice(it) }
            val orderLinkId = "bot_${System.currentTimeMillis()}_${(100..999).random()}"

            // Construct exact JSON payload
            val jsonObject = JSONObject().apply {
                put("category", "spot")
                put("symbol", "MNTUSDT")
                put("side", side)
                put("orderType", orderType)
                put("qty", formattedQty)
                if (orderType.equals("Limit", ignoreCase = true) && formattedPrice != null) {
                    put("price", formattedPrice)
                    put("timeInForce", "GTC")
                } else if (orderType.equals("Market", ignoreCase = true)) {
                    // In Bybit V5 Spot:
                    // For Market Buy, when qty is in base currency (MNT), marketUnit must be "baseCoin".
                    // For Market Sell, qty is always baseCoin. Setting marketUnit explicitly ensures compatibility.
                    put("marketUnit", "baseCoin")
                }
                put("orderLinkId", orderLinkId)
            }
            val jsonString = jsonObject.toString()

            // Calculate HMAC SHA256 over this exact jsonString
            val headers = createAuthHeaders(apiKey, apiSecret, jsonString)
            val requestBody = jsonString.toRequestBody("application/json; charset=utf-8".toMediaType())

            val api = createApiService(isTestnet)
            val response = api.createOrder(headers, requestBody)
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.isSuccess && body.result != null) {
                    val orderId = body.result.orderId
                    lastGridOrderPlacedTimeMs = System.currentTimeMillis()
                    val orderEntity = OrderEntity(
                        orderId = orderId,
                        orderLinkId = orderLinkId,
                        symbol = "MNTUSDT",
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
                    Result.success(orderId)
                } else {
                    val code = body?.retCode ?: -1
                    val msg = body?.retMsg ?: "Emir oluşturulamadı"
                    val formattedErr = parseBybitErrorMessage(code, msg)
                    log(LogLevel.ERROR, "OrderCreate", formattedErr, "İstek gövdesi: $jsonString")
                    Result.failure(Exception(formattedErr))
                }
            } else {
                val errBody = response.errorBody()?.string() ?: ""
                val err = "HTTP ${response.code()}: ${response.message()} $errBody".trim()
                log(LogLevel.ERROR, "OrderCreate", "HTTP Hatası: $err", "İstek: $jsonString")
                Result.failure(Exception(err))
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
                put("symbol", "MNTUSDT")
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
                        // Order already cancelled or filled or doesn't exist
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

    suspend fun cancelAllMntOrders(
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
                put("symbol", "MNTUSDT")
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
                log(LogLevel.INFO, "CancelAll", "Tüm açık MNTUSDT emirleri iptal edildi ve geçmişten temizlendi")
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
        orderId: String,
        category: String = "spot",
        symbol: String = "MNTUSDT",
        apiKey: String = preferences.apiKey,
        apiSecret: String = preferences.apiSecret,
        isTestnet: Boolean = preferences.isTestnet
    ): Result<BybitOrderDto?> = withContext(Dispatchers.IO) {
        try {
            if (apiKey.isBlank() || apiSecret.isBlank()) {
                return@withContext Result.failure(Exception("API Key eksik"))
            }

            val api = createApiService(isTestnet)
            val queryString = "category=$category&symbol=$symbol&orderId=$orderId"
            val headers = createAuthHeaders(apiKey, apiSecret, queryString)

            val response = api.getOrderHistory(headers, category, symbol, orderId)
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
            val queryString = "category=spot&symbol=MNTUSDT"
            val headers = createAuthHeaders(apiKey, apiSecret, queryString)

            val response = api.getOpenOrders(headers, "spot", "MNTUSDT")
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
        symbol: String = "MNTUSDT",
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
        symbol: String = "MNTUSDT",
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
        callerTag: String = "Reconcile"
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

            // Fetch recent order history and execution history from Bybit
            val recentOrdersRes = getRecentOrdersList(limit = 15)
            val recentOrders = recentOrdersRes.getOrDefault(emptyList())
            val recentExecutionsRes = getRecentExecutionsList(limit = 15)
            val recentExecutions = recentExecutionsRes.getOrDefault(emptyList())

            // CASE 1: Both Buy and Sell orders are open on Bybit
            if (openBuyOrder != null && openSellOrder != null) {
                // Ensure tracked IDs match
                if (activeBuyId != openBuyOrder.orderId) preferences.activeBuyOrderId = openBuyOrder.orderId
                if (activeSellId != openSellOrder.orderId) preferences.activeSellOrderId = openSellOrder.orderId

                // Make sure any stale past orders in DB are synced to Filled/Cancelled
                syncUnfilledOrdersWithExchange()

                return@withContext Result.success(
                    ReconciliationResult(
                        executedOrderFound = false,
                        newBasePrice = preferences.lastRebalancePrice,
                        message = "Her iki ızgara emri borsada aktif (Alış: ${openBuyOrder.price}, Satış: ${openSellOrder.price})"
                    )
                )
            }

            // CASE 2: Exactly ONE order is open on Bybit (The classic fill case: Sell filled while Buy remained, or vice versa)
            if ((openBuyOrder != null && openSellOrder == null) || (openBuyOrder == null && openSellOrder != null)) {
                val missingSide = if (openBuyOrder == null) "Buy" else "Sell"
                val remainingOrder = openBuyOrder ?: openSellOrder!!
                val missingOrderId = if (missingSide.equals("Sell", ignoreCase = true)) activeSellId else activeBuyId

                // Prioritize matching the exact tracked missingOrderId first
                val exactTrackedOrder = if (missingOrderId.isNotBlank()) {
                    recentOrders.firstOrNull { it.orderId == missingOrderId }
                } else null

                val isCancelledWithoutFill = exactTrackedOrder != null &&
                    (exactTrackedOrder.orderStatus.equals("Cancelled", ignoreCase = true) ||
                     exactTrackedOrder.orderStatus.equals("Deactivated", ignoreCase = true)) &&
                    exactTrackedOrder.filledQtyValue == 0.0

                // Find the filled order details if genuinely filled.
                // 1) Match exact missingOrderId if available
                // 2) Or match an order of missingSide created within the last 2 hours to avoid ancient orders
                val twoHoursAgo = System.currentTimeMillis() - 7_200_000L
                val filledOrder = if (exactTrackedOrder != null && (exactTrackedOrder.isFilled || exactTrackedOrder.filledQtyValue > 0.0)) {
                    exactTrackedOrder
                } else {
                    recentOrders.firstOrNull { order ->
                        order.side.equals(missingSide, ignoreCase = true) &&
                        (order.isFilled || order.filledQtyValue > 0.0) &&
                        order.orderId != remainingOrder.orderId &&
                        (order.createdTime.toLongOrNull() ?: 0L) >= twoHoursAgo
                    }
                }

                val filledExec = if (missingOrderId.isNotBlank()) {
                    recentExecutions.firstOrNull { it.orderId == missingOrderId }
                } else null ?: recentExecutions.firstOrNull { exec ->
                    exec.side.equals(missingSide, ignoreCase = true) &&
                    exec.orderId != remainingOrder.orderId &&
                    (exec.execTime.toLongOrNull() ?: 0L) >= twoHoursAgo
                }

                val lastBase = preferences.lastRebalancePrice
                val step = preferences.stepPercent
                val currentTickerPrice = getMntTicker().getOrNull()?.currentPrice ?: 0.0

                if (isCancelledWithoutFill) {
                    log(
                        LogLevel.WARN,
                        callerTag,
                        "Mutabakat: $missingSide emri borsada iptal edilmiş (Gerçekleşme yok). Karşı emir temizlenip yeni ızgara açılıyor..."
                    )
                } else {
                    log(LogLevel.INFO, callerTag, "Izgarada sadece tek taraf açık (${remainingOrder.side} ${remainingOrder.orderId}). Karşı taraf ($missingSide) dolmuş olarak tespit edildi!")
                }

                // Calculate the theoretical grid execution price based on the previous base price and step ratio
                val expectedGridPrice = if (missingSide.equals("Sell", ignoreCase = true)) {
                    if (lastBase > 0.0) lastBase * (1.0 + step / 100.0) else 0.0
                } else {
                    if (lastBase > 0.0) lastBase * (1.0 - step / 100.0) else 0.0
                }

                // If genuine fill price exists, use it. Otherwise, use expectedGridPrice if valid, preserving grid geometry!
                val rawExecPrice = filledOrder?.avgPriceValue?.takeIf { it > 0.0 }
                    ?: filledExec?.priceValue?.takeIf { it > 0.0 }
                    ?: expectedGridPrice

                val finalExecPrice = if (!isCancelledWithoutFill && rawExecPrice > 0.0) {
                    rawExecPrice
                } else {
                    if (lastBase > 0.0) lastBase else if (currentTickerPrice > 0.0) currentTickerPrice else 0.5
                }

                val execQty = filledOrder?.filledQtyValue ?: filledExec?.qtyValue ?: 0.0
                val execOrderId = filledOrder?.orderId
                    ?: filledExec?.orderId
                    ?: missingOrderId.ifBlank { "exec_${System.currentTimeMillis()}" }

                if (!isCancelledWithoutFill) {
                    log(
                        LogLevel.SUCCESS,
                        callerTag,
                        "Mutabakat: $missingSide emri GERÇEKLEŞMİŞ! Fiyat: $finalExecPrice, Miktar: $execQty MNT ($execOrderId)"
                    )
                }

                // 1. Cancel opposite remaining order
                log(LogLevel.INFO, callerTag, "Karşı açık emir (${remainingOrder.side} ${remainingOrder.orderId}) iptal ediliyor...")
                cancelOrder(remainingOrder.orderId)
                orderDao.deleteOrder(remainingOrder.orderId)

                // 2. Clear old active IDs
                preferences.activeBuyOrderId = ""
                preferences.activeSellOrderId = ""

                // 3. Record filled trade in Room database ONLY if genuinely filled
                if (!isCancelledWithoutFill && (filledOrder != null || filledExec != null || execQty > 0.0 || missingOrderId.isNotBlank())) {
                    val fillTime = filledOrder?.updatedTimeMillis?.takeIf { it > 0L }
                        ?: filledExec?.timeMillis?.takeIf { it > 0L }
                        ?: System.currentTimeMillis()
                    recordOrderFilled(
                        orderId = execOrderId,
                        side = missingSide,
                        price = finalExecPrice,
                        qty = execQty,
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
                    usdt = map["USDT"] ?: 0.0
                    mnt = map["MNT"] ?: 0.0
                }

                if (usdt > 0.0 && mnt > 0.0 && finalExecPrice > 0.0) {
                    val plan = RebalanceEngine.calculateGridOrders(
                        usdtBalance = usdt,
                        mntBalance = mnt,
                        basePrice = finalExecPrice,
                        stepPercent = step
                    )

                    if (plan.isValid) {
                        // Place new Limit Sell
                        val sellRes = createOrder(
                            side = "Sell",
                            orderType = "Limit",
                            qty = plan.sellMntQty,
                            price = plan.sellLimitPrice,
                            triggerReason = "GridStepUpSell"
                        )
                        sellRes.onSuccess { sid ->
                            preferences.activeSellOrderId = sid
                            log(LogLevel.INFO, callerTag, "Yeni Satış Limit Emri açıldı: $sid @ ${plan.sellLimitPrice}")
                        }

                        // Place new Limit Buy
                        val buyRes = createOrder(
                            side = "Buy",
                            orderType = "Limit",
                            qty = plan.buyMntQty,
                            price = plan.buyLimitPrice,
                            triggerReason = "GridStepDownBuy"
                        )
                        buyRes.onSuccess { bid ->
                            preferences.activeBuyOrderId = bid
                            log(LogLevel.INFO, callerTag, "Yeni Alış Limit Emri açıldı: $bid @ ${plan.buyLimitPrice}")
                        }

                        lastGridOrderPlacedTimeMs = System.currentTimeMillis()

                        log(
                            LogLevel.SUCCESS,
                            callerTag,
                            "Yeni ızgara limit emirleri açıldı. Yeni Baz: $${RebalanceEngine.format4(finalExecPrice)}"
                        )

                        return@withContext Result.success(
                            ReconciliationResult(
                                executedOrderFound = !isCancelledWithoutFill,
                                executedSide = if (!isCancelledWithoutFill) missingSide else null,
                                executedPrice = finalExecPrice,
                                executedQty = execQty,
                                executedOrderId = if (!isCancelledWithoutFill) execOrderId else "",
                                newBasePrice = finalExecPrice,
                                message = if (!isCancelledWithoutFill) "$missingSide emri gerçekleşti! Karşı emir iptal edilip yeni ızgara kuruldu."
                                          else "İptal edilen emir sonrası yeni ızgara kuruldu."
                            )
                        )
                    }
                }

                return@withContext Result.success(
                    ReconciliationResult(
                        executedOrderFound = !isCancelledWithoutFill,
                        executedSide = if (!isCancelledWithoutFill) missingSide else null,
                        executedPrice = finalExecPrice,
                        executedQty = execQty,
                        executedOrderId = if (!isCancelledWithoutFill) execOrderId else "",
                        newBasePrice = finalExecPrice,
                        message = if (!isCancelledWithoutFill) "$missingSide emri gerçekleşti." else "İptal tespit edildi."
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

                val latestFilled = if (isCancelledOnly) null else recentOrders.firstOrNull { it.isFilled || it.filledQtyValue > 0.0 }
                val latestExec = if (isCancelledOnly) null else recentExecutions.firstOrNull()

                val tickerPrice = getMntTicker().getOrNull()?.currentPrice ?: 0.0

                // Use stored base price first to prevent overwriting user-configured base prices
                val basePrice = if (preferences.lastRebalancePrice > 0.0) {
                    preferences.lastRebalancePrice
                } else if (tickerPrice > 0.0) {
                    tickerPrice
                } else if (latestFilled != null && latestFilled.avgPriceValue > 0.0) {
                    latestFilled.avgPriceValue
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
                        usdt = map["USDT"] ?: 0.0
                        mnt = map["MNT"] ?: 0.0
                    }

                    if (usdt > 0.0 && mnt > 0.0) {
                        val plan = RebalanceEngine.calculateGridOrders(
                            usdtBalance = usdt,
                            mntBalance = mnt,
                            basePrice = basePrice,
                            stepPercent = preferences.stepPercent
                        )
                        if (plan.isValid) {
                            val sellRes = createOrder("Sell", "Limit", plan.sellMntQty, plan.sellLimitPrice, "GridStepUpSell")
                            sellRes.onSuccess { preferences.activeSellOrderId = it }

                            val buyRes = createOrder("Buy", "Limit", plan.buyMntQty, plan.buyLimitPrice, "GridStepDownBuy")
                            buyRes.onSuccess { preferences.activeBuyOrderId = it }

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
                    cancelAllMntOrders()
                    preferences.activeBuyOrderId = ""
                    preferences.activeSellOrderId = ""

                    delay(1000)

                    // 2. Fetch fresh balances
                    var usdt = 0.0
                    var mnt = 0.0
                    getWalletBalance().onSuccess { map ->
                        usdt = map["USDT"] ?: 0.0
                        mnt = map["MNT"] ?: 0.0
                    }

                    if (usdt <= 0.0 && mnt <= 0.0) {
                        return@withContext Result.failure(Exception("Cüzdan bakiyesi okunamadı"))
                    }

                    // 3. Calculate grid with the EXACT new base price
                    val plan = RebalanceEngine.calculateGridOrders(
                        usdtBalance = usdt,
                        mntBalance = mnt,
                        basePrice = newPrice,
                        stepPercent = preferences.stepPercent
                    )

                    if (!plan.isValid) {
                        log(LogLevel.WARN, "BasePrice", "Izgara planı geçersiz: ${plan.validationMessage}")
                        return@withContext Result.failure(Exception(plan.validationMessage))
                    }

                    // 4. Place new orders on Bybit
                    val sellRes = createOrder("Sell", "Limit", plan.sellMntQty, plan.sellLimitPrice, "ManualBasePriceUpdate")
                    sellRes.onSuccess { preferences.activeSellOrderId = it }

                    val buyRes = createOrder("Buy", "Limit", plan.buyMntQty, plan.buyLimitPrice, "ManualBasePriceUpdate")
                    buyRes.onSuccess { preferences.activeBuyOrderId = it }

                    lastGridOrderPlacedTimeMs = System.currentTimeMillis()

                    log(
                        LogLevel.SUCCESS,
                        "BasePrice",
                        "Yeni baz fiyatla ($${RebalanceEngine.format4(newPrice)}) ızgara emirleri kuruldu. Alış: $${RebalanceEngine.format4(plan.buyLimitPrice)}, Satış: $${RebalanceEngine.format4(plan.sellLimitPrice)}"
                    )
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
        symbol: String? = "MNTUSDT",
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
        symbol: String? = "MNTUSDT",
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
        symbol: String? = "MNTUSDT",
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

            val buyExecs = executions.filter { it.isBuy }
            val sellExecs = executions.filter { it.isSell }

            val totalBuyQty = buyExecs.sumOf { it.qtyValue }
            val totalBuyValue = buyExecs.sumOf { it.totalValue }
            val avgBuyPrice = if (totalBuyQty > 0.0) totalBuyValue / totalBuyQty else 0.0

            val totalSellQty = sellExecs.sumOf { it.qtyValue }
            val totalSellValue = sellExecs.sumOf { it.totalValue }
            val avgSellPrice = if (totalSellQty > 0.0) totalSellValue / totalSellQty else 0.0

            val priceDiff = if (avgBuyPrice > 0.0 && avgSellPrice > 0.0) avgSellPrice - avgBuyPrice else 0.0
            val profitPcnt = if (avgBuyPrice > 0.0 && avgSellPrice > 0.0) (priceDiff / avgBuyPrice) * 100.0 else 0.0
            val netQty = totalBuyQty - totalSellQty

            // In Bybit Spot trading:
            // 1. Buy orders: fee is deducted in base coin (e.g. MNT). Value in USDT = feeValue * execPrice
            // 2. Sell orders: fee is deducted in quote coin (USDT). Value in USDT = feeValue
            val totalFee = executions.sumOf { exec ->
                if (exec.isBuy) {
                    val p = if (exec.priceValue > 0.0) exec.priceValue else if (avgBuyPrice > 0.0) avgBuyPrice else 0.0
                    exec.feeValue * p
                } else {
                    exec.feeValue
                }
            }

            val displaySymbol = if (symbol.isNullOrBlank() || symbol.equals("ALL", ignoreCase = true)) "Tüm Semboller" else symbol.uppercase()
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

            val analysis = TradeAnalysisResult(
                symbol = displaySymbol,
                daysRange = effectiveDays,
                dateRangeLabel = rangeLabel,
                totalBuyQty = totalBuyQty,
                totalBuyValue = totalBuyValue,
                avgBuyPrice = avgBuyPrice,
                buyTradeCount = buyExecs.size,
                totalSellQty = totalSellQty,
                totalSellValue = totalSellValue,
                avgSellPrice = avgSellPrice,
                sellTradeCount = sellExecs.size,
                priceDifference = priceDiff,
                profitPercentage = profitPcnt,
                netQty = netQty,
                totalFee = totalFee,
                executions = executions.sortedByDescending { it.timeMillis },
                fetchedAt = System.currentTimeMillis()
            )

            log(
                LogLevel.SUCCESS,
                "TradeAnalysis",
                "Borsa işlem geçmişi çekildi ($daysBack gün): ${executions.size} işlem. Ort Alış: $avgBuyPrice, Ort Satış: $avgSellPrice"
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
        try {
            database.withTransaction {
                val effectiveTime = if (fillTime > 0L) fillTime else System.currentTimeMillis()
            val existing = orderDao.getOrderByOrderId(orderId)
            if (existing != null) {
                orderDao.updateOrderStatus(
                    orderId = orderId,
                    status = "Filled",
                    filledQty = if (qty > 0.0) qty else existing.qty,
                    avgPrice = if (price > 0.0) price else existing.price,
                    fillTime = effectiveTime
                )
            } else {
                orderDao.insertOrder(
                    OrderEntity(
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
            }

            // Also keep exchange_trades in sync for instant analysis computation
            // IDEMPOTENCY GUARD: Check if a trade for this orderId already exists in exchange_trades
            val alreadyInTrades = if (orderId.isNotBlank()) exchangeTradeDao.hasTradeForOrder(orderId) else false
            if (!alreadyInTrades) {
                val execPrice = if (price > 0.0) price else existing?.price ?: 0.0
                val execQty = if (qty > 0.0) qty else existing?.qty ?: 0.0
                if (execPrice > 0.0 && execQty > 0.0) {
                    val execValue = execPrice * execQty
                    val execFee = execValue * 0.001
                    // Deterministic execId based on orderId to prevent duplicate insertions even with concurrent calls
                    val execId = if (orderId.isNotBlank()) "fill_$orderId" else "fill_$effectiveTime"
                    exchangeTradeDao.insertTrade(
                        ExchangeTradeEntity(
                            execId = execId,
                            orderId = orderId,
                            symbol = "MNTUSDT",
                            side = side,
                            orderPrice = execPrice,
                            orderQty = execQty,
                            orderType = "Limit",
                            execPrice = execPrice,
                            execQty = execQty,
                            execValue = execValue,
                            execFee = execFee,
                            timeMillis = effectiveTime,
                            isMaker = true
                        )
                    )
                }
            }
            } // Close withTransaction

            log(LogLevel.SUCCESS, "OrderHistory", "İşlem Room Veritabanına kaydedildi: $side $orderId @ $price")
        } catch (e: Exception) {
            log(LogLevel.WARN, "OrderHistory", "Veritabanı kayıt hatası: ${e.message}")
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
        symbol: String? = "MNTUSDT",
        daysBack: Int = 730,
        startTimestamp: Long? = null,
        apiKey: String = preferences.apiKey,
        apiSecret: String = preferences.apiSecret,
        isTestnet: Boolean = preferences.isTestnet,
        onProgress: ((currentWindow: Int, totalWindows: Int, fetchedCount: Int) -> Unit)? = null
    ): Result<TradeSyncResult> = withContext(Dispatchers.IO) {
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
                    val effectiveSymbol = if (exec.symbol.isNotBlank()) exec.symbol else (symbol ?: "MNTUSDT")
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
                        timeMillis = if (exec.timeMillis > 0) exec.timeMillis else System.currentTimeMillis(),
                        isMaker = exec.isMaker
                    )
                }

                exchangeTradeDao.insertTrades(entitiesToInsert)

                // Ayrıca botun Order tablosunda bu emirler yoksa orayı da senkronize edelim
                for (trade in entitiesToInsert) {
                    val orderExists = orderDao.getOrderByOrderId(trade.orderId) != null
                    if (!orderExists) {
                        orderDao.insertOrder(
                            OrderEntity(
                                orderId = trade.orderId,
                                orderLinkId = trade.orderLinkId,
                                symbol = trade.symbol,
                                side = trade.side,
                                orderType = trade.orderType.ifBlank { "Limit" },
                                price = trade.execPrice,
                                qty = trade.execQty,
                                status = "Filled",
                                filledQty = trade.execQty,
                                avgPrice = trade.execPrice,
                                timestamp = trade.timeMillis,
                                triggerReason = "BorsaSenkronizasyonu"
                            )
                        )
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

    suspend fun getLocalTradesCount(): Int = withContext(Dispatchers.IO) {
        exchangeTradeDao.getTradeCountSync()
    }

    suspend fun clearLocalExchangeTrades() = withContext(Dispatchers.IO) {
        exchangeTradeDao.clearAllTrades()
        log(LogLevel.INFO, "TradeSync", "Yerel borsa işlem geçmişi veritabanı temizlendi")
    }

    fun getAllExchangeTradesFlow() = exchangeTradeDao.getAllTrades()
}
