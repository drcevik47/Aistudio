package com.example.ui.components

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.remote.model.KlineResult
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object KlineExporter {
    fun shareKlinesAsFile(
        context: Context,
        klineResult: KlineResult,
        symbol: String,
        interval: String,
        format: String // "CSV" or "JSON"
    ) {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val extension = format.lowercase()
            val fileName = "BybitKline_${symbol}_${interval}_$timestamp.$extension"
            
            val logsDir = File(context.cacheDir, "klines").apply { if (!exists()) mkdirs() }
            val file = File(logsDir, fileName)
            
            val fileContent = buildString {
                if (format.equals("CSV", ignoreCase = true)) {
                    appendLine("StartTime,Open,High,Low,Close,Volume,Turnover,Date")
                    klineResult.list.forEach { kline ->
                        if (kline.size >= 7) {
                            val startMs = kline[0].toLongOrNull() ?: 0L
                            val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(startMs))
                            appendLine("${kline[0]},${kline[1]},${kline[2]},${kline[3]},${kline[4]},${kline[5]},${kline[6]},$dateStr")
                        }
                    }
                } else {
                    // JSON
                    appendLine("[")
                    klineResult.list.forEachIndexed { index, kline ->
                        if (kline.size >= 7) {
                            val startMs = kline[0].toLongOrNull() ?: 0L
                            val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(startMs))
                            appendLine("  {")
                            appendLine("    \"startTime\": ${kline[0]},")
                            appendLine("    \"date\": \"$dateStr\",")
                            appendLine("    \"open\": ${kline[1]},")
                            appendLine("    \"high\": ${kline[2]},")
                            appendLine("    \"low\": ${kline[3]},")
                            appendLine("    \"close\": ${kline[4]},")
                            appendLine("    \"volume\": ${kline[5]},")
                            appendLine("    \"turnover\": ${kline[6]}")
                            append("  }")
                            if (index < klineResult.list.size - 1) appendLine(",") else appendLine()
                        }
                    }
                    appendLine("]")
                }
            }
            
            file.writeText(fileContent, Charsets.UTF_8)
            
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            
            val mimeType = if (format.equals("CSV", ignoreCase = true)) "text/csv" else "application/json"
            
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri(fileName, uri)
                putExtra(Intent.EXTRA_SUBJECT, "$symbol $interval Kline Verisi ($timestamp).$extension")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Bybit $symbol ($interval) fiyat geçmişi (kline) verisi ektedir: $fileName."
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            val chooserIntent = Intent.createChooser(sendIntent, "Kline Verisini Paylaş (.$extension)")
            chooserIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(chooserIntent)
        } catch (e: Exception) {
            android.util.Log.e("KlineExporter", "Failed to export klines", e)
            Toast.makeText(context, "Kline dosyası oluşturulamadı: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
