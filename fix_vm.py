import re

with open("app/src/main/java/com/example/ui/MainViewModel.kt", "r") as f:
    lines = f.readlines()

out = []
i = 0
while i < len(lines):
    line = lines[i]
    if "var okxCurrentPrice = _uiState.value.okxCurrentPrice" in line:
        i += 25
        continue
    if "okxCurrentPrice = okxCurrentPrice" in line:
        i += 4
        continue
    out.append(line)
    i += 1

content = "".join(out)

# Now, we will add the OKX fetching logic cleanly into a new method `fetchOkxData()`
# and call it from silentRefresh and refreshData. Wait, they are suspend functions.

okx_helper = """
    private suspend fun fetchOkxData() {
        if (preferences.okxApiKey.isBlank()) return
        
        var okxCurrentPrice = _uiState.value.okxCurrentPrice
        var okxUsdt = _uiState.value.okxUsdtBalance
        var okxBaseQty = _uiState.value.okxBaseCoinBalance
        var okxAnalysis = _uiState.value.okxPortfolioAnalysis

        val okxTickerRes = okxRepository.getTicker()
        okxTickerRes.onSuccess { ticker ->
            okxCurrentPrice = ticker.last.toDoubleOrNull() ?: 0.0
        }

        val okxBalanceRes = okxRepository.getWalletBalance()
        okxBalanceRes.onSuccess { map ->
            okxUsdt = map["USDT"] ?: 0.0
            okxBaseQty = map[preferences.okxBaseCoin] ?: 0.0
        }

        if (okxCurrentPrice > 0.0) {
            okxAnalysis = RebalanceEngine.analyzePortfolio(
                usdtBalance = okxUsdt,
                baseCoinBalance = okxBaseQty,
                currentPrice = okxCurrentPrice
            )
        }

        _uiState.update {
            it.copy(
                okxCurrentPrice = okxCurrentPrice,
                okxUsdtBalance = okxUsdt,
                okxBaseCoinBalance = okxBaseQty,
                okxPortfolioAnalysis = okxAnalysis
            )
        }
    }
"""

content = content.replace("    private fun recalculateGridPlan() {", okx_helper + "\n    private fun recalculateGridPlan() {")

# Call fetchOkxData() in silentRefresh
content = content.replace("            _uiState.update {\n\n                it.copy(\n                    currentPrice = currentPrice,", "            fetchOkxData()\n            _uiState.update {\n\n                it.copy(\n                    currentPrice = currentPrice,")

# Call fetchOkxData() in refreshData
content = content.replace("            _uiState.update {\n                it.copy(\n                    isLoading = false,\n                    currentPrice = currentPrice,", "            fetchOkxData()\n            _uiState.update {\n                it.copy(\n                    isLoading = false,\n                    currentPrice = currentPrice,")

with open("app/src/main/java/com/example/ui/MainViewModel.kt", "w") as f:
    f.write(content)
