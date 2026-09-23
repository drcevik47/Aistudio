package com.example.bot

import com.example.data.local.entity.ExchangeTradeEntity
import com.example.data.local.entity.OrderEntity
import com.example.data.remote.model.BybitExecutionDto
import com.example.data.remote.model.TradeAnalysisResult
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

data class PortfolioAnalysis(
    val usdtBalance: Double,
    val baseCoinBalance: Double,
    val currentPrice: Double,
    val baseValueUsdt: Double,
    val totalEquityUsdt: Double,
    val usdtPercent: Double,
    val basePercent: Double,
    val isBalanced5050: Boolean,
    val requiredAction: RebalanceAction,
    val deltaBase: Double,
    val deltaUsdt: Double,
    val description: String
)

enum class RebalanceAction {
    BALANCED,
    BUY_BASE,
    SELL_BASE
}

data class GridOrdersPlan(
    val basePrice: Double,
    val stepPercent: Double,
    val sellLimitPrice: Double,
    val sellBaseQty: Double,
    val sellUsdtValue: Double,
    val buyLimitPrice: Double,
    val buyBaseQty: Double,
    val buyUsdtValue: Double,
    val isValid: Boolean,
    val validationMessage: String = "",
    val postSellUsdt: Double = 0.0,
    val postSellBaseValue: Double = 0.0,
    val postBuyUsdt: Double = 0.0,
    val postBuyBaseValue: Double = 0.0
)

object RebalanceEngine {

    /**
     * Analyzes the Unified Trading Account balances and calculates 50/50 split status.
     */
    fun analyzePortfolio(
        usdtBalance: Double,
        baseCoinBalance: Double,
        currentPrice: Double,
        tolerancePercent: Double = 0.8 // 49.2% - 50.8% considered balanced
    ): PortfolioAnalysis {
        if (currentPrice <= 0.0) {
            return PortfolioAnalysis(
                usdtBalance = usdtBalance,
                baseCoinBalance = baseCoinBalance,
                currentPrice = currentPrice,
                baseValueUsdt = 0.0,
                totalEquityUsdt = usdtBalance,
                usdtPercent = 100.0,
                basePercent = 0.0,
                isBalanced5050 = false,
                requiredAction = RebalanceAction.BALANCED,
                deltaBase = 0.0,
                deltaUsdt = 0.0,
                description = "Fiyat bilgisi bekleniyor"
            )
        }

        val baseValueUsdt = baseCoinBalance * currentPrice
        val totalEquity = usdtBalance + baseValueUsdt

        if (totalEquity <= 0.0) {
            return PortfolioAnalysis(
                usdtBalance = 0.0,
                baseCoinBalance = 0.0,
                currentPrice = currentPrice,
                baseValueUsdt = 0.0,
                totalEquityUsdt = 0.0,
                usdtPercent = 0.0,
                basePercent = 0.0,
                isBalanced5050 = true,
                requiredAction = RebalanceAction.BALANCED,
                deltaBase = 0.0,
                deltaUsdt = 0.0,
                description = "Hesapta USDT veya BASE bakiyesi bulunamadı"
            )
        }

        val usdtPercent = (usdtBalance / totalEquity) * 100.0
        val basePercent = (baseValueUsdt / totalEquity) * 100.0

        val targetEquityHalf = totalEquity * 0.5
        val targetBaseQty = targetEquityHalf / currentPrice

        // deltaBase = targetBaseQty - currentBase = (usdtBalance - baseValueUsdt) / (2 * currentPrice)
        val rawDeltaBase = targetBaseQty - baseCoinBalance
        val deltaBaseAbs = abs(rawDeltaBase)
        val deltaUsdt = deltaBaseAbs * currentPrice

        val diffFrom50 = abs(usdtPercent - 50.0)
        val isBalanced = diffFrom50 <= tolerancePercent || deltaUsdt < 5.0

        val (action, desc) = when {
            isBalanced -> {
                RebalanceAction.BALANCED to "Portföy dengeli (%${format2(usdtPercent)} USDT / %${format2(basePercent)} BASE)"
            }
            rawDeltaBase > 0 -> {
                RebalanceAction.BUY_BASE to "USDT fazlalığı var. %50 eşitlemek için ${formatCryptoQty(deltaBaseAbs)} BASE alınmalı (~${format2(deltaUsdt)} USDT harcanacak)"
            }
            else -> {
                RebalanceAction.SELL_BASE to "BASE fazlalığı var. %50 eşitlemek için ${formatCryptoQty(deltaBaseAbs)} BASE satılmalı (~${format2(deltaUsdt)} USDT alınacak)"
            }
        }

        return PortfolioAnalysis(
            usdtBalance = usdtBalance,
            baseCoinBalance = baseCoinBalance,
            currentPrice = currentPrice,
            baseValueUsdt = baseValueUsdt,
            totalEquityUsdt = totalEquity,
            usdtPercent = usdtPercent,
            basePercent = basePercent,
            isBalanced5050 = isBalanced,
            requiredAction = action,
            deltaBase = deltaBaseAbs,
            deltaUsdt = deltaUsdt,
            description = desc
        )
    }

    /**
     * Calculates +2% and -2% grid limit orders to maintain exact 50/50 portfolio balance upon execution.
     *
     * Mathematical Derivation:
     * Consider current balanced equity at basePrice:
     *   E_0 = usdtBalance + baseCoinBalance * basePrice
     * Target half equity = E_0 / 2
     *
     * When price moves by stepRatio (+s for sell, -s for buy):
     * - At sellPrice = basePrice * (1 + s):
     *   The portfolio's total equity becomes E_sell = usdtBalance + baseCoinBalance * sellPrice
     *   The target 50% USDT is Target_USDT = E_sell / 2
     *   To reach this target, we must sell enough BASE so that resulting USDT equals Target_USDT:
     *     usdtBalance + sellQty * sellPrice = E_sell / 2
     *     => sellQty * sellPrice = E_sell / 2 - usdtBalance
     *   If the base portfolio is 50/50 (usdtBalance == baseCoinBalance * basePrice == E_0 / 2):
     *     sellUsdtValue = (E_0 / 2) * (s / (2 + s)) ≈ (E_0 / 4) * s
     *   For general portfolios, we compute the target 50% rebalance amount at the trigger step:
     *     targetUsdtTrade = (baseCoinBalance * basePrice * stepRatio) / 2
     *     sellBaseQty = targetUsdtTrade / sellPrice
     *     buyBaseQty = targetUsdtTrade / buyPrice
     */
    fun stepToDecimals(stepStr: String?): Int {
        if (stepStr.isNullOrBlank()) return 2
        val dotIndex = stepStr.indexOf('.')
        if (dotIndex < 0) return 0
        val frac = stepStr.substring(dotIndex + 1).trimEnd('0')
        return frac.length
    }

    fun roundToStep(value: Double, stepStr: String?, mode: RoundingMode = RoundingMode.HALF_UP): Double {
        if (value <= 0.0) return 0.0
        if (stepStr.isNullOrBlank()) return value
        return try {
            val step = BigDecimal(stepStr)
            if (step <= BigDecimal.ZERO) return value
            val valBd = BigDecimal.valueOf(value)
            val numSteps = valBd.divide(step, 0, mode)
            numSteps.multiply(step).toDouble()
        } catch (e: Exception) {
            value
        }
    }

    fun formatWithStep(value: Double, stepStr: String?, mode: RoundingMode = RoundingMode.HALF_UP): String {
        if (value <= 0.0) return "0"
        if (stepStr.isNullOrBlank()) {
            return String.format(Locale.US, "%.4f", value).trimEnd('0').trimEnd('.')
        }
        return try {
            val step = BigDecimal(stepStr).stripTrailingZeros()
            if (step <= BigDecimal.ZERO) return value.toString()
            val valBd = BigDecimal.valueOf(value)
            val numSteps = valBd.divide(step, 0, mode)
            val rounded = numSteps.multiply(step)
            val scale = step.scale().coerceAtLeast(0)
            if (scale > 0) {
                rounded.setScale(scale, mode).toPlainString()
            } else {
                rounded.setScale(0, mode).toPlainString()
            }
        } catch (e: Exception) {
            String.format(Locale.US, "%.4f", value).trimEnd('0').trimEnd('.')
        }
    }

    fun calculateGridOrders(
        usdtBalance: Double,
        baseCoinBalance: Double,
        basePrice: Double,
        stepPercent: Double = 2.0,
        qtyPrecision: Int? = null,
        pricePrecision: Int? = null,
        tickSize: String? = null,
        lotStep: String? = null,
        minOrderAmt: Double = 5.0,
        minOrderQty: Double? = null,
        maxOrderQty: Double? = null
    ): GridOrdersPlan {
        if (basePrice <= 0.0 || usdtBalance <= 0.0 || baseCoinBalance <= 0.0) {
            return GridOrdersPlan(
                basePrice = basePrice,
                stepPercent = stepPercent,
                sellLimitPrice = 0.0,
                sellBaseQty = 0.0,
                sellUsdtValue = 0.0,
                buyLimitPrice = 0.0,
                buyBaseQty = 0.0,
                buyUsdtValue = 0.0,
                isValid = false,
                validationMessage = "Yetersiz bakiye veya geçersiz fiyat"
            )
        }

        val stepRatio = stepPercent / 100.0
        val effectivePricePrecision = pricePrecision ?: when {
            basePrice >= 100.0 -> 2
            basePrice >= 1.0 -> 4
            basePrice >= 0.01 -> 5
            else -> 6
        }

        val tickBd = tickSize?.takeIf { it.isNotBlank() }?.let { runCatching { BigDecimal(it).stripTrailingZeros() }.getOrNull() }
        val sellPriceBd = if (tickBd != null && tickBd > BigDecimal.ZERO) {
            val raw = BigDecimal.valueOf(basePrice).multiply(BigDecimal.ONE.add(BigDecimal.valueOf(stepRatio)))
            raw.divide(tickBd, 0, RoundingMode.HALF_UP).multiply(tickBd)
        } else {
            val priceFactor = Math.pow(10.0, effectivePricePrecision.toDouble())
            BigDecimal.valueOf(kotlin.math.round(basePrice * (1.0 + stepRatio) * priceFactor) / priceFactor)
        }

        val buyPriceBd = if (tickBd != null && tickBd > BigDecimal.ZERO) {
            val raw = BigDecimal.valueOf(basePrice).multiply(BigDecimal.ONE.subtract(BigDecimal.valueOf(stepRatio)))
            raw.divide(tickBd, 0, RoundingMode.HALF_UP).multiply(tickBd)
        } else {
            val priceFactor = Math.pow(10.0, effectivePricePrecision.toDouble())
            BigDecimal.valueOf(kotlin.math.round(basePrice * (1.0 - stepRatio) * priceFactor) / priceFactor)
        }

        val sellPrice = sellPriceBd.toDouble()
        val buyPrice = buyPriceBd.toDouble()

        // Calculate total equity evaluated at the base price
        val totalEquityAtBase = usdtBalance + (baseCoinBalance * basePrice)
        val halfEquityAtBase = totalEquityAtBase / 2.0

        // In a 50/50 grid, target rebalance amount at the trigger step
        val targetUsdtTrade = (halfEquityAtBase * stepRatio) / 2.0

        val qtyDecimals = qtyPrecision ?: when {
            basePrice >= 10000.0 -> 6
            basePrice >= 1000.0 -> 5
            basePrice >= 100.0 -> 4
            basePrice >= 10.0 -> 3
            basePrice >= 1.0 -> 2
            else -> 1
        }
        val stepBd = lotStep?.takeIf { it.isNotBlank() }?.let { runCatching { BigDecimal(it).stripTrailingZeros() }.getOrNull() }
        val factor = Math.pow(10.0, qtyDecimals.toDouble())
        val defaultMinBaseQty = stepBd?.toDouble() ?: (1.0 / factor)
        val effectiveMinBaseQty = maxOf(minOrderQty ?: 0.0, defaultMinBaseQty)

        // 1. SELL LIMIT ORDER (+stepPercent)
        var sellBaseQtyBd = if (stepBd != null && stepBd > BigDecimal.ZERO) {
            val raw = BigDecimal.valueOf(targetUsdtTrade).divide(sellPriceBd, 12, RoundingMode.DOWN)
            raw.divide(stepBd, 0, RoundingMode.FLOOR).multiply(stepBd)
        } else {
            BigDecimal.valueOf(kotlin.math.floor((targetUsdtTrade / sellPrice) * factor) / factor)
        }

        val maxSellAllowed = baseCoinBalance * 0.999
        val maxSellBd = if (stepBd != null && stepBd > BigDecimal.ZERO) {
            BigDecimal.valueOf(maxSellAllowed).divide(stepBd, 0, RoundingMode.FLOOR).multiply(stepBd)
        } else {
            BigDecimal.valueOf(kotlin.math.floor(maxSellAllowed * factor) / factor)
        }
        if (sellBaseQtyBd > maxSellBd) {
            sellBaseQtyBd = maxSellBd
        }

        if (maxOrderQty != null && maxOrderQty > 0.0) {
            val maxOrderBd = BigDecimal.valueOf(maxOrderQty)
            if (sellBaseQtyBd > maxOrderBd) {
                sellBaseQtyBd = maxOrderBd
            }
        }

        val sellBaseQty = sellBaseQtyBd.toDouble()
        val sellUsdtValue = sellBaseQty * sellPrice
        val postSellUsdt = usdtBalance + sellUsdtValue
        val postSellBaseValue = (baseCoinBalance - sellBaseQty) * sellPrice

        // 2. BUY LIMIT ORDER (-stepPercent)
        var buyBaseQtyBd = if (stepBd != null && stepBd > BigDecimal.ZERO) {
            val raw = BigDecimal.valueOf(targetUsdtTrade).divide(buyPriceBd, 12, RoundingMode.DOWN)
            raw.divide(stepBd, 0, RoundingMode.FLOOR).multiply(stepBd)
        } else {
            BigDecimal.valueOf(kotlin.math.floor((targetUsdtTrade / buyPrice) * factor) / factor)
        }

        val maxUsdt = usdtBalance * 0.999
        var buyUsdtValue = (buyBaseQtyBd.multiply(buyPriceBd)).toDouble()
        if (buyUsdtValue > maxUsdt) {
            buyBaseQtyBd = if (stepBd != null && stepBd > BigDecimal.ZERO) {
                val raw = BigDecimal.valueOf(maxUsdt).divide(buyPriceBd, 12, RoundingMode.DOWN)
                raw.divide(stepBd, 0, RoundingMode.FLOOR).multiply(stepBd)
            } else {
                BigDecimal.valueOf(kotlin.math.floor((maxUsdt / buyPrice) * factor) / factor)
            }
            buyUsdtValue = (buyBaseQtyBd.multiply(buyPriceBd)).toDouble()
        }

        if (maxOrderQty != null && maxOrderQty > 0.0) {
            val maxOrderBd = BigDecimal.valueOf(maxOrderQty)
            if (buyBaseQtyBd > maxOrderBd) {
                buyBaseQtyBd = maxOrderBd
                buyUsdtValue = (buyBaseQtyBd.multiply(buyPriceBd)).toDouble()
            }
        }

        val buyBaseQty = buyBaseQtyBd.toDouble()
        val postBuyUsdt = usdtBalance - buyUsdtValue
        val postBuyBaseValue = (baseCoinBalance + buyBaseQty) * buyPrice

        val isSellValid = sellBaseQty >= effectiveMinBaseQty && sellUsdtValue >= minOrderAmt && sellBaseQty <= baseCoinBalance
        val isBuyValid = buyBaseQty >= effectiveMinBaseQty && buyUsdtValue >= minOrderAmt && buyUsdtValue <= usdtBalance

        val isValid = isSellValid && isBuyValid
        val msg = when {
            sellBaseQty < effectiveMinBaseQty ->
                "Satış miktarı yetersiz (${formatCryptoQty(sellBaseQty)} < Min: ${formatCryptoQty(effectiveMinBaseQty)})"
            buyBaseQty < effectiveMinBaseQty ->
                "Alış miktarı yetersiz (${formatCryptoQty(buyBaseQty)} < Min: ${formatCryptoQty(effectiveMinBaseQty)})"
            sellBaseQty > baseCoinBalance || baseCoinBalance * sellPrice < minOrderAmt ->
                "Yetersiz coin bakiyesi (Min: ${minOrderAmt} USDT değerinde coin gerekir, Eldeki: ${formatCryptoQty(baseCoinBalance)})"
            buyUsdtValue > usdtBalance || usdtBalance < minOrderAmt ->
                "Yetersiz USDT bakiyesi (Min: ${minOrderAmt} USDT gerekir, Eldeki: ${format2(usdtBalance)} USDT)"
            sellUsdtValue < minOrderAmt || buyUsdtValue < minOrderAmt ->
                "Minimum spot emir tutarı ${minOrderAmt} USDT'dir (Satış: ${format2(sellUsdtValue)}, Alış: ${format2(buyUsdtValue)} USDT)."
            else -> "Hazır: +%$stepPercent (${format4(sellPrice)}) -> ${formatCryptoQty(sellBaseQty)} sat (~${format2(sellUsdtValue)} USDT) | -%$stepPercent (${format4(buyPrice)}) -> ${formatCryptoQty(buyBaseQty)} al (~${format2(buyUsdtValue)} USDT)"
        }

        return GridOrdersPlan(
            basePrice = basePrice,
            stepPercent = stepPercent,
            sellLimitPrice = sellPrice,
            sellBaseQty = sellBaseQty,
            sellUsdtValue = sellUsdtValue,
            buyLimitPrice = buyPrice,
            buyBaseQty = buyBaseQty,
            buyUsdtValue = buyUsdtValue,
            isValid = isValid,
            validationMessage = msg,
            postSellUsdt = postSellUsdt,
            postSellBaseValue = postSellBaseValue,
            postBuyUsdt = postBuyUsdt,
            postBuyBaseValue = postBuyBaseValue
        )
    }

    fun format2(value: Double): String = String.format(Locale.US, "%.2f", value)
    fun format4(value: Double): String = String.format(Locale.US, "%.4f", value)

    /**
     * Calculates trade analysis for a given list of executions.
     * When multiple symbols are present or symbol is "ALL"/blank:
     * - Computes a separate, accurate TradeAnalysisResult for EACH individual traded coin/symbol.
     * - Collects them into `symbolBreakdown` Map<String, TradeAnalysisResult>.
     * - Avoids cross-coin unit corruption (e.g. adding BTC qty to BASE qty).
     */
    fun calculateTradeAnalysis(
        symbol: String?,
        executions: List<BybitExecutionDto>,
        daysRange: Int = 0,
        dateRangeLabel: String = ""
    ): TradeAnalysisResult {
        if (executions.isEmpty()) {
            return TradeAnalysisResult(
                symbol = symbol?.takeIf { it.isNotBlank() } ?: "BASEUSDT",
                daysRange = daysRange,
                dateRangeLabel = dateRangeLabel,
                fetchedAt = System.currentTimeMillis()
            )
        }

        // Distinct symbols present in executions
        val distinctSymbols = executions
            .map { it.symbol.trim().uppercase().ifBlank { "BASEUSDT" } }
            .distinct()
            .sorted()

        val breakdown = mutableMapOf<String, TradeAnalysisResult>()

        for (sym in distinctSymbols) {
            val symExecs = executions.filter {
                it.symbol.trim().uppercase().ifBlank { "BASEUSDT" } == sym
            }
            val buyExecs = symExecs.filter { it.isBuy }
            val sellExecs = symExecs.filter { it.isSell }

            val totalBuyQty = buyExecs.sumOf { it.qtyValue }
            val totalBuyValue = buyExecs.sumOf { it.totalValue }
            val avgBuyPrice = if (totalBuyQty > 0.0) totalBuyValue / totalBuyQty else 0.0

            val totalSellQty = sellExecs.sumOf { it.qtyValue }
            val totalSellValue = sellExecs.sumOf { it.totalValue }
            val avgSellPrice = if (totalSellQty > 0.0) totalSellValue / totalSellQty else 0.0

            val priceDiff = if (avgBuyPrice > 0.0 && avgSellPrice > 0.0) avgSellPrice - avgBuyPrice else 0.0
            val profitPcnt = if (avgBuyPrice > 0.0 && avgSellPrice > 0.0) (priceDiff / avgBuyPrice) * 100.0 else 0.0
            val netQty = totalBuyQty - totalSellQty

            val matchedQty = minOf(totalBuyQty, totalSellQty).coerceAtLeast(0.0)
            
            val totalBuyFeeUsdt = buyExecs.sumOf { calculateExecutionFeeUsdt(it, avgBuyPrice, avgSellPrice) }
            val totalSellFeeUsdt = sellExecs.sumOf { calculateExecutionFeeUsdt(it, avgBuyPrice, avgSellPrice) }
            val totalFee = totalBuyFeeUsdt + totalSellFeeUsdt

            // Gerçekleşen (Realized) Komisyon: Sadece eşleşen hacim (arbitraj/kâr edilen kısım) kadarı net kârdan düşülür.
            val realizedBuyFeeUsdt = if (totalBuyQty > 0) (matchedQty / totalBuyQty) * totalBuyFeeUsdt else 0.0
            val realizedSellFeeUsdt = if (totalSellQty > 0) (matchedQty / totalSellQty) * totalSellFeeUsdt else 0.0
            val realizedFeeUsdt = realizedBuyFeeUsdt + realizedSellFeeUsdt

            breakdown[sym] = TradeAnalysisResult(
                symbol = sym,
                daysRange = daysRange,
                dateRangeLabel = dateRangeLabel,
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
                realizedFeeUsdt = realizedFeeUsdt,
                executions = symExecs.sortedByDescending { it.timeMillis },
                fetchedAt = System.currentTimeMillis()
            )
        }

        val isAllRequest = symbol.isNullOrBlank() ||
                symbol.equals("ALL", ignoreCase = true) ||
                symbol.equals("TÜM", ignoreCase = true) ||
                symbol.contains("TÜM SPOT", ignoreCase = true)

        if (!isAllRequest) {
            val reqSym = symbol!!.trim().uppercase()
            val specific = breakdown[reqSym]
            return if (specific != null) {
                specific.copy(symbolBreakdown = breakdown)
            } else {
                TradeAnalysisResult(
                    symbol = reqSym,
                    daysRange = daysRange,
                    dateRangeLabel = dateRangeLabel,
                    fetchedAt = System.currentTimeMillis(),
                    symbolBreakdown = breakdown
                )
            }
        }

        // If only 1 symbol exists, return that single symbol result directly with breakdown
        if (breakdown.size == 1) {
            val single = breakdown.values.first()
            return single.copy(symbolBreakdown = breakdown)
        }

        // Multi-symbol portfolio calculation:
        // Do NOT sum raw quantities across different coins! Sum USDT values and fees!
        val allBuyExecs = executions.filter { it.isBuy }
        val allSellExecs = executions.filter { it.isSell }
        val totalBuyValue = breakdown.values.sumOf { it.totalBuyValue }
        val totalSellValue = breakdown.values.sumOf { it.totalSellValue }
        val totalFee = breakdown.values.sumOf { it.totalFee }
        val totalRealizedFee = breakdown.values.sumOf { it.realizedFeeUsdt }

        val totalMatchedCost = breakdown.values.sumOf { it.matchedBuyCost }
        val totalNetProfit = breakdown.values.sumOf { it.netProfitUsdt }
        val overallRoi = if (totalMatchedCost > 0.0) (totalNetProfit / totalMatchedCost) * 100.0 else 0.0

        return TradeAnalysisResult(
            symbol = "ALL",
            daysRange = daysRange,
            dateRangeLabel = dateRangeLabel,
            totalBuyQty = 0.0,
            totalBuyValue = totalBuyValue,
            avgBuyPrice = 0.0,
            buyTradeCount = allBuyExecs.size,
            totalSellQty = 0.0,
            totalSellValue = totalSellValue,
            avgSellPrice = 0.0,
            sellTradeCount = allSellExecs.size,
            priceDifference = 0.0,
            profitPercentage = overallRoi,
            netQty = 0.0,
            totalFee = totalFee,
            realizedFeeUsdt = totalRealizedFee,
            executions = executions.sortedByDescending { it.timeMillis },
            fetchedAt = System.currentTimeMillis(),
            symbolBreakdown = breakdown
        )
    }

    fun computeLiveTradeAnalysis(
        orders: List<OrderEntity>,
        exchangeTrades: List<ExchangeTradeEntity> = emptyList(),
        apiAnalysis: TradeAnalysisResult? = null,
        symbol: String? = "BASEUSDT",
        startTimestamp: Long? = null,
        endTimestamp: Long? = null
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
                    symbol = trade.symbol.ifBlank { "BASEUSDT" },
                    side = trade.side,
                    orderPrice = trade.orderPrice.toString(),
                    orderQty = trade.orderQty.toString(),
                    orderType = trade.orderType,
                    execPrice = trade.execPrice.toString(),
                    execQty = trade.execQty.toString(),
                    execValue = trade.execValue.toString(),
                    execFee = trade.execFee.toString(),
                    feeRate = trade.feeRate.toString(),
                    feeCurrency = trade.feeCurrency,
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
                        symbol = order.symbol.ifBlank { "BASEUSDT" },
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

        val rangeLabel = if (startTimestamp != null && startTimestamp > 0L && endTimestamp != null && endTimestamp > 0L) {
            val startStr = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(startTimestamp))
            val endStr = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(endTimestamp))
            "$startStr - $endStr"
        } else if (startTimestamp != null && startTimestamp > 0L) {
            val dateStr = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(startTimestamp))
            "$dateStr Tarihinden İtibaren"
        } else if (endTimestamp != null && endTimestamp > 0L) {
            val endStr = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(endTimestamp))
            "$endStr Tarihine Kadar"
        } else {
            "Kayıtlı Tüm Geçmiş"
        }
        val calculatedDaysRange = if (startTimestamp != null && startTimestamp > 0L) {
            val endRef = if (endTimestamp != null && endTimestamp > 0L) endTimestamp else System.currentTimeMillis()
            ((endRef - startTimestamp) / (24L * 3600L * 1000L)).toInt().coerceAtLeast(1)
        } else {
            0
        }

        val activeExecutions = allExecutions.filter { exec ->
            val startTimeMatch = startTimestamp == null || startTimestamp <= 0L || exec.timeMillis >= startTimestamp
            val endTimeMatch = endTimestamp == null || endTimestamp <= 0L || exec.timeMillis <= endTimestamp
            startTimeMatch && endTimeMatch
        }

        if (activeExecutions.isNotEmpty()) {
            return calculateTradeAnalysis(
                symbol = symbol,
                executions = activeExecutions,
                daysRange = calculatedDaysRange,
                dateRangeLabel = rangeLabel
            )
        }

        // Fallback to apiAnalysis if provided and no activeExecutions found (and no date filter was set)
        if (startTimestamp == null && endTimestamp == null && apiAnalysis != null && apiAnalysis.executions.isNotEmpty()) {
            return apiAnalysis
        }

        return TradeAnalysisResult(
            symbol = symbol ?: "BASEUSDT",
            daysRange = calculatedDaysRange,
            dateRangeLabel = rangeLabel,
            fetchedAt = System.currentTimeMillis()
        )
    }

    /**
     * Bybit Spot işlem komisyonunu USDT karşılığına güvenli ve doğru şekilde dönüştürür.
     * Bybit Spot kuralları:
     * - Satışta komisyon her zaman USDT (quote) cinsinden kesilir.
     * - Alışta Bybit varsayılan olarak alınan coinden veya BASE/USDT indiriminden kesebilir.
     * - Ancak kullanıcı USDT veya BASE ile ödediyse ya da execFee değeri zaten USDT ise,
     *   bunu tekrar BTC fiyatıyla çarpmak astronomik (ör. $3,241) hatalı komisyonlara yol açar.
     */
    fun calculateExecutionFeeUsdt(
        exec: BybitExecutionDto,
        avgBuyPrice: Double = 0.0,
        avgSellPrice: Double = 0.0
    ): Double {
        val rawFee = Math.abs(exec.feeValue)
        if (rawFee == 0.0) return 0.0

        val execPrice = if (exec.priceValue > 0.0) exec.priceValue
        else if (exec.isBuy && avgBuyPrice > 0.0) avgBuyPrice
        else if (exec.isSell && avgSellPrice > 0.0) avgSellPrice
        else 0.0
        val execQty = exec.qtyValue
        val execTotalValue = if (exec.totalValue > 0.0) exec.totalValue else (execPrice * execQty)

        val feeCurr = exec.feeCurrency.trim().uppercase(Locale.US)
        val sym = exec.symbol.trim().uppercase(Locale.US)
        val baseAsset = if (sym.endsWith("USDT")) sym.removeSuffix("USDT") else sym

        // 1. Durum: feeCurrency USDT, USDC veya USD ise -> Değer doğrudan USDT'dir
        if (feeCurr == "USDT" || feeCurr == "USDC" || feeCurr == "USD") {
            return rawFee
        }

        // 2. Durum: Satış işlemi (SELL) -> Bybit Spot'ta satışta komisyon USDT'den kesilir
        if (exec.isSell) {
            if (feeCurr.isEmpty() || feeCurr == "USDT") {
                return rawFee
            }
        }

        // 3. Durum: Alış işlemi (BUY)
        // Eğer rawFee, işlem miktarından büyükse veya ona çok yakınsa (ör. 0.0008 BTC alımında fee 0.036),
        // bu değer kesinlikle BTC olamaz; zaten USDT tutarıdır! Asla BTC fiyatıyla çarpılmaz!
        if (execQty > 0.0 && rawFee > (execQty * 0.02)) {
            return rawFee
        }

        // 4. Durum: feeCurrency açıkça baseAsset (ör. "BTC", "BASE") ise veya boş olup rawFee makul bir coin miktarıysa:
        if (feeCurr == baseAsset || (feeCurr.isEmpty() && exec.isBuy && execQty > 0.0 && rawFee <= (execQty * 0.02))) {
            val converted = if (execPrice > 0.0) rawFee * execPrice else rawFee
            // Güvenlik tavanı: Bir spot işlemde komisyon işlem tutarının %2'sinden büyük olamaz!
            if (execTotalValue > 0.0 && converted > (execTotalValue * 0.02)) {
                return rawFee
            }
            return converted
        }

        // 5. Fallback güvenlik denetimi:
        // Eğer rawFee işlem hacminin %5'inden fazlaysa ve makul bir feeRate varsa (ör. 0.001)
        val feeRate = Math.abs(exec.feeRate.toDoubleOrNull() ?: 0.0)
        if (execTotalValue > 0.0 && rawFee > (execTotalValue * 0.05) && feeRate > 0.0 && feeRate < 0.05) {
            return execTotalValue * feeRate
        }

        return rawFee
    }

    /**
     * Kripto para miktarlarını (BTC, ETH, BASE vb.) sıfır basamağı kaybı olmadan akıllıca formatlar.
     * Örneğin 0.0008 BTC -> "0.0008", 0.0025 BTC -> "0.0025", 1500 BASE -> "1,500.00"
     */
    fun formatCryptoQty(qty: Double): String {
        val absQty = Math.abs(qty)
        val sign = if (qty < 0) "-" else ""
        return when {
            absQty == 0.0 -> "0.00"
            absQty < 0.0001 -> String.format(Locale.US, "%s%.8f", sign, absQty).trimEnd('0').trimEnd('.')
            absQty < 1.0 -> String.format(Locale.US, "%s%.6f", sign, absQty).trimEnd('0').trimEnd('.')
            absQty < 100.0 -> String.format(Locale.US, "%s%.4f", sign, absQty).trimEnd('0').trimEnd('.')
            absQty < 1000.0 -> String.format(Locale.US, "%s%.2f", sign, absQty)
            else -> String.format(Locale.US, "%s%,.2f", sign, absQty)
        }
    }

    fun formatCryptoQtySigned(qty: Double): String {
        val clean = formatCryptoQty(Math.abs(qty))
        return if (qty > 0) "+$clean" else if (qty < 0) "-$clean" else clean
    }
}
