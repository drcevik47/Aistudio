package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.model.TradeAnalysisResult
import com.example.ui.TradeAnalysisUiState
import com.example.ui.theme.*
import java.util.Locale
import com.example.bot.RebalanceEngine

@Composable
fun RealizedProfitDashboardCard(
    analysis: TradeAnalysisResult?
) {
    
    // Yalnızca analiz varsa (bot işlem yaptıysa/çektiyse) göster
    if (analysis == null || analysis.totalBuyQty == 0.0) return

    val isProfitable = analysis.netProfitUsdt >= 0.0

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MinimalSurface),
        border = BorderStroke(1.dp, if (isProfitable) MinimalSuccess.copy(alpha = 0.4f) else MinimalSurfaceBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(if (isProfitable) MinimalSuccessLight else MinimalSurfaceElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Savings,
                            contentDescription = null,
                            tint = if (isProfitable) MinimalSuccessDark else MinimalTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Gerçekleşen Kâr (PnL)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MinimalTextPrimary
                    )
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (isProfitable) MinimalSuccessLight else MinimalSurfaceElevated
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoGraph,
                            contentDescription = null,
                            tint = if (isProfitable) MinimalSuccessDark else MinimalTextMuted,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${analysis.buyTradeCount + analysis.sellTradeCount} İşlem",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isProfitable) MinimalSuccessDark else MinimalTextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Main PnL
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = if (analysis.netProfitUsdt > 0) "+" else "",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = if (isProfitable) MinimalSuccessDark else MinimalTextPrimary,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = String.format(Locale.US, "%.2f", analysis.netProfitUsdt),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    color = if (isProfitable) MinimalSuccessDark else MinimalTextPrimary,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.alignByBaseline()
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "USDT",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MinimalTextMuted,
                    modifier = Modifier.alignByBaseline()
                )
            }
            
            Text(
                text = "Grid al-sat döngüsünden biriken net kâr",
                fontSize = 12.sp,
                color = MinimalTextSecondary,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MinimalSurfaceBorderLight)
            Spacer(modifier = Modifier.height(16.dp))

            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Alınıp Satılan Hacim (Matched Volume)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Döndürülen Hacim",
                        fontSize = 11.sp,
                        color = MinimalTextSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${RebalanceEngine.formatCryptoQty(analysis.matchedQty)} ${analysis.baseAsset}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MinimalTextPrimary
                    )
                }
                
                // Net Birikim (Accumulated base asset)
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Net Birikim (${analysis.baseAsset})",
                        fontSize = 11.sp,
                        color = MinimalTextSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val prefix = if (analysis.netQty > 0) "+" else ""
                    val netQtyColor = if (analysis.netQty > 0) MinimalSuccessDark else MinimalTextPrimary
                    Text(
                        text = "$prefix${RebalanceEngine.formatCryptoQty(analysis.netQty)}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = netQtyColor
                    )
                }
            }

            if (analysis.executions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Son İşlemler (Alım: Yeşil, Satım: Kırmızı)",
                    fontSize = 10.sp,
                    color = MinimalTextMuted,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                PingPongChart(executions = analysis.executions)
            }
        }
    }
}
