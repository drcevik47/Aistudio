import re

with open("app/src/main/java/com/example/ui/MainViewModel.kt", "r") as f:
    content = f.read()

# Make fetchOkxData accept cycleCount and optimize fetching
new_fetch_def = """    private suspend fun fetchOkxData(cycleCount: Int = 0) {
        if (preferences.okxApiKey.isBlank()) return
        
        var okxCurrentPrice = _uiState.value.okxCurrentPrice
        var okxUsdt = _uiState.value.okxUsdtBalance
        var okxBaseQty = _uiState.value.okxBaseCoinBalance
        var okxAnalysis = _uiState.value.okxPortfolioAnalysis

        if (cycleCount % 2 == 0 || okxCurrentPrice <= 0.0) {
            val okxTickerRes = okxRepository.getTicker()
            okxTickerRes.onSuccess { ticker ->
                okxCurrentPrice = ticker.last.toDoubleOrNull() ?: 0.0
            }
        }

        if (cycleCount % 2 == 0 || okxUsdt <= 0.0) {
            val okxBalanceRes = okxRepository.getWalletBalance()
            okxBalanceRes.onSuccess { map ->
                okxUsdt = map["USDT"] ?: 0.0
                okxBaseQty = map[preferences.okxBaseCoin] ?: 0.0
            }
        }"""

content = re.sub(r'    private suspend fun fetchOkxData\(\) \{.*?        val okxBalanceRes = okxRepository\.getWalletBalance\(\)\n        okxBalanceRes\.onSuccess \{ map ->\n            okxUsdt = map\["USDT"\] \?: 0\.0\n            okxBaseQty = map\[preferences\.okxBaseCoin\] \?: 0\.0\n        \}', new_fetch_def, content, flags=re.DOTALL)

# Update the calls to fetchOkxData
content = content.replace("            fetchOkxData()\n            _uiState.update {", "            fetchOkxData(cycleCount)\n            _uiState.update {")
content = content.replace("            fetchOkxData()\n            _uiState.update {\n                it.copy(\n                    isLoading = false,", "            fetchOkxData(0)\n            _uiState.update {\n                it.copy(\n                    isLoading = false,")

with open("app/src/main/java/com/example/ui/MainViewModel.kt", "w") as f:
    f.write(content)
print("Done")
