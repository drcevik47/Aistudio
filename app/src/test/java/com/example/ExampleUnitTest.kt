package com.example

import com.example.bot.RebalanceAction
import com.example.bot.RebalanceEngine
import com.example.data.remote.BybitSigner
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testRebalanceEngine_EqualBalances_IsBalanced() {
        // Price = 1.0, USDT = 100, MNT = 100 -> Total = 200 (50% / 50%)
        val analysis = RebalanceEngine.analyzePortfolio(
            usdtBalance = 100.0,
            mntBalance = 100.0,
            currentPrice = 1.0
        )
        assertTrue(analysis.isBalanced5050)
        assertEquals(RebalanceAction.BALANCED, analysis.requiredAction)
        assertEquals(50.0, analysis.usdtPercent, 0.01)
        assertEquals(50.0, analysis.mntPercent, 0.01)
        assertEquals(0.0, analysis.deltaMnt, 0.01)
    }

    @Test
    fun testRebalanceEngine_USDTExcess_RequiresBuyMNT() {
        // Price = 2.0, USDT = 300, MNT = 50 (value = 100) -> Total = 400
        // Target half = 200 USDT each -> Need 100 MNT total (currently 50) -> BUY 50 MNT (100 USDT)
        val analysis = RebalanceEngine.analyzePortfolio(
            usdtBalance = 300.0,
            mntBalance = 50.0,
            currentPrice = 2.0
        )
        assertFalse(analysis.isBalanced5050)
        assertEquals(RebalanceAction.BUY_MNT, analysis.requiredAction)
        assertEquals(75.0, analysis.usdtPercent, 0.01)
        assertEquals(25.0, analysis.mntPercent, 0.01)
        assertEquals(50.0, analysis.deltaMnt, 0.01)
        assertEquals(100.0, analysis.deltaUsdt, 0.01)
    }

    @Test
    fun testRebalanceEngine_MNTExcess_RequiresSellMNT() {
        // Price = 2.0, USDT = 100, MNT = 150 (value = 300) -> Total = 400
        // Target half = 200 USDT each -> Need 100 MNT total (currently 150) -> SELL 50 MNT (100 USDT)
        val analysis = RebalanceEngine.analyzePortfolio(
            usdtBalance = 100.0,
            mntBalance = 150.0,
            currentPrice = 2.0
        )
        assertFalse(analysis.isBalanced5050)
        assertEquals(RebalanceAction.SELL_MNT, analysis.requiredAction)
        assertEquals(25.0, analysis.usdtPercent, 0.01)
        assertEquals(75.0, analysis.mntPercent, 0.01)
        assertEquals(50.0, analysis.deltaMnt, 0.01)
        assertEquals(100.0, analysis.deltaUsdt, 0.01)
    }

    @Test
    fun testRebalanceEngine_CalculateGridOrders_Step2Percent() {
        val basePrice = 1.0
        val usdt = 1000.0
        val mnt = 1000.0
        val plan = RebalanceEngine.calculateGridOrders(
            usdtBalance = usdt,
            mntBalance = mnt,
            basePrice = basePrice,
            stepPercent = 2.0
        )

        assertTrue(plan.isValid)
        assertEquals(1.02, plan.sellLimitPrice, 0.0001)
        assertEquals(0.98, plan.buyLimitPrice, 0.0001)
        assertTrue(plan.sellMntQty > 0.0)
        assertTrue(plan.buyMntQty > 0.0)
        // For 1000 USDT and 1000 MNT at 1.0 base (Total = 2000 USDT),
        // at 2% step: target trade is (1000 * 0.02) / 2 = 10.0 USDT
        assertEquals(10.0, plan.sellUsdtValue, 0.01)
        assertEquals(10.0, plan.buyUsdtValue, 0.01)
        assertEquals(1010.0, plan.postSellUsdt, 0.01)
        assertEquals(990.0, plan.postBuyUsdt, 0.01)
    }

    @Test
    fun testRebalanceEngine_SmallBalance_DoesNotDistortMath_MarksInvalidWithMinExplanation() {
        // 100 USDT & 100 MNT gives 1.0 USDT order at 2% step, which is below Bybit's 5 USDT limit
        val plan = RebalanceEngine.calculateGridOrders(
            usdtBalance = 100.0,
            mntBalance = 100.0,
            basePrice = 1.0,
            stepPercent = 2.0
        )
        assertFalse(plan.isValid)
        assertTrue(plan.validationMessage.contains("5.0 USDT"))
        // Check that math was not falsely inflated to 5 USDT
        assertEquals(1.0, plan.sellUsdtValue, 0.05)
        assertEquals(1.0, plan.buyUsdtValue, 0.05)
    }

    @Test
    fun testRebalanceEngine_MathematicalProof_ExecutionAchievesExact5050() {
        // Initial state: 1000 MNT, 1000 USDT at 1.00 USDT price (Total = 2000 USDT, exact 50/50)
        val initialMnt = 1000.0
        val initialUsdt = 1000.0
        val basePrice = 1.0
        val stepPercent = 2.0

        val plan = RebalanceEngine.calculateGridOrders(
            usdtBalance = initialUsdt,
            mntBalance = initialMnt,
            basePrice = basePrice,
            stepPercent = stepPercent
        )

        assertTrue(plan.isValid)
        // Both orders should trade exactly 10 USDT of MNT
        assertEquals(10.0, plan.sellUsdtValue, 0.01)
        assertEquals(10.0, plan.buyUsdtValue, 0.01)

        // Case 1: Price goes UP +2% to sellLimitPrice (1.02) and Sell Order Fills
        val newMntAfterSell = initialMnt - plan.sellMntQty
        val newUsdtAfterSell = initialUsdt + (plan.sellMntQty * plan.sellLimitPrice)
        val mntValueAfterSell = newMntAfterSell * plan.sellLimitPrice
        val totalEquityAfterSell = mntValueAfterSell + newUsdtAfterSell

        // Total equity becomes 1000 + 1000*1.02 = 2020 USDT. Target 50% = 1010 USDT.
        assertEquals(1010.0, newUsdtAfterSell, 0.01)
        assertEquals(1010.0, mntValueAfterSell, 0.05)
        assertEquals(50.0, (mntValueAfterSell / totalEquityAfterSell) * 100.0, 0.05)
        assertEquals(50.0, (newUsdtAfterSell / totalEquityAfterSell) * 100.0, 0.05)

        // Case 2: Price goes DOWN -2% to buyLimitPrice (0.98) and Buy Order Fills
        val newMntAfterBuy = initialMnt + plan.buyMntQty
        val newUsdtAfterBuy = initialUsdt - (plan.buyMntQty * plan.buyLimitPrice)
        val mntValueAfterBuy = newMntAfterBuy * plan.buyLimitPrice
        val totalEquityAfterBuy = mntValueAfterBuy + newUsdtAfterBuy

        // Total equity becomes 1000 + 1000*0.98 = 1980 USDT. Target 50% = 990 USDT.
        assertEquals(990.0, newUsdtAfterBuy, 0.01)
        assertEquals(990.0, mntValueAfterBuy, 0.05)
        assertEquals(50.0, (mntValueAfterBuy / totalEquityAfterBuy) * 100.0, 0.05)
        assertEquals(50.0, (newUsdtAfterBuy / totalEquityAfterBuy) * 100.0, 0.05)
    }

    @Test
    fun testBybitSigner_GeneratesValidHmacSha256() {
        val key = "test_key"
        val secret = "test_secret"
        val timestamp = 1700000000000L
        val recvWindow = "5000"
        val payload = "category=spot&symbol=MNTUSDT"

        val signature = BybitSigner.signRest(
            timestamp = timestamp,
            apiKey = key,
            recvWindow = recvWindow,
            paramStr = payload,
            apiSecret = secret
        )

        assertNotNull(signature)
        assertEquals(64, signature.length) // Hex string of SHA256 has 64 chars
    }

    @Test
    fun testRebalanceEngine_NextCycleUsesLastTradedPriceAsNewBasePrice() {
        // Initial state at Base Price = 1.00 USDT
        val initialMnt = 1000.0
        val initialUsdt = 1000.0
        val basePrice1 = 1.00
        val stepPercent = 2.0

        val cycle1Plan = RebalanceEngine.calculateGridOrders(
            usdtBalance = initialUsdt,
            mntBalance = initialMnt,
            basePrice = basePrice1,
            stepPercent = stepPercent
        )

        // Sell order triggers at executed price = 1.02
        val lastExecutedPrice = cycle1Plan.sellLimitPrice
        assertEquals(1.02, lastExecutedPrice, 0.0001)

        // Balances settle after 1 second
        val settledMnt = initialMnt - cycle1Plan.sellMntQty
        val settledUsdt = initialUsdt + (cycle1Plan.sellMntQty * lastExecutedPrice)

        // New cycle MUST use lastExecutedPrice (1.02) as the new base price
        val cycle2Plan = RebalanceEngine.calculateGridOrders(
            usdtBalance = settledUsdt,
            mntBalance = settledMnt,
            basePrice = lastExecutedPrice, // User requirement: new base price is last executed trade price
            stepPercent = stepPercent
        )

        assertTrue(cycle2Plan.isValid)
        // Next Sell should be 1.02 * 1.02 = 1.0404
        assertEquals(1.02 * 1.02, cycle2Plan.sellLimitPrice, 0.0001)
        // Next Buy should be 1.02 * 0.98 = 0.9996
        assertEquals(1.02 * 0.98, cycle2Plan.buyLimitPrice, 0.0001)
    }

    @Test
    fun testTradeAnalysis_WeightedAveragePriceCalculations() {
        val exec1 = com.example.data.remote.model.BybitExecutionDto(
            symbol = "MNTUSDT",
            side = "Buy",
            orderPrice = "0.50",
            execPrice = "0.50",
            execQty = "100.0",
            execValue = "50.0"
        )
        val exec2 = com.example.data.remote.model.BybitExecutionDto(
            symbol = "MNTUSDT",
            side = "Buy",
            orderPrice = "0.60",
            execPrice = "0.60",
            execQty = "100.0",
            execValue = "60.0"
        )
        val exec3 = com.example.data.remote.model.BybitExecutionDto(
            symbol = "MNTUSDT",
            side = "Sell",
            orderPrice = "0.70",
            execPrice = "0.70",
            execQty = "80.0",
            execValue = "56.0"
        )
        val exec4 = com.example.data.remote.model.BybitExecutionDto(
            symbol = "MNTUSDT",
            side = "Sell",
            orderPrice = "0.80",
            execPrice = "0.80",
            execQty = "120.0",
            execValue = "96.0"
        )

        val executions = listOf(exec1, exec2, exec3, exec4)
        val buyExecs = executions.filter { it.isBuy }
        val sellExecs = executions.filter { it.isSell }

        val totalBuyQty = buyExecs.sumOf { it.qtyValue }
        val totalBuyValue = buyExecs.sumOf { it.totalValue }
        val avgBuyPrice = totalBuyValue / totalBuyQty

        val totalSellQty = sellExecs.sumOf { it.qtyValue }
        val totalSellValue = sellExecs.sumOf { it.totalValue }
        val avgSellPrice = totalSellValue / totalSellQty

        // Buy total: 100 + 100 = 200 MNT
        assertEquals(200.0, totalBuyQty, 0.001)
        // Buy value: 50 + 60 = 110 USDT
        assertEquals(110.0, totalBuyValue, 0.001)
        // Weighted average buy: 110 / 200 = 0.55 USDT
        assertEquals(0.55, avgBuyPrice, 0.001)

        // Sell total: 80 + 120 = 200 MNT
        assertEquals(200.0, totalSellQty, 0.001)
        // Sell value: 56 + 96 = 152 USDT
        assertEquals(152.0, totalSellValue, 0.001)
        // Weighted average sell: 152 / 200 = 0.76 USDT
        assertEquals(0.76, avgSellPrice, 0.001)

        val priceDiff = avgSellPrice - avgBuyPrice
        // 0.76 - 0.55 = 0.21 USDT profit per unit
        assertEquals(0.21, priceDiff, 0.001)

        val profitPercent = (priceDiff / avgBuyPrice) * 100.0
        // (0.21 / 0.55) * 100 = 38.1818%
        assertEquals(38.18, profitPercent, 0.01)
    }
}
