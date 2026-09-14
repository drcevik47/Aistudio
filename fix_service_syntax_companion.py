import re

with open("app/src/main/java/com/example/service/TradingBotService.kt", "r") as f:
    lines = f.readlines()

new_lines = []
for line in lines:
    if "} catch (e: Exception) {" in line and "Log.e(\"TradingBotService\", \"Failed to start foreground service: ${e.message}\", e)" in lines[lines.index(line)+1]:
        new_lines.append("                }\n")
    new_lines.append(line)

with open("app/src/main/java/com/example/service/TradingBotService.kt", "w") as f:
    f.writelines(new_lines)
