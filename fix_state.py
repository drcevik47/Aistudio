import re

with open("app/src/main/java/com/example/ui/components/SettingsDialog.kt", "r") as f:
    content = f.read()

content = content.replace(
"""    var stepSlider by remember(currentStepPercent) { mutableFloatStateOf(currentStepPercent.toFloat()) }
    var currentBybitSymbol by remember(bybitSymbol) { mutableStateOf(bybitSymbol) }""",
"""    var stepSlider by remember(currentStepPercent) { mutableFloatStateOf(currentStepPercent.toFloat()) }
    var okxStepSlider by remember(okxStepPercent) { mutableFloatStateOf(okxStepPercent.toFloat()) }
    var currentBybitSymbol by remember(bybitSymbol) { mutableStateOf(bybitSymbol) }"""
)

with open("app/src/main/java/com/example/ui/components/SettingsDialog.kt", "w") as f:
    f.write(content)

print("Done")
