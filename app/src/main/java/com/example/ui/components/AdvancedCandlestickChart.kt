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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
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

data class KlineData(
    val timeMillis: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double
)

fun formatVolume(volume: Double): String {
    return when {
        volume >= 1_000_000_000 -> String.format(Locale.US, "%.2fB", volume / 1_000_000_000)
        volume >= 1_000_000 -> String.format(Locale.US, "%.2fM", volume / 1_000_000)
        volume >= 1_000 -> String.format(Locale.US, "%.2fK", volume / 1_000)
        else -> String.format(Locale.US, "%.2f", volume)
    }
}

fun formatPriceInfo(value: Double): String {
    return if (value < 1.0) String.format(Locale.US, "%.5f", value)
    else if (value < 10.0) String.format(Locale.US, "%.4f", value)
    else if (value < 1000.0) String.format(Locale.US, "%.2f", value)
    else String.format(Locale.US, "%.1f", value)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdvancedCandlestickChart(
    klines: List<KlineData>,
    executions: List<BybitExecutionDto>,
    selectedInterval: String,
    onIntervalChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var scaleX by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var touchPosition by remember { mutableStateOf<Offset?>(null) }
    
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    
    val yAxisWidthPx = with(density) { 55.dp.toPx() }
    val xAxisHeightPx = with(density) { 20.dp.toPx() }
    
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dateFormatter = remember { SimpleDateFormat("dd MMM", Locale.getDefault()) }
    val fullDateFormatter = remember { SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()) }

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
                        scaleX = 1f; offsetX = 0f; touchPosition = null
                        onIntervalChanged(apiValue) 
                    }
                )
            }
        }
        
        if (klines.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().height(350.dp), contentAlignment = Alignment.Center) {
                Text("Grafik verisi yükleniyor...", color = MinimalTextMuted, fontSize = 12.sp)
            }
            return@Column
        }

        val sortedKlines = remember(klines) { klines.sortedBy { it.timeMillis } }
        val sortedExecs = remember(executions) { executions.sortedBy { it.timeMillis } }

        BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(350.dp)) {
            val boxWidthPx = with(density) { maxWidth.toPx() }
            val boxHeightPx = with(density) { maxHeight.toPx() }
            
            val chartWidth = boxWidthPx - yAxisWidthPx
            val chartHeight = boxHeightPx - xAxisHeightPx
            
            val baseCandleWidth = with(density) { 4.dp.toPx() } // Made slightly thinner
            val candleSpacing = with(density) { 1.dp.toPx() }
            val totalCandleWidth = baseCandleWidth + candleSpacing
            val zoomedCandleWidth = totalCandleWidth * scaleX
            
            val minOffsetX = chartWidth - (sortedKlines.size * zoomedCandleWidth)
            val clampedOffsetX = if (minOffsetX < 0) offsetX.coerceIn(minOffsetX, 0f) else 0f
            
            // Calculate hover index for OHLC display
            var hoveredKline: KlineData? = null
            touchPosition?.let { touch ->
                if (touch.x in 0f..chartWidth && touch.y in 0f..chartHeight) {
                    val floatIdx = (touch.x - clampedOffsetX) / zoomedCandleWidth
                    val idx = floatIdx.toInt().coerceIn(0, sortedKlines.lastIndex)
                    hoveredKline = sortedKlines[idx]
                }
            }
            
            // Determine visible range
            val firstVisibleIdx = ((-clampedOffsetX) / zoomedCandleWidth).toInt().coerceAtLeast(0)
            val lastVisibleIdx = (((-clampedOffsetX) + chartWidth) / zoomedCandleWidth).toInt().coerceAtMost(sortedKlines.lastIndex)
            val visibleKlines = if (sortedKlines.isNotEmpty()) {
                sortedKlines.subList(firstVisibleIdx, (lastVisibleIdx + 1).coerceAtMost(sortedKlines.size))
            } else emptyList()
            
            val displayedKline = hoveredKline ?: visibleKlines.lastOrNull() ?: sortedKlines.last()

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            offsetX += pan.x
                            // Allow much further zoom out (0.1f) to see history
                            val newScaleX = (scaleX * zoom).coerceIn(0.01f, 50f)
                            val diffX = centroid.x - offsetX
                            offsetX -= diffX * (newScaleX / scaleX - 1)
                            scaleX = newScaleX
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
                // Ensure layout variables match what we calculated above
                offsetX = clampedOffsetX
                val candleBodyWidth = baseCandleWidth * scaleX
                val volAreaHeight = chartHeight * 0.15f
                val priceAreaHeight = chartHeight * 0.75f

                if (visibleKlines.isEmpty()) return@Canvas

                val minVisiblePrice = visibleKlines.minOf { it.low }
                val maxVisiblePrice = visibleKlines.maxOf { it.high }
                val priceRange = if (maxVisiblePrice == minVisiblePrice) 1.0 else maxVisiblePrice - minVisiblePrice
                val maxVisibleVol = visibleKlines.maxOf { it.volume }.coerceAtLeast(1.0)

                val yPriceStep = priceAreaHeight / priceRange.toFloat()
                val yVolStep = volAreaHeight / maxVisibleVol.toFloat()
                
                val priceTopMargin = chartHeight * 0.10f

                // Draw Grid & Y-Axis Labels
                val hGridLines = 4
                for (i in 0..hGridLines) {
                    val y = i * (priceAreaHeight / hGridLines) + priceTopMargin
                    // Softer grid line alpha
                    drawLine(MinimalSurfaceBorderLight.copy(alpha = 0.5f), Offset(0f, y), Offset(chartWidth, y), 1f)
                    
                    val priceVal = maxVisiblePrice - (i * priceRange / hGridLines)
                    val priceText = formatPriceInfo(priceVal)
                    val labelLayout = textMeasurer.measure(priceText, TextStyle(color = MinimalTextMuted, fontSize = 9.sp))
                    drawText(
                        textLayoutResult = labelLayout,
                        topLeft = Offset(chartWidth + with(density){4.dp.toPx()}, y - labelLayout.size.height / 2f)
                    )
                }

                // Draw X-Axis Labels & Vertical Grid
                val vGridLines = 3
                for (i in 1..vGridLines) {
                    val x = i * (chartWidth / (vGridLines + 1))
                    drawLine(MinimalSurfaceBorderLight.copy(alpha = 0.5f), Offset(x, 0f), Offset(x, chartHeight), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))
                    
                    val floatIdx = (x - offsetX) / zoomedCandleWidth
                    val idx = floatIdx.toInt().coerceIn(0, sortedKlines.lastIndex)
                    val kline = sortedKlines[idx]
                    
                    val timeText = if (selectedInterval == "D" || selectedInterval == "W") {
                        dateFormatter.format(Date(kline.timeMillis))
                    } else {
                        timeFormatter.format(Date(kline.timeMillis))
                    }
                    val labelLayout = textMeasurer.measure(timeText, TextStyle(color = MinimalTextMuted, fontSize = 9.sp))
                    drawText(
                        textLayoutResult = labelLayout,
                        topLeft = Offset(x - labelLayout.size.width / 2f, chartHeight + with(density){4.dp.toPx()})
                    )
                }

                // Draw Klines & Volume
                for (i in firstVisibleIdx..lastVisibleIdx.coerceAtMost(sortedKlines.lastIndex)) {
                    val kline = sortedKlines[i]
                    val cx = offsetX + (i * zoomedCandleWidth) + (zoomedCandleWidth / 2f)
                    
                    val yOpen = ((maxVisiblePrice - kline.open) * yPriceStep).toFloat() + priceTopMargin
                    val yClose = ((maxVisiblePrice - kline.close) * yPriceStep).toFloat() + priceTopMargin
                    val yHigh = ((maxVisiblePrice - kline.high) * yPriceStep).toFloat() + priceTopMargin
                    val yLow = ((maxVisiblePrice - kline.low) * yPriceStep).toFloat() + priceTopMargin
                    
                    val isBullish = kline.close >= kline.open
                    val candleColor = if (isBullish) MinimalSuccess else MinimalError
                    
                    drawLine(candleColor, Offset(cx, yHigh), Offset(cx, yLow), with(density){1.dp.toPx()})
                    
                    val top = minOf(yOpen, yClose)
                    val bottom = maxOf(yOpen, yClose)
                    val bodyHeight = maxOf(bottom - top, with(density){1.dp.toPx()})
                    drawRect(candleColor, Offset(cx - candleBodyWidth / 2f, top), Size(candleBodyWidth, bodyHeight))
                    
                    val volHeight = (kline.volume * yVolStep).toFloat()
                    drawRect(candleColor.copy(alpha = 0.3f), Offset(cx - candleBodyWidth / 2f, chartHeight - volHeight), Size(candleBodyWidth, volHeight))
                }

                // Draw Executions (Modern dots instead of large triangles)
                if (sortedKlines.isNotEmpty()) {
                    val firstTime = sortedKlines.first().timeMillis
                    val lastTime = sortedKlines.last().timeMillis + (sortedKlines.last().timeMillis - sortedKlines[0].timeMillis)/sortedKlines.size
                    
                    sortedExecs.forEach { exec ->
                        if (exec.timeMillis in firstTime..lastTime) {
                            val candleIdx = sortedKlines.indexOfFirst { it.timeMillis >= exec.timeMillis }.takeIf { it >= 0 } ?: sortedKlines.lastIndex
                            val cx = offsetX + (candleIdx * zoomedCandleWidth) + (zoomedCandleWidth / 2f)
                            val yPrice = ((maxVisiblePrice - exec.priceValue) * yPriceStep).toFloat() + priceTopMargin
                            
                            if (cx in 0f..chartWidth) {
                                val markerRadius = with(density){ 3.dp.toPx() }
                                val dotColor = if (exec.isBuy) MinimalSuccessDark else MinimalErrorDark
                                
                                // Draw white/bg border for clarity
                                drawCircle(
                                    color = MinimalBg,
                                    radius = markerRadius + with(density){1.dp.toPx()},
                                    center = Offset(cx, yPrice)
                                )
                                drawCircle(
                                    color = dotColor,
                                    radius = markerRadius,
                                    center = Offset(cx, yPrice)
                                )
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
                        
                        drawLine(MinimalTextPrimary.copy(alpha = 0.5f), Offset(cx, 0f), Offset(cx, chartHeight), with(density){1.dp.toPx()}, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f,10f), 0f))
                        drawLine(MinimalTextPrimary.copy(alpha = 0.5f), Offset(0f, touch.y), Offset(chartWidth, touch.y), with(density){1.dp.toPx()}, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f,10f), 0f))
                        
                        // Price tag
                        val priceAtTouch = maxVisiblePrice - ((touch.y - priceTopMargin) / yPriceStep)
                        val priceText = formatPriceInfo(priceAtTouch)
                        val priceLabelLayout = textMeasurer.measure(priceText, TextStyle(color = MinimalBg, fontSize = 10.sp, fontWeight = FontWeight.Bold))
                        val priceRectHeight = priceLabelLayout.size.height.toFloat() + with(density){8.dp.toPx()}
                        val priceRectY = touch.y - priceRectHeight/2f
                        
                        drawRoundRect(
                            color = MinimalTextPrimary,
                            topLeft = Offset(chartWidth, priceRectY),
                            size = Size(yAxisWidthPx, priceRectHeight),
                            cornerRadius = CornerRadius(with(density){4.dp.toPx()})
                        )
                        drawText(
                            textLayoutResult = priceLabelLayout,
                            topLeft = Offset(chartWidth + with(density){6.dp.toPx()}, touch.y - priceLabelLayout.size.height/2f)
                        )

                        // Date tag
                        val dateLabel = fullDateFormatter.format(Date(kline.timeMillis))
                        val dateLabelLayout = textMeasurer.measure(dateLabel, TextStyle(color = MinimalBg, fontSize = 10.sp, fontWeight = FontWeight.Bold))
                        val dateRectWidth = dateLabelLayout.size.width.toFloat() + with(density){12.dp.toPx()}
                        
                        // Ensure date tag stays within bounds
                        val dateTagX = (cx - dateRectWidth/2f).coerceIn(0f, chartWidth - dateRectWidth)
                        
                        drawRoundRect(
                            color = MinimalTextPrimary,
                            topLeft = Offset(dateTagX, chartHeight),
                            size = Size(dateRectWidth, xAxisHeightPx),
                            cornerRadius = CornerRadius(with(density){4.dp.toPx()})
                        )
                        drawText(
                            textLayoutResult = dateLabelLayout,
                            topLeft = Offset(dateTagX + with(density){6.dp.toPx()}, chartHeight + (xAxisHeightPx - dateLabelLayout.size.height)/2f)
                        )
                    }
                }
            }
            
            // OHLC Overlay Component using FlowRow for perfect wrapping
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                val isUp = displayedKline.close >= displayedKline.open
                val color = if (isUp) MinimalSuccessDark else MinimalErrorDark
                
                Text("O: ${formatPriceInfo(displayedKline.open)}", fontSize = 10.sp, color = color, fontWeight = FontWeight.Medium)
                Text("H: ${formatPriceInfo(displayedKline.high)}", fontSize = 10.sp, color = color, fontWeight = FontWeight.Medium)
                Text("L: ${formatPriceInfo(displayedKline.low)}", fontSize = 10.sp, color = color, fontWeight = FontWeight.Medium)
                Text("C: ${formatPriceInfo(displayedKline.close)}", fontSize = 10.sp, color = color, fontWeight = FontWeight.Medium)
                Text("Vol: ${formatVolume(displayedKline.volume)}", fontSize = 10.sp, color = MinimalTextSecondary, fontWeight = FontWeight.Medium)
            }
        }
    }
}
