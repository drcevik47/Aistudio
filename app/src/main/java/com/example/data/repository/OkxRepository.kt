package com.example.data.repository

import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.BotPreferences
import com.example.data.local.entity.ExchangeTradeEntity
import com.example.data.local.entity.LogEntity
import com.example.data.local.entity.LogLevel
import com.example.data.local.entity.OrderEntity
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
            level = HttpLoggingInterceptor.Level.BODY 
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

    suspend fun getWalletBalance(): Result<Map<String, Double>> {
        return withContext(Dispatchers.IO) {
            try {
                val api = createApiService()
                val map = mutableMapOf<String, Double>()
                
                val ccyParam = "${preferences.okxBaseCoin},USDT"
                var fetchSuccess = false
                var errorMsg = ""
                
                // 1. Try account/balance (Trading / Unified account)
                try {
                    val tradeRes = api.getBalance(ccyParam)
                    if (tradeRes.code == "0" && tradeRes.data.isNotEmpty()) {
                        tradeRes.data.first().details.forEach { detail ->
                            val avail = detail.availEq.toDoubleOrNull() ?: detail.availBal.toDoubleOrNull() ?: 0.0
                            map[detail.ccy] = (map[detail.ccy] ?: 0.0) + avail
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
                if (!fetchSuccess || map.values.all { it == 0.0 }) {
                    try {
                        val fundRes = api.getAssetBalances(ccyParam)
                        if (fundRes.code == "0" && fundRes.data.isNotEmpty()) {
                            fundRes.data.forEach { asset ->
                                val avail = asset.availBal.toDoubleOrNull() ?: 0.0
                                map[asset.ccy] = (map[asset.ccy] ?: 0.0) + avail
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

    suspend fun createOrder(
        side: String,
        orderType: String, // "market" or "limit"
        qty: Double,
        price: Double? = null,
        clOrdId: String? = null
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val api = createApiService()
                val request = com.example.data.remote.okx.model.OkxOrderRequest(
                    instId = preferences.okxSymbol,
                    tdMode = "cash",
                    side = side.lowercase(),
                    ordType = orderType.lowercase(),
                    sz = String.format(java.util.Locale.US, "%.8f", qty).trimEnd('0').trimEnd('.'),
                    px = price?.let { String.format(java.util.Locale.US, "%.4f", it).trimEnd('0').trimEnd('.') },
                    tgtCcy = "base_ccy", // Force quantity to mean base coin (e.g. BTC)
                    clOrdId = clOrdId
                )
                val response = api.placeOrder(request)
                if (response.code == "0" && response.data.isNotEmpty()) {
                    val resData = response.data.first()
                    if (resData.sCode == "0") {
                        log(LogLevel.INFO, "OKX_ORDER", "Emir iletildi (${side} $qty). OrderId: ${resData.ordId}")
                        Result.success(resData.ordId)
                    } else {
                        log(LogLevel.ERROR, "OKX_ORDER", "Emir Hatası: ${resData.sCode} - ${resData.sMsg}")
                        Result.failure(Exception("${resData.sCode} - ${resData.sMsg}"))
                    }
                } else {
                    log(LogLevel.ERROR, "OKX_ORDER", "API Hatası: ${response.code} - ${response.msg}")
                    Result.failure(Exception("${response.code} - ${response.msg}"))
                }
            } catch (e: Exception) {
                log(LogLevel.ERROR, "OKX_ORDER", "Ağ Hatası: ${e.message}")
                Result.failure(e)
            }
        }
    }


    private val reconcileMutex = kotlinx.coroutines.sync.Mutex()

    suspend fun reconcileGridOrders(
        callerTag: String = "OkxReconcile"
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

            val balanceRes = getWalletBalance()
            if (balanceRes.isFailure) return@withContext Result.failure(Exception("OKX Bakiye alınamadı"))
            val balances = balanceRes.getOrNull() ?: emptyMap()
            val usdtBalance = balances["USDT"] ?: 0.0
            val baseCoinBalance = balances[preferences.okxBaseCoin] ?: 0.0

            val basePrice = if (preferences.okxLastRebalancePrice > 0.0) preferences.okxLastRebalancePrice else currentPrice

            val gridPlan = com.example.bot.RebalanceEngine.calculateGridOrders(
                usdtBalance = usdtBalance,
                baseCoinBalance = baseCoinBalance,
                basePrice = basePrice,
                stepPercent = preferences.okxStepPercent
            )

            if (!gridPlan.isValid) {
                return@withContext Result.failure(Exception("OKX Grid planı geçersiz: ${gridPlan.validationMessage}"))
            }

            // Cancel any stray orders
            if (openBuyOrder == null && openSellOrder != null) {
                cancelOrder(openSellOrder.ordId)
            } else if (openSellOrder == null && openBuyOrder != null) {
                cancelOrder(openBuyOrder.ordId)
            }

            // Place Sell Order
            val sellRes = createOrder(
                side = "sell",
                orderType = "limit",
                qty = gridPlan.sellBaseQty,
                price = gridPlan.sellLimitPrice
            )
            sellRes.onSuccess { id -> preferences.okxActiveSellOrderId = id }
            sellRes.onFailure { err -> log(LogLevel.ERROR, callerTag, "OKX Limit Satış emri başarısız: ${err.message}") }

            // Place Buy Order
            val buyRes = createOrder(
                side = "buy",
                orderType = "limit",
                qty = gridPlan.buyBaseQty,
                price = gridPlan.buyLimitPrice
            )
            buyRes.onSuccess { id -> preferences.okxActiveBuyOrderId = id }
            buyRes.onFailure { err -> log(LogLevel.ERROR, callerTag, "OKX Limit Alış emri başarısız: ${err.message}") }

            Result.success(com.example.data.repository.ReconciliationResult(message = "OKX Grid emirleri yeniden kuruldu"))

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
}
