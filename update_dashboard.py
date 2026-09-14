import re

with open("app/src/main/java/com/example/ui/DashboardScreen.kt", "r") as f:
    content = f.read()

content = content.replace(
"""                    currentStepPercent = state.stepPercent,
                    isTestnet = state.isTestnet,""",
"""                    currentStepPercent = state.stepPercent,
                    okxStepPercent = state.okxStepPercent,
                    isTestnet = state.isTestnet,"""
)

content = content.replace(
"""                    onUpdateStepPercent = { viewModel.updateStepPercent(it) },""",
"""                    onUpdateStepPercent = { bybit, okx -> viewModel.updateStepPercent(bybit, okx) },"""
)

with open("app/src/main/java/com/example/ui/DashboardScreen.kt", "w") as f:
    f.write(content)

print("Done")
