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
                            val avail = detail.availEq.toDoubleOrNull() ?: detail.availBal.toDoubleOrNull() ?: 0.0
                            val usdEq = detail.eqUsd?.toDoubleOrNull() ?: 0.0
                            val current = map[detail.ccy]
                            val newAvail = (current?.quantity ?: 0.0) + avail
                            val newUsdEq = (current?.fiatValue ?: 0.0) + usdEq
                            map[detail.ccy] = AssetBalance(newAvail, newUsdEq)
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
                                val avail = asset.availBal.toDoubleOrNull() ?: 0.0
                                val current = map[asset.ccy]
                                val newAvail = (current?.quantity ?: 0.0) + avail
                                // Asset balances don't typically have eqUsd, so we just carry over or use 0
                                map[asset.ccy] = AssetBalance(newAvail, current?.fiatValue ?: 0.0)
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
            val usdtBalance = balances["USDT"]?.quantity ?: 0.0
            val baseCoinBalance = balances[preferences.okxBaseCoin]?.quantity ?: 0.0

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
                    val execPrice = if (price > 0.0) price else existing?.price ?: 0.0
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
        startTimestamp: Long? = null
    ): Result<TradeSyncResult> {
        return withContext(Dispatchers.IO) {
            try {
                val api = createApiService()
                val targetSymbol = symbol ?: preferences.okxSymbol
                // We'll just fetch recent fills history as OKX provides it
                val response = api.getFillsHistory(instId = targetSymbol, limit = 100)
                
                if (response.code != "0") {
                    return@withContext Result.failure(Exception("${response.code} - ${response.msg}"))
                }
                
                val remoteFills = response.data ?: emptyList()
                val exchangeTradeDao = database.exchangeTradeDao()
                val existingExecIds = exchangeTradeDao.getAllExecIds().toHashSet()
                val existingInDbCount = existingExecIds.size
                
                val newFills = remoteFills.filter { !existingExecIds.contains(it.billId) && !existingExecIds.contains(it.ordId) }
                
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
                    newlyAddedTrades = emptyList(),
                    analysis = okxAnalysis
                )
                Result.success(syncResult)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
