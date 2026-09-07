package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.bot.PortfolioAnalysis
import com.example.bot.RebalanceAction
import com.example.ui.components.PortfolioCard
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class GreetingScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun portfolio_card_screenshot() {
        val analysis = PortfolioAnalysis(
            usdtBalance = 500.0,
            mntBalance = 500.0,
            currentPrice = 1.0,
            mntValueUsdt = 500.0,
            totalEquityUsdt = 1000.0,
            usdtPercent = 50.0,
            mntPercent = 50.0,
            isBalanced5050 = true,
            requiredAction = RebalanceAction.BALANCED,
            deltaMnt = 0.0,
            deltaUsdt = 0.0,
            description = "Portföy dengeli (%50.00 USDT / %50.00 MNT)"
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                PortfolioCard(
                    analysis = analysis,
                    currentPrice = 1.0,
                    price24hChange = 2.45,
                    isBotActive = true,
                    onManualRebalanceClick = {}
                )
            }
        }

        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/portfolio_card.png")
    }
}
