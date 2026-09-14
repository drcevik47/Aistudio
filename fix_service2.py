import re

with open("app/src/main/java/com/example/service/TradingBotService.kt", "r") as f:
    lines = f.readlines()

new_lines = []
for line in lines:
    if "startForeground(NOTIFICATION_ID, buildForegroundNotification(\"Bybit Bot Başlatılıyor...\"))" in line:
        new_lines.append("                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {\n")
        new_lines.append("                    startForeground(NOTIFICATION_ID, buildForegroundNotification(\"Bybit Bot Başlatılıyor...\"), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)\n")
        new_lines.append("                } else {\n")
        new_lines.append("                    startForeground(NOTIFICATION_ID, buildForegroundNotification(\"Bybit Bot Başlatılıyor...\"))\n")
        new_lines.append("                }\n")
        continue
    new_lines.append(line)

with open("app/src/main/java/com/example/service/TradingBotService.kt", "w") as f:
    f.writelines(new_lines)
