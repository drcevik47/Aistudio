import re
with open("app/src/main/java/com/example/ui/components/PortfolioCard.kt", "r") as f:
    content = f.read()

content = content.replace(
    "text = analysis.description,",
    "text = analysis.description.replace(\"BASE\", activeBaseCoin),"
)

with open("app/src/main/java/com/example/ui/components/PortfolioCard.kt", "w") as f:
    f.write(content)
print("Done")
