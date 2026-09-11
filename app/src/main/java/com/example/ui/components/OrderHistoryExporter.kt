package com.example.ui.components

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.local.entity.ExchangeTradeEntity
import com.example.data.local.entity.OrderEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object OrderHistoryExporter {
    fun shareOrderHistoryAsFile(
        context: Context,
        orders: List<OrderEntity>,
        exchangeTrades: List<ExchangeTradeEntity>,
        selectedSymbol: String
    ) {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "BybitBot_IslemGecmisi_$timestamp.txt"
            
            val logsDir = File(context.cacheDir, "logs").apply { if (!exists()) mkdirs() }
            val file = File(logsDir, fileName)
            
            val reportContent = buildString {
                val filledOrders = orders.filter { 
                    it.status.equals("Filled", ignoreCase = true) && 
                    (selectedSymbol == "ALL" || it.symbol.equals(selectedSymbol, ignoreCase = true) || (selectedSymbol == "MNTUSDT" && it.symbol.isBlank()))
                }
                
                val filteredTrades = exchangeTrades.filter {
                    selectedSymbol == "ALL" || it.symbol.equals(selectedSymbol, ignoreCase = true) || (selectedSymbol == "MNTUSDT" && it.symbol.isBlank())
                }
                
                val allRecords = mutableListOf<Pair<Long, String>>()

                filledOrders.forEach { order ->
                    val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(order.timestamp))
                    allRecords.add(Pair(order.timestamp, "[$date] ${order.side} ${order.qty} ${order.symbol} @ $${order.price}"))
                }
                
                filteredTrades.forEach { trade ->
                    val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(trade.timeMillis))
                    allRecords.add(Pair(trade.timeMillis, "[$date] ${trade.side} ${trade.execQty} ${trade.symbol} @ $${trade.execPrice}"))
                }
                
                val distinctLines = allRecords.sortedByDescending { it.first }.map { it.second }.distinct()
                distinctLines.forEach { appendLine(it) }
            }
            
            file.writeText(reportContent, Charsets.UTF_8)
            
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri(fileName, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Bot İşlem Geçmişi Raporu ($timestamp).txt")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Bybit Rebalancer Bot işlem geçmişi raporu ektedir: $fileName."
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            val chooserIntent = Intent.createChooser(sendIntent, "İşlem Geçmişini Paylaş (.txt)")
            chooserIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(chooserIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "Rapor oluşturulamadı: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
