import re

with open("app/src/main/java/com/example/service/TradingBotService.kt", "r") as f:
    lines = f.readlines()

new_lines = []
for line in lines:
    if "ACTION_START_BOT, null -> {" in line:
        new_lines.append(line)
        new_lines.append("                try {\n")
        new_lines.append("                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {\n")
        new_lines.append("                        startForeground(NOTIFICATION_ID, buildForegroundNotification(\"Bybit Bot Başlatılıyor...\"), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)\n")
        new_lines.append("                    } else {\n")
        new_lines.append("                        startForeground(NOTIFICATION_ID, buildForegroundNotification(\"Bybit Bot Başlatılıyor...\"))\n")
        new_lines.append("                    }\n")
        new_lines.append("                } catch (e: Exception) {\n")
        new_lines.append("                    Log.e(\"TradingBotService\", \"startForeground failed\", e)\n")
        new_lines.append("                }\n")
        new_lines.append("                startBot()\n")
        new_lines.append("                scheduleKeepAliveAlarm()\n")
        new_lines.append("            }\n")
        continue
    
    if "try {" in line and "if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {" in "".join(lines[lines.index(line):lines.index(line)+5]) and "ACTION_START_BOT, null ->" in "".join(lines[max(0, lines.index(line)-5):lines.index(line)]):
        pass # already added
    elif "if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {" in line and "ACTION_START_BOT, null ->" in "".join(lines[max(0, lines.index(line)-5):lines.index(line)]):
        pass # already added
    elif "startForeground(NOTIFICATION_ID, buildForegroundNotification(\"Bybit Bot Başlatılıyor...\"), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)" in line and "ACTION_START_BOT, null ->" in "".join(lines[max(0, lines.index(line)-5):lines.index(line)]):
        pass # already added
    elif "} else {" in line and "startForeground(NOTIFICATION_ID, buildForegroundNotification(\"Bybit Bot Başlatılıyor...\"))" in "".join(lines[lines.index(line):lines.index(line)+2]) and "ACTION_START_BOT, null ->" in "".join(lines[max(0, lines.index(line)-6):lines.index(line)]):
        pass # already added
    elif "startForeground(NOTIFICATION_ID, buildForegroundNotification(\"Bybit Bot Başlatılıyor...\"))" in line and "ACTION_START_BOT, null ->" in "".join(lines[max(0, lines.index(line)-8):lines.index(line)]):
        pass # already added
    elif "} catch (e: Exception) {" in line and "Log.e(\"TradingBotService\", \"startForeground failed\", e)" in lines[lines.index(line)+1] and "ACTION_START_BOT, null ->" in "".join(lines[max(0, lines.index(line)-10):lines.index(line)]):
        pass # already added
    elif "Log.e(\"TradingBotService\", \"startForeground failed\", e)" in line and "ACTION_START_BOT, null ->" in "".join(lines[max(0, lines.index(line)-11):lines.index(line)]):
        pass # already added
    elif "}" in line and "Log.e(\"TradingBotService\", \"startForeground failed\", e)" in lines[lines.index(line)-1] and "ACTION_START_BOT, null ->" in "".join(lines[max(0, lines.index(line)-12):lines.index(line)]):
        pass # already added
    elif "startBot()" in line and "ACTION_START_BOT, null ->" in "".join(lines[max(0, lines.index(line)-15):lines.index(line)]):
        pass # already added
    elif "scheduleKeepAliveAlarm()" in line and "ACTION_START_BOT, null ->" in "".join(lines[max(0, lines.index(line)-16):lines.index(line)]):
        pass # already added
    elif "}" in line and "scheduleKeepAliveAlarm()" in lines[lines.index(line)-1] and "ACTION_START_BOT, null ->" in "".join(lines[max(0, lines.index(line)-17):lines.index(line)]):
        pass # already added
    else:
        new_lines.append(line)

with open("app/src/main/java/com/example/service/TradingBotService.kt", "w") as f:
    f.writelines(new_lines)
