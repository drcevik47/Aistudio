package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.bot.PortfolioAnalysis
import com.example.bot.RebalanceAction
import com.example.bot.RebalanceEngine
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

@Composable
fun InitialRebalanceDialog(
    analysis: PortfolioAnalysis,
    isExecuting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val isBuy = analysis.requiredAction == RebalanceAction.BUY_MNT
    val actionText = if (isBuy) "MNT Buy" else "MNT Sell"
    val actionColor = if (isBuy) MinimalSuccessDark else MinimalError
    val actionBg = if (isBuy) MinimalSuccessLight else MinimalErrorLight

    Dialog(
        onDismissRequest = {
            if (!isExecuting) onDismiss()
        },
        properties = DialogProperties(
            dismissOnBackPress = !isExecuting,
            dismissOnClickOutside = !isExecuting,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .testTag("initial_rebalance_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = MinimalSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MinimalSurfaceBorderLight),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
            ) {
                // Title
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MinimalPrimaryLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Balance,
                            contentDescription = null,
                            tint = MinimalPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "50% / 50% Portfolio Rebalance",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MinimalTextPrimary,
                            letterSpacing = (-0.2).sp
                        )
                        Text(
                            text = "Unified Trading Account Initial Order",
                            fontSize = 11.sp,
                            color = MinimalTextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Current Imbalance Display
                Card(
                    colors = CardDefaults.cardColors(containerColor = MinimalSurfaceElevated),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MinimalSurfaceBorderLight)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Current Ratio Breakdown:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MinimalTextSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "USDT: ${RebalanceEngine.format2(analysis.usdtPercent)}% ($${RebalanceEngine.format2(analysis.usdtBalance)})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MinimalTextPrimary
                            )
                            Text(
                                text = "MNT: ${RebalanceEngine.format2(analysis.mntPercent)}% ($${RebalanceEngine.format2(analysis.mntValueUsdt)})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MinimalTextPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Planned Trade Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = actionBg.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, actionColor.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Required Execution:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MinimalTextSecondary
                            )
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = actionBg
                            ) {
                                Text(
                                    text = actionText.uppercase(),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = actionColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Trade Amount:", fontSize = 12.sp, color = MinimalTextSecondary)
                            Text(
                                text = "${RebalanceEngine.format4(analysis.deltaMnt)} MNT",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MinimalTextPrimary
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (isBuy) "USDT to Spend:" else "USDT to Gain:",
                                fontSize = 12.sp,
                                color = MinimalTextSecondary
                            )
                            Text(
                                text = "≈ ${RebalanceEngine.format2(analysis.deltaUsdt)} USDT",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MinimalTextPrimary
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Current MNT Price:", fontSize = 12.sp, color = MinimalTextSecondary)
                            Text(
                                text = "$${RebalanceEngine.format4(analysis.currentPrice)}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MinimalPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Post-rebalance preview
                val halfValue = analysis.totalEquityUsdt * 0.5
                Card(
                    colors = CardDefaults.cardColors(containerColor = MinimalSurfaceElevated),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MinimalSurfaceBorderLight)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "✨ Target Post-Trade State:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MinimalSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "USDT: ~$${RebalanceEngine.format2(halfValue)} (50.00%)\nMNT: ~$${RebalanceEngine.format2(halfValue)} (50.00%)",
                            fontSize = 12.sp,
                            color = MinimalTextPrimary,
                            lineHeight = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isExecuting,
                        modifier = Modifier.testTag("dismiss_rebalance_button")
                    ) {
                        Text("Later", color = MinimalTextSecondary, fontWeight = FontWeight.Medium)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = onConfirm,
                        enabled = !isExecuting,
                        modifier = Modifier.testTag("confirm_rebalance_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MinimalPrimary,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isExecuting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = "Confirm & Rebalance",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
