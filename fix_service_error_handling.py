import re

with open("app/src/main/java/com/example/service/TradingBotService.kt", "r") as f:
    lines = f.readlines()

new_lines = []
for line in lines:
    if "if (e is android.app.ForegroundServiceStartNotAllowedException || e.cause is android.app.ForegroundServiceStartNotAllowedException || e.message?.contains(\"ForegroundServiceStartNotAllowedException\") == true) {" in line:
        new_lines.append("                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && (e is android.app.ForegroundServiceStartNotAllowedException || e.cause is android.app.ForegroundServiceStartNotAllowedException || e.message?.contains(\"ForegroundServiceStartNotAllowedException\") == true)) {\n")
        continue
    new_lines.append(line)

with open("app/src/main/java/com/example/service/TradingBotService.kt", "w") as f:
    f.writelines(new_lines)
