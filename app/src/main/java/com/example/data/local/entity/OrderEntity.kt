package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "orders")
data class OrderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val orderId: String,
    val orderLinkId: String = "",
    val symbol: String = "MNTUSDT",
    val side: String, // "Buy" or "Sell"
    val orderType: String, // "Limit" or "Market"
    val price: Double,
    val qty: Double,
    val status: String, // "New", "Filled", "PartiallyFilled", "Cancelled", "Rejected"
    val filledQty: Double = 0.0,
    val avgPrice: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis(),
    val triggerReason: String = "", // "InitialRebalance", "GridStepUpSell", "GridStepDownBuy", "Manual"
    val pnlEstimate: Double = 0.0
)
