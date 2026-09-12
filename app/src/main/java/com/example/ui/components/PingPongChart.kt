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
import androidx.compose.ui.unit.dp
import com.example.data.remote.model.BybitExecutionDto
import com.example.ui.theme.MinimalBg
import com.example.ui.theme.MinimalPrimary
import com.example.ui.theme.MinimalSuccess
import com.example.ui.theme.MinimalError

@Composable
fun PingPongChart(executions: List<BybitExecutionDto>, modifier: Modifier = Modifier) {
    if (executions.isEmpty()) return

    // Sort executions by time ascending (oldest first)
    val sortedExecs = remember(executions) { executions.sortedBy { it.timeMillis } }
    
    // Extract prices to plot a simple line
    val prices = sortedExecs.map { it.priceValue }
    if (prices.isEmpty()) return
    
    val minPrice = prices.minOrNull() ?: 0.0
    val maxPrice = prices.maxOrNull() ?: 0.0
    val range = maxPrice - minPrice
    
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MinimalBg)
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        val width = size.width
        val height = size.height
        
        if (prices.size == 1) return@Canvas
        
        val stepX = width / (prices.size - 1).coerceAtLeast(1).toFloat()
        
        val path = Path()
        val points = mutableListOf<Offset>()
        
        prices.forEachIndexed { index, price ->
            val x = index * stepX
            val normalizedY = if (range == 0.0) 0.5f else ((maxPrice - price) / range).toFloat()
            val y = normalizedY * height
            
            val offset = Offset(x, y)
            points.add(offset)
            
            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        
        // Draw the main price line
        drawPath(
            path = path,
            color = MinimalPrimary.copy(alpha = 0.5f),
            style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
        
        // Draw dots for buys and sells
        sortedExecs.forEachIndexed { index, exec ->
            val p = points[index]
            if (exec.isBuy) {
                // Bought at dip -> Green dot
                drawCircle(
                    color = MinimalSuccess,
                    radius = 3.dp.toPx(),
                    center = p
                )
            } else if (exec.isSell) {
                // Sold at peak -> Red dot
                drawCircle(
                    color = MinimalError,
                    radius = 3.dp.toPx(),
                    center = p
                )
            }
        }
    }
}
