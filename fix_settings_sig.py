import re

with open("app/src/main/java/com/example/ui/components/SettingsDialog.kt", "r") as f:
    content = f.read()

# Update signature
content = content.replace(
"""fun SettingsDialog(
    currentStepPercent: Double,
    isTestnet: Boolean,""",
"""fun SettingsDialog(
    currentStepPercent: Double,
    okxStepPercent: Double,
    isTestnet: Boolean,"""
)

content = content.replace(
"""    onUpdateStepPercent: (Double) -> Unit,""",
"""    onUpdateStepPercent: (Double, Double) -> Unit,"""
)

content = content.replace(
"""                                    onUpdateStepPercent(stepSlider.toDouble())""",
"""                                    onUpdateStepPercent(stepSlider.toDouble(), okxStepSlider.toDouble())"""
)

with open("app/src/main/java/com/example/ui/components/SettingsDialog.kt", "w") as f:
    f.write(content)

print("Done")
