package com.example.data.remote.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class BybitApiResponse<T>(
    @Json(name = "retCode") val retCode: Int = 0,
    @Json(name = "retMsg") val retMsg: String = "",
    @Json(name = "result") val result: T? = null,
    @Json(name = "time") val time: Long = 0
) {
    val isSuccess: Boolean get() = retCode == 0
}

@JsonClass(generateAdapter = true)
data class WalletBalanceResult(
    @Json(name = "list") val list: List<WalletAccount> = emptyList()
)

@JsonClass(generateAdapter = true)
data class WalletAccount(
    @Json(name = "accountType") val accountType: String = "",
    @Json(name = "totalEquity") val totalEquity: String = "0",
    @Json(name = "totalWalletBalance") val totalWalletBalance: String = "0",
    @Json(name = "coin") val coin: List<CoinBalance> = emptyList()
)

@JsonClass(generateAdapter = true)
data class CoinBalance(
    @Json(name = "coin") val coin: String = "",
    @Json(name = "equity") val equity: String = "0",
    @Json(name = "walletBalance") val walletBalance: String = "0",
    @Json(name = "availableToWithdraw") val availableToWithdraw: String = "0",
    @Json(name = "availableToBorrow") val availableToBorrow: String = "0",
    @Json(name = "totalOrderIM") val totalOrderIM: String = "0"
) {
    val balanceValue: Double get() = walletBalance.toDoubleOrNull() ?: equity.toDoubleOrNull() ?: 0.0
    val availableValue: Double get() = availableToWithdraw.toDoubleOrNull() ?: balanceValue
}

@JsonClass(generateAdapter = true)
data class TickersResult(
    @Json(name = "category") val category: String = "spot",
    @Json(name = "list") val list: List<SpotTicker> = emptyList()
)

@JsonClass(generateAdapter = true)
data class SpotTicker(
    @Json(name = "symbol") val symbol: String = "",
    @Json(name = "bid1Price") val bid1Price: String = "0",
    @Json(name = "bid1Size") val bid1Size: String = "0",
    @Json(name = "ask1Price") val ask1Price: String = "0",
    @Json(name = "ask1Size") val ask1Size: String = "0",
    @Json(name = "lastPrice") val lastPrice: String = "0",
    @Json(name = "prevPrice24h") val prevPrice24h: String = "0",
    @Json(name = "price24hPcnt") val price24hPcnt: String = "0",
    @Json(name = "highPrice24h") val highPrice24h: String = "0",
    @Json(name = "lowPrice24h") val lowPrice24h: String = "0",
    @Json(name = "volume24h") val volume24h: String = "0",
    @Json(name = "turnover24h") val turnover24h: String = "0"
) {
    val currentPrice: Double get() = lastPrice.toDoubleOrNull() ?: 0.0
    val changePercent24h: Double get() = (price24hPcnt.toDoubleOrNull() ?: 0.0) * 100.0
}

@JsonClass(generateAdapter = true)
data class CreateOrderRequest(
    @Json(name = "category") val category: String = "spot",
    @Json(name = "symbol") val symbol: String = "MNTUSDT",
    @Json(name = "side") val side: String, // "Buy" or "Sell"
    @Json(name = "orderType") val orderType: String, // "Limit" or "Market"
    @Json(name = "qty") val qty: String,
    @Json(name = "price") val price: String? = null,
    @Json(name = "timeInForce") val timeInForce: String = "GTC",
    @Json(name = "orderLinkId") val orderLinkId: String? = null
)

@JsonClass(generateAdapter = true)
data class ServerTimeResult(
    @Json(name = "timeSecond") val timeSecond: String = "",
    @Json(name = "timeNano") val timeNano: String = ""
)

@JsonClass(generateAdapter = true)
data class CreateOrderResult(
    @Json(name = "orderId") val orderId: String = "",
    @Json(name = "orderLinkId") val orderLinkId: String = ""
)

@JsonClass(generateAdapter = true)
data class CancelOrderRequest(
    @Json(name = "category") val category: String = "spot",
    @Json(name = "symbol") val symbol: String = "MNTUSDT",
    @Json(name = "orderId") val orderId: String? = null,
    @Json(name = "orderLinkId") val orderLinkId: String? = null
)

@JsonClass(generateAdapter = true)
data class CancelAllOrdersRequest(
    @Json(name = "category") val category: String = "spot",
    @Json(name = "symbol") val symbol: String = "MNTUSDT"
)

@JsonClass(generateAdapter = true)
data class CancelOrderResult(
    @Json(name = "orderId") val orderId: String = "",
    @Json(name = "orderLinkId") val orderLinkId: String = ""
)

@JsonClass(generateAdapter = true)
data class OpenOrdersResult(
    @Json(name = "list") val list: List<BybitOrderDto> = emptyList(),
    @Json(name = "nextPageCursor") val nextPageCursor: String = ""
)

@JsonClass(generateAdapter = true)
data class ExecutionListResult(
    @Json(name = "category") val category: String = "spot",
    @Json(name = "list") val list: List<BybitExecutionDto> = emptyList(),
    @Json(name = "nextPageCursor") val nextPageCursor: String = ""
)

@JsonClass(generateAdapter = true)
data class BybitExecutionDto(
    @Json(name = "symbol") val symbol: String = "",
    @Json(name = "orderId") val orderId: String = "",
    @Json(name = "orderLinkId") val orderLinkId: String = "",
    @Json(name = "side") val side: String = "", // "Buy" or "Sell"
    @Json(name = "orderPrice") val orderPrice: String = "0",
    @Json(name = "orderQty") val orderQty: String = "0",
    @Json(name = "orderType") val orderType: String = "",
    @Json(name = "execId") val execId: String = "",
    @Json(name = "execPrice") val execPrice: String = "0",
    @Json(name = "execQty") val execQty: String = "0",
    @Json(name = "execType") val execType: String = "",
    @Json(name = "execValue") val execValue: String = "0",
    @Json(name = "execFee") val execFee: String = "0",
    @Json(name = "feeRate") val feeRate: String = "0",
    @Json(name = "feeCurrency") val feeCurrency: String = "",
    @Json(name = "execTime") val execTime: String = "0",
    @Json(name = "isMaker") val isMaker: Boolean = false
) {
    val priceValue: Double get() = execPrice.toDoubleOrNull() ?: orderPrice.toDoubleOrNull() ?: 0.0
    val qtyValue: Double get() = execQty.toDoubleOrNull() ?: orderQty.toDoubleOrNull() ?: 0.0
    val totalValue: Double get() = execValue.toDoubleOrNull() ?: (priceValue * qtyValue)
    val feeValue: Double get() = execFee.toDoubleOrNull() ?: 0.0
    val timeMillis: Long get() = execTime.toLongOrNull() ?: 0L
    val isBuy: Boolean get() = side.equals("Buy", ignoreCase = true)
    val isSell: Boolean get() = side.equals("Sell", ignoreCase = true)
}

data class TradeAnalysisResult(
    val symbol: String = "MNTUSDT",
    val daysRange: Int = 365,
    val dateRangeLabel: String = "",
    val totalBuyQty: Double = 0.0,
    val totalBuyValue: Double = 0.0,
    val avgBuyPrice: Double = 0.0,
    val buyTradeCount: Int = 0,
    val totalSellQty: Double = 0.0,
    val totalSellValue: Double = 0.0,
    val avgSellPrice: Double = 0.0,
    val sellTradeCount: Int = 0,
    val priceDifference: Double = 0.0,
    val profitPercentage: Double = 0.0,
    val netQty: Double = 0.0,
    val totalFee: Double = 0.0,
    val executions: List<BybitExecutionDto> = emptyList(),
    val fetchedAt: Long = System.currentTimeMillis(),
    val symbolBreakdown: Map<String, TradeAnalysisResult> = emptyMap()
) {
    val isMultiSymbol: Boolean get() = symbolBreakdown.isNotEmpty()
    val symbolBreakdownsList: List<TradeAnalysisResult> get() = symbolBreakdown.values.sortedByDescending { it.totalBuyValue + it.totalSellValue }

    // Eşleşen alım-satım miktarı (Arbitraj / Alınıp satılmış olan net hacim)
    val matchedQty: Double get() = minOf(totalBuyQty, totalSellQty).coerceAtLeast(0.0)

    // Eşleşen hacmin ortalama alış maliyeti (USDT)
    val matchedBuyCost: Double get() = if (isMultiSymbol) {
        symbolBreakdown.values.sumOf { it.matchedBuyCost }
    } else {
        matchedQty * avgBuyPrice
    }

    // Eşleşen hacmin ortalama satış hasılatı (USDT)
    val matchedSellRevenue: Double get() = if (isMultiSymbol) {
        symbolBreakdown.values.sumOf { it.matchedSellRevenue }
    } else {
        matchedQty * avgSellPrice
    }

    // Gerçekleşen Brüt Kâr (USDT) = Eşleşen Miktar * (Ortalama Satış Fiyatı - Ortalama Alış Fiyatı)
    val grossProfitUsdt: Double get() = if (isMultiSymbol) {
        symbolBreakdown.values.sumOf { it.grossProfitUsdt }
    } else if (avgBuyPrice > 0.0 && avgSellPrice > 0.0) {
        matchedQty * (avgSellPrice - avgBuyPrice)
    } else {
        0.0
    }

    // Gerçekleşen Net Kâr (Komisyon Düşülmüş USDT)
    val netProfitUsdt: Double get() = if (isMultiSymbol) {
        symbolBreakdown.values.sumOf { it.netProfitUsdt }
    } else {
        grossProfitUsdt - totalFee
    }

    // Net Kâr Oranı (% ROI)
    val netProfitPercentage: Double get() = if (isMultiSymbol) {
        val totalCost = symbolBreakdown.values.sumOf { it.matchedBuyCost }
        if (totalCost > 0.0) (netProfitUsdt / totalCost) * 100.0 else 0.0
    } else if (matchedBuyCost > 0.0) {
        (netProfitUsdt / matchedBuyCost) * 100.0
    } else {
        0.0
    }

    // Kalan varlığın ortalama alış fiyatından maliyet değeri (USDT)
    val remainingInventoryCost: Double get() = if (isMultiSymbol) {
        symbolBreakdown.values.sumOf { it.remainingInventoryCost }
    } else if (netQty > 0.0 && avgBuyPrice > 0.0) {
        netQty * avgBuyPrice
    } else {
        0.0
    }

    // Varlık birimi (Örn: MNT, BTC, SOL)
    val baseAsset: String get() {
        val s = symbol.uppercase().trim()
        return when {
            s == "ALL" || s.contains("TÜM") -> "PORTFÖY"
            s.endsWith("USDT") -> s.removeSuffix("USDT")
            s.endsWith("USDC") -> s.removeSuffix("USDC")
            s.contains("/") -> s.substringBefore("/")
            else -> s.ifBlank { "MNT" }
        }
    }
}

@JsonClass(generateAdapter = true)
data class BybitOrderDto(
    @Json(name = "orderId") val orderId: String = "",
    @Json(name = "orderLinkId") val orderLinkId: String = "",
    @Json(name = "symbol") val symbol: String = "",
    @Json(name = "price") val price: String = "0",
    @Json(name = "qty") val qty: String = "0",
    @Json(name = "side") val side: String = "", // "Buy" or "Sell"
    @Json(name = "orderType") val orderType: String = "",
    @Json(name = "orderStatus") val orderStatus: String = "", // "New", "PartiallyFilled", "Filled", "Cancelled", "Rejected"
    @Json(name = "cumExecQty") val cumExecQty: String = "0",
    @Json(name = "cumExecValue") val cumExecValue: String = "0",
    @Json(name = "avgPrice") val avgPrice: String = "0",
    @Json(name = "createdTime") val createdTime: String = "0",
    @Json(name = "updatedTime") val updatedTime: String = "0"
) {
    val priceValue: Double get() = price.toDoubleOrNull() ?: 0.0
    val qtyValue: Double get() = qty.toDoubleOrNull() ?: 0.0
    val filledQtyValue: Double get() = cumExecQty.toDoubleOrNull() ?: 0.0
    val avgPriceValue: Double get() = avgPrice.toDoubleOrNull() ?: priceValue
    val isFilled: Boolean get() = orderStatus.equals("Filled", ignoreCase = true)
    val isCancelled: Boolean get() = orderStatus.equals("Cancelled", ignoreCase = true) || orderStatus.equals("Deactivated", ignoreCase = true)
    val isActive: Boolean get() = orderStatus.equals("New", ignoreCase = true) || orderStatus.equals("PartiallyFilled", ignoreCase = true)
    val createdTimeMillis: Long get() = createdTime.toLongOrNull() ?: 0L
    val updatedTimeMillis: Long get() = updatedTime.toLongOrNull() ?: 0L
}
