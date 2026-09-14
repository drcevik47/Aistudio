import re

with open("app/src/main/java/com/example/ui/components/OrderHistoryScreen.kt", "r") as f:
    content = f.read()

# Fix OrderHistoryScreen signature
content = re.sub(
    r"fun OrderHistoryScreen\((.*?)\n    activeBaseCoin: String,\n    activeBaseCoin: String,",
    r"fun OrderHistoryScreen(\1\n    activeSymbol: String,\n    activeBaseCoin: String,",
    content,
    flags=re.DOTALL
)

# Fix CalculateTradesDialog signature
content = re.sub(
    r"fun CalculateTradesDialog\((.*?)\n    activeBaseCoin: String,\n    activeBaseCoin: String,",
    r"fun CalculateTradesDialog(\1\n    activeSymbol: String,\n    activeBaseCoin: String,",
    content,
    flags=re.DOTALL
)

with open("app/src/main/java/com/example/ui/components/OrderHistoryScreen.kt", "w") as f:
    f.write(content)
