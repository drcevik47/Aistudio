import re

with open("app/src/main/java/com/example/service/TradingBotService.kt", "r") as f:
    lines = f.readlines()

new_lines = []
for line in lines:
    if "try { context.startForegroundService(intent) } catch(e: Exception) { Log.e(\"TradingBotService\", \"Foreground service failed\", e) }" in line:
        new_lines.append("                    try { context.startForegroundService(intent) } catch(e: android.app.ForegroundServiceStartNotAllowedException) { context.startService(intent) } catch(e: Exception) { Log.e(\"TradingBotService\", \"Foreground service failed\", e) }\n")
        continue
    new_lines.append(line)

with open("app/src/main/java/com/example/service/TradingBotService.kt", "w") as f:
    f.writelines(new_lines)
