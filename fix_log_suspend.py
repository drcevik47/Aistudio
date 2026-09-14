import re

with open("app/src/main/java/com/example/ui/MainViewModel.kt", "r") as f:
    lines = f.readlines()

new_lines = []
for line in lines:
    if "repository.log(LogLevel.ERROR, \"System\", \"Bot servisi başlatılamadı" in line:
        new_lines.append("                    viewModelScope.launch(Dispatchers.IO) {\n")
        new_lines.append("                        repository.log(LogLevel.ERROR, \"System\", \"Bot servisi başlatılamadı: ${e.message}\")\n")
        new_lines.append("                    }\n")
        continue
    new_lines.append(line)

with open("app/src/main/java/com/example/ui/MainViewModel.kt", "w") as f:
    f.writelines(new_lines)
