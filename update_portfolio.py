import re
with open("app/src/main/java/com/example/ui/components/PortfolioCard.kt", "r") as f:
    content = f.read()

content = content.replace(
    """fun PortfolioCard(
    analysis: PortfolioAnalysis?,
    activeBaseCoin: String,
    currentPrice: Double,
    price24hChange: Double,
    isBotActive: Boolean,
    onManualRebalanceClick: () -> Unit,
    modifier: Modifier = Modifier
)""",
    """fun PortfolioCard(
    analysis: PortfolioAnalysis?,
    activeBaseCoin: String,
    currentPrice: Double,
    price24hChange: Double,
    isBotActive: Boolean,
    onManualRebalanceClick: () -> Unit,
    modifier: Modifier = Modifier,
    exchangeName: String = "Bybit Unified"
)"""
)

content = content.replace('text = "Bybit Unified"', 'text = exchangeName')

with open("app/src/main/java/com/example/ui/components/PortfolioCard.kt", "w") as f:
    f.write(content)
print("Done")
