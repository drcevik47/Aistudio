import re
with open("app/src/main/java/com/example/ui/components/PortfolioCard.kt", "r") as f:
    content = f.read()

# We need to find where analysis?.description or something is used.
