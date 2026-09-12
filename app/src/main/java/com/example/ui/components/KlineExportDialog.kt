package com.example.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KlineExportDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    selectedSymbol: String,
    onExport: (symbol: String, interval: String, format: String, startTime: Long, endTime: Long, context: android.content.Context) -> Unit
) {
    if (!showDialog) return

    val context = LocalContext.current
    var selectedInterval by remember { mutableStateOf("60") } // Default 1h
    var selectedFormat by remember { mutableStateOf("CSV") }
    
    // Default range: Last 30 days
    val calendar = Calendar.getInstance()
    val defaultEnd = calendar.timeInMillis
    calendar.add(Calendar.DAY_OF_YEAR, -30)
    val defaultStart = calendar.timeInMillis

    var startTime by remember { mutableStateOf(defaultStart) }
    var endTime by remember { mutableStateOf(defaultEnd) }
    
    var showDatePicker by remember { mutableStateOf(false) }

    val intervals = listOf("1" to "1dk", "5" to "5dk", "15" to "15dk", "60" to "1 Saat", "240" to "4 Saat", "D" to "1 Gün", "W" to "1 Hafta")
    val formats = listOf("CSV", "JSON")
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    if (showDatePicker) {
        val dateRangePickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = startTime,
            initialSelectedEndDateMillis = endTime
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    showDatePicker = false
                    dateRangePickerState.selectedStartDateMillis?.let { startTime = it }
                    dateRangePickerState.selectedEndDateMillis?.let { endTime = it }
                }) {
                    Text("Tamam")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("İptal")
                }
            }
        ) {
            DateRangePicker(
                state = dateRangePickerState,
                modifier = Modifier.weight(1f)
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = MinimalPrimary, modifier = Modifier.size(28.dp)) },
        title = { Text("Fiyat Geçmişi (Kline) İndir", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column {
                Text("Bybit borsasından ${if (selectedSymbol == "ALL") "MNTUSDT" else selectedSymbol} fiyat geçmişini indirebilirsiniz.", fontSize = 13.sp, color = MinimalTextSecondary)
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("Zaman Dilimi (Interval):", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    intervals.forEach { (value, label) ->
                        FilterChip(
                            selected = selectedInterval == value,
                            onClick = { selectedInterval = value },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Tarih Aralığı:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "${dateFormat.format(Date(startTime))} - ${dateFormat.format(Date(endTime))}",
                        fontSize = 13.sp
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                Text("Dosya Formatı:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    formats.forEach { format ->
                        FilterChip(
                            selected = selectedFormat == format,
                            onClick = { selectedFormat = format },
                            label = { Text(format, fontSize = 12.sp) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onExport(selectedSymbol, selectedInterval, selectedFormat, startTime, endTime, context)
                onDismiss()
            }) {
                Text("İndir & Paylaş")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("İptal") }
        },
        containerColor = MinimalSurface,
        shape = RoundedCornerShape(16.dp)
    )
}
