package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Borsadan (Bybit Spot) çekilen gerçekleşmiş (Trade/Filled) işlemleri saklayan Room tablosu.
 * execId veya orderId + execTime bazında unique index ile mükerrer kayıtlar engellenir.
 */
@Entity(
    tableName = "exchange_trades",
    indices = [
        Index(value = ["execId"], unique = true),
        Index(value = ["orderId"]),
        Index(value = ["symbol"]),
        Index(value = ["timeMillis"])
    ]
)
data class ExchangeTradeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val execId: String, // Bybit'teki benzersiz trade/fill ID'si (veya fallback)
    val orderId: String,
    val orderLinkId: String = "",
    val symbol: String, // "MNTUSDT" vb.
    val side: String, // "Buy" or "Sell"
    val orderPrice: Double,
    val orderQty: Double,
    val orderType: String = "",
    val execPrice: Double,
    val execQty: Double,
    val execValue: Double,
    val execFee: Double = 0.0,
    val feeRate: Double = 0.0,
    val timeMillis: Long,
    val isMaker: Boolean = false,
    val syncedAt: Long = System.currentTimeMillis()
) {
    val isBuy: Boolean get() = side.equals("Buy", ignoreCase = true)
    val isSell: Boolean get() = side.equals("Sell", ignoreCase = true)
    val totalValue: Double get() = if (execValue > 0.0) execValue else (execPrice * execQty)
}
