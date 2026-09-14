files = [
    "app/src/main/java/com/example/ui/components/OrderHistoryScreen.kt",
    "app/src/main/java/com/example/ui/components/TradeAnalysisScreen.kt"
]

for file in files:
    with open(file, "r") as f:
        lines = f.readlines()
    
    new_lines = []
    for i, line in enumerate(lines):
        if "activeSymbol: String" in line and "activeSymbol: String" in lines[i-1]:
            line = line.replace("activeSymbol: String", "activeBaseCoin: String")
        if "activeBaseCoin: String" in line and "activeBaseCoin: String" in lines[i-1]:
            line = line.replace("activeBaseCoin: String", "activeSymbol: String")
        new_lines.append(line)
        
    with open(file, "w") as f:
        f.writelines(new_lines)
