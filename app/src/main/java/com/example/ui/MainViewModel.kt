package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BybitBotApp
import com.example.bot.GridOrdersPlan
import com.example.bot.PortfolioAnalysis
import com.example.bot.RebalanceAction
import com.example.bot.RebalanceEngine
import com.example.data.local.entity.ExchangeTradeEntity
import com.example.data.local.entity.LogEntity
import com.example.data.local.entity.LogLevel
import com.example.data.local.entity.OrderEntity
import com.example.data.remote.model.BybitOrderDto
import com.example.data.remote.model.TradeAnalysisResult
import com.example.data.remote.model.TradeSyncResult
import com.example.data.repository.LastFilledTradeInfo
import com.example.service.TradingBotService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class TradeAnalysisUiState(
    val isLoading: Boolean = false,
    val analysis: TradeAnalysisResult? = null,
    val errorMessage: String? = null,
    val lastFetchedAt: Long = 0L,
    val progressText: String? = null,
    val syncResult: TradeSyncResult? = null,
    val syncNotice: String? = null
)

data class MainUiState(
    val isConfigured: Boolean = false,
    val apiKey: String = "",
    val apiSecret: String = "",
    val okxApiKey: String = "",
    val okxApiSecret: String = "",
    val okxApiPassphrase: String = "",
    val isTestnet: Boolean = false,
    val activeExchange: String = "BYBIT",
    val activeBaseCoin: String = "MNT",
    val activeSymbol: String = "MNTUSDT",
    val isBotActive: Boolean = false,
    val isLoading: Boolean = false,
    val currentPrice: Double = 0.0,
    val price24hChange: Double = 0.0,
    val usdtBalance: Double = 0.0,
    val baseCoinBalance: Double = 0.0,
    val portfolioAnalysis: PortfolioAnalysis? = null,
    val gridPlan: GridOrdersPlan? = null,
    val okxCurrentPrice: Double = 0.0,
    val okxPrice24hChange: Double = 0.0,
    val okxUsdtBalance: Double = 0.0,
    val okxBaseCoinBalance: Double = 0.0,
    val okxPortfolioAnalysis: PortfolioAnalysis? = null,
    val activeOrders: List<BybitOrderDto> = emptyList(),
    val stepPercent: Double = 2.0,
    val okxStepPercent: Double = 2.0,
    val lastRebalancePrice: Double = 0.0,
    val showInitialRebalanceDialog: Boolean = false,
    val isExecutingRebalance: Boolean = false,
    val showApiKeyDialog: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as BybitBotApp
    val preferences = app.preferences
    private val repository = app.repository
    private val okxRepository = app.okxRepository
    private val database = app.database

    fun updateSymbols(bybitSymbol: String, okxSymbol: String) {
        preferences.bybitSymbol = bybitSymbol
        if (bybitSymbol.endsWith("USDT")) {
            preferences.bybitBaseCoin = bybitSymbol.removeSuffix("USDT")
        } else if (bybitSymbol.endsWith("USDC")) {
            preferences.bybitBaseCoin = bybitSymbol.removeSuffix("USDC")
        }

        preferences.okxSymbol = okxSymbol
        if (okxSymbol.contains("-")) {
            preferences.okxBaseCoin = okxSymbol.substringBefore("-")
        }

        _uiState.update { 
            it.copy(
                activeSymbol = preferences.bybitSymbol,
                activeBaseCoin = preferences.bybitBaseCoin
            )
        }
    }

    private val _uiState = MutableStateFlow(
        MainUiState(
            isConfigured = preferences.isConfigured,
            apiKey = preferences.apiKey,
            apiSecret = preferences.apiSecret,
            okxApiKey = preferences.okxApiKey,
            okxApiSecret = preferences.okxApiSecret,
            okxApiPassphrase = preferences.okxApiPassphrase,
            activeExchange = "BYBIT", // default to Bybit for now
            activeBaseCoin = preferences.bybitBaseCoin,
            activeSymbol = preferences.bybitSymbol,
            isTestnet = preferences.isTestnet,
            isBotActive = preferences.isBotActive,
            stepPercent = preferences.stepPercent,
            okxStepPercent = preferences.okxStepPercent,
            lastRebalancePrice = preferences.lastRebalancePrice,
            showApiKeyDialog = !preferences.isConfigured && preferences.okxApiKey.isBlank()
        )
    )

    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    val orders: StateFlow<List<OrderEntity>> = database.orderDao().getAllOrders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val logs: StateFlow<List<LogEntity>> = database.logDao().getAllLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _tradeAnalysis = MutableStateFlow(TradeAnalysisUiState())
    val tradeAnalysis: StateFlow<TradeAnalysisUiState> = _tradeAnalysis.asStateFlow()

    val liveAnalysis: StateFlow<TradeAnalysisResult?> = combine(
        orders,
        repository.getAllExchangeTradesFlow()
    ) { orderList, tradeList ->
        RebalanceEngine.computeLiveTradeAnalysis(
            orders = orderList,
            exchangeTrades = tradeList
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        if (preferences.isConfigured || preferences.okxApiKey.isNotBlank()) {
            if (preferences.isBotActive) {
                try {
                    TradingBotService.start(getApplication())
                } catch (e: Exception) {
                    viewModelScope.launch(Dispatchers.IO) {
                        repository.log(LogLevel.ERROR, "System", "Bot servisi başlatılamadı: ${e.message}")
                    }
                }
            }
            viewModelScope.launch(Dispatchers.IO) {
                repository.pruneLogs()
                repository.syncUnfilledOrdersWithExchange()
            }
            refreshData()
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                repository.pruneLogs()
            }
        }

        // Live background update loop
        viewModelScope.launch(Dispatchers.IO) {
            var cycleCount = 0
            while (isActive) {
                delay(4000)
                if ((preferences.isConfigured || preferences.okxApiKey.isNotBlank()) && !_uiState.value.isLoading) {
                    silentRefresh(cycleCount)
                    cycleCount++
                }
            }
        }
    }

    private suspend fun silentRefresh(cycleCount: Int = 0) {
        try {
            if (cycleCount % 4 == 0) {
                repository.syncUnfilledOrdersWithExchange()
            }
            if (cycleCount % 30 == 0) {
                repository.pruneLogs()
            }

            var currentPrice = _uiState.value.currentPrice
            var priceChange = _uiState.value.price24hChange
            
            val tickerRes = repository.getTicker()
            tickerRes.onSuccess { ticker ->
                currentPrice = ticker.currentPrice
                priceChange = ticker.changePercent24h
            }

            var usdt = _uiState.value.usdtBalance
            var baseQty = _uiState.value.baseCoinBalance

            if (cycleCount % 2 == 0 || usdt <= 0.0) {
                val balanceRes = repository.getWalletBalance()
                balanceRes.onSuccess { map ->
                    usdt = map["USDT"] ?: 0.0
                    baseQty = map[_uiState.value.activeBaseCoin] ?: 0.0
                }
            }

            var openOrders = _uiState.value.activeOrders
            if (cycleCount % 2 == 1 || openOrders.isEmpty()) {
                val openOrdersRes = repository.getOpenOrders()
                openOrdersRes.onSuccess { list ->
                    openOrders = list
                }
            }

            val analysis = RebalanceEngine.analyzePortfolio(
                usdtBalance = usdt,
                baseCoinBalance = baseQty,
                currentPrice = currentPrice
            )

            val anchorBasePrice = if (preferences.isBotActive && preferences.lastRebalancePrice > 0.0) {
                preferences.lastRebalancePrice
            } else {
                currentPrice
            }

            val gridPlan = RebalanceEngine.calculateGridOrders(
                usdtBalance = usdt,
                baseCoinBalance = baseQty,
                basePrice = anchorBasePrice,
                stepPercent = _uiState.value.stepPercent
            )



            fetchOkxData(cycleCount)
            _uiState.update {

                it.copy(
                    currentPrice = currentPrice,
                    price24hChange = priceChange,
                    usdtBalance = usdt,
                    baseCoinBalance = baseQty,
                    activeOrders = openOrders,
                    portfolioAnalysis = analysis,
                    gridPlan = gridPlan,
                    isBotActive = preferences.isBotActive,
                    lastRebalancePrice = preferences.lastRebalancePrice
                )
            }
        } catch (e: Exception) {
            // Silently ignore background polling exceptions
        }
    }

    fun openApiKeyDialog() {
        _uiState.update { it.copy(showApiKeyDialog = true) }
    }

    fun closeApiKeyDialog() {
        _uiState.update { it.copy(showApiKeyDialog = false) }
    }

    fun saveOkxApiCredentials(key: String, secret: String, passphrase: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            preferences.okxApiKey = key
            preferences.okxApiSecret = secret
            preferences.okxApiPassphrase = passphrase
            repository.log(LogLevel.INFO, "Auth", "OKX TR API bilgileri kaydedildi")
            _uiState.update {
                it.copy(
                    okxApiKey = key,
                    okxApiSecret = secret,
                    okxApiPassphrase = passphrase,
                    isLoading = false,
                    statusMessage = "OKX TR API bilgileri başarıyla kaydedildi"
                )
            }
        }
    }

    fun saveApiCredentials(key: String, secret: String, testnet: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            preferences.saveCredentials(key, secret, testnet)
            repository.log(LogLevel.INFO, "Auth", "API bilgileri kaydedildi (Testnet: $testnet)")
            _uiState.update {
                it.copy(
                    isConfigured = true,
                    apiKey = key,
                    apiSecret = secret,
                    isTestnet = testnet,
                    showApiKeyDialog = false
                )
            }
            refreshData()
        }
    }

    fun updateStepPercent(bybitStep: Double, okxStep: Double) {
        preferences.stepPercent = bybitStep
        preferences.okxStepPercent = okxStep
        _uiState.update { it.copy(stepPercent = bybitStep, okxStepPercent = okxStep) }
        recalculateGridPlan()
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun refreshData() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            repository.syncServerTime()

            if (preferences.isBotActive) {
                try {
                    TradingBotService.start(getApplication())
                } catch (e: Exception) {
                    viewModelScope.launch(Dispatchers.IO) {
                        repository.log(LogLevel.ERROR, "System", "Bot servisi başlatılamadı: ${e.message}")
                    }
                }
                val reconRes = repository.reconcileGridOrders(callerTag = "ManuelYenile")
                reconRes.onSuccess { rec ->
                    if (rec.executedOrderFound) {
                        _uiState.update { it.copy(statusMessage = rec.message) }
                    }
                }
            }

            var currentPrice = _uiState.value.currentPrice
            var priceChange = _uiState.value.price24hChange
            val tickerRes = repository.getTicker()
            tickerRes.onSuccess { ticker ->
                currentPrice = ticker.currentPrice
                priceChange = ticker.changePercent24h
            }.onFailure { err ->
                repository.log(LogLevel.WARN, "Market", "Ticker alınamadı: ${err.message}")
            }

            var usdt = _uiState.value.usdtBalance
            var baseQty = _uiState.value.baseCoinBalance
            val balanceRes = repository.getWalletBalance()
            balanceRes.onSuccess { map ->
                usdt = map["USDT"] ?: 0.0
                baseQty = map[_uiState.value.activeBaseCoin] ?: 0.0
            }.onFailure { err ->
                _uiState.update { it.copy(errorMessage = "Bakiye çekilemedi: ${err.message}", isLoading = false) }
                return@launch
            }

            var openOrders = emptyList<BybitOrderDto>()
            val openOrdersRes = repository.getOpenOrders()
            openOrdersRes.onSuccess { list ->
                openOrders = list
            }

            val analysis = RebalanceEngine.analyzePortfolio(
                usdtBalance = usdt,
                baseCoinBalance = baseQty,
                currentPrice = currentPrice
            )

            val anchorBasePrice = if (preferences.isBotActive && preferences.lastRebalancePrice > 0.0) {
                preferences.lastRebalancePrice
            } else {
                currentPrice
            }

            val gridPlan = RebalanceEngine.calculateGridOrders(
                usdtBalance = usdt,
                baseCoinBalance = baseQty,
                basePrice = anchorBasePrice,
                stepPercent = _uiState.value.stepPercent
            )

            val shouldShowInitialDialog = !analysis.isBalanced5050 &&
                    analysis.requiredAction != RebalanceAction.BALANCED &&
                    !preferences.isBotActive &&
                    analysis.deltaUsdt >= 1.0

            fetchOkxData(0)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    currentPrice = currentPrice,
                    price24hChange = priceChange,
                    usdtBalance = usdt,
                    baseCoinBalance = baseQty,
                    portfolioAnalysis = analysis,
                    gridPlan = gridPlan,
                    activeOrders = openOrders,
                    isBotActive = preferences.isBotActive,
                    lastRebalancePrice = preferences.lastRebalancePrice,
                    showInitialRebalanceDialog = shouldShowInitialDialog,
                )
            }
        }
    }


    private suspend fun fetchOkxData(cycleCount: Int = 0) {
        if (preferences.okxApiKey.isBlank()) return
        
        var okxCurrentPrice = _uiState.value.okxCurrentPrice
        var okxUsdt = _uiState.value.okxUsdtBalance
        var okxBaseQty = _uiState.value.okxBaseCoinBalance
        var okxAnalysis = _uiState.value.okxPortfolioAnalysis

        if (cycleCount % 2 == 0 || okxCurrentPrice <= 0.0) {
            val okxTickerRes = okxRepository.getTicker()
            okxTickerRes.onSuccess { ticker ->
                okxCurrentPrice = ticker.last.toDoubleOrNull() ?: 0.0
            }
        }

        if (cycleCount % 2 == 0 || okxUsdt <= 0.0) {
            val okxBalanceRes = okxRepository.getWalletBalance()
            okxBalanceRes.onSuccess { map ->
                okxUsdt = map["USDT"] ?: 0.0
                okxBaseQty = map[preferences.okxBaseCoin] ?: 0.0
            }
        }

        if (okxCurrentPrice > 0.0) {
            okxAnalysis = RebalanceEngine.analyzePortfolio(
                usdtBalance = okxUsdt,
                baseCoinBalance = okxBaseQty,
                currentPrice = okxCurrentPrice
            )
        }

        _uiState.update {
            it.copy(
                okxCurrentPrice = okxCurrentPrice,
                okxUsdtBalance = okxUsdt,
                okxBaseCoinBalance = okxBaseQty,
                okxPortfolioAnalysis = okxAnalysis
            )
        }
    }

    private fun recalculateGridPlan() {
        val state = _uiState.value
        val anchorBasePrice = if (preferences.isBotActive && preferences.lastRebalancePrice > 0.0) {
            preferences.lastRebalancePrice
        } else {
            state.currentPrice
        }
        val plan = RebalanceEngine.calculateGridOrders(
            usdtBalance = state.usdtBalance,
            baseCoinBalance = state.baseCoinBalance,
            basePrice = anchorBasePrice,
            stepPercent = state.stepPercent
        )
        _uiState.update { it.copy(gridPlan = plan) }
    }

    fun requestInitialRebalanceDialog() {
        _uiState.update { it.copy(showInitialRebalanceDialog = true) }
    }

    fun dismissInitialRebalanceDialog() {
        _uiState.update { it.copy(showInitialRebalanceDialog = false) }
    }

    fun executeInitialRebalance() {
        val analysis = _uiState.value.portfolioAnalysis ?: return
        val currentPrice = _uiState.value.currentPrice
        if (currentPrice <= 0.0) return

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, isExecutingRebalance = true, showInitialRebalanceDialog = false) }

            val side = if (analysis.requiredAction == RebalanceAction.BUY_BASE) "Buy" else "Sell"
            val qty = analysis.deltaBase

            val res = repository.createOrder(
                side = side,
                orderType = "Market",
                qty = qty,
                price = null
            )

            res.fold(
                onSuccess = { orderId ->
                    repository.log(LogLevel.INFO, "Rebalance", "İlk dengeleme Market emri (${side} ${qty}) başarıyla iletildi.")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isExecutingRebalance = false,
                            statusMessage = "Dengeleme işlemi ($side ${RebalanceEngine.format4(qty)}) başarıyla gerçekleşti."
                        )
                    }
                    startBot()
                },
                onFailure = { err ->
                    repository.log(LogLevel.ERROR, "Rebalance", "İlk dengeleme başarısız: ${err.message}")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isExecutingRebalance = false,
                            errorMessage = "Dengeleme başarısız: ${err.message}"
                        )
                    }
                }
            )
            refreshData()
        }
    }

    fun startBot() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }
            val startRes = repository.reconcileGridOrders(callerTag = "StartBot")
            startRes.onSuccess {
                preferences.isBotActive = true
                preferences.lastRebalancePrice = _uiState.value.currentPrice
                try {
                    TradingBotService.start(getApplication())
                } catch (e: Exception) {
                    viewModelScope.launch(Dispatchers.IO) {
                        repository.log(LogLevel.ERROR, "System", "Bot servisi başlatılamadı: ${e.message}")
                    }
                }
                repository.log(LogLevel.INFO, "System", "Bot başlatıldı. Aktif Baz Fiyat: $${RebalanceEngine.format4(preferences.lastRebalancePrice)}")
                _uiState.update { it.copy(isBotActive = true, isLoading = false, lastRebalancePrice = preferences.lastRebalancePrice) }
                refreshData()
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, errorMessage = "Bot başlatılamadı: ${err.message}") }
            }
        }
    }

    fun stopBot() {
        preferences.isBotActive = false
        preferences.lastRebalancePrice = 0.0
        TradingBotService.stop(getApplication())
        
        viewModelScope.launch(Dispatchers.IO) {
            repository.log(LogLevel.INFO, "System", "Bot durduruldu")
            _uiState.update { it.copy(isBotActive = false, lastRebalancePrice = 0.0) }
            cancelAllOrders()
        }
    }

    fun cancelAllOrders() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }
            val res = repository.cancelAllBaseOrders()
            res.fold(
                onSuccess = {
                    repository.log(LogLevel.INFO, "System", "Tüm açık ızgara emirleri iptal edildi.")
                    _uiState.update { it.copy(isLoading = false, statusMessage = "Tüm açık emirler iptal edildi.") }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(errorMessage = "İptal hatası: ${err.message}", isLoading = false) }
                }
            )
            refreshData()
        }
    }

    fun clearLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            database.logDao().clearAllLogs()
            repository.log(LogLevel.INFO, "System", "Log geçmişi temizlendi")
        }
    }

    fun clearOrders() {
        viewModelScope.launch(Dispatchers.IO) {
            database.orderDao().clearAllOrders()
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(statusMessage = null, errorMessage = null) }
    }

    fun setCustomBasePrice(newPrice: Double) {
        if (newPrice <= 0.0) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }
            val res = repository.applyCustomBasePrice(newPrice)
            res.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            lastRebalancePrice = newPrice,
                            statusMessage = "Baz fiyat $${RebalanceEngine.format4(newPrice)} olarak belirlendi ve yeni ızgara aktif edildi."
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Baz fiyat ayarlanamadı: ${err.message}"
                        )
                    }
                }
            )
            refreshData()
        }
    }

    fun getLastRealFilledPrice(callback: (Double?) -> Unit) {
        viewModelScope.launch {
            val price = repository.getLastRealFilledPrice()
            callback(price)
        }
    }

    fun getLastRealFilledTradeInfo(callback: (LastFilledTradeInfo?) -> Unit) {
        viewModelScope.launch {
            val info = repository.getLastRealFilledTradeInfo()
            callback(info)
        }
    }

    val exchangeTrades: StateFlow<List<ExchangeTradeEntity>> = repository.getAllExchangeTradesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun fetchTradeAnalysis(symbol: String? = _uiState.value.activeSymbol, daysBack: Int = 730, startTimestamp: Long? = null) {
        viewModelScope.launch {
            _tradeAnalysis.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    syncNotice = null,
                    progressText = "Borsadan işlemler çekiliyor ve veritabanı kontrol ediliyor..."
                )
            }
            val result = repository.syncTradesFromExchange(
                symbol = symbol,
                daysBack = daysBack,
                startTimestamp = startTimestamp,
                onProgress = { currentWindow, totalWindows, fetchedCount ->
                    _tradeAnalysis.update {
                        it.copy(progressText = "$currentWindow / $totalWindows hafta tarandı ($fetchedCount işlem)")
                    }
                }
            )
            result.onSuccess { syncRes ->
                val notice = if (syncRes.newlyAddedCount > 0) {
                    "✓ ${syncRes.totalFetched} işlem tarandı. ${syncRes.newlyAddedCount} YENİ işlem veritabanına eklendi! (Toplam DB: ${syncRes.totalInDb})"
                } else {
                    "✓ ${syncRes.totalFetched} işlem tarandı. Tüm işlemler zaten veritabanında kayıtlı (Yeni işlem yok)."
                }
                _tradeAnalysis.update {
                    it.copy(
                        isLoading = false,
                        analysis = syncRes.analysis,
                        syncResult = syncRes,
                        syncNotice = notice,
                        errorMessage = null,
                        lastFetchedAt = System.currentTimeMillis(),
                        progressText = null
                    )
                }
            }.onFailure { err ->
                _tradeAnalysis.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = err.message ?: "İşlem geçmişi çekilemedi",
                        progressText = null
                    )
                }
            }
        }
    }

    fun exportKlines(context: android.content.Context, symbol: String, interval: String, format: String, startTime: Long, endTime: Long) {
        viewModelScope.launch {
            try {
                val effectiveSymbol = if (symbol == "ALL" || symbol.isBlank()) _uiState.value.activeSymbol else symbol
                android.widget.Toast.makeText(context, "Veriler indiriliyor... Lütfen bekleyin.", android.widget.Toast.LENGTH_SHORT).show()
                
                var currentEnd: Long = endTime
                val allKlinesList = mutableListOf<List<String>>()
                var apiError: String? = null
                val limitToFetch = 1000
                
                while (true) {
                    val result = repository.getKlines(symbol = effectiveSymbol, interval = interval, start = startTime, end = currentEnd, limit = limitToFetch)
                    
                    if (result.isSuccess) {
                        val klineResult = result.getOrNull()
                        val data = klineResult?.list ?: emptyList()
                        
                        if (data.isEmpty()) break
                        
                        val validData = data.filter { k -> 
                            val ts = k[0].toLongOrNull() ?: 0L
                            ts >= startTime && ts <= currentEnd
                        }
                        
                        if (validData.isEmpty()) break
                        
                        allKlinesList.addAll(validData)
                        
                        if (data.size < limitToFetch) break
                        
                        val oldestTimestamp = data.last()[0].toLongOrNull()
                        if (oldestTimestamp != null) {
                            if (oldestTimestamp <= startTime) break
                            currentEnd = oldestTimestamp - 1
                        } else {
                            break
                        }
                    } else {
                        apiError = result.exceptionOrNull()?.message ?: "Bilinmeyen hata"
                        break
                    }
                    
                    kotlinx.coroutines.delay(250)
                }
                
                if (allKlinesList.isEmpty()) {
                    if (apiError != null) {
                        android.widget.Toast.makeText(context, "Veri alınamadı: $apiError", android.widget.Toast.LENGTH_LONG).show()
                    } else {
                        android.widget.Toast.makeText(context, "Seçilen tarih aralığında veri bulunamadı", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } else {
                    val combinedResult = com.example.data.remote.model.KlineResult(
                        symbol = effectiveSymbol,
                        category = "spot",
                        list = allKlinesList
                    )
                    com.example.ui.components.KlineExporter.shareKlinesAsFile(context, combinedResult, effectiveSymbol, interval, format)
                }
            } catch (e: Exception) {
                android.util.Log.e("KlineExport", "Error exporting klines", e)
                android.widget.Toast.makeText(context, "Veri alınırken hata oluştu: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    fun clearLocalExchangeDatabase() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearLocalExchangeTrades()
            _tradeAnalysis.update {
                it.copy(
                    analysis = null,
                    syncResult = null,
                    syncNotice = "Yerel borsa işlem veritabanı temizlendi."
                )
            }
        }
    }
}
