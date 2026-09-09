package com.example.bot

import com.example.data.local.entity.ExchangeTradeEntity
import com.example.data.local.entity.OrderEntity
import com.example.data.remote.model.BybitExecutionDto
import com.example.data.remote.model.TradeAnalysisResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

data class PortfolioAnalysis(
    val usdtBalance: Double,
    val mntBalance: Double,
    val currentPrice: Double,
    val mntValueUsdt: Double,
    val totalEquityUsdt: Double,
    val usdtPercent: Double,
    val mntPercent: Double,
    val isBalanced5050: Boolean,
    val requiredAction: RebalanceAction,
    val deltaMnt: Double,
    val deltaUsdt: Double,
    val description: String
)

enum class RebalanceAction {
    BALANCED,
    BUY_MNT,
    SELL_MNT
}

data class GridOrdersPlan(
    val basePrice: Double,
    val stepPercent: Double,
    val sellLimitPrice: Double,
    val sellMntQty: Double,
    val sellUsdtValue: Double,
    val buyLimitPrice: Double,
    val buyMntQty: Double,
    val buyUsdtValue: Double,
    val isValid: Boolean,
    val validationMessage: String = "",
    val postSellUsdt: Double = 0.0,
    val postSellMntValue: Double = 0.0,
    val postBuyUsdt: Double = 0.0,
    val postBuyMntValue: Double = 0.0
)

object RebalanceEngine {

    /**
     * Analyzes the Unified Trading Account balances and calculates 50/50 split status.
     */
    fun analyzePortfolio(
        usdtBalance: Double,
        mntBalance: Double,
        currentPrice: Double,
        tolerancePercent: Double = 0.8 // 49.2% - 50.8% considered balanced
    ): PortfolioAnalysis {
        if (currentPrice <= 0.0) {
            return PortfolioAnalysis(
                usdtBalance = usdtBalance,
                mntBalance = mntBalance,
                currentPrice = currentPrice,
                mntValueUsdt = 0.0,
                totalEquityUsdt = usdtBalance,
                usdtPercent = 100.0,
                mntPercent = 0.0,
                isBalanced5050 = false,
                requiredAction = RebalanceAction.BALANCED,
                deltaMnt = 0.0,
                deltaUsdt = 0.0,
                description = "Fiyat bilgisi bekleniyor"
            )
        }

        val mntValueUsdt = mntBalance * currentPrice
        val totalEquity = usdtBalance + mntValueUsdt

        if (totalEquity <= 0.0) {
            return PortfolioAnalysis(
                usdtBalance = 0.0,
                mntBalance = 0.0,
                currentPrice = currentPrice,
                mntValueUsdt = 0.0,
                totalEquityUsdt = 0.0,
                usdtPercent = 0.0,
                mntPercent = 0.0,
                isBalanced5050 = true,
                requiredAction = RebalanceAction.BALANCED,
                deltaMnt = 0.0,
                deltaUsdt = 0.0,
                description = "Hesapta USDT veya MNT bakiyesi bulunamadı"
            )
        }

        val usdtPercent = (usdtBalance / totalEquity) * 100.0
        val mntPercent = (mntValueUsdt / totalEquity) * 100.0

        val targetEquityHalf = totalEquity * 0.5
        val targetMnt = targetEquityHalf / currentPrice

        // deltaMnt = targetMnt - currentMnt = (usdtBalance - mntValueUsdt) / (2 * currentPrice)
        val rawDeltaMnt = targetMnt - mntBalance
        val deltaMntAbs = abs(rawDeltaMnt)
        val deltaUsdt = deltaMntAbs * currentPrice

        val diffFrom50 = abs(usdtPercent - 50.0)
        val isBalanced = diffFrom50 <= tolerancePercent || deltaUsdt < 5.0

        val (action, desc) = when {
            isBalanced -> {
                RebalanceAction.BALANCED to "Portföy dengeli (%${format2(usdtPercent)} USDT / %${format2(mntPercent)} MNT)"
            }
            rawDeltaMnt > 0 -> {
                RebalanceAction.BUY_MNT to "USDT fazlalığı var. %50 eşitlemek için ${format2(deltaMntAbs)} MNT alınmalı (~${format2(deltaUsdt)} USDT harcanacak)"
            }
            else -> {
                RebalanceAction.SELL_MNT to "MNT fazlalığı var. %50 eşitlemek için ${format2(deltaMntAbs)} MNT satılmalı (~${format2(deltaUsdt)} USDT alınacak)"
            }
        }

        return PortfolioAnalysis(
            usdtBalance = usdtBalance,
            mntBalance = mntBalance,
            currentPrice = currentPrice,
            mntValueUsdt = mntValueUsdt,
            totalEquityUsdt = totalEquity,
            usdtPercent = usdtPercent,
            mntPercent = mntPercent,
            isBalanced5050 = isBalanced,
            requiredAction = action,
            deltaMnt = deltaMntAbs,
            deltaUsdt = deltaUsdt,
            description = desc
        )
    }

    /**
     * Calculates +2% and -2% grid limit orders to maintain exact 50/50 portfolio balance upon execution.
     *
     * Mathematical Derivation:
     * Consider current balanced equity at basePrice:
     *   E_0 = usdtBalance + mntBalance * basePrice
     * Target half equity = E_0 / 2
     *
     * When price moves by stepRatio (+s for sell, -s for buy):
     * - At sellPrice = basePrice * (1 + s):
     *   The portfolio's total equity becomes E_sell = usdtBalance + mntBalance * sellPrice
     *   The target 50% USDT is Target_USDT = E_sell / 2
     *   To reach this target, we must sell enough MNT so that resulting USDT equals Target_USDT:
     *     usdtBalance + sellQty * sellPrice = E_sell / 2
     *     => sellQty * sellPrice = E_sell / 2 - usdtBalance
     *   If the base portfolio is 50/50 (usdtBalance == mntBalance * basePrice == E_0 / 2):
     *     sellUsdtValue = (E_0 / 2) * (s / (2 + s)) ≈ (E_0 / 4) * s
     *   For general portfolios, we compute the target 50% rebalance amount at the trigger step:
     *     targetUsdtTrade = (mntBalance * basePrice * stepRatio) / 2
     *     sellMntQty = targetUsdtTrade / sellPrice
     *     buyMntQty = targetUsdtTrade / buyPrice
     */
    fun calculateGridOrders(
        usdtBalance: Double,
        mntBalance: Double,
        basePrice: Double,
        stepPercent: Double = 2.0
    ): GridOrdersPlan {
        if (basePrice <= 0.0 || usdtBalance <= 0.0 || mntBalance <= 0.0) {
            return GridOrdersPlan(
                basePrice = basePrice,
                stepPercent = stepPercent,
                sellLimitPrice = 0.0,
                sellMntQty = 0.0,
                sellUsdtValue = 0.0,
                buyLimitPrice = 0.0,
                buyMntQty = 0.0,
                buyUsdtValue = 0.0,
                isValid = false,
                validationMessage = "Yetersiz bakiye veya geçersiz fiyat"
            )
        }

        val stepRatio = stepPercent / 100.0
        val sellPrice = basePrice * (1.0 + stepRatio)
        val buyPrice = basePrice * (1.0 - stepRatio)

        // Calculate total equity evaluated at the base price
        val totalEquityAtBase = usdtBalance + (mntBalance * basePrice)
        val halfEquityAtBase = totalEquityAtBase / 2.0

        // In a 50/50 grid, when price moves by stepRatio (e.g. 2%),
        // the theoretical infinitesimal rebalance size is:
        val targetUsdtTrade = (halfEquityAtBase * stepRatio) / 2.0

        // Bybit Spot minimum order amount is 5.0 USDT
        val minUsdtAmt = 5.0

        // 1. SELL LIMIT ORDER (+stepPercent)
        // Sell targetUsdtTrade worth of MNT at sellPrice
        var sellMntQty = targetUsdtTrade / sellPrice
        sellMntQty = kotlin.math.floor(sellMntQty * 100.0) / 100.0
        if (sellMntQty > mntBalance * 0.99) {
            sellMntQty = kotlin.math.floor(mntBalance * 0.99 * 100.0) / 100.0
        }
        val sellUsdtValue = sellMntQty * sellPrice
        val postSellUsdt = usdtBalance + sellUsdtValue
        val postSellMntValue = (mntBalance - sellMntQty) * sellPrice

        // 2. BUY LIMIT ORDER (-stepPercent)
        // Buy targetUsdtTrade worth of MNT at buyPrice
        var buyMntQty = targetUsdtTrade / buyPrice
        buyMntQty = kotlin.math.floor(buyMntQty * 100.0) / 100.0
        var buyUsdtValue = buyMntQty * buyPrice
        if (buyUsdtValue > usdtBalance * 0.99) {
            val maxUsdt = usdtBalance * 0.99
            buyMntQty = kotlin.math.floor((maxUsdt / buyPrice) * 100.0) / 100.0
            buyUsdtValue = buyMntQty * buyPrice
        }
        val postBuyUsdt = usdtBalance - buyUsdtValue
        val postBuyMntValue = (mntBalance + buyMntQty) * buyPrice

        val isSellValid = sellMntQty >= 0.01 && sellUsdtValue >= minUsdtAmt && sellMntQty <= mntBalance
        val isBuyValid = buyMntQty >= 0.01 && buyUsdtValue >= minUsdtAmt && buyUsdtValue <= usdtBalance

        val isValid = isSellValid && isBuyValid
        val msg = when {
            sellMntQty > mntBalance || mntBalance * sellPrice < minUsdtAmt ->
                "Yetersiz MNT bakiyesi (Min: ${format2(minUsdtAmt)} USDT değerinde MNT gerekir, Eldeki: ${format4(mntBalance)} MNT)"
            buyUsdtValue > usdtBalance || usdtBalance < minUsdtAmt ->
                "Yetersiz USDT bakiyesi (Min: ${format2(minUsdtAmt)} USDT gerekir, Eldeki: ${format2(usdtBalance)} USDT)"
            sellUsdtValue < minUsdtAmt || buyUsdtValue < minUsdtAmt ->
                "Bybit minimum spot emir tutarı 5.0 USDT'dir. Hesaptaki bakiyeler (USDT ve MNT) en az 5.2 USDT olmalıdır."
            else -> "Hazır: +%$stepPercent (${format4(sellPrice)}) -> ${format4(sellMntQty)} MNT sat (~${format2(sellUsdtValue)} USDT) | -%$stepPercent (${format4(buyPrice)}) -> ${format4(buyMntQty)} MNT al (~${format2(buyUsdtValue)} USDT)"
        }

        return GridOrdersPlan(
            basePrice = basePrice,
            stepPercent = stepPercent,
            sellLimitPrice = sellPrice,
            sellMntQty = sellMntQty,
            sellUsdtValue = sellUsdtValue,
            buyLimitPrice = buyPrice,
            buyMntQty = buyMntQty,
            buyUsdtValue = buyUsdtValue,
            isValid = isValid,
            validationMessage = msg,
            postSellUsdt = postSellUsdt,
            postSellMntValue = postSellMntValue,
            postBuyUsdt = postBuyUsdt,
            postBuyMntValue = postBuyMntValue
        )
    }

    fun format2(value: Double): String = String.format(Locale.US, "%.2f", value)
    fun format4(value: Double): String = String.format(Locale.US, "%.4f", value)

    fun computeLiveTradeAnalysis(
        orders: List<OrderEntity>,
        exchangeTrades: List<ExchangeTradeEntity> = emptyList(),
        apiAnalysis: TradeAnalysisResult? = null,
        symbol: String = "MNTUSDT",
        startTimestamp: Long? = null
    ): TradeAnalysisResult {
        // Collect all distinct registered executions from exchangeTrades and orders
        val allExecutions = mutableListOf<BybitExecutionDto>()
        val knownOrderIds = exchangeTrades.map { it.orderId }.toHashSet()
        val knownExecIds = exchangeTrades.map { it.execId }.toHashSet()

        exchangeTrades.forEach { trade ->
            allExecutions.add(
                BybitExecutionDto(
                    execId = trade.execId,
                    orderId = trade.orderId,
                    orderLinkId = trade.orderLinkId,
                    symbol = trade.symbol,
                    side = trade.side,
                    orderPrice = trade.orderPrice.toString(),
                    orderQty = trade.orderQty.toString(),
                    orderType = trade.orderType,
                    execPrice = trade.execPrice.toString(),
                    execQty = trade.execQty.toString(),
                    execValue = trade.execValue.toString(),
                    execFee = trade.execFee.toString(),
                    feeRate = trade.feeRate.toString(),
                    execTime = trade.timeMillis.toString(),
                    isMaker = trade.isMaker
                )
            )
        }

        // Add any filled orders from the local orders table that might not be in exchangeTrades yet
        val filledOrders = orders.filter { it.status.equals("Filled", ignoreCase = true) }
        filledOrders.forEach { order ->
            val isKnown = knownOrderIds.contains(order.orderId) ||
                    knownExecIds.contains(order.orderId) ||
                    knownExecIds.contains("fill_${order.orderId}")
            if (!isKnown) {
                val p = if (order.avgPrice > 0.0) order.avgPrice else order.price
                val q = if (order.filledQty > 0.0) order.filledQty else order.qty
                val v = p * q
                allExecutions.add(
                    BybitExecutionDto(
                        execId = "order_${order.orderId}",
                        orderId = order.orderId,
                        orderLinkId = order.orderLinkId,
                        symbol = order.symbol,
                        side = order.side,
                        orderPrice = p.toString(),
                        orderQty = q.toString(),
                        orderType = order.orderType,
                        execPrice = p.toString(),
                        execQty = q.toString(),
                        execValue = v.toString(),
                        execFee = (v * 0.001).toString(),
                        execTime = order.timestamp.toString(),
                        isMaker = true
                    )
                )
            }
        }

        val rangeLabel = if (startTimestamp != null && startTimestamp > 0L) {
            val dateStr = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(startTimestamp))
            "$dateStr Tarihinden İtibaren"
        } else {
            "Kayıtlı Tüm Geçmiş"
        }
        val calculatedDaysRange = if (startTimestamp != null && startTimestamp > 0L) {
            ((System.currentTimeMillis() - startTimestamp) / (24L * 3600L * 1000L)).toInt().coerceAtLeast(1)
        } else {
            0
        }

        val activeExecutions = allExecutions.filter { exec ->
            val timeMatch = startTimestamp == null || startTimestamp <= 0L || exec.timeMillis >= startTimestamp
            val symbolMatch = symbol.isBlank() || symbol.equals("ALL", ignoreCase = true) || exec.symbol.equals(symbol, ignoreCase = true)
            timeMatch && symbolMatch
        }

        if (activeExecutions.isNotEmpty()) {
            val buyExecs = activeExecutions.filter { it.isBuy }
            val sellExecs = activeExecutions.filter { it.isSell }

            val totalBuyQty = buyExecs.sumOf { it.qtyValue }
            val totalBuyValue = buyExecs.sumOf { it.totalValue }
            val avgBuyPrice = if (totalBuyQty > 0.0) totalBuyValue / totalBuyQty else 0.0

            val totalSellQty = sellExecs.sumOf { it.qtyValue }
            val totalSellValue = sellExecs.sumOf { it.totalValue }
            val avgSellPrice = if (totalSellQty > 0.0) totalSellValue / totalSellQty else 0.0

            val priceDiff = if (avgBuyPrice > 0.0 && avgSellPrice > 0.0) avgSellPrice - avgBuyPrice else 0.0
            val profitPcnt = if (avgBuyPrice > 0.0 && avgSellPrice > 0.0) (priceDiff / avgBuyPrice) * 100.0 else 0.0
            val netQty = totalBuyQty - totalSellQty

            val totalFee = activeExecutions.sumOf { exec ->
                if (exec.isBuy) {
                    val p = if (exec.priceValue > 0.0) exec.priceValue else avgBuyPrice
                    exec.feeValue * p
                } else {
                    exec.feeValue
                }
            }

            return TradeAnalysisResult(
                symbol = symbol,
                daysRange = calculatedDaysRange,
                dateRangeLabel = rangeLabel,
                totalBuyQty = totalBuyQty,
                totalBuyValue = totalBuyValue,
                avgBuyPrice = avgBuyPrice,
                buyTradeCount = buyExecs.size,
                totalSellQty = totalSellQty,
                totalSellValue = totalSellValue,
                avgSellPrice = avgSellPrice,
                sellTradeCount = sellExecs.size,
                priceDifference = priceDiff,
                profitPercentage = profitPcnt,
                netQty = netQty,
                totalFee = totalFee,
                executions = activeExecutions.sortedByDescending { it.timeMillis },
                fetchedAt = System.currentTimeMillis()
            )
        }

        // Fallback to apiAnalysis if provided and no activeExecutions found (and no date filter was set)
        if (startTimestamp == null && apiAnalysis != null && apiAnalysis.executions.isNotEmpty()) {
            return apiAnalysis
        }

        return TradeAnalysisResult(
            symbol = symbol,
            daysRange = calculatedDaysRange,
            dateRangeLabel = rangeLabel,
            fetchedAt = System.currentTimeMillis()
        )
    }
}
