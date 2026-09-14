import re
with open("app/src/main/java/com/example/ui/DashboardScreen.kt", "r") as f:
    content = f.read()

new_card = """                        // Portfolio Status Card
                        item {
                            PortfolioCard(
                                analysis = state.portfolioAnalysis,
                                activeBaseCoin = viewModel.preferences.bybitBaseCoin,
                                currentPrice = state.currentPrice,
                                price24hChange = state.price24hChange,
                                isBotActive = state.isBotActive,
                                onManualRebalanceClick = {
                                    viewModel.requestInitialRebalanceDialog()
                                },
                                exchangeName = "Bybit Unified"
                            )
                        }

                        if (viewModel.preferences.okxApiKey.isNotBlank()) {
                            item {
                                Spacer(modifier = Modifier.height(16.dp))
                                PortfolioCard(
                                    analysis = state.okxPortfolioAnalysis,
                                    activeBaseCoin = viewModel.preferences.okxBaseCoin,
                                    currentPrice = state.okxCurrentPrice,
                                    price24hChange = state.price24hChange, // Or Okx specific if added
                                    isBotActive = state.isBotActive, // If bot works for both
                                    onManualRebalanceClick = {
                                        // TODO: Not implemented for OKX yet, just a visual card for now
                                    },
                                    exchangeName = "OKX TR"
                                )
                            }
                        }"""

content = content.replace("""                        // Portfolio Status Card
                        item {
                            PortfolioCard(
                                analysis = state.portfolioAnalysis,
                                activeBaseCoin = viewModel.preferences.bybitBaseCoin,
                                currentPrice = state.currentPrice,
                                price24hChange = state.price24hChange,
                                isBotActive = state.isBotActive,
                                onManualRebalanceClick = {
                                    viewModel.requestInitialRebalanceDialog()
                                }
                            )
                        }""", new_card)

with open("app/src/main/java/com/example/ui/DashboardScreen.kt", "w") as f:
    f.write(content)
print("Done")
