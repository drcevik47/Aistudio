import re

with open("app/src/main/java/com/example/service/TradingBotService.kt", "r") as f:
    lines = f.readlines()

new_lines = []
for line in lines:
    if "if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && (e is android.app.ForegroundServiceStartNotAllowedException" in line:
        new_lines.append("                        if (e is Exception && (e.javaClass.simpleName == \"ForegroundServiceStartNotAllowedException\" || e.message?.contains(\"ForegroundServiceStartNotAllowedException\") == true || e.cause?.javaClass?.simpleName == \"ForegroundServiceStartNotAllowedException\")) {\n")
        continue
    new_lines.append(line)

with open("app/src/main/java/com/example/service/TradingBotService.kt", "w") as f:
    f.writelines(new_lines)
