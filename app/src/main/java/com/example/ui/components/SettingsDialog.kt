package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
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
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.bot.RebalanceEngine
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
fun SettingsDialog(
    currentStepPercent: Double,
    isTestnet: Boolean,
    onUpdateStepPercent: (Double) -> Unit,
    onOpenApiKeys: () -> Unit,
    onDismiss: () -> Unit
) {
    var stepSlider by remember(currentStepPercent) { mutableFloatStateOf(currentStepPercent.toFloat()) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .testTag("settings_dialog"),
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
                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MinimalPrimaryLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = MinimalPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "Bot & Grid Settings",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MinimalTextPrimary,
                            letterSpacing = (-0.2).sp
                        )
                        Text(
                            text = "Rebalance step parameters and trading rules",
                            fontSize = 11.sp,
                            color = MinimalTextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Step Percent Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MinimalSurfaceElevated),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MinimalSurfaceBorderLight)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Percent, contentDescription = null, tint = MinimalPrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Rebalance Grid Step",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MinimalTextPrimary
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = MinimalPrimaryLight
                            ) {
                                Text(
                                    text = "±${RebalanceEngine.format2(stepSlider.toDouble())}%",
                                    fontWeight = FontWeight.Bold,
                                    color = MinimalPrimaryDark,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Default: 2.00% (Orders placed at +2% sell and -2% buy to maintain 50/50 balance)",
                            fontSize = 11.sp,
                            color = MinimalTextSecondary
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Slider(
                            value = stepSlider,
                            onValueChange = { stepSlider = it },
                            valueRange = 0.5f..10.0f,
                            steps = 18,
                            colors = SliderDefaults.colors(
                                thumbColor = MinimalPrimary,
                                activeTrackColor = MinimalPrimary,
                                inactiveTrackColor = MinimalSurfaceBorder
                            ),
                            modifier = Modifier.testTag("step_percent_slider")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(1.0f, 1.5f, 2.0f, 3.0f, 5.0f).forEach { preset ->
                                FilterChip(
                                    selected = kotlin.math.abs(stepSlider - preset) < 0.05f,
                                    onClick = { stepSlider = preset },
                                    shape = RoundedCornerShape(999.dp),
                                    label = { Text("${preset.toInt()}%", fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MinimalPrimary,
                                        selectedLabelColor = Color.White,
                                        containerColor = MinimalSurface,
                                        labelColor = MinimalTextSecondary
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = kotlin.math.abs(stepSlider - preset) < 0.05f,
                                        borderColor = MinimalSurfaceBorder,
                                        selectedBorderColor = MinimalPrimary
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // API Key Settings Action
                Card(
                    colors = CardDefaults.cardColors(containerColor = MinimalSurfaceElevated),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MinimalSurfaceBorderLight)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Bybit API Credentials",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = MinimalTextPrimary
                            )
                            Text(
                                text = if (isTestnet) "Testnet Mode Active" else "Mainnet (Real Trading)",
                                fontSize = 11.sp,
                                color = if (isTestnet) MinimalSecondary else MinimalTextSecondary
                            )
                        }
                        OutlinedButton(
                            onClick = onOpenApiKeys,
                            shape = RoundedCornerShape(999.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MinimalPrimary),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MinimalPrimary.copy(alpha = 0.5f)),
                            modifier = Modifier.testTag("edit_api_keys_button")
                        ) {
                            Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Edit Keys", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                val context = LocalContext.current
                val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as? PowerManager }
                val isIgnoringBatteryOpt = remember {
                    powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
                }

                // 24/7 Service Info & Battery Optimization Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MinimalSurfaceElevated),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MinimalSurfaceBorderLight)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.Top) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(if (isIgnoringBatteryOpt) MinimalSuccessLight else MinimalSecondaryLight),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isIgnoringBatteryOpt) Icons.Default.CheckCircle else Icons.Default.BatteryAlert,
                                    contentDescription = null,
                                    tint = if (isIgnoringBatteryOpt) MinimalSuccessDark else MinimalSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "7/24 Kesintisiz Arka Plan & Pil Koruması",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MinimalTextPrimary
                                )
                                Text(
                                    text = if (isIgnoringBatteryOpt) {
                                        "Pil optimizasyonu devre dışı bırakıldı (Kısıtlamasız). Android sistemi uygulamayı ve WebSocket akışını arka planda ASLA uyutmaz/öldürmez."
                                    } else {
                                        "Telefonunuz uygulamayı arka planda uyutmasın diye 'Pil Tasarrufu Kısıtlamasını Kaldırın'."
                                    },
                                    fontSize = 11.sp,
                                    color = MinimalTextSecondary,
                                    lineHeight = 15.sp,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }

                        if (!isIgnoringBatteryOpt) {
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = {
                                    try {
                                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                        context.startActivity(intent)
                                    }
                                },
                                shape = RoundedCornerShape(999.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MinimalSecondary),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MinimalSecondary.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth().testTag("battery_opt_button")
                            ) {
                                Icon(Icons.Default.BatteryChargingFull, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Pil Kısıtlamasını Kaldır (Kısıtlamasız Yap)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Save / Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Close", color = MinimalTextSecondary, fontWeight = FontWeight.Medium)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onUpdateStepPercent(stepSlider.toDouble())
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MinimalPrimary,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Save Changes", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
