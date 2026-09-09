package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.model.BybitExecutionDto
import com.example.data.remote.model.TradeAnalysisResult
import com.example.ui.TradeAnalysisUiState
import com.example.ui.theme.MinimalBg
import com.example.ui.theme.MinimalError
import com.example.ui.theme.MinimalErrorDark
import com.example.ui.theme.MinimalErrorLight
import com.example.ui.theme.MinimalPrimary
import com.example.ui.theme.MinimalPrimaryDark
import com.example.ui.theme.MinimalPrimaryLight
import com.example.ui.theme.MinimalSecondary
import com.example.ui.theme.MinimalSecondaryLight
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
import android.app.DatePickerDialog
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun TradeAnalysisScreen(
    state: TradeAnalysisUiState,
    isApiConfigured: Boolean,
    currentPrice: Double = 0.0,
    onFetchAnalysis: (symbol: String?, daysBack: Int, startTimestamp: Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf("ALL") } // "ALL", "BUY", "SELL"
    var selectedDaysBack by remember { mutableStateOf(730) } // Default 730 days (2 years - Bybit API maximum)
    var selectedStartDateMillis by remember { mutableStateOf<Long?>(null) } // Custom start date chosen by user
    var selectedSymbolOption by remember { mutableStateOf("MNTUSDT") } // "MNTUSDT", "ALL", "CUSTOM"
    var customSymbolText by remember { mutableStateOf("") }

    val triggerFetch = {
        val symbol = when (selectedSymbolOption) {
            "ALL" -> null
            "CUSTOM" -> customSymbolText.trim().ifBlank { "MNTUSDT" }
            else -> "MNTUSDT"
        }
        onFetchAnalysis(symbol, selectedDaysBack, selectedStartDateMillis)
    }

    val analysis = state.analysis
    val allExecutions = analysis?.executions ?: emptyList()
    val filteredExecutions = remember(allExecutions, selectedFilter) {
        when (selectedFilter) {
            "BUY" -> allExecutions.filter { it.isBuy }
            "SELL" -> allExecutions.filter { it.isSell }
            else -> allExecutions
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MinimalBg)
            .testTag("trade_analysis_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Action & Header Card
        item {
            TradeAnalysisHeaderCard(
                isLoading = state.isLoading,
                isApiConfigured = isApiConfigured,
                lastFetchedAt = state.lastFetchedAt,
                selectedDaysBack = selectedDaysBack,
                onDaysBackChange = { selectedDaysBack = it },
                selectedStartDateMillis = selectedStartDateMillis,
                onStartDateChange = { selectedStartDateMillis = it },
                selectedSymbolOption = selectedSymbolOption,
                onSymbolOptionChange = { selectedSymbolOption = it },
                customSymbolText = customSymbolText,
                onCustomSymbolTextChange = { customSymbolText = it },
                onFetchAnalysis = triggerFetch
            )
        }

        // Sync Notice / Result Banner (Veritabanı Senkronizasyon Durumu)
        if (state.syncResult != null || state.syncNotice != null) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MinimalSuccess.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, MinimalSuccess.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MinimalSuccess.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Storage,
                                contentDescription = "Veritabanı",
                                tint = MinimalSuccessDark,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Veritabanı Senkronize Edildi",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MinimalSuccessDark
                                )
                                if (state.syncResult != null && state.syncResult.newlyAddedCount > 0) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = MinimalSuccess
                                    ) {
                                        Text(
                                            text = "+${state.syncResult.newlyAddedCount} Yeni",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = state.syncNotice ?: "Borsadaki tüm işlemler yerel veritabanıyla karşılaştırıldı ve güncellendi.",
                                fontSize = 12.sp,
                                color = MinimalTextPrimary,
                                modifier = Modifier.padding(top = 2.dp),
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }

        // Error message if any
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
                                text = "Borsadan Veri Çekme Hatası",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MinimalError
                            )
                            Text(
                                text = state.errorMessage,
                                fontSize = 12.sp,
                                color = MinimalTextPrimary,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        // Loading indicator
        if (state.isLoading) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MinimalSurface),
                    border = BorderStroke(1.dp, MinimalSurfaceBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = MinimalPrimary,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = if (!state.progressText.isNullOrBlank()) state.progressText else "Bybit'ten işlem geçmişi çekiliyor...",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MinimalTextPrimary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Text(
                            text = "Tüm alım ve satım verileri toplanıp ağırlıklı ortalama fiyatlar hesaplanıyor.",
                            fontSize = 12.sp,
                            color = MinimalTextSecondary,
                            modifier = Modifier.padding(top = 4.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else if (analysis != null) {
            // Summary Cards: Buy vs Sell Calculations
            item {
                TradeAveragesOverview(analysis = analysis)
            }

            // Profitability & Spread Analysis Card
            item {
                TradeProfitabilityCard(
                    analysis = analysis,
                    currentPrice = currentPrice
                )
            }

            // Filter Chips Header
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "İşlem Kayıtları (${filteredExecutions.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MinimalTextPrimary
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "ALL" to "Tümü (${allExecutions.size})",
                            "BUY" to "Alışlar (${analysis.buyTradeCount})",
                            "SELL" to "Satışlar (${analysis.sellTradeCount})"
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

            // Executions list
            if (filteredExecutions.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MinimalSurface,
                        border = BorderStroke(1.dp, MinimalSurfaceBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Bu filtreye uygun işlem kaydı bulunamadı.",
                                fontSize = 13.sp,
                                color = MinimalTextSecondary
                            )
                        }
                    }
                }
            } else {
                items(filteredExecutions, key = { it.execId.ifBlank { "${it.orderId}_${it.execTime}" } }) { exec ->
                    ExecutionItemCard(exec = exec)
                }
            }
        } else {
            // Empty State (Not yet fetched)
            item {
                TradeAnalysisEmptyState(
                    isApiConfigured = isApiConfigured,
                    selectedDaysBack = selectedDaysBack,
                    selectedStartDateMillis = selectedStartDateMillis,
                    onFetchAnalysis = triggerFetch
                )
            }
        }
    }
}

@Composable
private fun TradeAnalysisHeaderCard(
    isLoading: Boolean,
    isApiConfigured: Boolean,
    lastFetchedAt: Long,
    selectedDaysBack: Int,
    onDaysBackChange: (Int) -> Unit,
    selectedStartDateMillis: Long? = null,
    onStartDateChange: (Long?) -> Unit = {},
    selectedSymbolOption: String,
    onSymbolOptionChange: (String) -> Unit,
    customSymbolText: String,
    onCustomSymbolTextChange: (String) -> Unit,
    onFetchAnalysis: () -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MinimalSurface),
        border = BorderStroke(1.dp, MinimalSurfaceBorder)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                            .clip(RoundedCornerShape(12.dp))
                            .background(MinimalPrimaryLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.QueryStats,
                            contentDescription = null,
                            tint = MinimalPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Borsa İşlem Analizi",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MinimalTextPrimary,
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = "Bybit Spot • Derin Geçmiş Taraması",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MinimalTextSecondary,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MinimalSecondaryLight
                ) {
                    val pillLabel = if (selectedStartDateMillis != null) {
                        val dateFormatted = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(selectedStartDateMillis))
                        "$dateFormatted İtibaren"
                    } else {
                        "$selectedDaysBack Gün"
                    }
                    Text(
                        text = pillLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MinimalSecondary,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Borsadaki gerçekleşen alım ve satım işlemlerinizi çekerek ağırlıklı ortalama alış/satış fiyatlarını ve net kâr/zarar durumunuzu hesaplar.",
                fontSize = 13.sp,
                color = MinimalTextSecondary,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Range Selection Title
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.DateRange,
                    contentDescription = null,
                    tint = MinimalPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Taranacak Zaman Aralığı:",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = MinimalTextPrimary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Time Range Options Chips
            val rangeOptions = listOf(
                730 to "2 Yıl (Bybit Maksimum)",
                365 to "1 Yıl",
                180 to "6 Ay",
                90 to "3 Ay",
                30 to "1 Ay",
                7 to "7 Gün"
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                rangeOptions.forEach { (days, label) ->
                    val isSelected = selectedStartDateMillis == null && selectedDaysBack == days
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
                                onStartDateChange(null)
                                onDaysBackChange(days)
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

                // Custom Date Selection Chip
                val isCustomDate = selectedStartDateMillis != null
                val customChipLabel = if (isCustomDate) {
                    val dateFormatted = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(selectedStartDateMillis!!))
                    "📅 $dateFormatted'den İtibaren"
                } else {
                    "📅 Tarih Seç..."
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isCustomDate) MinimalPrimary else MinimalSurfaceElevated,
                    border = BorderStroke(
                        1.dp,
                        if (isCustomDate) MinimalPrimary else MinimalSurfaceBorder
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            val calendar = Calendar.getInstance()
                            if (selectedStartDateMillis != null) {
                                calendar.timeInMillis = selectedStartDateMillis
                            } else {
                                calendar.add(Calendar.DAY_OF_YEAR, -selectedDaysBack)
                            }
                            val datePickerDialog = DatePickerDialog(
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
                                    val pickedMs = pickedCal.timeInMillis
                                    onStartDateChange(pickedMs)
                                    val nowMs = System.currentTimeMillis()
                                    val calculatedDays = ((nowMs - pickedMs) / (24L * 60L * 60L * 1000L)).toInt().coerceAtLeast(1)
                                    onDaysBackChange(calculatedDays)
                                },
                                calendar.get(Calendar.YEAR),
                                calendar.get(Calendar.MONTH),
                                calendar.get(Calendar.DAY_OF_MONTH)
                            )
                            datePickerDialog.datePicker.maxDate = System.currentTimeMillis()
                            val minMs = System.currentTimeMillis() - (720L * 24L * 60L * 60L * 1000L)
                            datePickerDialog.datePicker.minDate = minMs
                            datePickerDialog.show()
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = customChipLabel,
                            fontSize = 11.sp,
                            fontWeight = if (isCustomDate) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (isCustomDate) Color.White else MinimalPrimary
                        )
                    }
                }
            }

            // Notice banner when custom start date is active
            if (selectedStartDateMillis != null) {
                val dateFormatted = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(selectedStartDateMillis))
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MinimalPrimaryLight.copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, MinimalPrimary.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = MinimalPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Başlangıç: $dateFormatted ($selectedDaysBack gün öncesi)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MinimalTextPrimary
                                )
                                Text(
                                    text = "Bu tarihten bugüne kadar gerçekleşen işlemler çekilecektir.",
                                    fontSize = 11.sp,
                                    color = MinimalTextSecondary
                                )
                            }
                        }
                        TextButton(
                            onClick = { onStartDateChange(null) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Kaldır",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MinimalErrorDark
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Symbol Selection Title
            Text(
                text = "Parite / Sembol:",
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = MinimalTextPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            val symbolOptions = listOf(
                "MNTUSDT" to "MNT/USDT (Bot)",
                "ALL" to "Tüm Spot (Sembolsüz)",
                "CUSTOM" to "Diğer Sembol"
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                symbolOptions.forEach { (sym, label) ->
                    val isSelected = selectedSymbolOption == sym
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) MinimalSecondary else MinimalSurfaceElevated,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) MinimalSecondary else MinimalSurfaceBorder
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSymbolOptionChange(sym) }
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

            if (selectedSymbolOption == "CUSTOM") {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = customSymbolText,
                    onValueChange = onCustomSymbolTextChange,
                    placeholder = { Text("Örn: BTCUSDT, ETHUSDT", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MinimalPrimary,
                        unfocusedBorderColor = MinimalSurfaceBorder
                    ),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Explanatory info box
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MinimalPrimaryLight.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MinimalPrimary.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MinimalPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Bybit V5 borsa API'si en fazla 2 yıllık (730 gün) geçmişi saklar ve sorgulamaya izin verir. 2 yıldan daha eski veriler borsa tarafından API'den arşive kaldırıldığından yalnızca Bybit web sitesindeki Emir Geçmişi > Dışa Aktar (CSV) bölümünden indirilebilir. Uygulama, seçtiğiniz süreyi 7'şer günlük pencerelerle geriye doğru tarayarak tüm işlemlerinizi eksiksiz birleştirir.",
                        fontSize = 11.sp,
                        color = MinimalTextPrimary,
                        lineHeight = 15.sp
                    )
                }
            }

            if (lastFetchedAt > 0) {
                val timeStr = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date(lastFetchedAt))
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 2.dp)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        tint = MinimalTextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Son Güncelleme: $timeStr",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MinimalTextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            val dateFormatted = if (selectedStartDateMillis != null) {
                SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(selectedStartDateMillis))
            } else null

            val buttonLabel = if (dateFormatted != null) {
                if (lastFetchedAt > 0) {
                    "Verileri Yeniden Çek ($dateFormatted Tarihinden İtibaren)"
                } else {
                    "Verileri Çek ($dateFormatted Tarihinden İtibaren)"
                }
            } else {
                if (lastFetchedAt > 0) {
                    "Verileri Yeniden Çek ve Senkronize Et ($selectedDaysBack Gün)"
                } else {
                    "Verileri Çek ve Veritabanına Kaydet ($selectedDaysBack Gün)"
                }
            }

            val loadingLabel = if (dateFormatted != null) {
                "Borsadan $dateFormatted Tarihinden İtibaren Taranıyor..."
            } else {
                "Borsadan $selectedDaysBack Günlük Veri Taranıyor..."
            }

            Button(
                onClick = onFetchAnalysis,
                enabled = !isLoading && isApiConfigured,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("fetch_trade_history_button"),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MinimalPrimary,
                    contentColor = Color.White
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = loadingLabel,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = buttonLabel,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            lineHeight = 17.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TradeAveragesOverview(
    analysis: TradeAnalysisResult,
    showHeaderBanner: Boolean = true
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showHeaderBanner) {
            // Range & Count Info Banner
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MinimalSurfaceElevated,
                border = BorderStroke(1.dp, MinimalSurfaceBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.QueryStats,
                            contentDescription = null,
                            tint = MinimalPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        val rangeTitle = if (analysis.dateRangeLabel.isNotBlank()) {
                            analysis.dateRangeLabel
                        } else if (analysis.daysRange > 0) {
                            "Son ${analysis.daysRange} Gün"
                        } else {
                            "Kayıtlı Tüm Geçmiş"
                        }
                        Text(
                            text = "${analysis.symbol} • $rangeTitle",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MinimalTextPrimary,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    Text(
                        text = "Toplam ${analysis.executions.size} İşlem",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MinimalPrimary,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        // Toplam Alışlar Kartı (BUY)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("buy_analysis_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MinimalSurface),
            border = BorderStroke(1.dp, MinimalSuccess.copy(alpha = 0.35f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(MinimalSuccessLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowDownward,
                                contentDescription = null,
                                tint = MinimalSuccessDark,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "TÜM ALIŞ İŞLEMLERİ",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MinimalSuccessDark,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MinimalSuccessLight
                    ) {
                        Text(
                            text = "${analysis.buyTradeCount} İşlem",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MinimalSuccessDark,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Ortalama Alış Fiyatı",
                            fontSize = 11.sp,
                            color = MinimalTextSecondary
                        )
                        Text(
                            text = if (analysis.avgBuyPrice > 0) String.format(Locale.US, "$%.4f", analysis.avgBuyPrice) else "—",
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                            color = MinimalSuccessDark,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Toplam Alınan Miktar",
                            fontSize = 11.sp,
                            color = MinimalTextSecondary
                        )
                        Text(
                            text = String.format(Locale.US, "%.2f MNT", analysis.totalBuyQty),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MinimalTextPrimary
                        )
                        Text(
                            text = String.format(Locale.US, "Tutar: $%.2f USDT", analysis.totalBuyValue),
                            fontSize = 11.sp,
                            color = MinimalTextMuted
                        )
                    }
                }
            }
        }

        // Toplam Satışlar Kartı (SELL)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("sell_analysis_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MinimalSurface),
            border = BorderStroke(1.dp, MinimalError.copy(alpha = 0.35f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(MinimalErrorLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowUpward,
                                contentDescription = null,
                                tint = MinimalErrorDark,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "TÜM SATIŞ İŞLEMLERİ",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MinimalErrorDark,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MinimalErrorLight
                    ) {
                        Text(
                            text = "${analysis.sellTradeCount} İşlem",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MinimalErrorDark,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Ortalama Satış Fiyatı",
                            fontSize = 11.sp,
                            color = MinimalTextSecondary
                        )
                        Text(
                            text = if (analysis.avgSellPrice > 0) String.format(Locale.US, "$%.4f", analysis.avgSellPrice) else "—",
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                            color = MinimalErrorDark,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Toplam Satılan Miktar",
                            fontSize = 11.sp,
                            color = MinimalTextSecondary
                        )
                        Text(
                            text = String.format(Locale.US, "%.2f MNT", analysis.totalSellQty),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MinimalTextPrimary
                        )
                        Text(
                            text = String.format(Locale.US, "Tutar: $%.2f USDT", analysis.totalSellValue),
                            fontSize = 11.sp,
                            color = MinimalTextMuted
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TradeProfitabilityCard(
    analysis: TradeAnalysisResult,
    currentPrice: Double = 0.0
) {
    val isGrossProfitable = analysis.grossProfitUsdt > 0.0
    val isNetProfitable = analysis.netProfitUsdt > 0.0
    var showExplanation by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("profitability_analysis_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MinimalSurface),
        border = BorderStroke(
            1.5.dp,
            if (isNetProfitable) MinimalSuccess.copy(alpha = 0.5f) else MinimalError.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Title and ROI Badges (Cleanly weighted so badge never wraps)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(if (isNetProfitable) MinimalSuccessLight else MinimalErrorLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isNetProfitable) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                            contentDescription = null,
                            tint = if (isNetProfitable) MinimalSuccessDark else MinimalErrorDark,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "KARLILIK & KAZANÇ",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MinimalTextPrimary,
                            letterSpacing = 0.5.sp,
                            maxLines = 1
                        )
                        Text(
                            text = "Ortalama Fiyat ve Eşleşen Hacim",
                            fontSize = 11.sp,
                            color = MinimalTextMuted,
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Single-line Badges
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (isNetProfitable) MinimalSuccessLight else MinimalErrorLight
                    ) {
                        Text(
                            text = String.format(Locale.US, "Net %+.2f%%", analysis.netProfitPercentage),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isNetProfitable) MinimalSuccessDark else MinimalErrorDark,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Main Hero Net Profit Box
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (isNetProfitable) MinimalSuccessLight.copy(alpha = 0.45f) else MinimalErrorLight.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, if (isNetProfitable) MinimalSuccess.copy(alpha = 0.35f) else MinimalError.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "GERÇEKLEŞEN NET KÂR (KOMİSYON DÜŞÜLMÜŞ)",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isNetProfitable) MinimalSuccessDark else MinimalErrorDark,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = String.format(Locale.US, "%+,.2f USDT", analysis.netProfitUsdt),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        color = if (isNetProfitable) MinimalSuccessDark else MinimalErrorDark
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = String.format(
                            Locale.US,
                            "Eşleşen %,.2f %s ticaret hacmi üzerinden hesaplanmıştır.",
                            analysis.matchedQty,
                            analysis.baseAsset
                        ),
                        fontSize = 11.sp,
                        color = MinimalTextSecondary
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Separate pill for Brüt Kâr & Komisyon so texts never collide
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MinimalSurface,
                        border = BorderStroke(0.5.dp, MinimalSurfaceBorderLight),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Brüt Kâr: ",
                                    fontSize = 11.sp,
                                    color = MinimalTextSecondary
                                )
                                Text(
                                    text = String.format(Locale.US, "%+,.2f USDT", analysis.grossProfitUsdt),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isGrossProfitable) MinimalSuccessDark else MinimalTextPrimary,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Komisyon: ",
                                    fontSize = 11.sp,
                                    color = MinimalTextMuted
                                )
                                Text(
                                    text = String.format(Locale.US, "-%.4f USDT", analysis.totalFee),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MinimalTextSecondary,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Detailed Metric Breakdown Grid
            // Row 1: Eşleşen Hacim & Ortalama Fiyat Farkı
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Eşleşen Ticaret Hacmi",
                        fontSize = 11.sp,
                        color = MinimalTextSecondary
                    )
                    Text(
                        text = String.format(Locale.US, "%,.2f %s", analysis.matchedQty, analysis.baseAsset),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MinimalTextPrimary
                    )
                    Text(
                        text = "Alınıp satılan ortak miktar",
                        fontSize = 10.sp,
                        color = MinimalTextMuted
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "Ortalama Fiyat Farkı",
                        fontSize = 11.sp,
                        color = MinimalTextSecondary
                    )
                    Text(
                        text = String.format(Locale.US, "%+.4f USDT", analysis.priceDifference),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (analysis.priceDifference > 0) MinimalSuccessDark else if (analysis.priceDifference < 0) MinimalErrorDark else MinimalTextPrimary,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = String.format(Locale.US, "Satış $%.4f - Alış $%.4f", analysis.avgSellPrice, analysis.avgBuyPrice),
                        fontSize = 10.sp,
                        color = MinimalTextMuted
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = MinimalSurfaceBorderLight
            )

            // Row 2: Eşleşen Alış Tutarı vs Eşleşen Satış Geliri
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Eşleşen Alış Tutarı",
                        fontSize = 11.sp,
                        color = MinimalTextSecondary
                    )
                    Text(
                        text = String.format(Locale.US, "$%,.2f USDT", analysis.matchedBuyCost),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MinimalTextPrimary
                    )
                    Text(
                        text = String.format(Locale.US, "%,.2f × $%.4f", analysis.matchedQty, analysis.avgBuyPrice),
                        fontSize = 10.sp,
                        color = MinimalTextMuted
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "Eşleşen Satış Hasılatı",
                        fontSize = 11.sp,
                        color = MinimalTextSecondary
                    )
                    Text(
                        text = String.format(Locale.US, "$%,.2f USDT", analysis.matchedSellRevenue),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MinimalTextPrimary
                    )
                    Text(
                        text = String.format(Locale.US, "%,.2f × $%.4f", analysis.matchedQty, analysis.avgSellPrice),
                        fontSize = 10.sp,
                        color = MinimalTextMuted
                    )
                }
            }

            // Kalan Envanter / Portföy Dengesi
            if (analysis.netQty != 0.0) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 10.dp),
                    color = MinimalSurfaceBorderLight
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MinimalSurfaceElevated,
                    border = BorderStroke(1.dp, MinimalSurfaceBorderLight),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Kalan Varlık ve Ortalama Maliyet
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (analysis.netQty > 0) "Kalan Satılmamış Varlık" else "Önceki Dönemden Satılan",
                                    fontSize = 11.sp,
                                    color = MinimalTextSecondary
                                )
                                Text(
                                    text = String.format(Locale.US, "%+,.2f %s", analysis.netQty, analysis.baseAsset),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MinimalTextPrimary
                                )
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.End
                            ) {
                                Text(
                                    text = "Ortalama Alış Değeri",
                                    fontSize = 11.sp,
                                    color = MinimalTextSecondary
                                )
                                Text(
                                    text = String.format(Locale.US, "$%,.2f USDT", analysis.remainingInventoryCost),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MinimalTextPrimary
                                )
                            }
                        }

                        // Canlı Fiyat Değerlemesi (eğer currentPrice mevcutsa ve pozitif miktar varsa)
                        if (currentPrice > 0.0 && analysis.netQty > 0.0) {
                            val liveInventoryValue = analysis.netQty * currentPrice
                            val unrealizedPnl = analysis.netQty * (currentPrice - analysis.avgBuyPrice)
                            val unrealizedPcnt = if (analysis.avgBuyPrice > 0) ((currentPrice - analysis.avgBuyPrice) / analysis.avgBuyPrice) * 100.0 else 0.0
                            val totalCombinedProfit = analysis.netProfitUsdt + unrealizedPnl

                            HorizontalDivider(color = MinimalSurfaceBorder)

                            // Canlı Değer ve Açık Kâr/Zarar
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Canlı Piyasa Değeri",
                                        fontSize = 10.sp,
                                        color = MinimalTextMuted
                                    )
                                    Text(
                                        text = String.format(Locale.US, "$%,.2f USDT", liveInventoryValue),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MinimalTextPrimary
                                    )
                                    Text(
                                        text = String.format(Locale.US, "Anlık: $%.4f", currentPrice),
                                        fontSize = 9.sp,
                                        color = MinimalTextMuted
                                    )
                                }

                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.End
                                ) {
                                    Text(
                                        text = "Açık Kâr / Zarar",
                                        fontSize = 10.sp,
                                        color = MinimalTextMuted
                                    )
                                    Text(
                                        text = String.format(Locale.US, "%+,.2f USDT (%+.1f%%)", unrealizedPnl, unrealizedPcnt),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (unrealizedPnl >= 0) MinimalSuccessDark else MinimalErrorDark,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            // Toplam Portföy Getirisi (Net Gerçekleşen + Açık K/Z)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = (if (totalCombinedProfit >= 0) MinimalSuccessLight else MinimalErrorLight).copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Genel Portföy Getirisi",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MinimalTextPrimary
                                    )
                                    Text(
                                        text = String.format(Locale.US, "%+,.2f USDT", totalCombinedProfit),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (totalCombinedProfit >= 0) MinimalSuccessDark else MinimalErrorDark,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Explanation & Formula Toggle
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MinimalPrimaryLight.copy(alpha = 0.5f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showExplanation = !showExplanation }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MinimalPrimaryDark,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (showExplanation) "Hesaplama Detaylarını Gizle" else "Karlılık Nasıl Hesaplandı? (Formülü Gör)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MinimalPrimaryDark
                        )
                    }
                    Text(
                        text = if (showExplanation) "▲" else "▼",
                        fontSize = 10.sp,
                        color = MinimalPrimaryDark
                    )
                }
            }

            AnimatedVisibility(visible = showExplanation) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .background(MinimalSurfaceElevated, RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Karlılık Hesaplama Formülü:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MinimalTextPrimary
                    )
                    Text(
                        text = "1. Eşleşen Miktar = min(Toplam Alış, Toplam Satış) = ${String.format(Locale.US, "%,.2f %s", analysis.matchedQty, analysis.baseAsset)}",
                        fontSize = 10.sp,
                        color = MinimalTextSecondary
                    )
                    Text(
                        text = "2. Brüt Kâr = Eşleşen Miktar × (Ort. Satış - Ort. Alış)\n   = ${String.format(Locale.US, "%,.2f × ($%.4f - $%.4f) = %+,.2f USDT", analysis.matchedQty, analysis.avgSellPrice, analysis.avgBuyPrice, analysis.grossProfitUsdt)}",
                        fontSize = 10.sp,
                        color = MinimalTextSecondary
                    )
                    Text(
                        text = "3. Net Kâr = Brüt Kâr - Bybit Komisyonu\n   = ${String.format(Locale.US, "%+,.2f - %.4f = %+,.2f USDT", analysis.grossProfitUsdt, analysis.totalFee, analysis.netProfitUsdt)}",
                        fontSize = 10.sp,
                        color = MinimalTextSecondary
                    )
                    if (analysis.netQty > 0) {
                        Text(
                            text = "4. Kalan Portföy = Satılmamış ${String.format(Locale.US, "%,.2f %s", analysis.netQty, analysis.baseAsset)}, ortalama $${String.format(Locale.US, "%.4f", analysis.avgBuyPrice)} maliyetle ($${String.format(Locale.US, "%,.2f", analysis.remainingInventoryCost)} USDT) cüzdanınızdadır.",
                            fontSize = 10.sp,
                            color = MinimalTextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExecutionItemCard(exec: BybitExecutionDto) {
    val isBuy = exec.isBuy
    val sideColor = if (isBuy) MinimalSuccessDark else MinimalErrorDark
    val sideBg = if (isBuy) MinimalSuccessLight else MinimalErrorLight

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("execution_item_${exec.execId}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MinimalSurface),
        border = BorderStroke(1.dp, MinimalSurfaceBorderLight)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = sideBg
                    ) {
                        Text(
                            text = if (isBuy) "ALIŞ" else "SATIŞ",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = sideColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = exec.symbol,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MinimalTextPrimary
                    )

                    if (exec.orderType.isNotBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "• ${exec.orderType}",
                            fontSize = 11.sp,
                            color = MinimalTextMuted
                        )
                    }
                }

                // Date/Time
                val dateStr = if (exec.timeMillis > 0) {
                    SimpleDateFormat("dd.MM.yy HH:mm", Locale.getDefault()).format(Date(exec.timeMillis))
                } else {
                    ""
                }
                Text(
                    text = dateStr,
                    fontSize = 11.sp,
                    color = MinimalTextMuted
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "İşlem Fiyatı",
                        fontSize = 10.sp,
                        color = MinimalTextSecondary
                    )
                    Text(
                        text = String.format(Locale.US, "$%.4f", exec.priceValue),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MinimalTextPrimary,
                        fontFamily = FontFamily.Monospace
                    )
                }

                val baseAsset = if (exec.symbol.endsWith("USDT", ignoreCase = true)) {
                    exec.symbol.substring(0, exec.symbol.length - 4).uppercase(Locale.getDefault())
                } else if (exec.symbol.isNotBlank()) {
                    exec.symbol
                } else {
                    "MNT"
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "İşlem Miktarı",
                        fontSize = 10.sp,
                        color = MinimalTextSecondary
                    )
                    Text(
                        text = String.format(Locale.US, "%.2f %s", exec.qtyValue, baseAsset),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = MinimalTextPrimary
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Toplam Değer",
                        fontSize = 10.sp,
                        color = MinimalTextSecondary
                    )
                    Text(
                        text = String.format(Locale.US, "$%.2f", exec.totalValue),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = sideColor,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
private fun TradeAnalysisEmptyState(
    isApiConfigured: Boolean,
    selectedDaysBack: Int,
    selectedStartDateMillis: Long? = null,
    onFetchAnalysis: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MinimalSurface),
        border = BorderStroke(1.dp, MinimalSurfaceBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MinimalSurfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.QueryStats,
                    contentDescription = null,
                    tint = MinimalPrimary,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Borsa İşlem Geçmişi Henüz Çekilmedi",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MinimalTextPrimary
            )

            val descText = if (isApiConfigured) {
                if (selectedStartDateMillis != null) {
                    val dateFormatted = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(selectedStartDateMillis))
                    "Bybit hesabınızdaki $dateFormatted tarihinden itibaren gerçekleşen tüm alım ve satım işlemlerini çekerek ortalama alış fiyatını, ortalama satış fiyatını ve net kâr/zarar durumunu görmek için butona tıklayın."
                } else {
                    "Bybit hesabınızdaki $selectedDaysBack günlük tüm alım ve satım geçmişini getirerek ortalama alış fiyatını, ortalama satış fiyatını ve net miktarları görmek için butona tıklayın."
                }
            } else {
                "Geçmiş verilerini çekebilmek için lütfen önce Ayarlar menüsünden Bybit API anahtarlarınızı girin."
            }

            Text(
                text = descText,
                fontSize = 13.sp,
                color = MinimalTextSecondary,
                modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 18.sp
            )

            if (isApiConfigured) {
                val buttonText = if (selectedStartDateMillis != null) {
                    val dateFormatted = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(selectedStartDateMillis))
                    "İşlem Geçmişini Şimdi Çek ($dateFormatted İtibaren)"
                } else {
                    "İşlem Geçmişini Şimdi Çek ($selectedDaysBack Gün)"
                }
                Button(
                    onClick = onFetchAnalysis,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MinimalPrimary)
                ) {
                    Icon(
                        Icons.Default.CloudDownload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(buttonText, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
