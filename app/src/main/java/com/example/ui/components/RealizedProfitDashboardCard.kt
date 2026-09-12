package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.model.TradeAnalysisResult
import com.example.ui.MainUiState
import com.example.ui.theme.*
import java.util.Locale
import com.example.bot.RebalanceEngine

@Composable
fun RealizedProfitDashboardCard(
    analysis: TradeAnalysisResult?,
    uiState: MainUiState,
    onIntervalChanged: (String) -> Unit
) {
    LaunchedEffect(Unit) {
        if (uiState.chartKlines.isEmpty()) {
            onIntervalChanged("15") // Fetch initial 15m interval
        }
    }

    if (analysis == null || analysis.totalBuyQty == 0.0) return

    val isProfitable = analysis.netProfitUsdt >= 0.0

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MinimalSurface),
        border = BorderStroke(1.dp, if (isProfitable) MinimalSuccess.copy(alpha = 0.4f) else MinimalSurfaceBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(if (isProfitable) MinimalSuccessLight else MinimalSurfaceElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Savings,
                            contentDescription = null,
                            tint = if (isProfitable) MinimalSuccessDark else MinimalTextSecondary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Gerçekleşen Kâr (PnL)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MinimalTextPrimary
                    )
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (isProfitable) MinimalSuccessLight else MinimalSurfaceElevated
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoGraph,
                            contentDescription = null,
                            tint = if (isProfitable) MinimalSuccessDark else MinimalTextMuted,
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${analysis.buyTradeCount + analysis.sellTradeCount} İşlem",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isProfitable) MinimalSuccessDark else MinimalTextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = if (analysis.netProfitUsdt > 0) "+" else "",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    color = if (isProfitable) MinimalSuccessDark else MinimalTextPrimary,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = String.format(Locale.US, "%.2f", analysis.netProfitUsdt),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = if (isProfitable) MinimalSuccessDark else MinimalTextPrimary,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.alignByBaseline()
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "USDT",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MinimalTextMuted,
                    modifier = Modifier.alignByBaseline()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MinimalSurfaceBorderLight)
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Döndürülen Hacim",
                        fontSize = 10.sp,
                        color = MinimalTextSecondary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${RebalanceEngine.formatCryptoQty(analysis.matchedQty)} ${analysis.baseAsset}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MinimalTextPrimary
                    )
                }
                
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Net Birikim (${analysis.baseAsset})",
                        fontSize = 10.sp,
                        color = MinimalTextSecondary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val prefix = if (analysis.netQty > 0) "+" else ""
                    val netQtyColor = if (analysis.netQty > 0) MinimalSuccessDark else MinimalTextPrimary
                    Text(
                        text = "$prefix${RebalanceEngine.formatCryptoQty(analysis.netQty)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = netQtyColor
                    )
                }
            }

            if (analysis.executions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Gelişmiş İşlem Haritası",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MinimalTextPrimary,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                AdvancedCandlestickChart(
                    klines = uiState.chartKlines,
                    executions = analysis.executions,
                    selectedInterval = uiState.chartInterval,
                    onIntervalChanged = onIntervalChanged
                )
            }
        }
    }
}
