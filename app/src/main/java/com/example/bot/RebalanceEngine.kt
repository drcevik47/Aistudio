package com.example.bot

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

        // Bybit Spot minimum order amount is 5.0 USDT (we use 5.15 USDT buffer to avoid rounding rejections).
        val minUsdtAmt = 5.0
        val safeMinTrade = 5.15
        // If portfolio is small (e.g. 50-500 USDT), targetUsdtTrade would be < 5.0 USDT and rejected.
        // We safely clamp effective trade size to safeMinTrade if balance permits.
        val effectiveUsdtTrade = if (targetUsdtTrade < safeMinTrade) {
            safeMinTrade
        } else {
            targetUsdtTrade
        }

        // 1. SELL LIMIT ORDER (+stepPercent)
        // Sell effectiveUsdtTrade worth of MNT at sellPrice
        var sellMntQty = effectiveUsdtTrade / sellPrice
        sellMntQty = kotlin.math.floor(sellMntQty * 100.0) / 100.0
        if (sellMntQty > mntBalance * 0.99) {
            sellMntQty = kotlin.math.floor(mntBalance * 0.99 * 100.0) / 100.0
        }
        val sellUsdtValue = sellMntQty * sellPrice
        val postSellUsdt = usdtBalance + sellUsdtValue
        val postSellMntValue = (mntBalance - sellMntQty) * sellPrice

        // 2. BUY LIMIT ORDER (-stepPercent)
        // Buy effectiveUsdtTrade worth of MNT at buyPrice
        var buyMntQty = effectiveUsdtTrade / buyPrice
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
}
