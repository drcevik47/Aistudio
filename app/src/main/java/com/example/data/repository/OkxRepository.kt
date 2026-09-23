package com.example.data.repository

import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.BotPreferences
import com.example.data.local.entity.ExchangeTradeEntity
import com.example.data.local.entity.LogEntity
import com.example.data.local.entity.LogLevel
import com.example.data.local.entity.OrderEntity
import androidx.room.withTransaction
import com.example.data.remote.model.TradeSyncResult
import com.example.data.remote.okx.OkxApiService
import com.example.data.remote.okx.OkxAuthInterceptor
import com.example.data.remote.okx.model.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

import com.example.ui.AssetBalance
class OkxRepository(
    private val preferences: BotPreferences,
    private val database: AppDatabase
) {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private fun createApiService(): OkxApiService {
        val apiKey = preferences.okxApiKey.trim()
        val apiSecret = preferences.okxApiSecret.trim()
        val passphrase = preferences.okxApiPassphrase.trim()
        val isTestnet = preferences.isTestnet 

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (com.example.BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
            redactHeader("OK-ACCESS-KEY")
            redactHeader("OK-ACCESS-SIGN")
            redactHeader("OK-ACCESS-TIMESTAMP")
            redactHeader("OK-ACCESS-PASSPHRASE")
            redactHeader("Authorization")
        }

        val authInterceptor = OkxAuthInterceptor(apiKey, apiSecret, passphrase, isTestnet)

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor(authInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        val baseUrl = "https://tr.okx.com"

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(OkxApiService::class.java)
    }


    suspend fun getTicker(): Result<OkxTicker> {
        return withContext(Dispatchers.IO) {
            try {
                val api = createApiService()
                val response = api.getTicker(preferences.okxSymbol)
                if (response.code == "0" && response.data.isNotEmpty()) {
                    Result.success(response.data.first())
                } else {
                    Result.failure(Exception(response.msg))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getWalletBalance(): Result<Map<String, AssetBalance>> {
        return withContext(Dispatchers.IO) {
            try {
                val api = createApiService()
                val map = mutableMapOf<String, AssetBalance>()
                
                var fetchSuccess = false
                var errorMsg = ""
                
                // 1. Try account/balance (Trading / Unified account)
                try {
                    val tradeRes = api.getBalance(null)
                    if (tradeRes.code == "0" && tradeRes.data.isNotEmpty()) {
                        tradeRes.data.first().details.forEach { detail ->
                            val totalQty = detail.eq.toDoubleOrNull() ?: detail.availEq.toDoubleOrNull() ?: detail.availBal.toDoubleOrNull() ?: 0.0
                            val availQty = detail.availEq.toDoubleOrNull() ?: detail.availBal.toDoubleOrNull() ?: 0.0
                            val totalUsdEq = detail.eqUsd?.toDoubleOrNull() ?: 0.0
                            
                            val availUsdEq = if (totalQty > 0.0) totalUsdEq * (availQty / totalQty) else 0.0

                            val current = map[detail.ccy]
                            val newQty = (current?.quantity ?: 0.0) + availQty
                            val newUsdEq = (current?.fiatValue ?: 0.0) + availUsdEq
                            map[detail.ccy] = AssetBalance(newQty, newUsdEq)
                        }
                        fetchSuccess = true
                        // Removed spammy log
                    } else {
                        errorMsg += "[account/balance: ${tradeRes.code} - ${tradeRes.msg}] "
                    }
                } catch(e: Exception) {
                    errorMsg += "[account/balance Ağ Hatası: ${e.message}] "
                }

                // 2. Try asset/balances (Funding account)
                if (!fetchSuccess || map.values.all { it.quantity == 0.0 }) {
                    try {
                        val fundRes = api.getAssetBalances(null)
                        if (fundRes.code == "0" && fundRes.data.isNotEmpty()) {
                            fundRes.data.forEach { asset ->
                                val availQty = asset.availBal.toDoubleOrNull() ?: 0.0
                                val current = map[asset.ccy]
                                val newQty = (current?.quantity ?: 0.0) + availQty
                                // Asset balances don't typically have eqUsd, so we just carry over or use 0
                                map[asset.ccy] = AssetBalance(newQty, current?.fiatValue ?: 0.0)
                            }
                            fetchSuccess = true
                            // Removed spammy log
                        } else {
                            errorMsg += "[asset/balances: ${fundRes.code} - ${fundRes.msg}] "
                        }
                    } catch(e: Exception) {
                        errorMsg += "[asset/balances Ağ Hatası: ${e.message}] "
                    }
                }
                
                if (fetchSuccess) {
                    Result.success(map)
                } else {
                    log(LogLevel.ERROR, "OKX_API", "Bakiye API Hatası: $errorMsg")
                    Result.failure(Exception(errorMsg))
                }
            } catch (e: Exception) {
                log(LogLevel.ERROR, "OKX_API", "Bakiye Kritik Ağ Hatası: ${e.message}")
                Result.failure(e)
            }
        }
    }

    private val okxInstrumentCache = java.util.concurrent.ConcurrentHashMap<String, com.example.data.remote.okx.model.OkxInstrument>()

    suspend fun getInstrumentInfo(symbol: String = preferences.okxSymbol): com.example.data.remote.okx.model.OkxInstrument? = withContext(Dispatchers.IO) {
        okxInstrumentCache[symbol]?.let { return@withContext it }
        try {
            val api = createApiService()
            val response = api.getInstruments(instType = "SPOT", instId = symbol)
            if (response.code == "0" && response.data.isNotEmpty()) {
                val inst = response.data.first()
                okxInstrumentCache[symbol] = inst
                return@withContext inst
            }
        } catch (e: Exception) {
            Log.w("OkxRepository", "Failed to fetch OKX instrument info: ${e.message}")
        }
        null
    }

    fun getCachedInstrumentInfo(symbol: String = preferences.okxSymbol): com.example.data.remote.okx.model.OkxInstrument? {
        return okxInstrumentCache[symbol]
    }

    fun getPrecisionForSymbol(symbol: String = preferences.okxSymbol): Pair<Int?, Int?> {
        val inst = okxInstrumentCache[symbol]
        val qtyDecimals = inst?.lotSz?.let { com.example.bot.RebalanceEngine.stepToDecimals(it) }
        val priceDecimals = inst?.tickSz?.let { com.example.bot.RebalanceEngine.stepToDecimals(it) }
        return Pair(qtyDecimals, priceDecimals)
    }

    fun formatQty(qty: Double, symbol: String = preferences.okxSymbol, price: Double? = null): String {
        val stepStr = okxInstrumentCache[symbol]?.lotSz
        if (!stepStr.isNullOrBlank()) {
            return com.example.bot.RebalanceEngine.formatWithStep(qty, stepStr, java.math.RoundingMode.FLOOR)
        }
        val decimals = run {
            val refPrice = price ?: preferences.okxLastRebalancePrice
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
        val truncated = kotlin.math.floor(qty * factor) / factor
        return String.format(java.util.Locale.US, "%.${decimals}f", truncated)
    }

    fun formatPrice(price: Double, symbol: String = preferences.okxSymbol): String {
        val tickStr = okxInstrumentCache[symbol]?.tickSz
        if (!tickStr.isNullOrBlank()) {
            return com.example.bot.RebalanceEngine.formatWithStep(price, tickStr, java.math.RoundingMode.HALF_UP)
        }
        val decimals = when {
            price >= 1000.0 -> 2
            price >= 1.0 -> 4
            price >= 0.01 -> 6
            else -> 8
        }
        return String.format(java.util.Locale.US, "%.${decimals}f", price).trimEnd('0').trimEnd('.')
    }

    private fun parseOkxErrorMessage(code: String, msg: String): String {
        val hint = when (code) {
            "50004" -> "API anahtarı veya passphrase geçersiz"
            "50011" -> "İstek sınırı aşıldı (Rate Limit): Lütfen bekleyin"
            "50013" -> "Yetki hatası: API anahtarınızda Alım-Satım (Trade) izni açık olmalıdır"
            "51000" -> "Parametre hatası: Miktar veya fiyat borsa kurallarına uymuyor"
            "51001" -> "Zaman aşımı: Cihaz saati OKX sunucusu ile senkronize değil"
            "51004" -> "Yetersiz bakiye: İşlem için yeterli USDT veya kripto varlık yok"
            "51006" -> "Emir tutarı çok küçük: OKX minimum spot emir tutarını karşılamıyor"
            else -> ""
        }
        return if (hint.isNotBlank()) "OKX Hata $code ($hint): $msg" else "OKX Hata [$code]: $msg"
    }

    suspend fun createOrder(
        side: String,
        orderType: String, // "market" or "limit"
        qty: Double,
        price: Double? = null,
        clOrdId: String? = null
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            val effectiveClOrdId = clOrdId?.takeIf { it.isNotBlank() }
                ?: "okx_${System.currentTimeMillis()}_${(100..999).random()}"
            try {
                val api = createApiService()
                val formattedQty = formatQty(qty, preferences.okxSymbol)
                val request = com.example.data.remote.okx.model.OkxOrderRequest(
                    instId = preferences.okxSymbol,
                    tdMode = "cash",
                    side = side.lowercase(),
                    ordType = orderType.lowercase(),
                    sz = formattedQty,
                    px = price?.let { formatPrice(it, preferences.okxSymbol) },
                    tgtCcy = "base_ccy", // Force quantity to mean base coin (e.g. BTC)
                    clOrdId = effectiveClOrdId
                )
                try {
                    val response = api.placeOrder(request)
                    if (response.code == "0" && response.data.isNotEmpty()) {
                        val resData = response.data.first()
                        if (resData.sCode == "0") {
                            log(LogLevel.INFO, "OKX_ORDER", "Emir iletildi (${side} $qty). OrderId: ${resData.ordId}")
                            Result.success(resData.ordId)
                        } else {
                            // Check for duplicate clOrdId codes (e.g. 51000, 51007, 51008, 51121)
                            if (resData.sCode in listOf("51000", "51007", "51008", "51121")) {
                                log(LogLevel.WARN, "OKX_ORDER", "OKX clOrdId ($effectiveClOrdId) zaten mevcut döndü (${resData.sCode}). Borsa sorgulanıyor...")
                                val checkRes = api.getOrder(instId = preferences.okxSymbol, clOrdId = effectiveClOrdId)
                                if (checkRes.code == "0" && checkRes.data.isNotEmpty()) {
                                    val found = checkRes.data.first()
                                    log(LogLevel.SUCCESS, "OKX_ORDER", "Çifte emir engellendi: Mevcut OKX emri tespit edildi (${found.ordId})")
                                    return@withContext Result.success(found.ordId)
                                }
                            }
                            val friendlyMsg = parseOkxErrorMessage(resData.sCode, resData.sMsg)
                            log(LogLevel.ERROR, "OKX_ORDER", friendlyMsg)
                            Result.failure(Exception(friendlyMsg))
                        }
                    } else {
                        val friendlyMsg = parseOkxErrorMessage(response.code, response.msg)
                        log(LogLevel.ERROR, "OKX_ORDER", friendlyMsg)
                        Result.failure(Exception(friendlyMsg))
                    }
                } catch (networkEx: Exception) {
                    if (networkEx is kotlinx.coroutines.CancellationException) throw networkEx
                    log(LogLevel.WARN, "OKX_ORDER", "Ağ zaman aşımı/hatası (${networkEx.message}). Emir OKX'e ulaşmış olabilir, $effectiveClOrdId sorgulanıyor...")
                    kotlinx.coroutines.delay(800)
                    try {
                        val checkRes = api.getOrder(instId = preferences.okxSymbol, clOrdId = effectiveClOrdId)
                        if (checkRes.code == "0" && checkRes.data.isNotEmpty()) {
                            val found = checkRes.data.first()
                            log(LogLevel.SUCCESS, "OKX_ORDER", "Zaman aşımı sonrası emir OKX'te kurtarıldı (${found.ordId}). Çifte emir engellendi.")
                            return@withContext Result.success(found.ordId)
                        }
                    } catch (checkEx: Exception) {
                        android.util.Log.w("OkxRepository", "Zaman aşımı sonrası kontrol hatası: ${checkEx.message}")
                    }
                    throw networkEx
                }
            } catch (e: Exception) {
                log(LogLevel.ERROR, "OKX_ORDER", "Ağ Hatası: ${e.message}")
                Result.failure(e)
            }
        }
    }


    private val reconcileMutex = kotlinx.coroutines.sync.Mutex()

    suspend fun reconcileGridOrders(
        callerTag: String = "OkxReconcile",
        triggeringFilledOrder: com.example.data.remote.okx.model.OkxOrderDetails? = null
    ): Result<com.example.data.repository.ReconciliationResult> = withContext(Dispatchers.IO) {
        if (!preferences.isConfigured || !preferences.isOkxBotActive) {
            return@withContext Result.success(com.example.data.repository.ReconciliationResult(message = "OKX Bot aktif değil"))
        }

        if (!reconcileMutex.tryLock()) {
            return@withContext Result.success(com.example.data.repository.ReconciliationResult(message = "Başka bir mutabakat devam ediyor"))
        }

        try {
            val apiKey = preferences.okxApiKey
            val apiSecret = preferences.okxApiSecret
            if (apiKey.isBlank() || apiSecret.isBlank()) {
                return@withContext Result.failure(Exception("OKX API Key eksik"))
            }

            val openOrdersRes = getPendingOrders()
            if (openOrdersRes.isFailure) {
                val err = openOrdersRes.exceptionOrNull()?.message ?: "Açık emirler alınamadı"
                log(LogLevel.WARN, callerTag, "OKX Açık emir kontrolü başarısız: $err")
                return@withContext Result.failure(Exception(err))
            }

            val openOrders = openOrdersRes.getOrDefault(emptyList())
            val openBuyOrder = openOrders.firstOrNull { it.side.equals("buy", ignoreCase = true) }
            val openSellOrder = openOrders.firstOrNull { it.side.equals("sell", ignoreCase = true) }

            val activeBuyId = preferences.okxActiveBuyOrderId
            val activeSellId = preferences.okxActiveSellOrderId

            if (openBuyOrder != null && openSellOrder != null) {
                if (activeBuyId != openBuyOrder.ordId) preferences.okxActiveBuyOrderId = openBuyOrder.ordId
                if (activeSellId != openSellOrder.ordId) preferences.okxActiveSellOrderId = openSellOrder.ordId
                return@withContext Result.success(com.example.data.repository.ReconciliationResult(message = "OKX Grid emirleri sorunsuz çalışıyor"))
            }

            val tickerRes = getTicker()
            if (tickerRes.isFailure) return@withContext Result.failure(Exception("OKX Ticker alınamadı"))
            val currentPrice = tickerRes.getOrNull()?.last?.toDoubleOrNull() ?: 0.0

            if ((openBuyOrder != null && openSellOrder == null) || (openBuyOrder == null && openSellOrder != null)) {
                val missingSide = if (triggeringFilledOrder != null) {
                    triggeringFilledOrder.side.replaceFirstChar { it.uppercase() }
                } else if (openBuyOrder == null) "Buy" else "Sell"

                val remainingOrder = openBuyOrder ?: openSellOrder!!
                val missingOrderId = if (triggeringFilledOrder != null && triggeringFilledOrder.ordId.isNotBlank()) {
                    triggeringFilledOrder.ordId
                } else if (missingSide.equals("Sell", ignoreCase = true)) {
                    activeSellId
                } else {
                    activeBuyId
                }

                // CRITICAL SAFETY SHIELD: Positive Proof of Fill Requirement
                // An order missing from open orders CANNOT be assumed filled without explicit exchange proof.
                var confirmedFilledOrder: com.example.data.remote.okx.model.OkxOrderDetails? = triggeringFilledOrder?.takeIf {
                    it.state.equals("filled", ignoreCase = true) || ((it.accFillSz?.toDoubleOrNull() ?: 0.0) > 0.0)
                }
                var isExplicitlyCancelled = false

                // 1. If not directly confirmed via WebSocket event, query OKX order history
                if (confirmedFilledOrder == null) {
                    try {
                        val api = createApiService()
                        val historyRes = api.getOrdersHistory(
                            instType = "SPOT",
                            instId = preferences.okxSymbol,
                            ordId = missingOrderId.ifBlank { null },
                            limit = 10
                        )
                        if (historyRes.code == "0" && historyRes.data.isNotEmpty()) {
                            val historyOrder = if (missingOrderId.isNotBlank()) {
                                historyRes.data.firstOrNull { it.ordId == missingOrderId }
                            } else {
                                historyRes.data.firstOrNull { it.side.equals(missingSide, ignoreCase = true) }
                            }
                            if (historyOrder != null) {
                                if (historyOrder.state.equals("filled", ignoreCase = true) || 
                                    ((historyOrder.accFillSz.toDoubleOrNull() ?: 0.0) > 0.0)) {
                                    confirmedFilledOrder = historyOrder
                                } else if (historyOrder.state.equals("canceled", ignoreCase = true) ||
                                           historyOrder.state.equals("order_failed", ignoreCase = true)) {
                                    isExplicitlyCancelled = true
                                }
                            }
                        }
                    } catch (e: Exception) {
                        log(LogLevel.WARN, callerTag, "OKX Emir geçmişi sorgulama uyarısı: ${e.message}")
                    }
                }

                // 2. Also check OKX fills endpoint if still not confirmed
                if (confirmedFilledOrder == null && !isExplicitlyCancelled) {
                    try {
                        val api = createApiService()
                        val fillsRes = api.getFills(
                            instType = "SPOT",
                            instId = preferences.okxSymbol,
                            limit = 10
                        )
                        if (fillsRes.code == "0" && fillsRes.data.isNotEmpty()) {
                            val fillItem = if (missingOrderId.isNotBlank()) {
                                fillsRes.data.firstOrNull { it.ordId == missingOrderId }
                            } else {
                                fillsRes.data.firstOrNull { it.side.equals(missingSide, ignoreCase = true) }
                            }
                            if (fillItem != null) {
                                val fillQty = fillItem.fillSz.toDoubleOrNull() ?: 0.0
                                val fillPx = fillItem.fillPx.toDoubleOrNull() ?: 0.0
                                if (fillQty > 0.0 && fillPx > 0.0) {
                                    confirmedFilledOrder = com.example.data.remote.okx.model.OkxOrderDetails(
                                        ordId = fillItem.ordId.ifBlank { missingOrderId },
                                        clOrdId = "",
                                        instId = fillItem.instId.ifBlank { preferences.okxSymbol },
                                        side = fillItem.side.ifBlank { missingSide },
                                        px = fillItem.fillPx,
                                        sz = fillItem.fillSz,
                                        state = "filled",
                                        accFillSz = fillItem.fillSz,
                                        avgPx = fillItem.fillPx,
                                        cTime = fillItem.ts,
                                        uTime = fillItem.ts
                                    )
                                }
                            }
                        }
                    } catch (e: Exception) {
                        log(LogLevel.WARN, callerTag, "OKX Fills sorgulama uyarısı: ${e.message}")
                    }
                }

                // If neither filled nor cancelled can be verified from the exchange: SAFE WAIT!
                if (confirmedFilledOrder == null && !isExplicitlyCancelled) {
                    log(
                        LogLevel.WARN,
                        callerTag,
                        "OKX Mutabakat: $missingSide emri ($missingOrderId) açık emirler arasında yok, ancak borsa dolum veya iptal kaydı henüz teyit edilemedi. Sahte dolum açılmaması için bekleniyor."
                    )
                    return@withContext Result.failure(Exception("OKX $missingSide emri için borsadan kesin dolum veya iptal kanıtı henüz alınamadı (işlem beklemede)."))
                }

                val lastBase = preferences.okxLastRebalancePrice
                val step = preferences.okxStepPercent

                val expectedGridPrice = if (missingSide.equals("Sell", ignoreCase = true)) {
                    if (lastBase > 0.0) lastBase * (1.0 + step / 100.0) else 0.0
                } else {
                    if (lastBase > 0.0) lastBase * (1.0 - step / 100.0) else 0.0
                }

                val directPrice = confirmedFilledOrder?.avgPx?.toDoubleOrNull()?.takeIf { it > 0.0 }
                    ?: confirmedFilledOrder?.px?.toDoubleOrNull()?.takeIf { it > 0.0 }

                val rawPrice = directPrice ?: (if (expectedGridPrice > 0.0) expectedGridPrice else currentPrice)
                val isPriceWithinGridBounds = if (expectedGridPrice > 0.0 && rawPrice > 0.0) {
                    val maxAllowedDeviation = (step / 100.0) * 2.0
                    val deviation = Math.abs(rawPrice - expectedGridPrice) / expectedGridPrice
                    deviation <= maxAllowedDeviation
                } else true

                val finalExecPrice = if (isPriceWithinGridBounds && rawPrice > 0.0) {
                    rawPrice
                } else {
                    log(LogLevel.WARN, callerTag, "OKX Fiyat sapması engellendi (Tespit: $rawPrice, Beklenen: $expectedGridPrice). Güvenli ızgara fiyatı kullanılıyor.")
                    if (expectedGridPrice > 0.0) expectedGridPrice else lastBase
                }
                val execOrderId = confirmedFilledOrder?.ordId?.ifBlank { missingOrderId } ?: missingOrderId.ifBlank { "okx_exec_${System.currentTimeMillis()}" }

                // Retrieve verified quantity from exchange: NEVER fallback to unverified remainingOrder size!
                val verifiedQty = confirmedFilledOrder?.accFillSz?.toDoubleOrNull()?.takeIf { it > 0.0 }
                    ?: confirmedFilledOrder?.sz?.toDoubleOrNull()?.takeIf { it > 0.0 }
                    ?: 0.0

                if (isExplicitlyCancelled) {
                    log(LogLevel.WARN, callerTag, "OKX $missingSide emri ($missingOrderId) borsada iptal edilmiş (dolum yok). Karşı açık emir temizlenip yeni ızgara açılıyor...")
                } else {
                    if (verifiedQty <= 0.0) {
                        log(LogLevel.ERROR, callerTag, "OKX $missingSide emri için borsa dolum miktarı 0 veya geçersiz ($verifiedQty). Yeni ızgara açılması durduruldu.")
                        return@withContext Result.failure(Exception("OKX Geçersiz dolum miktarı ($verifiedQty)"))
                    }

                    log(LogLevel.SUCCESS, callerTag, "OKX Mutabakat: $missingSide emri GERÇEKLEŞMİŞ! Fiyat: $finalExecPrice, Miktar: $verifiedQty ($execOrderId)")

                    recordOrderFilled(
                        orderId = execOrderId,
                        side = missingSide,
                        price = finalExecPrice,
                        qty = verifiedQty,
                        triggerReason = "reconciliation"
                    )
                }

                log(LogLevel.INFO, callerTag, "OKX Karşı açık emir (${remainingOrder.side} ${remainingOrder.ordId}) iptal ediliyor...")
                val cancelRes = cancelOrder(remainingOrder.ordId)
                if (cancelRes.isFailure) {
                    val cancelErr = cancelRes.exceptionOrNull()?.message ?: "İptal başarısız"
                    log(LogLevel.ERROR, callerTag, "OKX Karşı açık emir (${remainingOrder.ordId}) iptal EDİLEMEDİ: $cancelErr. Çifte emir oluşmaması için yeni ızgara açılması durduruldu.")
                    return@withContext Result.failure(Exception("OKX Karşı emir (${remainingOrder.ordId}) iptal edilemediği için yeni ızgara açılamaz: $cancelErr"))
                }

                preferences.okxLastRebalancePrice = if (isExplicitlyCancelled) (if (currentPrice > 0.0) currentPrice else finalExecPrice) else finalExecPrice
                preferences.okxActiveBuyOrderId = ""
                preferences.okxActiveSellOrderId = ""
            } else if (openBuyOrder == null && openSellOrder == null) {
                preferences.okxActiveBuyOrderId = ""
                preferences.okxActiveSellOrderId = ""
            }

            val balanceRes = getWalletBalance()
            if (balanceRes.isFailure) return@withContext Result.failure(Exception("OKX Bakiye alınamadı"))
            val balances = balanceRes.getOrNull() ?: emptyMap()
            val usdtBalance = balances["USDT"]?.quantity ?: 0.0
            val baseCoinBalance = balances[preferences.okxBaseCoin]?.quantity ?: 0.0

            val basePrice = if (preferences.okxLastRebalancePrice > 0.0) preferences.okxLastRebalancePrice else currentPrice

            val inst = getInstrumentInfo(preferences.okxSymbol)
            val (qtyPrec, pricePrec) = getPrecisionForSymbol(preferences.okxSymbol)
            val minSz = inst?.minSz?.toDoubleOrNull()
            val minAmt = maxOf(2.0, (minSz ?: 0.0) * basePrice)
            val gridPlan = com.example.bot.RebalanceEngine.calculateGridOrders(
                usdtBalance = usdtBalance,
                baseCoinBalance = baseCoinBalance,
                basePrice = basePrice,
                stepPercent = preferences.okxStepPercent,
                qtyPrecision = qtyPrec,
                pricePrecision = pricePrec,
                tickSize = inst?.tickSz,
                lotStep = inst?.lotSz,
                minOrderAmt = minAmt,
                minOrderQty = minSz
            )

            if (!gridPlan.isValid) {
                return@withContext Result.failure(Exception("OKX Grid planı geçersiz: ${gridPlan.validationMessage}"))
            }

            val cycleTag = "okx_${System.currentTimeMillis()}"
            // Place Sell Order
            val sellRes = createOrder(
                side = "sell",
                orderType = "limit",
                qty = gridPlan.sellBaseQty,
                price = gridPlan.sellLimitPrice,
                clOrdId = "${cycleTag}_s"
            )

            // Place Buy Order
            val buyRes = createOrder(
                side = "buy",
                orderType = "limit",
                qty = gridPlan.buyBaseQty,
                price = gridPlan.buyLimitPrice,
                clOrdId = "${cycleTag}_b"
            )

            if (sellRes.isSuccess && buyRes.isSuccess) {
                val sid = sellRes.getOrNull().orEmpty()
                val bid = buyRes.getOrNull().orEmpty()
                preferences.okxActiveSellOrderId = sid
                preferences.okxActiveBuyOrderId = bid
                log(LogLevel.INFO, callerTag, "OKX Yeni Satış Limit Emri: $sid @ ${gridPlan.sellLimitPrice}")
                log(LogLevel.INFO, callerTag, "OKX Yeni Alış Limit Emri: $bid @ ${gridPlan.buyLimitPrice}")
                Result.success(com.example.data.repository.ReconciliationResult(message = "OKX Grid emirleri yeniden kuruldu"))
            } else {
                // ASYMMETRIC FAILURE GUARD / ROLLBACK
                val sid = sellRes.getOrNull().orEmpty()
                val bid = buyRes.getOrNull().orEmpty()
                if (sellRes.isSuccess && sid.isNotBlank()) {
                    log(LogLevel.ERROR, callerTag, "OKX Alış emri açılamadı (${buyRes.exceptionOrNull()?.message}). Açılan satış emri ($sid) geri alınıyor...")
                    cancelOrder(sid)
                }
                if (buyRes.isSuccess && bid.isNotBlank()) {
                    log(LogLevel.ERROR, callerTag, "OKX Satış emri açılamadı (${sellRes.exceptionOrNull()?.message}). Açılan alış emri ($bid) geri alınıyor...")
                    cancelOrder(bid)
                }
                preferences.okxActiveSellOrderId = ""
                preferences.okxActiveBuyOrderId = ""
                val errMsg = "OKX Izgara tam açılamadı. Satış: ${sellRes.exceptionOrNull()?.message ?: "OK"}, Alış: ${buyRes.exceptionOrNull()?.message ?: "OK"}"
                log(LogLevel.ERROR, callerTag, errMsg)
                Result.failure(Exception(errMsg))
            }

        } finally {
            reconcileMutex.unlock()
        }
    }

    suspend fun getPendingOrders(): Result<List<OkxOrderDetails>> {
        return withContext(Dispatchers.IO) {
            try {
                val api = createApiService()
                val response = api.getPendingOrders(instId = preferences.okxSymbol)
                if (response.code == "0") {
                    Result.success(response.data)
                } else {
                    Result.failure(Exception("${response.code} - ${response.msg}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun cancelOrder(orderId: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val api = createApiService()
                val request = OkxCancelOrderRequest(
                    instId = preferences.okxSymbol,
                    ordId = orderId
                )
                val response = api.cancelOrder(request)
                if (response.code == "0" && response.data.isNotEmpty()) {
                    val resData = response.data.first()
                    if (resData.sCode == "0") {
                        Result.success(resData.ordId)
                    } else if (resData.sCode in listOf("51400", "51401", "51402")) {
                        // Order already cancelled, filled or doesn't exist on active book.
                        // Check if it was actually FILLED on OKX so we don't lose the fill record!
                        try {
                            val checkRes = api.getOrder(instId = preferences.okxSymbol, ordId = orderId)
                            if (checkRes.code == "0" && checkRes.data.isNotEmpty()) {
                                val ord = checkRes.data.first()
                                if (ord.state.equals("filled", ignoreCase = true) || (ord.accFillSz?.toDoubleOrNull() ?: 0.0) > 0.0) {
                                    log(LogLevel.WARN, "OKX_CANCEL", "İptal edilmek istenen emir ($orderId) OKX'te DOLMUŞ! Dolum kaydı işleniyor.")
                                    val px = ord.avgPx?.toDoubleOrNull() ?: ord.px?.toDoubleOrNull() ?: 0.0
                                    val sz = ord.accFillSz?.toDoubleOrNull() ?: 0.0
                                    if (px > 0.0 && sz > 0.0) {
                                        recordOrderFilled(
                                            orderId = orderId,
                                            side = ord.side.replaceFirstChar { it.uppercase() },
                                            price = px,
                                            qty = sz,
                                            triggerReason = "CancelCheckFill"
                                        )
                                    }
                                }
                            }
                        } catch (ex: Exception) {
                            android.util.Log.w("OkxRepository", "Cancel fill check hatası: ${ex.message}")
                        }
                        Result.success(orderId)
                    } else {
                        Result.failure(Exception("${resData.sCode} - ${resData.sMsg}"))
                    }
                } else {
                    Result.failure(Exception("${response.code} - ${response.msg}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun log(level: LogLevel, tag: String, message: String, details: String = "") {
        withContext(Dispatchers.IO) {
            database.logDao().insertLog(
                LogEntity(
                    exchange = "OKX",
                    level = level.name,
                    tag = tag,
                    message = message,
                    details = details
                )
            )
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
            log(LogLevel.WARN, "OkxOrderFill", "Dolum kaydı reddedildi: Geçersiz fiyat ($price) veya miktar ($qty) ($orderId)")
            return@withContext
        }
        try {
            database.withTransaction {
                val effectiveTime = if (fillTime > 0L) fillTime else System.currentTimeMillis()
                val orderDao = database.orderDao()
                val exchangeTradeDao = database.exchangeTradeDao()
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
                        com.example.data.local.entity.OrderEntity(
                            exchange = "OKX",
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

                val alreadyInTrades = if (orderId.isNotBlank()) exchangeTradeDao.hasTradeForOrder(orderId) else false
                if (!alreadyInTrades) {
                    val execPrice = if (price > 0.0) price else existing?.price ?: preferences.okxLastRebalancePrice
                    val execQty = if (qty > 0.0) qty else existing?.qty ?: 0.0
                    if (execPrice > 0.0 && execQty > 0.0) {
                        val execValue = execPrice * execQty
                        val execFee = execValue * 0.001
                        val execId = if (orderId.isNotBlank()) "fill_$orderId" else "fill_$effectiveTime"
                        exchangeTradeDao.insertTrade(
                            com.example.data.local.entity.ExchangeTradeEntity(
                                exchange = "OKX",
                                execId = execId,
                                orderId = orderId,
                                symbol = preferences.okxSymbol,
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
            }
        } catch (e: Exception) {
            Log.e("OkxRepository", "recordOrderFilled error: ${e.message}")
        }
    }
    suspend fun syncTradesFromExchange(
        symbol: String? = null,
        daysBack: Int = 30,
        startTimestamp: Long? = null,
        onProgress: ((currentWindow: Int, totalWindows: Int, fetchedCount: Int) -> Unit)? = null
    ): Result<TradeSyncResult> {
        return withContext(Dispatchers.IO) {
            try {
                val api = createApiService()
                val targetSymbol = symbol ?: preferences.okxSymbol
                val now = System.currentTimeMillis()
                val effectiveStartTime = startTimestamp ?: (now - daysBack.toLong() * 24L * 3600L * 1000L)

                val allFills = mutableListOf<OkxFill>()
                
                // 1. Fetch recent fills (last 3 days, contains immediately filled trades)
                try {
                    val recentRes = api.getFills(
                        instType = "SPOT",
                        instId = targetSymbol,
                        limit = 100,
                        begin = effectiveStartTime
                    )
                    if (recentRes.code == "0" && recentRes.data.isNotEmpty()) {
                        allFills.addAll(recentRes.data)
                    }
                } catch (e: Exception) {
                    Log.w("OkxRepository", "getFills (recent) warning: ${e.message}")
                }

                var currentEnd: Long? = null
                var page = 0
                val maxPages = 20
                var hasMore = true

                while (hasMore && page < maxPages) {
                    page++
                    val response = api.getFillsHistory(
                        instType = "SPOT",
                        instId = targetSymbol,
                        limit = 100,
                        begin = effectiveStartTime,
                        end = currentEnd
                    )

                    if (response.code != "0") {
                        if (allFills.isEmpty()) {
                            return@withContext Result.failure(Exception("${response.code} - ${response.msg}"))
                        }
                        break
                    }

                    val fills = response.data ?: emptyList()
                    if (fills.isEmpty()) {
                        hasMore = false
                    } else {
                        allFills.addAll(fills)
                        onProgress?.invoke(page, maxPages, allFills.size)

                        if (fills.size < 100) {
                            hasMore = false
                        } else {
                            val oldestTs = fills.mapNotNull { it.ts.toLongOrNull() }.minOrNull()
                            if (oldestTs != null && oldestTs > effectiveStartTime && (currentEnd == null || oldestTs < currentEnd)) {
                                currentEnd = oldestTs
                            } else {
                                hasMore = false
                            }
                        }
                    }
                }

                // Deduplicate within-batch fills from getFills and getFillsHistory overlapping range
                val remoteFills = allFills.distinctBy { fill ->
                    fill.billId.ifBlank { fill.ordId }
                }
                val exchangeTradeDao = database.exchangeTradeDao()
                val existingExecIds = exchangeTradeDao.getAllExecIds().toHashSet()
                val existingOrderIds = exchangeTradeDao.getAllOrderIds().toHashSet()
                val existingInDbCount = existingExecIds.size

                val newFills = remoteFills.filter { fill ->
                    val execId = fill.billId.ifBlank { fill.ordId }
                    val ordId = fill.ordId
                    !existingExecIds.contains(execId) &&
                    !existingExecIds.contains("fill_$ordId") &&
                    (ordId.isBlank() || !existingOrderIds.contains(ordId))
                }

                val newlyAddedExecutionDtos = newFills.map { fill ->
                    com.example.data.remote.model.BybitExecutionDto(
                        symbol = fill.instId,
                        orderId = fill.ordId,
                        side = fill.side.replaceFirstChar { it.uppercase() },
                        orderPrice = fill.fillPx,
                        orderQty = fill.fillSz,
                        orderType = "Limit",
                        execId = fill.billId.ifBlank { fill.ordId },
                        execPrice = fill.fillPx,
                        execQty = fill.fillSz,
                        execType = "Trade",
                        execValue = ((fill.fillPx.toDoubleOrNull() ?: 0.0) * (fill.fillSz.toDoubleOrNull() ?: 0.0)).toString(),
                        execFee = fill.fee,
                        feeCurrency = fill.feeCcy,
                        execTime = fill.ts,
                        isMaker = fill.execType.equals("M", ignoreCase = true)
                    )
                }

                if (newFills.isNotEmpty()) {
                    val entitiesToInsert = newFills.map { fill ->
                        com.example.data.local.entity.ExchangeTradeEntity(
                            exchange = "OKX",
                            execId = fill.billId.ifBlank { fill.ordId },
                            orderId = fill.ordId,
                            symbol = fill.instId,
                            side = fill.side.replaceFirstChar { it.uppercase() },
                            orderPrice = fill.fillPx.toDoubleOrNull() ?: 0.0,
                            orderQty = fill.fillSz.toDoubleOrNull() ?: 0.0,
                            orderType = "Limit",
                            execPrice = fill.fillPx.toDoubleOrNull() ?: 0.0,
                            execQty = fill.fillSz.toDoubleOrNull() ?: 0.0,
                            execValue = (fill.fillPx.toDoubleOrNull() ?: 0.0) * (fill.fillSz.toDoubleOrNull() ?: 0.0),
                            execFee = fill.fee.toDoubleOrNull() ?: 0.0,
                            feeCurrency = fill.feeCcy,
                            timeMillis = fill.ts.toLongOrNull() ?: System.currentTimeMillis(),
                            isMaker = fill.execType.equals("M", ignoreCase = true)
                        )
                    }
                    exchangeTradeDao.insertTrades(entitiesToInsert)
                }

                val totalInDb = exchangeTradeDao.getTradeCountSync()

                val okxExecutions = remoteFills.map { fill ->
                    com.example.data.remote.model.BybitExecutionDto(
                        symbol = fill.instId,
                        orderId = fill.ordId,
                        side = fill.side.replaceFirstChar { it.uppercase() },
                        orderPrice = fill.fillPx,
                        orderQty = fill.fillSz,
                        orderType = "Limit",
                        execId = fill.billId.ifBlank { fill.ordId },
                        execPrice = fill.fillPx,
                        execQty = fill.fillSz,
                        execType = "Trade",
                        execValue = ((fill.fillPx.toDoubleOrNull() ?: 0.0) * (fill.fillSz.toDoubleOrNull() ?: 0.0)).toString(),
                        execFee = fill.fee,
                        feeCurrency = fill.feeCcy,
                        execTime = fill.ts,
                        isMaker = fill.execType.equals("M", ignoreCase = true)
                    )
                }

                val okxAnalysis = com.example.bot.RebalanceEngine.calculateTradeAnalysis(
                    symbol = targetSymbol,
                    executions = okxExecutions,
                    daysRange = daysBack,
                    dateRangeLabel = "Son $daysBack Gün (OKX)"
                )

                val syncResult = TradeSyncResult(
                    totalFetched = remoteFills.size,
                    existingInDb = existingInDbCount,
                    newlyAddedCount = newFills.size,
                    totalInDb = totalInDb,
                    newlyAddedTrades = newlyAddedExecutionDtos,
                    analysis = okxAnalysis
                )
                Result.success(syncResult)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
