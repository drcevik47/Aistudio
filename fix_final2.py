with open("app/src/main/java/com/example/ui/DashboardScreen.kt", "r") as f:
    lines = f.readlines()

new_lines = []
for i, line in enumerate(lines):
    if "activeBaseCoin = viewModel.preferences.bybitBaseCoin," in line and "PortfolioCard(" in "".join(lines[i-10:i]):
        new_lines.append(line)
        new_lines.append("                                currentPrice = state.currentPrice,\n")
    elif "currentPrice = state.currentPrice," in line and "EditBasePriceDialog(" in "".join(lines[i-10:i]):
        continue
    else:
        new_lines.append(line)

with open("app/src/main/java/com/example/ui/DashboardScreen.kt", "w") as f:
    f.writelines(new_lines)
