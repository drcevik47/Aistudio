package com.example.ui.components

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.bot.RebalanceEngine
import com.example.data.local.entity.ExchangeTradeEntity
import com.example.data.local.entity.OrderEntity
import com.example.data.remote.model.TradeAnalysisResult
import com.example.ui.theme.MinimalError
import com.example.ui.theme.MinimalErrorDark
import com.example.ui.theme.MinimalErrorLight
import com.example.ui.theme.MinimalPrimary
import com.example.ui.theme.MinimalPrimaryDark
import com.example.ui.theme.MinimalPrimaryLight
import com.example.ui.theme.MinimalSecondary
import com.example.ui.theme.MinimalSuccess
import com.example.ui.theme.MinimalSuccessDark
import com.example.ui.theme.MinimalSuccessLight
import com.example.ui.theme.MinimalSurface
import com.example.ui.theme.MinimalSurfaceBorder
import com.example.ui.theme.MinimalSurfaceBorderLight
import com.example.ui.theme.MinimalSurfaceElevated
import com.example.ui.theme.MinimalTextMuted
import com.example.ui.theme.MinimalTextPrimary
import com.example.ui.theme.MinimalTextSecondary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun OrderHistoryScreen(
    orders: List<OrderEntity>,
    exchangeTrades: List<ExchangeTradeEntity> = emptyList(),
    liveAnalysis: TradeAnalysisResult? = null,
    currentPrice: Double = 0.0,
    onClearOrders: () -> Unit,
    onClearExchangeTrades: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf("ALL") } // ALL, BUY, SELL, FILLED
    var showCalculateDialog by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var activeFilterStartDateMillis by remember { mutableStateOf<Long?>(null) }
    var activeFilterEndDateMillis by remember { mutableStateOf<Long?>(null) }

    // Distinct traded symbols from DB, with MNT always positioned first as the default
    val availableSymbols = remember(orders, exchangeTrades) {
        val syms = (orders.map { it.symbol } + exchangeTrades.map { it.symbol })
            .filter { it.isNotBlank() }
            .map { it.uppercase().trim() }
            .distinct()
            .toMutableList()
        if (!syms.contains("MNTUSDT")) {
            syms.add(0, "MNTUSDT")
        } else {
            syms.remove("MNTUSDT")
            syms.add(0, "MNTUSDT")
        }
        syms
    }

    // Default symbol to "MNTUSDT" (MNT coin) as requested
    var selectedSymbol by remember { mutableStateOf("MNTUSDT") }
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()) }

    // Screen Analysis: calculated from database for the selected coin and date filter
    val screenAnalysis = remember(orders, exchangeTrades, selectedSymbol, activeFilterStartDateMillis, activeFilterEndDateMillis) {
        RebalanceEngine.computeLiveTradeAnalysis(
            orders = orders,
            exchangeTrades = exchangeTrades,
            symbol = selectedSymbol,
            startTimestamp = activeFilterStartDateMillis,
            endTimestamp = activeFilterEndDateMillis
        )
    }

    val filteredOrders = remember(orders, selectedFilter, selectedSymbol, activeFilterStartDateMillis, activeFilterEndDateMillis) {
        orders.filter { order ->
            val symbolMatch = if (selectedSymbol == "ALL") {
                true
            } else {
                order.symbol.equals(selectedSymbol, ignoreCase = true) ||
                        (selectedSymbol == "MNTUSDT" && order.symbol.isBlank())
            }
            val filterMatch = when (selectedFilter) {
                "BUY" -> order.side.equals("Buy", ignoreCase = true)
                "SELL" -> order.side.equals("Sell", ignoreCase = true)
                "FILLED" -> order.status.equals("Filled", ignoreCase = true)
                else -> true
            }
            val startMatch = activeFilterStartDateMillis == null || order.timestamp >= activeFilterStartDateMillis!!
            val endMatch = activeFilterEndDateMillis == null || order.timestamp <= activeFilterEndDateMillis!!
            symbolMatch && filterMatch && startMatch && endMatch
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("order_history_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Header & Actions (Hesapla Tuşu & Temizle)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MinimalPrimaryLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ReceiptLong,
                            contentDescription = null,
                            tint = MinimalPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Emir & Ticaret Geçmişi",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MinimalTextPrimary,
                            letterSpacing = (-0.2).sp,
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = "Toplam ${orders.size} emir kaydı",
                            fontSize = 12.sp,
                            color = MinimalTextSecondary,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Hesapla Butonu
                    FilledTonalButton(
                        onClick = { showCalculateDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (activeFilterStartDateMillis != null || activeFilterEndDateMillis != null) MinimalPrimary else MinimalPrimaryLight,
                            contentColor = if (activeFilterStartDateMillis != null || activeFilterEndDateMillis != null) Color.White else MinimalPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("calculate_trades_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Calculate,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (activeFilterStartDateMillis != null || activeFilterEndDateMillis != null) "Hesaplandı" else "Hesapla",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    // Sıfırla / Temizle Butonu (Hesapla kutusunun hemen sağında yer alır)
                    val hasHistory = orders.isNotEmpty() || exchangeTrades.isNotEmpty()
                    IconButton(
                        onClick = {
                            if (hasHistory) {
                                showClearHistoryDialog = true
                            }
                        },
                        enabled = hasHistory,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (hasHistory) MinimalErrorLight.copy(alpha = 0.5f) else MinimalSurfaceElevated)
                            .border(
                                1.dp,
                                if (hasHistory) MinimalErrorDark.copy(alpha = 0.35f) else MinimalSurfaceBorder,
                                CircleShape
                            )
                            .testTag("clear_orders_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Geçmişi Sıfırla",
                            tint = if (hasHistory) MinimalErrorDark else MinimalTextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Active Date Filter Banner (if applied from calculator)
        if (activeFilterStartDateMillis != null || activeFilterEndDateMillis != null) {
            item {
                val rangeStr = if (activeFilterStartDateMillis != null && activeFilterEndDateMillis != null) {
                    val s = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(activeFilterStartDateMillis!!))
                    val e = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(activeFilterEndDateMillis!!))
                    "$s - $e Aralığı"
                } else if (activeFilterStartDateMillis != null) {
                    val s = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(activeFilterStartDateMillis!!))
                    "$s Tarihinden İtibaren"
                } else {
                    val e = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(activeFilterEndDateMillis!!))
                    "$e Tarihine Kadar"
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MinimalPrimaryLight.copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, MinimalPrimary.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = MinimalPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Özel Filtre: $rangeStr",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MinimalTextPrimary
                                )
                                Text(
                                    text = "Veritabanındaki seçili aralık hesaplanıyor (${filteredOrders.size} emir).",
                                    fontSize = 11.sp,
                                    color = MinimalTextSecondary
                                )
                            }
                        }
                        TextButton(
                            onClick = {
                                activeFilterStartDateMillis = null
                                activeFilterEndDateMillis = null
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Tümünü Göster",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MinimalPrimary
                            )
                        }
                    }
                }
            }
        }

        // 2. Persistent Analysis Section: Karlılık ve Kazanç
        if (screenAnalysis != null && (screenAnalysis.buyTradeCount > 0 || screenAnalysis.sellTradeCount > 0)) {
            item {
                TradeProfitabilityCard(
                    analysis = screenAnalysis,
                    currentPrice = currentPrice
                )
            }

            // 3. Persistent Analysis Section: Tüm Alış ve Satış İşlemleri Tabloları
            item {
                TradeAveragesOverview(analysis = screenAnalysis)
            }
        }

        // 4. Filter Chips Header (Coin / Sembol Seçimi + Emir Tipi Filtresi)
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Coin / Parite Seçici (MNT Varsayılan)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Coin / Parite Filtresi",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MinimalTextSecondary
                        )
                        Text(
                            text = if (selectedSymbol == "MNTUSDT") "Varsayılan (MNT)" else if (selectedSymbol == "ALL") "Tüm Pariteler" else selectedSymbol,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MinimalPrimary
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableSymbols.forEach { sym ->
                            val isSelected = selectedSymbol.equals(sym, ignoreCase = true)
                            val cleanName = if (sym.endsWith("USDT")) sym.removeSuffix("USDT") else sym
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedSymbol = sym },
                                shape = RoundedCornerShape(999.dp),
                                label = {
                                    Text(
                                        text = if (sym == "MNTUSDT") "🪙 MNT (Varsayılan)" else "🪙 $cleanName",
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MinimalPrimary,
                                    selectedLabelColor = Color.White,
                                    containerColor = MinimalSurfaceElevated,
                                    labelColor = MinimalTextPrimary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = MinimalSurfaceBorder,
                                    selectedBorderColor = MinimalPrimary
                                )
                            )
                        }

                        // Tüm Pariteler
                        val isAll = selectedSymbol == "ALL"
                        FilterChip(
                            selected = isAll,
                            onClick = { selectedSymbol = "ALL" },
                            shape = RoundedCornerShape(999.dp),
                            label = {
                                Text(
                                    text = "🌐 Tüm Pariteler",
                                    fontSize = 12.sp,
                                    fontWeight = if (isAll) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MinimalPrimary,
                                selectedLabelColor = Color.White,
                                containerColor = MinimalSurfaceElevated,
                                labelColor = MinimalTextPrimary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isAll,
                                borderColor = MinimalSurfaceBorder,
                                selectedBorderColor = MinimalPrimary
                            )
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Emir Kayıtları (${filteredOrders.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MinimalTextPrimary
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "ALL" to "Tümü (${orders.size})",
                        "BUY" to "Alışlar",
                        "SELL" to "Satışlar",
                        "FILLED" to "Gerçekleşenler"
                    ).forEach { (key, label) ->
                        FilterChip(
                            selected = selectedFilter == key,
                            onClick = { selectedFilter = key },
                            shape = RoundedCornerShape(999.dp),
                            label = {
                                Text(
                                    label,
                                    fontSize = 11.sp,
                                    fontWeight = if (selectedFilter == key) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MinimalPrimary,
                                selectedLabelColor = Color.White,
                                containerColor = MinimalSurface,
                                labelColor = MinimalTextSecondary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selectedFilter == key,
                                borderColor = MinimalSurfaceBorder,
                                selectedBorderColor = MinimalPrimary
                            )
                        )
                    }
                }
            }
        }

        // 5. Orders List Items
        if (filteredOrders.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MinimalSurface),
                    border = BorderStroke(1.dp, MinimalSurfaceBorderLight)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(MinimalSurfaceElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = MinimalTextMuted,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Filtreye uygun kayıt bulunamadı",
                            color = MinimalTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Bot çalıştıkça ve alım/satım yaptıkça tüm emirler burada listelenir.",
                            color = MinimalTextSecondary,
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        } else {
            items(filteredOrders, key = { it.id }) { order ->
                OrderItemCard(order = order, dateFormat = dateFormat)
            }
        }
    }

    // Açılır Pencere: Veritabanından Kârlılık Hesaplayıcı
    if (showCalculateDialog) {
        CalculateTradesDialog(
            orders = orders,
            exchangeTrades = exchangeTrades,
            currentPrice = currentPrice,
            activeStartDateMillis = activeFilterStartDateMillis,
            activeEndDateMillis = activeFilterEndDateMillis,
            activeSymbol = selectedSymbol,
            onApplyFilter = { newStart, newEnd, newSym ->
                activeFilterStartDateMillis = newStart
                activeFilterEndDateMillis = newEnd
                selectedSymbol = newSym
            },
            onDismiss = { showCalculateDialog = false }
        )
    }

    // Açılır Pencere: İşlem Geçmişini Sıfırla Onayı
    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            icon = {
                Icon(
                    Icons.Default.DeleteSweep,
                    contentDescription = null,
                    tint = MinimalErrorDark,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "İşlem Geçmişini Sıfırla?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = "Telefon hafızasında kayıtlı borsa işlem verileri ve bot emir kayıtları temizlenecektir. Borsadan dilediğiniz zaman 'Borsa Analiz' sekmesinden yeniden veri çekebilirsiniz. Devam etmek istiyor musunuz?",
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = MinimalTextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearHistoryDialog = false
                        onClearOrders()
                        onClearExchangeTrades?.invoke()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MinimalErrorDark)
                ) {
                    Text("Evet, Sıfırla", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("İptal")
                }
            },
            containerColor = MinimalSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun CalculateTradesDialog(
    orders: List<OrderEntity>,
    exchangeTrades: List<ExchangeTradeEntity>,
    currentPrice: Double,
    activeStartDateMillis: Long?,
    activeEndDateMillis: Long? = null,
    activeSymbol: String = "MNTUSDT",
    onApplyFilter: (Long?, Long?, String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedStartDateMillis by remember {
        mutableStateOf<Long?>(
            activeStartDateMillis ?: (System.currentTimeMillis() - 30L * 24 * 3600 * 1000L)
        )
    }
    var selectedEndDateMillis by remember {
        mutableStateOf<Long?>(activeEndDateMillis)
    }
    var selectedDaysOption by remember {
        mutableStateOf<Int?>(
            if (activeStartDateMillis == null && activeEndDateMillis == null) 0
            else if (activeStartDateMillis != null && activeEndDateMillis == null) 30
            else -1
        )
    }

    // Available symbols in DB with MNT first
    val availableSymbols = remember(orders, exchangeTrades) {
        val syms = (orders.map { it.symbol } + exchangeTrades.map { it.symbol })
            .filter { it.isNotBlank() }
            .map { it.uppercase().trim() }
            .distinct()
            .toMutableList()
        if (!syms.contains("MNTUSDT")) {
            syms.add(0, "MNTUSDT")
        } else {
            syms.remove("MNTUSDT")
            syms.add(0, "MNTUSDT")
        }
        syms
    }

    var selectedSymbol by remember { mutableStateOf(activeSymbol) }

    val analysis = remember(orders, exchangeTrades, selectedSymbol, selectedStartDateMillis, selectedEndDateMillis) {
        RebalanceEngine.computeLiveTradeAnalysis(
            orders = orders,
            exchangeTrades = exchangeTrades,
            symbol = selectedSymbol,
            startTimestamp = selectedStartDateMillis,
            endTimestamp = selectedEndDateMillis
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 16.dp)
                .heightIn(max = 700.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MinimalSurface),
            border = BorderStroke(1.dp, MinimalSurfaceBorderLight),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // 1. Başlık Alanı
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MinimalPrimaryLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Calculate,
                                contentDescription = null,
                                tint = MinimalPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Kârlılık Hesaplayıcı",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = MinimalTextPrimary
                            )
                            Text(
                                text = "Veritabanından hesaplanır (Borsadan çekilmez)",
                                fontSize = 11.sp,
                                color = MinimalSuccessDark,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MinimalSurfaceElevated)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Kapat",
                            tint = MinimalTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 2. İçerik (Kaydırılabilir)
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Coin / Parite Seçimi
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Hesaplanacak Coin / Parite:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MinimalTextSecondary
                            )
                            Text(
                                text = if (selectedSymbol == "MNTUSDT") "MNT (Varsayılan)" else if (selectedSymbol == "ALL") "Tüm Portföy" else selectedSymbol,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MinimalPrimary
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            availableSymbols.forEach { sym ->
                                val isSelected = selectedSymbol.equals(sym, ignoreCase = true)
                                val cleanName = if (sym.endsWith("USDT")) sym.removeSuffix("USDT") else sym
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedSymbol = sym },
                                    shape = RoundedCornerShape(999.dp),
                                    label = {
                                        Text(
                                            text = if (sym == "MNTUSDT") "🪙 MNT (Varsayılan)" else "🪙 $cleanName",
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MinimalPrimary,
                                        selectedLabelColor = Color.White,
                                        containerColor = MinimalSurfaceElevated,
                                        labelColor = MinimalTextPrimary
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = isSelected,
                                        borderColor = MinimalSurfaceBorder,
                                        selectedBorderColor = MinimalPrimary
                                    )
                                )
                            }

                            // Tüm Portföy
                            val isAll = selectedSymbol == "ALL"
                            FilterChip(
                                selected = isAll,
                                onClick = { selectedSymbol = "ALL" },
                                shape = RoundedCornerShape(999.dp),
                                label = {
                                    Text(
                                        text = "📊 Tüm Portföy",
                                        fontSize = 11.sp,
                                        fontWeight = if (isAll) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MinimalPrimary,
                                    selectedLabelColor = Color.White,
                                    containerColor = MinimalSurfaceElevated,
                                    labelColor = MinimalTextPrimary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isAll,
                                    borderColor = MinimalSurfaceBorder,
                                    selectedBorderColor = MinimalPrimary
                                )
                            )
                        }
                    }
                    // Tarih Seçici Başlık
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Hesaplama Tarih Aralığı:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MinimalTextSecondary
                        )
                        if (selectedStartDateMillis != null || selectedEndDateMillis != null) {
                            Text(
                                text = "Tümünü Seç",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MinimalPrimary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable {
                                        selectedDaysOption = 0
                                        selectedStartDateMillis = null
                                        selectedEndDateMillis = null
                                    }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Hızlı Süre Butonları
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val quickPresets = listOf(
                            1 to "Son 24S",
                            7 to "Son 7 Gün",
                            15 to "Son 15 Gün",
                            30 to "Son 30 Gün",
                            90 to "Son 90 Gün",
                            180 to "Son 6 Ay",
                            0 to "Tüm Geçmiş"
                        )

                        quickPresets.forEach { (days, label) ->
                            val isSelected = if (days == 0) {
                                selectedStartDateMillis == null && selectedEndDateMillis == null
                            } else {
                                selectedDaysOption == days && selectedStartDateMillis != null && selectedEndDateMillis == null
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) MinimalPrimary else MinimalSurfaceElevated,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) MinimalPrimary else MinimalSurfaceBorder
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        if (days == 0) {
                                            selectedDaysOption = 0
                                            selectedStartDateMillis = null
                                            selectedEndDateMillis = null
                                        } else {
                                            selectedDaysOption = days
                                            selectedStartDateMillis = System.currentTimeMillis() - (days * 24L * 3600L * 1000L)
                                            selectedEndDateMillis = null
                                        }
                                    }
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else MinimalTextSecondary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    // Başlangıç ve Bitiş Tarihi Seçim Kartları
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Başlangıç Tarihi
                        val startStr = if (selectedStartDateMillis != null) {
                            SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(selectedStartDateMillis!!))
                        } else {
                            "En Baştan"
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MinimalSurfaceElevated,
                            border = BorderStroke(
                                1.dp,
                                if (selectedDaysOption == -1 && selectedStartDateMillis != null) MinimalPrimary else MinimalSurfaceBorder
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    val cal = Calendar.getInstance()
                                    if (selectedStartDateMillis != null) {
                                        cal.timeInMillis = selectedStartDateMillis!!
                                    }
                                    DatePickerDialog(
                                        context,
                                        { _, year, month, dayOfMonth ->
                                            val pickedCal = Calendar.getInstance().apply {
                                                set(Calendar.YEAR, year)
                                                set(Calendar.MONTH, month)
                                                set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                                set(Calendar.HOUR_OF_DAY, 0)
                                                set(Calendar.MINUTE, 0)
                                                set(Calendar.SECOND, 0)
                                                set(Calendar.MILLISECOND, 0)
                                            }
                                            selectedDaysOption = -1
                                            selectedStartDateMillis = pickedCal.timeInMillis
                                            if (selectedEndDateMillis != null && selectedEndDateMillis!! < pickedCal.timeInMillis) {
                                                val endPicked = Calendar.getInstance().apply {
                                                    timeInMillis = pickedCal.timeInMillis
                                                    set(Calendar.HOUR_OF_DAY, 23)
                                                    set(Calendar.MINUTE, 59)
                                                    set(Calendar.SECOND, 59)
                                                    set(Calendar.MILLISECOND, 999)
                                                }
                                                selectedEndDateMillis = endPicked.timeInMillis
                                            }
                                        },
                                        cal.get(Calendar.YEAR),
                                        cal.get(Calendar.MONTH),
                                        cal.get(Calendar.DAY_OF_MONTH)
                                    ).apply {
                                        val maxLimit = selectedEndDateMillis ?: System.currentTimeMillis()
                                        datePicker.maxDate = maxLimit
                                        show()
                                    }
                                }
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "BAŞLANGIÇ",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MinimalTextMuted
                                    )
                                    Icon(
                                        imageVector = Icons.Default.CalendarToday,
                                        contentDescription = null,
                                        tint = MinimalPrimary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = startStr,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedStartDateMillis != null) MinimalTextPrimary else MinimalTextSecondary,
                                    maxLines = 1
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = null,
                            tint = MinimalTextMuted,
                            modifier = Modifier.size(14.dp)
                        )

                        // 2. Bitiş Tarihi
                        val endStr = if (selectedEndDateMillis != null) {
                            SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(selectedEndDateMillis!!))
                        } else {
                            "Bugün / Şimdi"
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MinimalSurfaceElevated,
                            border = BorderStroke(
                                1.dp,
                                if (selectedDaysOption == -1 && selectedEndDateMillis != null) MinimalPrimary else MinimalSurfaceBorder
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    val cal = Calendar.getInstance()
                                    if (selectedEndDateMillis != null) {
                                        cal.timeInMillis = selectedEndDateMillis!!
                                    }
                                    DatePickerDialog(
                                        context,
                                        { _, year, month, dayOfMonth ->
                                            val pickedCal = Calendar.getInstance().apply {
                                                set(Calendar.YEAR, year)
                                                set(Calendar.MONTH, month)
                                                set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                                set(Calendar.HOUR_OF_DAY, 23)
                                                set(Calendar.MINUTE, 59)
                                                set(Calendar.SECOND, 59)
                                                set(Calendar.MILLISECOND, 999)
                                            }
                                            selectedDaysOption = -1
                                            selectedEndDateMillis = pickedCal.timeInMillis
                                            if (selectedStartDateMillis != null && selectedStartDateMillis!! > pickedCal.timeInMillis) {
                                                val startPicked = Calendar.getInstance().apply {
                                                    timeInMillis = pickedCal.timeInMillis
                                                    set(Calendar.HOUR_OF_DAY, 0)
                                                    set(Calendar.MINUTE, 0)
                                                    set(Calendar.SECOND, 0)
                                                    set(Calendar.MILLISECOND, 0)
                                                }
                                                selectedStartDateMillis = startPicked.timeInMillis
                                            }
                                        },
                                        cal.get(Calendar.YEAR),
                                        cal.get(Calendar.MONTH),
                                        cal.get(Calendar.DAY_OF_MONTH)
                                    ).apply {
                                        if (selectedStartDateMillis != null) {
                                            datePicker.minDate = selectedStartDateMillis!!
                                        }
                                        datePicker.maxDate = System.currentTimeMillis() + 86400000L
                                        show()
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "BİTİŞ",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MinimalTextMuted
                                        )
                                        Icon(
                                            imageVector = Icons.Default.CalendarToday,
                                            contentDescription = null,
                                            tint = MinimalPrimary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = endStr,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selectedEndDateMillis != null) MinimalTextPrimary else MinimalTextSecondary,
                                        maxLines = 1
                                    )
                                }
                                if (selectedEndDateMillis != null) {
                                    IconButton(
                                        onClick = {
                                            selectedEndDateMillis = null
                                            if (selectedStartDateMillis == null) {
                                                selectedDaysOption = 0
                                            }
                                        },
                                        modifier = Modifier.size(18.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Bugün",
                                            tint = MinimalTextMuted,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Seçili Tarih Bilgi Şeridi
                    val activeLabel = if (selectedStartDateMillis != null && selectedEndDateMillis != null) {
                        val formattedStart = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(selectedStartDateMillis!!))
                        val formattedEnd = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(selectedEndDateMillis!!))
                        "📅 $formattedStart – $formattedEnd Aralığı"
                    } else if (selectedStartDateMillis != null) {
                        val formatted = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(selectedStartDateMillis!!))
                        "📅 $formatted Tarihinden İtibaren"
                    } else if (selectedEndDateMillis != null) {
                        val formatted = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(selectedEndDateMillis!!))
                        "📅 $formatted Tarihine Kadar"
                    } else {
                        "📅 Kayıtlı Tüm Geçmiş"
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MinimalSurfaceElevated,
                        border = BorderStroke(1.dp, MinimalSurfaceBorderLight),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = activeLabel,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MinimalTextPrimary
                            )
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = MinimalSuccessLight
                            ) {
                                Text(
                                    text = "Yerel DB Verisi",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MinimalSuccessDark,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    // Hesaplama Sonuçları
                    if (analysis.buyTradeCount == 0 && analysis.sellTradeCount == 0) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MinimalSurfaceElevated,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MinimalTextMuted,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Bu tarihten sonra işlem kaydı yok",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MinimalTextPrimary
                                )
                                Text(
                                    text = "Lütfen daha erken bir başlangıç tarihi seçin veya botun işlem yapmasını bekleyin.",
                                    fontSize = 11.sp,
                                    color = MinimalTextSecondary,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    } else {
                        // Rozetler (Toplam İşlem, Net Varlık Değişimi)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MinimalSurfaceElevated,
                                border = BorderStroke(1.dp, MinimalSurfaceBorderLight),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "Toplam İşlem",
                                        fontSize = 10.sp,
                                        color = MinimalTextSecondary
                                    )
                                    Text(
                                        text = "${analysis.buyTradeCount + analysis.sellTradeCount} Adet",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MinimalTextPrimary
                                    )
                                    Text(
                                        text = "${analysis.buyTradeCount} Alış • ${analysis.sellTradeCount} Satış",
                                        fontSize = 9.sp,
                                        color = MinimalTextMuted
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MinimalSurfaceElevated,
                                border = BorderStroke(1.dp, MinimalSurfaceBorderLight),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "Net Varlık Değişimi",
                                        fontSize = 10.sp,
                                        color = MinimalTextSecondary
                                    )
                                    Text(
                                        text = "${if (analysis.netQty >= 0) "+" else ""}${RebalanceEngine.format4(analysis.netQty)}",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (analysis.netQty >= 0) MinimalSuccessDark else MinimalErrorDark
                                    )
                                    Text(
                                        text = analysis.baseAsset,
                                        fontSize = 9.sp,
                                        color = MinimalTextMuted
                                    )
                                }
                            }
                        }

                        // Alış vs Satış Karşılaştırma Kartı
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MinimalSurfaceElevated,
                            border = BorderStroke(1.dp, MinimalSurfaceBorderLight),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // Alış Tarafı
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "ORTALAMA ALIŞ",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MinimalSuccessDark
                                        )
                                        Text(
                                            text = "$${RebalanceEngine.format4(analysis.avgBuyPrice)}",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MinimalTextPrimary
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Alınan: ${RebalanceEngine.format2(analysis.totalBuyQty)} ${analysis.baseAsset}",
                                            fontSize = 10.sp,
                                            color = MinimalTextSecondary
                                        )
                                        Text(
                                            text = "Tutar: $${RebalanceEngine.format2(analysis.totalBuyValue)}",
                                            fontSize = 10.sp,
                                            color = MinimalTextMuted
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    // Satış Tarafı
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "ORTALAMA SATIŞ",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MinimalErrorDark
                                        )
                                        Text(
                                            text = "$${RebalanceEngine.format4(analysis.avgSellPrice)}",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MinimalTextPrimary
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Satılan: ${RebalanceEngine.format2(analysis.totalSellQty)} ${analysis.baseAsset}",
                                            fontSize = 10.sp,
                                            color = MinimalTextSecondary
                                        )
                                        Text(
                                            text = "Hasılat: $${RebalanceEngine.format2(analysis.totalSellValue)}",
                                            fontSize = 10.sp,
                                            color = MinimalTextMuted
                                        )
                                    }
                                }
                            }
                        }

                        // Kâr / Zarar & Arbitraj Kartı
                        val isProfitable = analysis.priceDifference > 0.0
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isProfitable) MinimalSuccessLight.copy(alpha = 0.5f) else MinimalErrorLight.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, if (isProfitable) MinimalSuccess.copy(alpha = 0.3f) else MinimalError.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Fiyat Farkı (Alış / Satış)",
                                            fontSize = 11.sp,
                                            color = MinimalTextSecondary
                                        )
                                        Text(
                                            text = "${if (analysis.priceDifference >= 0) "+" else ""}$${RebalanceEngine.format4(analysis.priceDifference)} (${if (analysis.profitPercentage >= 0) "+" else ""}%${RebalanceEngine.format2(analysis.profitPercentage)})",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isProfitable) MinimalSuccessDark else MinimalErrorDark
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "Gerçekleşen Net Kâr",
                                            fontSize = 11.sp,
                                            color = MinimalTextSecondary
                                        )
                                        Text(
                                            text = "${if (analysis.netProfitUsdt >= 0) "+" else ""}$${RebalanceEngine.format2(analysis.netProfitUsdt)}",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (analysis.netProfitUsdt >= 0) MinimalSuccessDark else MinimalErrorDark
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Eşleşen Hacim: ${RebalanceEngine.format2(analysis.matchedQty)} ${analysis.baseAsset}",
                                        fontSize = 10.sp,
                                        color = MinimalTextSecondary
                                    )
                                    Text(
                                        text = "Toplam Komisyon: $${RebalanceEngine.format4(analysis.totalFee)}",
                                        fontSize = 10.sp,
                                        color = MinimalTextSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 3. Alt Butonlar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = "Kapat",
                            fontWeight = FontWeight.SemiBold,
                            color = MinimalTextSecondary
                        )
                    }

                    Button(
                        onClick = {
                            onApplyFilter(selectedStartDateMillis, selectedEndDateMillis, selectedSymbol)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MinimalPrimary,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Ekrana Filtre Olarak Uygula",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderItemCard(
    order: OrderEntity,
    dateFormat: SimpleDateFormat
) {
    val isBuy = order.side.equals("Buy", ignoreCase = true)
    val sideColor = if (isBuy) MinimalSuccessDark else MinimalError
    val sideBg = if (isBuy) MinimalSuccessLight else MinimalErrorLight

    val displayPair = if (order.symbol.isNotBlank()) {
        if (order.symbol.endsWith("USDT", ignoreCase = true)) {
            "${order.symbol.removeSuffix("USDT").uppercase()}/USDT"
        } else {
            order.symbol.uppercase()
        }
    } else {
        "MNT/USDT"
    }

    val baseAsset = if (order.symbol.isNotBlank()) {
        if (order.symbol.endsWith("USDT", ignoreCase = true)) {
            order.symbol.removeSuffix("USDT").uppercase()
        } else {
            order.symbol.uppercase()
        }
    } else {
        "MNT"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MinimalSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MinimalSurfaceBorderLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(sideBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isBuy) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                    contentDescription = null,
                    tint = sideColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .padding(end = 6.dp)
                    ) {
                        Text(
                            text = "${if (isBuy) "ALIŞ" else "SATIŞ"} ($displayPair)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = sideColor,
                            maxLines = 1,
                            softWrap = false
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MinimalSurfaceElevated
                        ) {
                            Text(
                                text = order.orderType,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MinimalTextSecondary,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Status Badge
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = when (order.status) {
                            "Filled" -> MinimalSuccessLight
                            "Cancelled" -> MinimalSurfaceElevated
                            else -> MinimalPrimaryLight
                        }
                    ) {
                        Text(
                            text = when (order.status) {
                                "Filled" -> "Gerçekleşti"
                                "Cancelled" -> "İptal Edildi"
                                "New" -> "Açık Emir"
                                else -> order.status
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            color = when (order.status) {
                                "Filled" -> MinimalSuccessDark
                                "Cancelled" -> MinimalTextMuted
                                else -> MinimalPrimaryDark
                            },
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Miktar: ${RebalanceEngine.format4(order.qty)} $baseAsset",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MinimalTextPrimary,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = "Fiyat: ${if (order.price > 0) "$${RebalanceEngine.format4(order.price)}" else "Market"}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MinimalPrimary,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val trigger = when (order.triggerReason) {
                        "InitialRebalance" -> "%50 Dengeleme"
                        "GridStepUpSell" -> "Grid Satış"
                        "GridStepDownBuy" -> "Grid Alış"
                        else -> order.triggerReason.ifBlank { "Manuel" }
                    }
                    Text(
                        text = "Tetik: $trigger",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = MinimalSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    )
                    Text(
                        text = dateFormat.format(Date(order.timestamp)),
                        fontSize = 10.sp,
                        color = MinimalTextMuted,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}
