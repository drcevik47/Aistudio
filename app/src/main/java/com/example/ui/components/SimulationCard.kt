package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bot.RebalanceEngine
import com.example.ui.theme.*

@Composable
fun SimulationCard(
    currentUsdtBalance: Double,
    currentMntBalance: Double,
    currentBasePrice: Double,
    stepPercent: Double,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    var simUsdtBalance by remember { mutableDoubleStateOf(currentUsdtBalance) }
    var simMntBalance by remember { mutableDoubleStateOf(currentMntBalance) }
    var simBasePrice by remember { mutableDoubleStateOf(currentBasePrice) }
    var simSteps by remember { mutableIntStateOf(0) }

    // Reset when base properties change drastically (like bot resetting)
    LaunchedEffect(currentUsdtBalance, currentMntBalance, currentBasePrice, stepPercent) {
        if (!isExpanded) {
            simUsdtBalance = currentUsdtBalance
            simMntBalance = currentMntBalance
            simBasePrice = currentBasePrice
            simSteps = 0
        }
    }

    val gridPlan = remember(simUsdtBalance, simMntBalance, simBasePrice, stepPercent) {
        RebalanceEngine.calculateGridOrders(
            usdtBalance = simUsdtBalance,
            mntBalance = simMntBalance,
            basePrice = simBasePrice,
            stepPercent = stepPercent
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Science, contentDescription = null, tint = MinimalPrimary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Grid Simülatörü",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MinimalTextPrimary
                    )
                }
                TextButton(
                    onClick = { isExpanded = !isExpanded },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(if (isExpanded) "Kapat" else "Aç", fontSize = 12.sp, color = MinimalPrimary)
                }
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MinimalSurfaceElevated)
                Spacer(modifier = Modifier.height(12.dp))

                if (currentBasePrice <= 0.0) {
                    Text("Simülasyon için botun çalışıyor olması veya bir baz fiyat ayarlanmış olması gereklidir.", fontSize = 12.sp, color = MinimalTextSecondary)
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Simülasyon Adımı", fontSize = 11.sp, color = MinimalTextSecondary)
                            Text(
                                if (simSteps == 0) "Mevcut Durum" else "$simSteps Adım",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MinimalTextPrimary
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Simüle Edilen Baz Fiyat", fontSize = 11.sp, color = MinimalTextSecondary)
                            Text(
                                "$${RebalanceEngine.format4(simBasePrice)}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MinimalPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val simTotalUsdt = simUsdtBalance + (simMntBalance * simBasePrice)
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MinimalSurfaceElevated,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("USDT Bakiye", fontSize = 10.sp, color = MinimalTextSecondary)
                                Text("$${RebalanceEngine.format2(simUsdtBalance)}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MinimalTextPrimary)
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MinimalSurfaceElevated,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("MNT Bakiye", fontSize = 10.sp, color = MinimalTextSecondary)
                                Text("${RebalanceEngine.format4(simMntBalance)} MNT", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MinimalTextPrimary)
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MinimalSurfaceElevated,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("Toplam Varlık", fontSize = 10.sp, color = MinimalTextSecondary)
                                Text("$${RebalanceEngine.format2(simTotalUsdt)}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MinimalPrimary)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (!gridPlan.isValid) {
                        Text("Limitler aşıldı: ${gridPlan.validationMessage}", fontSize = 12.sp, color = MinimalError)
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Simulate Sell Fill (Price Goes Up)
                            Button(
                                onClick = {
                                    simUsdtBalance += gridPlan.sellUsdtValue
                                    simMntBalance -= gridPlan.sellMntQty
                                    simBasePrice = gridPlan.sellLimitPrice
                                    simSteps++
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MinimalSuccess.copy(alpha = 0.1f)),
                                border = BorderStroke(1.dp, MinimalSuccess.copy(alpha = 0.5f)),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(vertical = 12.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = MinimalSuccess, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Fiyat Yükseldi", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MinimalSuccess)
                                    }
                                    Text("Satış Emri Gerçekleşti", fontSize = 10.sp, color = MinimalSuccess.copy(alpha = 0.8f))
                                    Text("$${RebalanceEngine.format4(gridPlan.sellLimitPrice)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MinimalSuccess)
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Simulate Buy Fill (Price Goes Down)
                            Button(
                                onClick = {
                                    simUsdtBalance -= gridPlan.buyUsdtValue
                                    simMntBalance += gridPlan.buyMntQty
                                    simBasePrice = gridPlan.buyLimitPrice
                                    simSteps++
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MinimalError.copy(alpha = 0.1f)),
                                border = BorderStroke(1.dp, MinimalError.copy(alpha = 0.5f)),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(vertical = 12.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = MinimalError, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Fiyat Düştü", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MinimalError)
                                    }
                                    Text("Alış Emri Gerçekleşti", fontSize = 10.sp, color = MinimalError.copy(alpha = 0.8f))
                                    Text("$${RebalanceEngine.format4(gridPlan.buyLimitPrice)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MinimalError)
                                }
                            }
                        }
                    }

                    if (simSteps > 0) {
                        Spacer(modifier = Modifier.height(12.dp))
                        TextButton(
                            onClick = {
                                simUsdtBalance = currentUsdtBalance
                                simMntBalance = currentMntBalance
                                simBasePrice = currentBasePrice
                                simSteps = 0
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Simülasyonu Sıfırla", color = MinimalTextSecondary)
                        }
                    }
                }
            }
        }
    }
}
