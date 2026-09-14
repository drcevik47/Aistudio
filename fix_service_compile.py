import re

with open("app/src/main/java/com/example/service/TradingBotService.kt", "r") as f:
    lines = f.readlines()

new_lines = []
for line in lines:
    if "fillTime = order.updatedTime" in line:
        line = line.replace("order.updatedTime", "order.updatedTime.toLongOrNull() ?: 0L")
    if "fillPrice = if (order.avgPrice > 0) order.avgPrice else (order.price ?: 0.0)" in line:
        line = line.replace("> 0", "> 0.0")
    if "fillQty = if (order.cumExecQty > 0) order.cumExecQty else order.qtyValue" in line:
        line = line.replace("> 0", "> 0.0")
    
    new_lines.append(line)

with open("app/src/main/java/com/example/service/TradingBotService.kt", "w") as f:
    f.writelines(new_lines)
