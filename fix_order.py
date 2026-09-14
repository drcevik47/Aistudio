with open("app/src/main/java/com/example/ui/components/OrderHistoryScreen.kt", "r") as f:
    lines = f.readlines()

new_lines = []
for i, line in enumerate(lines):
    if "activeBaseCoin: String," in line:
        if "OrderHistoryScreen(" in "".join(lines[i-15:i]):
            if "activeBaseCoin: String," in lines[i-1]:
                line = line.replace("activeBaseCoin", "activeSymbol")
    new_lines.append(line)

with open("app/src/main/java/com/example/ui/components/OrderHistoryScreen.kt", "w") as f:
    f.writelines(new_lines)
