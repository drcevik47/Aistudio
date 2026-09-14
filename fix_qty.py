with open("app/src/main/java/com/example/ui/MainViewModel.kt", "r") as f:
    lines = f.readlines()

new_lines = []
for line in lines:
    if "val qty = analysis.tradeQty" in line:
        line = line.replace("tradeQty", "deltaBase")
    if "symbol = preferences.bybitSymbol," in line:
        continue # remove this line
    if "qty = qty.toString()," in line:
        line = line.replace("qty.toString()", "qty")
    new_lines.append(line)

with open("app/src/main/java/com/example/ui/MainViewModel.kt", "w") as f:
    f.writelines(new_lines)
