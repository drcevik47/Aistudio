import re

with open("app/src/main/java/com/example/service/TradingBotService.kt", "r") as f:
    lines = f.readlines()

new_lines = []
for line in lines:
    if "if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {" in line and "ACTION_START_BOT, null ->" in "".join(lines[max(0, lines.index(line)-10):lines.index(line)]):
        new_lines.append("                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {\n")
        continue
    new_lines.append(line)

with open("app/src/main/java/com/example/service/TradingBotService.kt", "w") as f:
    f.writelines(new_lines)
