import re

with open("app/src/main/java/com/example/ui/MainViewModel.kt", "r") as f:
    lines = f.readlines()

new_lines = []
for i, line in enumerate(lines):
    # Fix init block
    if "repository.syncUnfilledOrdersWithExchange()" in line and "init {" in "".join(lines[max(0, i-10):i]):
        new_lines.append(line)
        new_lines.append("            }\n")
        continue
    if "refreshData()" in line and "init {" in "".join(lines[max(0, i-15):i]) and "else {" in lines[i+1]:
        new_lines.append(line)
        new_lines.append("        } else {\n")
        continue
    if "else {" in line and "init {" in "".join(lines[max(0, i-15):i]) and "refreshData()" in lines[i-1]:
        continue # skip the original else since we added it
    if "repository.pruneLogs()" in line and "else {" in "".join(lines[max(0, i-5):i]):
        new_lines.append(line)
        new_lines.append("            }\n")
        new_lines.append("        }\n")
        continue

    # Fix silentRefresh
    if "cycleCount++" in line and "silentRefresh" in "".join(lines[max(0, i-10):i]):
        new_lines.append(line)
        new_lines.append("                }\n")
        new_lines.append("            }\n")
        new_lines.append("        }\n")
        continue
    if "repository.syncUnfilledOrdersWithExchange()" in line and "silentRefresh" in "".join(lines[max(0, i-20):i]) and "cycleCount % 4" in lines[i-1]:
        new_lines.append(line)
        new_lines.append("            }\n")
        continue
    if "repository.pruneLogs()" in line and "silentRefresh" in "".join(lines[max(0, i-30):i]) and "cycleCount % 30" in lines[i-1]:
        new_lines.append(line)
        new_lines.append("            }\n")
        continue
    if "priceChange = ticker.changePercent24h" in line and "tickerRes.onSuccess" in "".join(lines[max(0, i-3):i]):
        new_lines.append(line)
        new_lines.append("            }\n")
        continue
    if "baseQty = map[_uiState.value.activeBaseCoin] ?: 0.0" in line and "balanceRes.onSuccess" in "".join(lines[max(0, i-3):i]):
        new_lines.append(line)
        new_lines.append("                }\n")
        new_lines.append("            }\n")
        continue
    if "openOrders = list" in line and "openOrdersRes.onSuccess" in "".join(lines[max(0, i-3):i]):
        new_lines.append(line)
        new_lines.append("                }\n")
        new_lines.append("            }\n")
        continue

    if "lastRebalancePrice = preferences.lastRebalancePrice" in line and "isBotActive = preferences.isBotActive" in lines[i-1]:
        new_lines.append(line)
        new_lines.append("                )\n")
        new_lines.append("            }\n")
        continue
    if "currentPrice" in line.strip() and "anchorBasePrice =" in lines[i-1]:
        new_lines.append(line)
        new_lines.append("            }\n")
        continue

    if "statusMessage = \"OKX TR API bilgileri başarıyla kaydedildi\"" in line:
        new_lines.append(line)
        new_lines.append("                )\n")
        new_lines.append("            }\n")
        new_lines.append("        }\n")
        continue

    if "showApiKeyDialog = false" in line and "isTestnet = testnet" in lines[i-1]:
        new_lines.append(line)
        new_lines.append("                )\n")
        new_lines.append("            }\n")
        continue

    if "refreshData()" in line and "saveApiCredentials" in "".join(lines[max(0, i-15):i]):
        new_lines.append(line)
        new_lines.append("        }\n")
        continue

    if "refreshData()" in line and "silentRefresh" in "".join(lines[max(0, i-30):i]):
        # wait, that's inside refreshData...
        pass
    
    new_lines.append(line)

with open("app/src/main/java/com/example/ui/MainViewModel.tmp", "w") as f:
    f.writelines(new_lines)
