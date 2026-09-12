package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.model.BybitExecutionDto
import com.example.ui.theme.*

@Composable
fun PingPongChart(executions: List<BybitExecutionDto>, modifier: Modifier = Modifier) {
    if (executions.isEmpty()) return

    val sortedExecs = remember(executions) { executions.sortedBy { it.timeMillis } }
    val prices = sortedExecs.map { it.priceValue }
    if (prices.isEmpty()) return
    
    val minPrice = prices.minOrNull() ?: 0.0
    val maxPrice = prices.maxOrNull() ?: 0.0
    val range = maxPrice - minPrice
    
    val textMeasurer = rememberTextMeasurer()

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MinimalBg)
            .padding(horizontal = 8.dp, vertical = 12.dp)
    ) {
        val width = size.width
        val height = size.height
        
        // Draw horizontal grid lines
        val gridLineCount = 4
        for (i in 0..gridLineCount) {
            val y = i * (height / gridLineCount)
            drawLine(
                color = MinimalSurfaceBorderLight,
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1f
            )
        }

        // Draw vertical grid lines
        val vGridLineCount = 6
        for (i in 0..vGridLineCount) {
            val x = i * (width / vGridLineCount)
            drawLine(
                color = MinimalSurfaceBorderLight,
                start = Offset(x, 0f),
                end = Offset(x, height),
                strokeWidth = 1f
            )
        }

        // Draw min/max price labels
        drawText(
            textMeasurer = textMeasurer,
            text = String.format("%.4f", maxPrice),
            style = TextStyle(color = MinimalTextMuted, fontSize = 9.sp),
            topLeft = Offset(4f, 0f)
        )
        drawText(
            textMeasurer = textMeasurer,
            text = String.format("%.4f", minPrice),
            style = TextStyle(color = MinimalTextMuted, fontSize = 9.sp),
            topLeft = Offset(4f, height - 12.dp.toPx())
        )

        if (prices.size == 1) return@Canvas
        
        val stepX = width / (prices.size - 1).coerceAtLeast(1).toFloat()
        
        val path = Path()
        val fillPath = Path()
        val points = mutableListOf<Offset>()
        
        prices.forEachIndexed { index, price ->
            val x = index * stepX
            val normalizedY = if (range == 0.0) 0.5f else ((maxPrice - price) / range).toFloat()
            val y = normalizedY * height
            
            val offset = Offset(x, y)
            points.add(offset)
            
            if (index == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, height)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        
        fillPath.lineTo(width, height)
        fillPath.close()

        // Gradient fill under the line
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    MinimalPrimary.copy(alpha = 0.3f),
                    MinimalPrimary.copy(alpha = 0.0f)
                ),
                startY = 0f,
                endY = height
            ),
            style = Fill
        )

        // Draw the smooth line
        drawPath(
            path = path,
            color = MinimalPrimary,
            style = Stroke(
                width = 2.5.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
        
        // Draw transaction dots
        sortedExecs.forEachIndexed { index, exec ->
            val p = points[index]
            if (exec.isBuy) {
                drawCircle(color = MinimalBg, radius = 5.dp.toPx(), center = p)
                drawCircle(color = MinimalSuccess, radius = 3.5.dp.toPx(), center = p)
            } else if (exec.isSell) {
                drawCircle(color = MinimalBg, radius = 5.dp.toPx(), center = p)
                drawCircle(color = MinimalError, radius = 3.5.dp.toPx(), center = p)
            }
        }
    }
}
