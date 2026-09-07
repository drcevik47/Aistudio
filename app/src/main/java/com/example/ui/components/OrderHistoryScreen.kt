package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bot.RebalanceEngine
import com.example.data.local.entity.OrderEntity
import com.example.ui.theme.MinimalError
import com.example.ui.theme.MinimalErrorDark
import com.example.ui.theme.MinimalErrorLight
import com.example.ui.theme.MinimalPrimary
import com.example.ui.theme.MinimalPrimaryDark
import com.example.ui.theme.MinimalPrimaryLight
import com.example.ui.theme.MinimalSecondary
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun OrderHistoryScreen(
    orders: List<OrderEntity>,
    onClearOrders: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf("ALL") } // ALL, BUY, SELL, FILLED
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()) }

    val filteredOrders = remember(orders, selectedFilter) {
        when (selectedFilter) {
            "BUY" -> orders.filter { it.side.equals("Buy", ignoreCase = true) }
            "SELL" -> orders.filter { it.side.equals("Sell", ignoreCase = true) }
            "FILLED" -> orders.filter { it.status.equals("Filled", ignoreCase = true) }
            else -> orders
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("order_history_screen")
    ) {
        // Header & Clear Action
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MinimalPrimaryLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ReceiptLong,
                        contentDescription = null,
                        tint = MinimalPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Emir & Ticaret Geçmişi",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MinimalTextPrimary,
                        letterSpacing = (-0.2).sp,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = "Toplam ${orders.size} kayıtlı işlem",
                        fontSize = 12.sp,
                        color = MinimalTextSecondary,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            if (orders.isNotEmpty()) {
                IconButton(
                    onClick = onClearOrders,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MinimalSurfaceElevated)
                        .border(1.dp, MinimalSurfaceBorder, CircleShape)
                        .testTag("clear_orders_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Geçmişi Temizle",
                        tint = MinimalTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                "ALL" to "Tümü (${orders.size})",
                "BUY" to "Alışlar",
                "SELL" to "Satışlar",
                "FILLED" to "Gerçekleşenler"
            ).forEach { (key, label) ->
                FilterChip(
                    selected = selectedFilter == key,
                    onClick = { selectedFilter = key },
                    shape = RoundedCornerShape(999.dp),
                    label = {
                        Text(
                            label,
                            fontSize = 11.sp,
                            fontWeight = if (selectedFilter == key) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MinimalPrimary,
                        selectedLabelColor = Color.White,
                        containerColor = MinimalSurface,
                        labelColor = MinimalTextSecondary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selectedFilter == key,
                        borderColor = MinimalSurfaceBorder,
                        selectedBorderColor = MinimalPrimary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (filteredOrders.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MinimalSurfaceElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = MinimalTextMuted,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Henüz işlem kaydı bulunamadı",
                        color = MinimalTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "Bot çalıştıkça ve alım/satım yaptıkça tüm emirler burada listelenir.",
                        color = MinimalTextSecondary,
                        fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredOrders, key = { it.id }) { order ->
                    OrderItemCard(order = order, dateFormat = dateFormat)
                }
            }
        }
    }
}

@Composable
private fun OrderItemCard(
    order: OrderEntity,
    dateFormat: SimpleDateFormat
) {
    val isBuy = order.side.equals("Buy", ignoreCase = true)
    val sideColor = if (isBuy) MinimalSuccessDark else MinimalError
    val sideBg = if (isBuy) MinimalSuccessLight else MinimalErrorLight

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MinimalSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MinimalSurfaceBorderLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(sideBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isBuy) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                    contentDescription = null,
                    tint = sideColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .padding(end = 6.dp)
                    ) {
                        Text(
                            text = "${if (isBuy) "ALIŞ" else "SATIŞ"} (MNT/USDT)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = sideColor,
                            maxLines = 1,
                            softWrap = false
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MinimalSurfaceElevated
                        ) {
                            Text(
                                text = order.orderType,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MinimalTextSecondary,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Status Badge
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = when (order.status) {
                            "Filled" -> MinimalSuccessLight
                            "Cancelled" -> MinimalSurfaceElevated
                            else -> MinimalPrimaryLight
                        }
                    ) {
                        Text(
                            text = when (order.status) {
                                "Filled" -> "Gerçekleşti"
                                "Cancelled" -> "İptal Edildi"
                                "New" -> "Açık Emir"
                                else -> order.status
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            color = when (order.status) {
                                "Filled" -> MinimalSuccessDark
                                "Cancelled" -> MinimalTextMuted
                                else -> MinimalPrimaryDark
                            },
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Miktar: ${RebalanceEngine.format4(order.qty)} MNT",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MinimalTextPrimary,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = "Fiyat: ${if (order.price > 0) "$${RebalanceEngine.format4(order.price)}" else "Market"}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MinimalPrimary,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val trigger = when (order.triggerReason) {
                        "InitialRebalance" -> "%50 Dengeleme"
                        "GridStepUpSell" -> "Grid Satış"
                        "GridStepDownBuy" -> "Grid Alış"
                        else -> order.triggerReason.ifBlank { "Manuel" }
                    }
                    Text(
                        text = "Tetik: $trigger",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = MinimalSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    )
                    Text(
                        text = dateFormat.format(Date(order.timestamp)),
                        fontSize = 10.sp,
                        color = MinimalTextMuted,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}
