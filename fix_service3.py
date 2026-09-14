import re

with open("app/src/main/java/com/example/service/TradingBotService.kt", "r") as f:
    lines = f.readlines()

new_lines = []
for line in lines:
    if "try { context.startForegroundService(intent) } catch(e: android.app.ForegroundServiceStartNotAllowedException) { context.startService(intent) } catch(e: Exception) { Log.e(\"TradingBotService\", \"Foreground service failed\", e) }" in line:
        new_lines.append("                    try {\n")
        new_lines.append("                        context.startForegroundService(intent)\n")
        new_lines.append("                    } catch(e: Exception) {\n")
        new_lines.append("                        if (e is android.app.ForegroundServiceStartNotAllowedException || e.cause is android.app.ForegroundServiceStartNotAllowedException || e.message?.contains(\"ForegroundServiceStartNotAllowedException\") == true) {\n")
        new_lines.append("                            // Ignore the failure if background started is restricted\n")
        new_lines.append("                            Log.w(\"TradingBotService\", \"Foreground service start not allowed, starting normally. \")\n")
        new_lines.append("                            context.startService(intent)\n")
        new_lines.append("                        } else {\n")
        new_lines.append("                            Log.e(\"TradingBotService\", \"Foreground service failed\", e)\n")
        new_lines.append("                        }\n")
        new_lines.append("                    }\n")
        continue
    new_lines.append(line)

with open("app/src/main/java/com/example/service/TradingBotService.kt", "w") as f:
    f.writelines(new_lines)
