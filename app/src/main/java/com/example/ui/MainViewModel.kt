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
    val isTestnet: Boolean = false,
    val isBotActive: Boolean = false,
    val isLoading: Boolean = false,
    val currentPrice: Double = 0.0,
    val price24hChange: Double = 0.0,
    val usdtBalance: Double = 0.0,
    val mntBalance: Double = 0.0,
    val portfolioAnalysis: PortfolioAnalysis? = null,
    val gridPlan: GridOrdersPlan? = null,
    val activeOrders: List<BybitOrderDto> = emptyList(),
    val stepPercent: Double = 2.0,
    val lastRebalancePrice: Double = 0.0,
    val showInitialRebalanceDialog: Boolean = false,
    val isExecutingRebalance: Boolean = false,
    val showApiKeyDialog: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as BybitBotApp
    private val preferences = app.preferences
    private val repository = app.repository
    private val database = app.database

    private val _uiState = MutableStateFlow(
        MainUiState(
            isConfigured = preferences.isConfigured,
            apiKey = preferences.apiKey,
            apiSecret = preferences.apiSecret,
            isTestnet = preferences.isTestnet,
            isBotActive = preferences.isBotActive,
            stepPercent = preferences.stepPercent,
            lastRebalancePrice = preferences.lastRebalancePrice,
            showApiKeyDialog = !preferences.isConfigured
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
        repository.getAllExchangeTradesFlow(),
        _tradeAnalysis
    ) { orderList, tradeList, analysisUiState ->
        RebalanceEngine.computeLiveTradeAnalysis(
            orders = orderList,
            exchangeTrades = tradeList,
            apiAnalysis = analysisUiState.analysis
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        if (preferences.isConfigured) {
            if (preferences.isBotActive) {
                TradingBotService.start(getApplication())
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

        // Live background update loop: keeps active orders, balances, and prices up-to-date in real-time
        viewModelScope.launch(Dispatchers.IO) {
            var cycleCount = 0
            while (isActive) {
                delay(4000)
                if (preferences.isConfigured && !_uiState.value.isLoading) {
                    silentRefresh(cycleCount)
                    cycleCount++
                }
            }
        }
    }

    private suspend fun silentRefresh(cycleCount: Int = 0) {
        try {
            // Periodically check and sync any unfilled orders in DB with exchange
            if (cycleCount % 4 == 0) {
                repository.syncUnfilledOrdersWithExchange()
            }
            // Periodically prune logs older than 24 hours or exceeding 1000 items
            if (cycleCount % 30 == 0) {
                repository.pruneLogs()
            }
            var currentPrice = _uiState.value.currentPrice
            var priceChange = _uiState.value.price24hChange
            val tickerRes = repository.getMntTicker()
            tickerRes.onSuccess { ticker ->
                currentPrice = ticker.currentPrice
                priceChange = ticker.changePercent24h
            }

            var usdt = _uiState.value.usdtBalance
            var mnt = _uiState.value.mntBalance
            // Stagger balance fetch every ~8 seconds (every 2 cycles) or on initial load
            if (cycleCount % 2 == 0 || usdt <= 0.0) {
                val balanceRes = repository.getWalletBalance()
                balanceRes.onSuccess { map ->
                    usdt = map["USDT"] ?: 0.0
                    mnt = map["MNT"] ?: 0.0
                }
            }

            var openOrders = _uiState.value.activeOrders
            // Stagger open orders fetch every ~8 seconds (alternating cycle)
            if (cycleCount % 2 == 1 || openOrders.isEmpty()) {
                val openOrdersRes = repository.getOpenOrders()
                openOrdersRes.onSuccess { list ->
                    openOrders = list
                }
            }

            val analysis = RebalanceEngine.analyzePortfolio(
                usdtBalance = usdt,
                mntBalance = mnt,
                currentPrice = currentPrice
            )

            val anchorBasePrice = if (preferences.isBotActive && preferences.lastRebalancePrice > 0.0) {
                preferences.lastRebalancePrice
            } else {
                currentPrice
            }

            val gridPlan = RebalanceEngine.calculateGridOrders(
                usdtBalance = usdt,
                mntBalance = mnt,
                basePrice = anchorBasePrice,
                stepPercent = _uiState.value.stepPercent
            )

            _uiState.update {
                it.copy(
                    currentPrice = currentPrice,
                    price24hChange = priceChange,
                    usdtBalance = usdt,
                    mntBalance = mnt,
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
        if (_uiState.value.isConfigured) {
            _uiState.update { it.copy(showApiKeyDialog = false) }
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

    fun updateStepPercent(percent: Double) {
        preferences.stepPercent = percent
        _uiState.update { it.copy(stepPercent = percent) }
        recalculateGridPlan()
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun refreshData() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            // 0. Synchronize server time
            repository.syncServerTime()

            // If bot is active, actively reconcile with Bybit exchange state first
            if (preferences.isBotActive) {
                TradingBotService.start(getApplication())
                val reconRes = repository.reconcileGridOrders(callerTag = "ManuelYenile")
                reconRes.onSuccess { rec ->
                    if (rec.executedOrderFound) {
                        _uiState.update { it.copy(statusMessage = rec.message) }
                    }
                }
            }

            // 1. Fetch Ticker
            var currentPrice = _uiState.value.currentPrice
            var priceChange = _uiState.value.price24hChange
            val tickerRes = repository.getMntTicker()
            tickerRes.onSuccess { ticker ->
                currentPrice = ticker.currentPrice
                priceChange = ticker.changePercent24h
            }.onFailure { err ->
                repository.log(LogLevel.WARN, "Market", "Ticker alınamadı: ${err.message}")
            }

            // 2. Fetch Wallet Balances
            var usdt = _uiState.value.usdtBalance
            var mnt = _uiState.value.mntBalance
            val balanceRes = repository.getWalletBalance()
            balanceRes.onSuccess { map ->
                usdt = map["USDT"] ?: 0.0
                mnt = map["MNT"] ?: 0.0
            }.onFailure { err ->
                _uiState.update { it.copy(errorMessage = "Bakiye çekilemedi: ${err.message}", isLoading = false) }
                return@launch
            }

            // 3. Fetch Open Orders
            var openOrders = emptyList<BybitOrderDto>()
            val openOrdersRes = repository.getOpenOrders()
            openOrdersRes.onSuccess { list ->
                openOrders = list
            }

            val analysis = RebalanceEngine.analyzePortfolio(
                usdtBalance = usdt,
                mntBalance = mnt,
                currentPrice = currentPrice
            )

            val anchorBasePrice = if (preferences.isBotActive && preferences.lastRebalancePrice > 0.0) {
                preferences.lastRebalancePrice
            } else {
                currentPrice
            }

            val gridPlan = RebalanceEngine.calculateGridOrders(
                usdtBalance = usdt,
                mntBalance = mnt,
                basePrice = anchorBasePrice,
                stepPercent = _uiState.value.stepPercent
            )

            val shouldShowInitialDialog = !analysis.isBalanced5050 &&
                    analysis.requiredAction != RebalanceAction.BALANCED &&
                    !preferences.isBotActive &&
                    analysis.deltaUsdt >= 1.0

            _uiState.update {
                it.copy(
                    isLoading = false,
                    currentPrice = currentPrice,
                    price24hChange = priceChange,
                    usdtBalance = usdt,
                    mntBalance = mnt,
                    portfolioAnalysis = analysis,
                    gridPlan = gridPlan,
                    activeOrders = openOrders,
                    isBotActive = preferences.isBotActive,
                    lastRebalancePrice = preferences.lastRebalancePrice,
                    showInitialRebalanceDialog = shouldShowInitialDialog
                )
            }
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
            mntBalance = state.mntBalance,
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

    /**
     * Executes the initial 50/50 balance trade upon user confirmation.
     */
    fun executeInitialRebalance() {
        val analysis = _uiState.value.portfolioAnalysis ?: return
        val currentPrice = _uiState.value.currentPrice
        if (currentPrice <= 0.0) return

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isExecutingRebalance = true, errorMessage = null) }

            val side = if (analysis.requiredAction == RebalanceAction.BUY_MNT) "Buy" else "Sell"
            val qty = analysis.deltaMnt

            repository.log(
                LogLevel.INFO,
                "InitialRebalance",
                "Kullanıcı onayı ile başlangıç eşitlemesi yapılıyor: $side ${RebalanceEngine.format4(qty)} MNT (~${RebalanceEngine.format2(analysis.deltaUsdt)} USDT)"
            )

            // Execute Market order for instant 50/50 equalization
            val res = repository.createOrder(
                side = side,
                orderType = "Market",
                qty = qty,
                price = null,
                triggerReason = "InitialRebalance"
            )

            res.onSuccess { orderId ->
                repository.log(LogLevel.SUCCESS, "InitialRebalance", "Başlangıç %50-%50 eşitleme emri gerçekleşti: $orderId")
                preferences.lastRebalancePrice = currentPrice
                repository.recordOrderFilled(
                    orderId = orderId,
                    side = side,
                    price = currentPrice,
                    qty = qty,
                    triggerReason = "InitialRebalance"
                )
                _uiState.update {
                    it.copy(
                        isExecutingRebalance = false,
                        showInitialRebalanceDialog = false,
                        lastRebalancePrice = currentPrice,
                        statusMessage = "Portföy %50-%50 olarak başarıyla dengelendi!"
                    )
                }
                kotlinx.coroutines.delay(1000)
                refreshData()
            }.onFailure { err ->
                repository.log(LogLevel.ERROR, "InitialRebalance", "Eşitleme emri verilemedi: ${err.message}")
                _uiState.update {
                    it.copy(
                        isExecutingRebalance = false,
                        errorMessage = "Eşitleme emri hatası: ${err.message}"
                    )
                }
            }
        }
    }

    /**
     * Starts 24/7 background Bot Service and places grid orders (+2% sell & -2% buy).
     */
    fun startBot() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val state = _uiState.value
            val price = state.currentPrice
            val usdt = state.usdtBalance
            val mnt = state.mntBalance

            if (price <= 0.0 || usdt <= 0.0 || mnt <= 0.0) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Botu başlatmak için MNT ve USDT bakiyesi gereklidir."
                    )
                }
                return@launch
            }

            val plan = RebalanceEngine.calculateGridOrders(
                usdtBalance = usdt,
                mntBalance = mnt,
                basePrice = price,
                stepPercent = state.stepPercent
            )

            if (!plan.isValid) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = plan.validationMessage
                    )
                }
                return@launch
            }

            // Cancel any old orders first
            repository.cancelAllMntOrders()

            // Place Limit Sell (+2%)
            val sellRes = repository.createOrder(
                side = "Sell",
                orderType = "Limit",
                qty = plan.sellMntQty,
                price = plan.sellLimitPrice,
                triggerReason = "GridStepUpSell"
            )

            // Place Limit Buy (-2%)
            val buyRes = repository.createOrder(
                side = "Buy",
                orderType = "Limit",
                qty = plan.buyMntQty,
                price = plan.buyLimitPrice,
                triggerReason = "GridStepDownBuy"
            )

            val orderError = sellRes.exceptionOrNull()?.message ?: buyRes.exceptionOrNull()?.message
            if (orderError != null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Emir iletilemedi: $orderError"
                    )
                }
                return@launch
            }

            sellRes.onSuccess { sellId ->
                preferences.activeSellOrderId = sellId
            }
            buyRes.onSuccess { buyId ->
                preferences.activeBuyOrderId = buyId
            }

            preferences.isBotActive = true
            preferences.lastRebalancePrice = price
            repository.lastGridOrderPlacedTimeMs = System.currentTimeMillis()

            // Start Foreground 24/7 Service
            TradingBotService.start(getApplication())

            _uiState.update {
                it.copy(
                    isLoading = false,
                    isBotActive = true,
                    statusMessage = "7/24 Dengeleme Botu Başlatıldı! %${state.stepPercent} Limit emirler aktifleştirildi."
                )
            }
            refreshData()
        }
    }

    fun stopBot() {
        preferences.isBotActive = false
        TradingBotService.stop(getApplication())
        _uiState.update {
            it.copy(
                isBotActive = false,
                statusMessage = "Bot durduruldu."
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            repository.log(LogLevel.INFO, "BotControl", "Kullanıcı botu durdurdu.")
            refreshData()
        }
    }

    fun cancelAllOrders() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }
            val res = repository.cancelAllMntOrders()
            res.onSuccess {
                _uiState.update { it.copy(statusMessage = "Tüm açık emirler iptal edildi.", isLoading = false) }
            }.onFailure { err ->
                _uiState.update { it.copy(errorMessage = "İptal hatası: ${err.message}", isLoading = false) }
            }
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

    fun fetchTradeAnalysis(symbol: String? = "MNTUSDT", daysBack: Int = 730) {
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
