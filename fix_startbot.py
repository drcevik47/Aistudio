import re

with open("app/src/main/java/com/example/ui/MainViewModel.kt", "r") as f:
    lines = f.readlines()

new_lines = []
for line in lines:
    if "TradingBotService.start(getApplication())" in line:
        new_lines.append("                try {\n")
        new_lines.append("                    TradingBotService.start(getApplication())\n")
        new_lines.append("                } catch (e: Exception) {\n")
        new_lines.append("                    repository.log(LogLevel.ERROR, \"System\", \"Bot servisi başlatılamadı: ${e.message}\")\n")
        new_lines.append("                }\n")
        continue
    new_lines.append(line)

with open("app/src/main/java/com/example/ui/MainViewModel.kt", "w") as f:
    f.writelines(new_lines)
