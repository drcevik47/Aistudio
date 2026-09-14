import re

with open("app/src/main/java/com/example/service/TradingBotService.kt", "r") as f:
    lines = f.readlines()

new_lines = []
for line in lines:
    if "if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {" in line:
        continue
    if "startForeground(NOTIFICATION_ID, buildForegroundNotification(\"Bybit Bot Başlatılıyor...\"), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)" in line:
        new_lines.append("                try {\n")
        new_lines.append("                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {\n")
        new_lines.append("                        startForeground(NOTIFICATION_ID, buildForegroundNotification(\"Bybit Bot Başlatılıyor...\"), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)\n")
        new_lines.append("                    } else {\n")
        new_lines.append("                        startForeground(NOTIFICATION_ID, buildForegroundNotification(\"Bybit Bot Başlatılıyor...\"))\n")
        new_lines.append("                    }\n")
        new_lines.append("                } catch (e: Exception) {\n")
        new_lines.append("                    Log.e(\"TradingBotService\", \"startForeground hatasi\", e)\n")
        new_lines.append("                }\n")
        continue
    if "} else {" in line and "startForeground(NOTIFICATION_ID, buildForegroundNotification(\"Bybit Bot Başlatılıyor...\"))" in "".join(lines[lines.index(line)+1:lines.index(line)+3]):
        continue
    if "startForeground(NOTIFICATION_ID, buildForegroundNotification(\"Bybit Bot Başlatılıyor...\"))" in line and "else {" in lines[lines.index(line)-1]:
        continue
    if "}" in line and "startForeground(NOTIFICATION_ID, buildForegroundNotification(\"Bybit Bot Başlatılıyor...\"))" in lines[lines.index(line)-1] and "else" in lines[lines.index(line)-2]:
        continue
    
    new_lines.append(line)

with open("app/src/main/java/com/example/service/TradingBotService.kt", "w") as f:
    f.writelines(new_lines)
