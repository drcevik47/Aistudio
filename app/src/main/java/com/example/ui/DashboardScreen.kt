package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bot.RebalanceEngine
import com.example.data.repository.LastFilledTradeInfo
import com.example.ui.components.ActiveOrdersCard
import com.example.ui.components.ApiKeySetupDialog
import com.example.ui.components.InitialRebalanceDialog
import com.example.ui.components.LogViewerScreen
import com.example.ui.components.OrderHistoryScreen
import com.example.ui.components.PortfolioCard
import com.example.ui.components.SettingsDialog
import com.example.ui.components.TradeAnalysisScreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.ui.theme.MinimalBg
import com.example.ui.theme.MinimalError
import com.example.ui.theme.MinimalPrimary
import com.example.ui.theme.MinimalPrimaryLight
import com.example.ui.theme.MinimalSecondary
import com.example.ui.theme.MinimalSecondaryLight
import com.example.ui.theme.MinimalSuccess
import com.example.ui.theme.MinimalSuccessDark
import com.example.ui.theme.MinimalSuccessLight
import com.example.ui.theme.MinimalSurface
import com.example.ui.theme.MinimalSurfaceBorder
import com.example.ui.theme.MinimalSurfaceBorderLight
import com.example.ui.theme.MinimalWarning
import com.example.ui.theme.MinimalSurfaceElevated
import com.example.ui.theme.MinimalTextMuted
import com.example.ui.theme.MinimalTextPrimary
import com.example.ui.theme.MinimalTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val orders by viewModel.orders.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val tradeAnalysisState by viewModel.tradeAnalysis.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showEditBasePriceDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.statusMessage) {
        state.statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar("Hata: $it")
            viewModel.clearMessages()
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("dashboard_scaffold"),
        containerColor = MinimalBg,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MinimalPrimaryLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "B",
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                color = MinimalPrimary
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Bybit Rebalancer",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MinimalTextPrimary,
                                letterSpacing = (-0.3).sp
                            )
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 1.dp)) {
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = if (state.isTestnet) MinimalSecondaryLight else MinimalPrimaryLight
                                ) {
                                    Text(
                                        text = if (state.isTestnet) "TESTNET" else "UNIFIED SPOT",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (state.isTestnet) MinimalSecondary else MinimalPrimary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                if (state.isBotActive) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(MinimalSuccess)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "BOT ACTIVE • 24/7",
                                        fontSize = 9.sp,
                                        color = MinimalSuccessDark,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshData() },
                        enabled = !state.isLoading,
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MinimalSurfaceElevated)
                            .border(1.dp, MinimalSurfaceBorder, CircleShape)
                            .testTag("refresh_data_button")
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MinimalPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Yenile",
                                tint = MinimalTextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = { showSettingsDialog = true },
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MinimalSurfaceElevated)
                            .border(1.dp, MinimalSurfaceBorder, CircleShape)
                            .testTag("open_settings_button")
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Ayarlar",
                            tint = MinimalTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MinimalSurface,
                    titleContentColor = MinimalTextPrimary
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MinimalSurface,
                contentColor = MinimalTextPrimary,
                tonalElevation = 0.dp,
                modifier = Modifier
                    .border(width = 1.dp, color = MinimalSurfaceBorderLight)
                    .testTag("bottom_nav_bar")
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.ShowChart, contentDescription = null) },
                    label = {
                        Text(
                            "Dashboard",
                            fontSize = 11.sp,
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MinimalPrimary,
                        selectedTextColor = MinimalPrimary,
                        indicatorColor = MinimalPrimaryLight,
                        unselectedIconColor = MinimalTextMuted,
                        unselectedTextColor = MinimalTextMuted
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.QueryStats, contentDescription = null) },
                    label = {
                        Text(
                            "Borsa Analiz",
                            fontSize = 11.sp,
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MinimalPrimary,
                        selectedTextColor = MinimalPrimary,
                        indicatorColor = MinimalPrimaryLight,
                        unselectedIconColor = MinimalTextMuted,
                        unselectedTextColor = MinimalTextMuted
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.ReceiptLong, contentDescription = null) },
                    label = {
                        Text(
                            "Bot (${orders.size})",
                            fontSize = 11.sp,
                            fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MinimalPrimary,
                        selectedTextColor = MinimalPrimary,
                        indicatorColor = MinimalPrimaryLight,
                        unselectedIconColor = MinimalTextMuted,
                        unselectedTextColor = MinimalTextMuted
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Terminal, contentDescription = null) },
                    label = {
                        Text(
                            "Logs (${logs.size})",
                            fontSize = 11.sp,
                            fontWeight = if (selectedTab == 3) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MinimalPrimary,
                        selectedTextColor = MinimalPrimary,
                        indicatorColor = MinimalPrimaryLight,
                        unselectedIconColor = MinimalTextMuted,
                        unselectedTextColor = MinimalTextMuted
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> {
                    // Main Trading & Rebalancing View
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Error Banner
                        if (state.errorMessage != null) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MinimalError.copy(alpha = 0.08f),
                                    border = BorderStroke(1.dp, MinimalError.copy(alpha = 0.3f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Warning,
                                            contentDescription = "Hata",
                                            tint = MinimalError,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "İşlem Bildirimi",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = MinimalError
                                            )
                                            Text(
                                                text = state.errorMessage ?: "",
                                                fontSize = 12.sp,
                                                color = MinimalTextPrimary,
                                                modifier = Modifier.padding(top = 2.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { viewModel.dismissError() },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Kapat",
                                                tint = MinimalTextMuted,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Status Banner
                        if (state.statusMessage != null) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MinimalSuccess.copy(alpha = 0.08f),
                                    border = BorderStroke(1.dp, MinimalSuccess.copy(alpha = 0.3f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Info,
                                            contentDescription = "Bilgi",
                                            tint = MinimalSuccessDark,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = state.statusMessage ?: "",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MinimalSuccessDark,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }

                        // Portfolio Status Card
                        item {
                            PortfolioCard(
                                analysis = state.portfolioAnalysis,
                                currentPrice = state.currentPrice,
                                price24hChange = state.price24hChange,
                                isBotActive = state.isBotActive,
                                onManualRebalanceClick = {
                                    viewModel.requestInitialRebalanceDialog()
                                }
                            )
                        }

                        // 24/7 Grid Limit Orders Card
                        item {
                            ActiveOrdersCard(
                                gridPlan = state.gridPlan,
                                activeOrders = state.activeOrders,
                                currentPrice = state.currentPrice,
                                isBotActive = state.isBotActive,
                                stepPercent = state.stepPercent,
                                lastRebalancePrice = state.lastRebalancePrice,
                                isLoading = state.isLoading,
                                onStartBot = { viewModel.startBot() },
                                onStopBot = { viewModel.stopBot() },
                                onCancelAllOrders = { viewModel.cancelAllOrders() },
                                onEditBasePriceClick = { showEditBasePriceDialog = true }
                            )
                        }
                    }
                }
                1 -> {
                    TradeAnalysisScreen(
                        state = tradeAnalysisState,
                        isApiConfigured = state.isConfigured,
                        currentPrice = state.currentPrice,
                        onFetchAnalysis = { symbol, days -> viewModel.fetchTradeAnalysis(symbol, days) }
                    )
                }
                2 -> {
                    OrderHistoryScreen(
                        orders = orders,
                        onClearOrders = { viewModel.clearOrders() }
                    )
                }
                3 -> {
                    LogViewerScreen(
                        logs = logs,
                        onClearLogs = { viewModel.clearLogs() }
                    )
                }
            }

            // Initial Rebalance Confirmation Dialog
            if (state.showInitialRebalanceDialog && state.portfolioAnalysis != null) {
                InitialRebalanceDialog(
                    analysis = state.portfolioAnalysis!!,
                    isExecuting = state.isExecutingRebalance,
                    onConfirm = { viewModel.executeInitialRebalance() },
                    onDismiss = { viewModel.dismissInitialRebalanceDialog() }
                )
            }

            // API Keys Setup Dialog
            if (state.showApiKeyDialog) {
                ApiKeySetupDialog(
                    initialApiKey = state.apiKey,
                    initialApiSecret = state.apiSecret,
                    initialIsTestnet = state.isTestnet,
                    isDismissable = state.isConfigured,
                    isLoading = state.isLoading,
                    onDismiss = { viewModel.closeApiKeyDialog() },
                    onSave = { key, secret, testnet ->
                        viewModel.saveApiCredentials(key, secret, testnet)
                    }
                )
            }

            // Settings Dialog
            if (showSettingsDialog) {
                SettingsDialog(
                    currentStepPercent = state.stepPercent,
                    isTestnet = state.isTestnet,
                    onUpdateStepPercent = { viewModel.updateStepPercent(it) },
                    onOpenApiKeys = {
                        showSettingsDialog = false
                        viewModel.openApiKeyDialog()
                    },
                    onDismiss = { showSettingsDialog = false }
                )
            }

            // Edit Base Price Dialog
            if (showEditBasePriceDialog) {
                EditBasePriceDialog(
                    currentBasePrice = if (state.lastRebalancePrice > 0.0) state.lastRebalancePrice else state.currentPrice,
                    currentTickerPrice = state.currentPrice,
                    isBotActive = state.isBotActive,
                    stepPercent = state.stepPercent,
                    onGetLastRealFilledTradeInfo = { callback ->
                        viewModel.getLastRealFilledTradeInfo(callback)
                    },
                    onConfirm = { newPrice ->
                        viewModel.setCustomBasePrice(newPrice)
                        showEditBasePriceDialog = false
                    },
                    onDismiss = { showEditBasePriceDialog = false }
                )
            }
        }
    }
}

@Composable
fun EditBasePriceDialog(
    currentBasePrice: Double,
    currentTickerPrice: Double,
    isBotActive: Boolean,
    stepPercent: Double,
    onGetLastRealFilledTradeInfo: ((LastFilledTradeInfo?) -> Unit) -> Unit,
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit
) {
    var priceText by remember(currentBasePrice) {
        mutableStateOf(if (currentBasePrice > 0.0) RebalanceEngine.format4(currentBasePrice) else "")
    }
    var lastTradeInfo by remember { mutableStateOf<LastFilledTradeInfo?>(null) }
    var isLoadingTradeInfo by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun refreshTradeInfo() {
        isLoadingTradeInfo = true
        onGetLastRealFilledTradeInfo { info ->
            lastTradeInfo = info
            isLoadingTradeInfo = false
        }
    }

    LaunchedEffect(Unit) {
        refreshTradeInfo()
    }

    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = null,
                        tint = MinimalPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Baz Fiyatı Düzenle", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
                IconButton(
                    onClick = { refreshTradeInfo() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Yenile",
                        tint = MinimalPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Izgara (Grid) Alış ve Satış limit emirleri bu baz fiyata göre kurulur. Son iptal edilen veya gerçekleşen emirlerden sonra doğru baz fiyatı seçebilir veya elle yazabilirsiniz.",
                    fontSize = 11.sp,
                    color = MinimalTextSecondary,
                    lineHeight = 15.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = priceText,
                    onValueChange = {
                        priceText = it
                        errorMessage = null
                    },
                    label = { Text("Belirlenen Baz Fiyat ($)") },
                    placeholder = { Text("Örn: 0.5679") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = errorMessage != null,
                    supportingText = {
                        if (errorMessage != null) {
                            Text(errorMessage!!, color = MinimalError, fontSize = 10.sp)
                        } else {
                            val parsed = priceText.replace(",", ".").trim().toDoubleOrNull() ?: 0.0
                            if (parsed > 0.0) {
                                val buyP = parsed * (1.0 - stepPercent / 100.0)
                                val sellP = parsed * (1.0 + stepPercent / 100.0)
                                Text("Alış Emri: $${RebalanceEngine.format4(buyP)} | Satış Emri: $${RebalanceEngine.format4(sellP)}", fontSize = 10.sp)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text("Hızlı Değer Seçenekleri:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MinimalTextPrimary)

                Spacer(modifier = Modifier.height(6.dp))

                // Option 1: Last Real Trade from Bybit
                if (isLoadingTradeInfo) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MinimalSurfaceBorder.copy(alpha = 0.3f),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = MinimalPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Bybit son işlem geçmişi taranıyor...", fontSize = 11.sp, color = MinimalTextSecondary)
                        }
                    }
                } else if (lastTradeInfo != null && lastTradeInfo!!.price > 0.0) {
                    val info = lastTradeInfo!!
                    val isSelected = priceText.replace(",", ".").trim() == RebalanceEngine.format4(info.price)
                    val isBuy = info.side.equals("Buy", ignoreCase = true)
                    val sideColor = if (isBuy) MinimalSuccess else MinimalError
                    val sideLabel = if (isBuy) "Alış" else if (info.side.equals("Sell", ignoreCase = true)) "Satış" else info.side

                    Surface(
                        onClick = {
                            priceText = RebalanceEngine.format4(info.price)
                            errorMessage = null
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) MinimalSuccess.copy(alpha = 0.15f) else MinimalSuccess.copy(alpha = 0.06f),
                        border = BorderStroke(
                            if (isSelected) 1.5.dp else 1.dp,
                            if (isSelected) MinimalSuccess else MinimalSuccess.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    if (isBuy) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                    contentDescription = null,
                                    tint = sideColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Son Gerçekleşen İşlem:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MinimalTextPrimary)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = sideColor.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = sideLabel,
                                                color = sideColor,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    val timeStr = if (info.timestamp > 0) timeFormatter.format(Date(info.timestamp)) else ""
                                    Text(
                                        text = "${info.source}${if (timeStr.isNotBlank()) " ($timeStr)" else ""}${if (info.qty > 0) " • ${RebalanceEngine.format2(info.qty)} MNT" else ""}",
                                        fontSize = 9.sp,
                                        color = MinimalTextSecondary
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "$${RebalanceEngine.format4(info.price)}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MinimalSuccess
                                )
                                if (isSelected) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MinimalSuccess, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }

                // Option 2: Current Ticker Price
                if (currentTickerPrice > 0.0) {
                    val isSelected = priceText.replace(",", ".").trim() == RebalanceEngine.format4(currentTickerPrice)
                    Surface(
                        onClick = {
                            priceText = RebalanceEngine.format4(currentTickerPrice)
                            errorMessage = null
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) MinimalPrimary.copy(alpha = 0.15f) else MinimalPrimary.copy(alpha = 0.06f),
                        border = BorderStroke(
                            if (isSelected) 1.5.dp else 1.dp,
                            if (isSelected) MinimalPrimary else MinimalPrimary.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ShowChart, contentDescription = null, tint = MinimalPrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Anlık Piyasa Fiyatı:", fontSize = 11.sp, color = MinimalTextPrimary)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("$${RebalanceEngine.format4(currentTickerPrice)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MinimalPrimary)
                                if (isSelected) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MinimalPrimary, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }

                if (isBotActive) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MinimalWarning.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, MinimalWarning.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = MinimalWarning, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Bot aktif olduğu için Bybit'teki eski emirler iptal edilecek ve bu yeni baz fiyata göre taze limit Alış ve limit Satış emirleri derhal kurulacaktır.",
                                fontSize = 10.sp,
                                color = MinimalTextSecondary,
                                lineHeight = 13.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsed = priceText.replace(",", ".").trim().toDoubleOrNull()
                    if (parsed == null || parsed <= 0.0) {
                        errorMessage = "Lütfen geçerli bir fiyat girin"
                    } else {
                        onConfirm(parsed)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MinimalPrimary)
            ) {
                Text(if (isBotActive) "Kaydet ve Izgarayı Kur" else "Baz Fiyatı Kaydet")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Vazgeç")
            }
        }
    )
}

