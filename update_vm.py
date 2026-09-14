import re
with open("app/src/main/java/com/example/ui/MainViewModel.kt", "r") as f:
    content = f.read()

content = content.replace(
    "private val repository = app.repository",
    "private val repository = app.repository\n    private val okxRepository = app.okxRepository"
)

content = content.replace(
    """    val portfolioAnalysis: PortfolioAnalysis? = null,
    val gridPlan: GridOrdersPlan? = null,""",
    """    val portfolioAnalysis: PortfolioAnalysis? = null,
    val gridPlan: GridOrdersPlan? = null,
    val okxCurrentPrice: Double = 0.0,
    val okxPrice24hChange: Double = 0.0,
    val okxUsdtBalance: Double = 0.0,
    val okxBaseCoinBalance: Double = 0.0,
    val okxPortfolioAnalysis: PortfolioAnalysis? = null,"""
)

with open("app/src/main/java/com/example/ui/MainViewModel.kt", "w") as f:
    f.write(content)
print("Done")
