import re

with open("app/src/main/java/com/example/service/TradingBotService.kt", "r") as f:
    lines = f.readlines()

new_lines = []
for line in lines:
    if "fillPrice = if (order.avgPrice" in line:
        new_lines.append("            val avgPrice = order.avgPrice.toDoubleOrNull() ?: 0.0\n")
        new_lines.append("            val fillPrice = if (avgPrice > 0.0) avgPrice else (order.priceValue)\n")
        continue
    if "fillQty = if (order.cumExecQty" in line:
        new_lines.append("            val execQty = order.cumExecQty.toDoubleOrNull() ?: 0.0\n")
        new_lines.append("            val fillQty = if (execQty > 0.0) execQty else order.qtyValue\n")
        continue
    if "${order.cumExecQty} ${preferences.bybitBaseCoin} @ ${order.avgPrice}" in line:
        line = line.replace("${order.cumExecQty}", "${order.cumExecQty.toDoubleOrNull() ?: 0.0}")
        line = line.replace("${order.avgPrice}", "${order.avgPrice.toDoubleOrNull() ?: 0.0}")
        
    new_lines.append(line)

with open("app/src/main/java/com/example/service/TradingBotService.kt", "w") as f:
    f.writelines(new_lines)
