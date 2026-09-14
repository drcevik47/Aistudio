import re

with open("app/src/main/java/com/example/ui/DashboardScreen.kt", "r") as f:
    lines = f.readlines()

new_lines = []
for i, line in enumerate(lines):
    if i >= 430 and i <= 520:
        if "currentPrice = state.currentPrice" in line and lines[i-1].strip() == "activeBaseCoin = viewModel.preferences.bybitBaseCoin,":
            continue # Skip duplicate
        if "onClearOrders =" in line and "TradeAnalysisScreen" in "".join(lines[i-15:i]):
            line = line.replace("onClearOrders = { viewModel.clearOrders() }", "onFetchAnalysis = { symbol, days, startTimestamp -> viewModel.fetchTradeAnalysis(symbol, days, startTimestamp) }")
    new_lines.append(line)

with open("app/src/main/java/com/example/ui/DashboardScreen.kt", "w") as f:
    f.writelines(new_lines)
