package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bot.PortfolioAnalysis
import com.example.bot.RebalanceAction
import com.example.bot.RebalanceEngine
import com.example.ui.theme.MinimalError
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

@Composable
fun PortfolioCard(
    analysis: PortfolioAnalysis?,
    currentPrice: Double,
    price24hChange: Double,
    isBotActive: Boolean,
    onManualRebalanceClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("portfolio_card"),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MinimalSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MinimalSurfaceBorderLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(22.dp)
        ) {
            // Header: Total Equity & Bybit Unified Badge
            val totalEquity = analysis?.totalEquityUsdt ?: 0.0
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = "TOTAL EQUITY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MinimalTextMuted,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        text = "$${RebalanceEngine.format2(totalEquity)}",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Black,
                        color = MinimalTextPrimary,
                        letterSpacing = (-0.5).sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MinimalPrimaryLight
                ) {
                    Text(
                        text = "Bybit Unified",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MinimalPrimaryDark,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Live Price & 24h Change Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MinimalSurfaceElevated)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "MNT / USDT",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MinimalTextPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "$${RebalanceEngine.format4(currentPrice)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MinimalPrimary
                    )
                }

                val isPositive = price24hChange >= 0
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (isPositive) MinimalSuccessLight else MinimalErrorLight
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = if (isPositive) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                            contentDescription = null,
                            tint = if (isPositive) MinimalSuccessDark else MinimalError,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "${if (isPositive) "+" else ""}${RebalanceEngine.format2(price24hChange)}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isPositive) MinimalSuccessDark else MinimalError
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Balances & Minimalist Progress Bars
            val usdtBalance = analysis?.usdtBalance ?: 0.0
            val mntBalance = analysis?.mntBalance ?: 0.0
            val mntValueUsdt = analysis?.mntValueUsdt ?: 0.0
            val usdtPct = analysis?.usdtPercent?.toFloat() ?: 50f
            val mntPct = analysis?.mntPercent?.toFloat() ?: 50f

            val animatedUsdtPct by animateFloatAsState(targetValue = usdtPct.coerceIn(0f, 100f), label = "usdt_bar")
            val animatedMntPct by animateFloatAsState(targetValue = mntPct.coerceIn(0f, 100f), label = "mnt_bar")

            // USDT Progress Item
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "USDT Balance",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MinimalTextSecondary
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "$${RebalanceEngine.format2(usdtBalance)} ",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MinimalTextPrimary
                        )
                        Text(
                            text = "(${RebalanceEngine.format2(usdtPct.toDouble())}%)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            color = MinimalTextMuted
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(MinimalSurfaceElevated)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedUsdtPct / 100f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(999.dp))
                            .background(MinimalPrimary)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // MNT Progress Item
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "MNT Balance",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MinimalTextSecondary
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "$${RebalanceEngine.format2(mntValueUsdt)} ",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MinimalTextPrimary
                        )
                        Text(
                            text = "(${RebalanceEngine.format2(mntPct.toDouble())}%)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            color = MinimalTextMuted
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(MinimalSurfaceElevated)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedMntPct / 100f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(999.dp))
                            .background(MinimalSecondary)
                    )
                }
            }

            // Status / Action Banner
            if (analysis != null) {
                Spacer(modifier = Modifier.height(20.dp))
                val isBalanced = analysis.isBalanced5050
                val bannerBg = if (isBalanced) MinimalSuccessLight else MinimalPrimaryLight
                val bannerBorder = if (isBalanced) MinimalSuccess.copy(alpha = 0.3f) else MinimalPrimary.copy(alpha = 0.2f)
                val iconBg = if (isBalanced) MinimalSuccess.copy(alpha = 0.2f) else MinimalPrimary.copy(alpha = 0.15f)
                val iconColor = if (isBalanced) MinimalSuccessDark else MinimalPrimary

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(bannerBg)
                        .border(1.dp, bannerBorder, RoundedCornerShape(18.dp))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(iconBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isBalanced) Icons.Default.CheckCircle else Icons.Default.SwapHoriz,
                            contentDescription = null,
                            tint = iconColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isBalanced) "Portföy Dengeli (%50 / %50)" else "Dengeleme Gerekiyor",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isBalanced) MinimalSuccessDark else MinimalPrimaryDark
                        )
                        Text(
                            text = analysis.description,
                            fontSize = 11.sp,
                            color = MinimalTextSecondary,
                            modifier = Modifier.padding(top = 1.dp)
                        )
                    }
                    if (!isBalanced && analysis.requiredAction != RebalanceAction.BALANCED && !isBotActive) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = onManualRebalanceClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MinimalPrimary,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier.testTag("rebalance_now_button")
                        ) {
                            Text("Eşitle", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

