import re

with open("app/src/main/java/com/example/ui/MainViewModel.kt", "r") as f:
    content = f.read()

# Replace fetchOkxData(cycleCount) with fetchOkxData(0) inside refreshData
# Let's just find the exact line and replace it
content = content.replace("            val shouldShowInitialDialog = !analysis.isBalanced5050 &&\n                    analysis.requiredAction != RebalanceAction.BALANCED &&\n                    !preferences.isBotActive &&\n                    analysis.deltaUsdt >= 1.0\n\n            fetchOkxData(cycleCount)", "            val shouldShowInitialDialog = !analysis.isBalanced5050 &&\n                    analysis.requiredAction != RebalanceAction.BALANCED &&\n                    !preferences.isBotActive &&\n                    analysis.deltaUsdt >= 1.0\n\n            fetchOkxData(0)")

with open("app/src/main/java/com/example/ui/MainViewModel.kt", "w") as f:
    f.write(content)
print("Done")
