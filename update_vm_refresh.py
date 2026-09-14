with open("app/src/main/java/com/example/ui/MainViewModel.kt", "r") as f:
    content = f.read()

okx_logic = """
            var okxCurrentPrice = _uiState.value.okxCurrentPrice
            var okxUsdt = _uiState.value.okxUsdtBalance
            var okxBaseQty = _uiState.value.okxBaseCoinBalance
            var okxAnalysis: PortfolioAnalysis? = _uiState.value.okxPortfolioAnalysis

            if (preferences.okxApiKey.isNotBlank()) {
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
            }

            _uiState.update {
"""

content = content.replace("            _uiState.update {", okx_logic, 1)

state_update = """                    showInitialRebalanceDialog = shouldShowInitialDialog,
                    okxCurrentPrice = okxCurrentPrice,
                    okxUsdtBalance = okxUsdt,
                    okxBaseCoinBalance = okxBaseQty,
                    okxPortfolioAnalysis = okxAnalysis"""
content = content.replace("                    showInitialRebalanceDialog = shouldShowInitialDialog", state_update)

with open("app/src/main/java/com/example/ui/MainViewModel.kt", "w") as f:
    f.write(content)

print("Done")
