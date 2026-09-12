package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Fill
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
import com.example.bot.RebalanceEngine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@Composable
fun PingPongChart(executions: List<BybitExecutionDto>, modifier: Modifier = Modifier) {
    if (executions.isEmpty()) return

    val sortedExecs = remember(executions) { executions.sortedBy { it.timeMillis } }
    val prices = sortedExecs.map { it.priceValue }
    val volumes = sortedExecs.map { it.qtyValue }
    
    if (prices.isEmpty()) return
    
    val minPrice = prices.minOrNull() ?: 0.0
    val maxPrice = prices.maxOrNull() ?: 0.0
    val maxVolume = volumes.maxOrNull() ?: 0.0
    val priceRange = if (maxPrice == minPrice) 1.0 else maxPrice - minPrice
    
    val textMeasurer = rememberTextMeasurer()
    var touchPosition by remember { mutableStateOf<Offset?>(null) }

    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dateFormatter = remember { SimpleDateFormat("dd MMM", Locale.getDefault()) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp) // Profesyonel ve geniş alan
            .clip(RoundedCornerShape(8.dp))
            .background(MinimalBg)
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
        val yAxisWidth = 120f
        val xAxisHeight = 60f
        val chartWidth = size.width - yAxisWidth
        val chartHeight = size.height - xAxisHeight
        
        val volumeAreaHeight = chartHeight * 0.25f
        val priceAreaHeight = chartHeight * 0.75f
        
        // Theme Colors
        val gridColor = MinimalSurfaceBorderLight
        val textColor = MinimalTextMuted
        val buyColor = MinimalSuccess
        val sellColor = MinimalError
        val lineColor = MinimalPrimary
        val tooltipBg = MinimalSurfaceElevated
        val tooltipTextCol = MinimalTextPrimary
        val axisTagBg = MinimalTextPrimary
        val axisTagText = MinimalBg

        val fillGradient = Brush.verticalGradient(
            colors = listOf(lineColor.copy(alpha = 0.25f), Color.Transparent),
            startY = 0f,
            endY = priceAreaHeight
        )

        // 1. Grid & Yatay Fiyat Eksenleri
        val hGridLines = 5
        for (i in 0..hGridLines) {
            val y = i * (priceAreaHeight / hGridLines)
            drawLine(color = gridColor, start = Offset(0f, y), end = Offset(chartWidth, y), strokeWidth = 1f)
            
            val priceVal = maxPrice - (i * priceRange / hGridLines)
            drawText(
                textMeasurer = textMeasurer,
                text = String.format(Locale.US, "%.4f", priceVal),
                style = TextStyle(color = textColor, fontSize = 9.sp),
                topLeft = Offset(chartWidth + 12f, y - 16f)
            )
        }

        // Dikey Zaman Izgaraları
        val vGridLines = 4
        for (i in 0..vGridLines) {
            val x = i * (chartWidth / vGridLines)
            drawLine(
                color = gridColor, 
                start = Offset(x, 0f), 
                end = Offset(x, chartHeight), 
                strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
            )
        }

        if (prices.size < 2) return@Canvas

        val stepX = chartWidth / (prices.size - 1)
        val points = mutableListOf<Offset>()
        
        prices.forEachIndexed { index, price ->
            val x = index * stepX
            val normalizedY = ((maxPrice - price) / priceRange).toFloat()
            val y = normalizedY * priceAreaHeight
            points.add(Offset(x, y))
        }

        // 2. Hacim Çubukları (Volume Bars)
        sortedExecs.forEachIndexed { index, exec ->
            val x = index * stepX
            val volHeight = if (maxVolume > 0) (exec.qtyValue / maxVolume).toFloat() * volumeAreaHeight else 0f
            val yTop = chartHeight - volHeight
            
            val barColor = if (exec.isBuy) buyColor.copy(alpha = 0.4f) else sellColor.copy(alpha = 0.4f)
            val barWidth = (stepX * 0.7f).coerceAtMost(16f).coerceAtLeast(3f)
            
            drawRect(
                color = barColor,
                topLeft = Offset(x - barWidth / 2f, yTop),
                size = Size(barWidth, volHeight)
            )
        }

        // 3. Fiyat Çizgisi ve Alan Dolgusu
        val path = Path()
        val fillPath = Path()
        
        points.forEachIndexed { index, point ->
            if (index == 0) {
                path.moveTo(point.x, point.y)
                fillPath.moveTo(point.x, priceAreaHeight)
                fillPath.lineTo(point.x, point.y)
            } else {
                path.lineTo(point.x, point.y)
                fillPath.lineTo(point.x, point.y)
            }
        }
        fillPath.lineTo(points.last().x, priceAreaHeight)
        fillPath.close()

        drawPath(path = fillPath, brush = fillGradient, style = Fill)
        drawPath(
            path = path, 
            color = lineColor, 
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // 4. İşlem İşaretçileri (Yukarı/Aşağı Oklar)
        sortedExecs.forEachIndexed { index, exec ->
            val p = points[index]
            val markerSize = 14f
            val markerPath = Path()
            
            if (exec.isBuy) {
                markerPath.moveTo(p.x, p.y + markerSize * 0.5f)
                markerPath.lineTo(p.x - markerSize * 0.6f, p.y + markerSize * 1.5f)
                markerPath.lineTo(p.x + markerSize * 0.6f, p.y + markerSize * 1.5f)
                markerPath.close()
                drawPath(markerPath, buyColor)
            } else {
                markerPath.moveTo(p.x, p.y - markerSize * 0.5f)
                markerPath.lineTo(p.x - markerSize * 0.6f, p.y - markerSize * 1.5f)
                markerPath.lineTo(p.x + markerSize * 0.6f, p.y - markerSize * 1.5f)
                markerPath.close()
                drawPath(markerPath, sellColor)
            }
        }

        // 5. Alt Kısım Zaman Etiketleri
        val timeLabelStep = (prices.size - 1) / vGridLines
        if (timeLabelStep > 0) {
            for (i in 0..vGridLines) {
                val dataIndex = (i * timeLabelStep).coerceAtMost(prices.size - 1)
                val x = points[dataIndex].x
                val timeStr = timeFormatter.format(Date(sortedExecs[dataIndex].timeMillis))
                drawText(
                    textMeasurer = textMeasurer,
                    text = timeStr,
                    style = TextStyle(color = textColor, fontSize = 9.sp),
                    topLeft = Offset(x - 20f, chartHeight + 8f)
                )
            }
        }

        // 6. Çapraz İşaretçi (Crosshair) & Tooltip
        touchPosition?.let { touch ->
            if (touch.x in 0f..chartWidth && touch.y in 0f..chartHeight) {
                var nearestIndex = 0
                var minDistance = Float.MAX_VALUE
                points.forEachIndexed { i, p ->
                    val dist = abs(p.x - touch.x)
                    if (dist < minDistance) {
                        minDistance = dist
                        nearestIndex = i
                    }
                }
                
                val np = points[nearestIndex]
                val exec = sortedExecs[nearestIndex]
                
                // Crosshair çizgileri
                drawLine(
                    color = textColor.copy(alpha = 0.8f),
                    start = Offset(np.x, 0f),
                    end = Offset(np.x, chartHeight),
                    strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )
                drawLine(
                    color = textColor.copy(alpha = 0.8f),
                    start = Offset(0f, np.y),
                    end = Offset(chartWidth, np.y),
                    strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )
                
                // Seçili noktanın vurgulanması
                drawCircle(color = lineColor, radius = 6.dp.toPx(), center = np)
                drawCircle(color = axisTagBg, radius = 2.5.dp.toPx(), center = np)
                
                // Fiyat Etiketi (Sağ Eksen)
                val priceLabel = String.format(Locale.US, "%.4f", exec.priceValue)
                val priceLabelLayout = textMeasurer.measure(priceLabel, TextStyle(color = axisTagText, fontSize = 10.sp, fontWeight = FontWeight.Bold))
                drawRoundRect(
                    color = axisTagBg,
                    topLeft = Offset(chartWidth + 4f, np.y - priceLabelLayout.size.height/2f - 6f),
                    size = Size(yAxisWidth - 8f, priceLabelLayout.size.height + 12f),
                    cornerRadius = CornerRadius(6f, 6f)
                )
                drawText(priceLabelLayout, topLeft = Offset(chartWidth + 12f, np.y - priceLabelLayout.size.height/2f))

                // Zaman Etiketi (Alt Eksen)
                val timeLabel = timeFormatter.format(Date(exec.timeMillis)) + " " + dateFormatter.format(Date(exec.timeMillis))
                val timeLabelLayout = textMeasurer.measure(timeLabel, TextStyle(color = axisTagText, fontSize = 10.sp, fontWeight = FontWeight.Bold))
                drawRoundRect(
                    color = axisTagBg,
                    topLeft = Offset(np.x - timeLabelLayout.size.width/2f - 12f, chartHeight + 4f),
                    size = Size(timeLabelLayout.size.width + 24f, xAxisHeight - 12f),
                    cornerRadius = CornerRadius(6f, 6f)
                )
                drawText(timeLabelLayout, topLeft = Offset(np.x - timeLabelLayout.size.width/2f, chartHeight + 14f))
                
                // Yüzen Bilgi Kutusu (Tooltip)
                val typeStr = if (exec.isBuy) "ALIŞ (BUY)" else "SATIŞ (SELL)"
                val typeColor = if (exec.isBuy) buyColor else sellColor
                val qtyStr = RebalanceEngine.formatCryptoQty(exec.qtyValue)
                
                val tooltipTitleLayout = textMeasurer.measure(typeStr, TextStyle(color = typeColor, fontSize = 11.sp, fontWeight = FontWeight.Black))
                val tooltipBodyLayout = textMeasurer.measure(
                    text = "Fiyat: ${priceLabel}\nMiktar: $qtyStr",
                    style = TextStyle(color = tooltipTextCol, fontSize = 11.sp, lineHeight = 16.sp)
                )
                
                val ttWidth = maxOf(tooltipTitleLayout.size.width, tooltipBodyLayout.size.width) + 32f
                val ttHeight = tooltipTitleLayout.size.height + tooltipBodyLayout.size.height + 32f
                
                val ttX = if (np.x < chartWidth / 2) np.x + 30f else np.x - ttWidth - 30f
                val ttY = 20f
                
                drawRoundRect(
                    color = tooltipBg.copy(alpha = 0.95f),
                    topLeft = Offset(ttX, ttY),
                    size = Size(ttWidth, ttHeight),
                    cornerRadius = CornerRadius(12f, 12f),
                    style = Fill
                )
                
                drawRoundRect(
                    color = gridColor,
                    topLeft = Offset(ttX, ttY),
                    size = Size(ttWidth, ttHeight),
                    cornerRadius = CornerRadius(12f, 12f),
                    style = Stroke(width = 2f)
                )

                drawText(tooltipTitleLayout, topLeft = Offset(ttX + 16f, ttY + 12f))
                drawText(tooltipBodyLayout, topLeft = Offset(ttX + 16f, ttY + 16f + tooltipTitleLayout.size.height))
            }
        }
    }
}
