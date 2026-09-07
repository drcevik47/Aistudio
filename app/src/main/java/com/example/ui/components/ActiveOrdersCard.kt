package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bot.GridOrdersPlan
import com.example.bot.RebalanceEngine
import com.example.data.remote.model.BybitOrderDto
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

@Composable
fun ActiveOrdersCard(
    gridPlan: GridOrdersPlan?,
    activeOrders: List<BybitOrderDto>,
    currentPrice: Double,
    isBotActive: Boolean,
    stepPercent: Double,
    lastRebalancePrice: Double = 0.0,
    isLoading: Boolean,
    onStartBot: () -> Unit,
    onStopBot: () -> Unit,
    onCancelAllOrders: () -> Unit,
    onEditBasePriceClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("active_orders_card"),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MinimalSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MinimalSurfaceBorderLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(22.dp)
        ) {
            // Header & 24/7 Service Control
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
                            .clip(CircleShape)
                            .background(if (isBotActive) MinimalSuccessLight else MinimalPrimaryLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ElectricBolt,
                            contentDescription = null,
                            tint = if (isBotActive) MinimalSuccessDark else MinimalPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Grid Emirleri (±%$stepPercent)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MinimalTextPrimary,
                            letterSpacing = (-0.2).sp,
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = if (isBotActive) "Continuous Real-Time Loop" else "Bot Inactive • Standing By",
                            fontSize = 11.sp,
                            color = if (isBotActive) MinimalSuccessDark else MinimalTextSecondary,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                // Bot Start/Stop Button
                Button(
                    onClick = {
                        if (isBotActive) onStopBot() else onStartBot()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isBotActive) MinimalError else MinimalPrimary,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(999.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier
                        .defaultMinSize(minWidth = 1.dp, minHeight = 36.dp)
                        .testTag("bot_toggle_button")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (isBotActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (isBotActive) "Stop" else "Start Bot",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Grid Orders Visual Box (+2% Sell & -2% Buy)
            val openSellOrder = activeOrders.find { it.side.equals("Sell", ignoreCase = true) }
            val openBuyOrder = activeOrders.find { it.side.equals("Buy", ignoreCase = true) }
            val basePriceToShow = if (lastRebalancePrice > 0.0) lastRebalancePrice else currentPrice

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MinimalSurfaceElevated)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Limit Sell Order Card (+2%)
                val sellPrice: Double = openSellOrder?.price?.toDoubleOrNull()
                    ?: gridPlan?.sellLimitPrice
                    ?: (basePriceToShow * (1.0 + stepPercent / 100.0))
                val sellQty: Double = openSellOrder?.qty?.toDoubleOrNull()
                    ?: gridPlan?.sellMntQty
                    ?: 0.0
                val sellUsdt: Double = if (openSellOrder != null) (sellQty * sellPrice) else (gridPlan?.sellUsdtValue ?: (sellQty * sellPrice))
                val sellDiffPct: Double = if (currentPrice > 0.0) ((sellPrice - currentPrice) / currentPrice) * 100.0 else stepPercent
                val postSellUsdt: Double = gridPlan?.postSellUsdt ?: 0.0

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MinimalErrorLight)
                        .border(1.dp, MinimalError.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = null,
                            tint = MinimalError,
                            modifier = Modifier.size(18.dp)
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
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "LIMIT SELL",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = MinimalErrorDark,
                                    letterSpacing = 0.3.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MinimalError.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = "+%${RebalanceEngine.format2(sellDiffPct)}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MinimalErrorDark,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = if (openSellOrder != null) Color.White else MinimalSuccessLight,
                                border = androidx.compose.foundation.BorderStroke(1.dp, MinimalSurfaceBorderLight)
                            ) {
                                Text(
                                    text = if (openSellOrder != null) {
                                        "$${RebalanceEngine.format4(sellPrice)}"
                                    } else if (isBotActive) {
                                        "DOLDU / YENİLENİYOR"
                                    } else {
                                        "$${RebalanceEngine.format4(sellPrice)}"
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (openSellOrder != null) MinimalTextPrimary else MinimalSuccessDark,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Miktar: ${RebalanceEngine.format4(sellQty)} MNT (~$${RebalanceEngine.format2(sellUsdt)})",
                            fontSize = 11.sp,
                            color = MinimalTextSecondary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        if (postSellUsdt > 0.0) {
                            Text(
                                text = "Gerçekleşince USDT: ~$${RebalanceEngine.format2(postSellUsdt)} (%50 Denge)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = MinimalTextMuted,
                                modifier = Modifier.padding(top = 1.dp)
                            )
                        }
                    }
                }

                // Current Price and Last Base Price Midpoint Indicator
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(1.dp)
                                .background(MinimalSurfaceBorder)
                        )
                        Surface(
                            onClick = onEditBasePriceClick,
                            shape = RoundedCornerShape(12.dp),
                            color = MinimalPrimary.copy(alpha = 0.08f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MinimalPrimary.copy(alpha = 0.25f)),
                            modifier = Modifier
                                .padding(horizontal = 6.dp)
                                .testTag("edit_base_price_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = if (lastRebalancePrice > 0.0) {
                                        "BAZ (SON İŞLEM): $${RebalanceEngine.format4(lastRebalancePrice)}"
                                    } else {
                                        "BAZ: $${RebalanceEngine.format4(currentPrice)}"
                                    },
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MinimalPrimary,
                                    letterSpacing = 0.5.sp
                                )
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Baz Fiyatı Düzenle",
                                    tint = MinimalPrimary,
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(1.dp)
                                .background(MinimalSurfaceBorder)
                        )
                    }
                    Text(
                        text = "Anlık Borsa Fiyatı: $${RebalanceEngine.format4(currentPrice)} • Düzenlemek için dokunun",
                        fontSize = 10.sp,
                        color = MinimalTextMuted,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                // Limit Buy Order Card (-2%)
                val buyPrice: Double = openBuyOrder?.price?.toDoubleOrNull()
                    ?: gridPlan?.buyLimitPrice
                    ?: (basePriceToShow * (1.0 - stepPercent / 100.0))
                val buyQty: Double = openBuyOrder?.qty?.toDoubleOrNull()
                    ?: gridPlan?.buyMntQty
                    ?: 0.0
                val buyUsdt: Double = if (openBuyOrder != null) (buyQty * buyPrice) else (gridPlan?.buyUsdtValue ?: (buyQty * buyPrice))
                val buyDiffPct: Double = if (currentPrice > 0.0) ((currentPrice - buyPrice) / currentPrice) * 100.0 else stepPercent
                val postBuyUsdt: Double = gridPlan?.postBuyUsdt ?: 0.0

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MinimalSuccessLight)
                        .border(1.dp, MinimalSuccess.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = null,
                            tint = MinimalSuccessDark,
                            modifier = Modifier.size(18.dp)
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
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "LIMIT BUY",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = MinimalSuccessDark,
                                    letterSpacing = 0.3.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MinimalSuccess.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = "-%${RebalanceEngine.format2(buyDiffPct)}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MinimalSuccessDark,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = if (openBuyOrder != null) Color.White else MinimalSuccessLight,
                                border = androidx.compose.foundation.BorderStroke(1.dp, MinimalSurfaceBorderLight)
                            ) {
                                Text(
                                    text = if (openBuyOrder != null) {
                                        "$${RebalanceEngine.format4(buyPrice)}"
                                    } else if (isBotActive) {
                                        "DOLDU / YENİLENİYOR"
                                    } else {
                                        "$${RebalanceEngine.format4(buyPrice)}"
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (openBuyOrder != null) MinimalTextPrimary else MinimalSuccessDark,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Miktar: ${RebalanceEngine.format4(buyQty)} MNT (~$${RebalanceEngine.format2(buyUsdt)})",
                            fontSize = 11.sp,
                            color = MinimalTextSecondary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        if (postBuyUsdt > 0.0) {
                            Text(
                                text = "Gerçekleşince USDT: ~$${RebalanceEngine.format2(postBuyUsdt)} (%50 Denge)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = MinimalTextMuted,
                                modifier = Modifier.padding(top = 1.dp)
                            )
                        }
                    }
                }
            }

            // Open Orders count & Cancel All button
            if (activeOrders.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${activeOrders.size} aktif emir",
                        fontSize = 12.sp,
                        color = MinimalTextSecondary,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .padding(end = 8.dp)
                    )

                    OutlinedButton(
                        onClick = onCancelAllOrders,
                        modifier = Modifier
                            .defaultMinSize(minWidth = 1.dp, minHeight = 32.dp)
                            .testTag("cancel_all_orders_button"),
                        shape = RoundedCornerShape(999.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MinimalError),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MinimalError.copy(alpha = 0.4f))
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Cancel All",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

