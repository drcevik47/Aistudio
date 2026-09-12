package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.model.BybitExecutionDto
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

data class KlineData(
    val timeMillis: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double
)

@Composable
fun AdvancedCandlestickChart(
    klines: List<KlineData>,
    executions: List<BybitExecutionDto>,
    selectedInterval: String,
    onIntervalChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var scaleX by remember { mutableFloatStateOf(1f) }
    var scaleY by remember { mutableFloatStateOf(1f) } // Not always needed but good for custom Y zoom
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    
    // Auto-scale flag
    var isYAutoScaled by remember { mutableStateOf(true) }

    val textMeasurer = rememberTextMeasurer()
    var touchPosition by remember { mutableStateOf<Offset?>(null) }
    
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dateFormatter = remember { SimpleDateFormat("dd MMM", Locale.getDefault()) }

    Column(modifier = modifier.fillMaxWidth().background(MinimalBg).clip(RoundedCornerShape(12.dp))) {
        // Interval Selector Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val intervals = listOf("5" to "5m", "15" to "15m", "60" to "1S", "240" to "4S", "D" to "1G", "W" to "1H")
            intervals.forEach { (apiValue, label) ->
                val isSelected = selectedInterval == apiValue
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) MinimalPrimary else MinimalTextMuted,
                    modifier = Modifier.clickable { 
                        // Reset transforms on interval change
                        scaleX = 1f; offsetX = 0f; touchPosition = null; isYAutoScaled = true
                        onIntervalChanged(apiValue) 
                    }
                )
            }
        }
        
        if (klines.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                Text("Grafik verisi yükleniyor veya boş...", color = MinimalTextMuted, fontSize = 12.sp)
            }
            return@Column
        }

        // Ensure chronological order (oldest first)
        val sortedKlines = remember(klines) { klines.sortedBy { it.timeMillis } }
        val sortedExecs = remember(executions) { executions.sortedBy { it.timeMillis } }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(350.dp)
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        // Panning
                        offsetX += pan.x
                        if (!isYAutoScaled) offsetY += pan.y
                        
                        // Zooming
                        val newScaleX = (scaleX * zoom).coerceIn(0.5f, 20f)
                        // Adjust offsetX so we zoom into the centroid
                        val diffX = centroid.x - offsetX
                        offsetX -= diffX * (newScaleX / scaleX - 1)
                        scaleX = newScaleX
                        
                        if (!isYAutoScaled) {
                            val newScaleY = (scaleY * zoom).coerceIn(0.5f, 10f)
                            val diffY = centroid.y - offsetY
                            offsetY -= diffY * (newScaleY / scaleY - 1)
                            scaleY = newScaleY
                        }
                    }
                }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val pos = event.changes.firstOrNull()?.position
                            if (event.changes.any { it.pressed }) {
                                touchPosition = pos
                            } else {
                                touchPosition = null
                            }
                        }
                    }
                }
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val yAxisWidth = 120f
            val xAxisHeight = 60f
            val chartWidth = canvasWidth - yAxisWidth
            val chartHeight = canvasHeight - xAxisHeight
            val volAreaHeight = chartHeight * 0.2f
            val priceAreaHeight = chartHeight * 0.8f

            val baseCandleWidth = 8f
            val candleSpacing = 2f
            val totalCandleWidth = baseCandleWidth + candleSpacing
            val zoomedCandleWidth = totalCandleWidth * scaleX
            val candleBodyWidth = baseCandleWidth * scaleX

            // Limit panning
            val minOffsetX = chartWidth - (sortedKlines.size * zoomedCandleWidth)
            if (minOffsetX < 0) {
                offsetX = offsetX.coerceIn(minOffsetX, 0f)
            } else {
                offsetX = 0f
            }

            // Calculate visible range
            val firstVisibleIdx = ((-offsetX) / zoomedCandleWidth).toInt().coerceAtLeast(0)
            val lastVisibleIdx = (((-offsetX) + chartWidth) / zoomedCandleWidth).toInt().coerceAtMost(sortedKlines.lastIndex)
            val visibleKlines = sortedKlines.subList(firstVisibleIdx, lastVisibleIdx + 1)
            
            if (visibleKlines.isEmpty()) return@Canvas

            // Auto-scale Y based on visible Klines
            val minVisiblePrice = visibleKlines.minOf { it.low }
            val maxVisiblePrice = visibleKlines.maxOf { it.high }
            val priceRange = if (maxVisiblePrice == minVisiblePrice) 1.0 else maxVisiblePrice - minVisiblePrice
            val maxVisibleVol = visibleKlines.maxOf { it.volume }.coerceAtLeast(1.0)

            val yPriceStep = priceAreaHeight / priceRange.toFloat()
            val yVolStep = volAreaHeight / maxVisibleVol.toFloat()

            // Draw Grid & Axes (similar to previous)
            val hGridLines = 5
            for (i in 0..hGridLines) {
                val y = i * (priceAreaHeight / hGridLines)
                drawLine(MinimalSurfaceBorderLight, Offset(0f, y), Offset(chartWidth, y), 1f)
                val priceVal = maxVisiblePrice - (i * priceRange / hGridLines)
                drawText(textMeasurer = textMeasurer, text = String.format(Locale.US, "%.4f", priceVal), style = TextStyle(color = MinimalTextMuted, fontSize = 9.sp), topLeft = Offset(chartWidth + 12f, y - 16f))
            }

            val vGridLines = 4
            for (i in 0..vGridLines) {
                val x = i * (chartWidth / vGridLines)
                drawLine(MinimalSurfaceBorderLight, Offset(x, 0f), Offset(x, chartHeight), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))
            }

            // Draw Klines (Candles & Volume)
            for (i in firstVisibleIdx..lastVisibleIdx) {
                val kline = sortedKlines[i]
                val cx = offsetX + (i * zoomedCandleWidth) + (zoomedCandleWidth / 2f)
                
                val yOpen = ((maxVisiblePrice - kline.open) * yPriceStep).toFloat()
                val yClose = ((maxVisiblePrice - kline.close) * yPriceStep).toFloat()
                val yHigh = ((maxVisiblePrice - kline.high) * yPriceStep).toFloat()
                val yLow = ((maxVisiblePrice - kline.low) * yPriceStep).toFloat()
                
                val isBullish = kline.close >= kline.open
                val candleColor = if (isBullish) MinimalSuccess else MinimalError
                
                // Wick
                drawLine(candleColor, Offset(cx, yHigh), Offset(cx, yLow), 2f)
                // Body
                val top = minOf(yOpen, yClose)
                val bottom = maxOf(yOpen, yClose)
                val bodyHeight = maxOf(bottom - top, 2f) // at least 2px height
                drawRect(candleColor, Offset(cx - candleBodyWidth / 2f, top), Size(candleBodyWidth, bodyHeight))
                
                // Volume
                val volHeight = (kline.volume * yVolStep).toFloat()
                drawRect(candleColor.copy(alpha = 0.5f), Offset(cx - candleBodyWidth / 2f, chartHeight - volHeight), Size(candleBodyWidth, volHeight))
            }

            // Draw Executions
            // Executions might not align exactly with candle timestamps if they are arbitrary.
            // We can map their timestamp to the X axis linearly.
            if (sortedKlines.isNotEmpty()) {
                val firstTime = sortedKlines.first().timeMillis
                val lastTime = sortedKlines.last().timeMillis + (sortedKlines.last().timeMillis - sortedKlines[0].timeMillis)/sortedKlines.size // approximate end
                val timeRange = lastTime - firstTime
                
                sortedExecs.forEach { exec ->
                    // Find x pos based on time mapping
                    if (exec.timeMillis in firstTime..lastTime) {
                        // Find closest candle index
                        val candleIdx = sortedKlines.indexOfFirst { it.timeMillis >= exec.timeMillis }.takeIf { it >= 0 } ?: sortedKlines.lastIndex
                        val cx = offsetX + (candleIdx * zoomedCandleWidth) + (zoomedCandleWidth / 2f)
                        
                        val yPrice = ((maxVisiblePrice - exec.priceValue) * yPriceStep).toFloat()
                        
                        // Only draw if visible
                        if (cx in 0f..chartWidth) {
                            val markerSize = 16f
                            val markerPath = Path()
                            if (exec.isBuy) {
                                markerPath.moveTo(cx, yPrice + markerSize * 0.5f)
                                markerPath.lineTo(cx - markerSize * 0.6f, yPrice + markerSize * 1.5f)
                                markerPath.lineTo(cx + markerSize * 0.6f, yPrice + markerSize * 1.5f)
                                markerPath.close()
                                drawPath(markerPath, MinimalSuccessDark)
                            } else {
                                markerPath.moveTo(cx, yPrice - markerSize * 0.5f)
                                markerPath.lineTo(cx - markerSize * 0.6f, yPrice - markerSize * 1.5f)
                                markerPath.lineTo(cx + markerSize * 0.6f, yPrice - markerSize * 1.5f)
                                markerPath.close()
                                drawPath(markerPath, MinimalErrorDark)
                            }
                        }
                    }
                }
            }
            
            // Draw Crosshair
            touchPosition?.let { touch ->
                if (touch.x in 0f..chartWidth && touch.y in 0f..chartHeight) {
                    val floatIdx = (touch.x - offsetX) / zoomedCandleWidth
                    val idx = floatIdx.toInt().coerceIn(0, sortedKlines.lastIndex)
                    val kline = sortedKlines[idx]
                    val cx = offsetX + (idx * zoomedCandleWidth) + (zoomedCandleWidth / 2f)
                    
                    drawLine(MinimalTextPrimary.copy(alpha = 0.5f), Offset(cx, 0f), Offset(cx, chartHeight), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f,10f), 0f))
                    drawLine(MinimalTextPrimary.copy(alpha = 0.5f), Offset(0f, touch.y), Offset(chartWidth, touch.y), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f,10f), 0f))
                    
                    // Price tag
                    val priceAtTouch = maxVisiblePrice - (touch.y / yPriceStep)
                    val priceLabel = String.format(Locale.US, "%.4f", priceAtTouch)
                    val priceLabelLayout = textMeasurer.measure(priceLabel, TextStyle(color = MinimalBg, fontSize = 10.sp, fontWeight = FontWeight.Bold))
                    drawRect(MinimalTextPrimary, Offset(chartWidth + 4f, touch.y - 12f), Size(yAxisWidth - 8f, 24f))
                    drawText(textLayoutResult = priceLabelLayout, topLeft = Offset(chartWidth + 12f, touch.y - priceLabelLayout.size.height/2f))

                    // Date tag
                    val dateLabel = timeFormatter.format(Date(kline.timeMillis)) + " " + dateFormatter.format(Date(kline.timeMillis))
                    val dateLabelLayout = textMeasurer.measure(dateLabel, TextStyle(color = MinimalBg, fontSize = 10.sp, fontWeight = FontWeight.Bold))
                    drawRect(MinimalTextPrimary, Offset(cx - dateLabelLayout.size.width/2f - 8f, chartHeight + 4f), Size(dateLabelLayout.size.width + 16f, 24f))
                    drawText(textLayoutResult = dateLabelLayout, topLeft = Offset(cx - dateLabelLayout.size.width/2f, chartHeight + 8f))

                    // OHLC Tooltip
                    val ohlcText = "O: ${kline.open} H: ${kline.high} L: ${kline.low} C: ${kline.close} Vol: ${String.format(Locale.US, "%.2f", kline.volume)}"
                    val ohlcLayout = textMeasurer.measure(ohlcText, TextStyle(color = MinimalTextPrimary, fontSize = 9.sp))
                    drawRect(MinimalSurfaceElevated.copy(alpha=0.9f), Offset(4f, 4f), Size(ohlcLayout.size.width + 16f, ohlcLayout.size.height + 8f))
                    drawText(textLayoutResult = ohlcLayout, topLeft = Offset(12f, 8f))
                }
            }
        }
    }
}
