import re

with open("app/src/main/java/com/example/ui/components/SettingsDialog.kt", "r") as f:
    content = f.read()

# Update signature
content = content.replace(
"""@Composable
fun SettingsDialog(
    currentStepPercent: Double,
    isTestnet: Boolean,""",
"""@Composable
fun SettingsDialog(
    currentStepPercent: Double,
    okxStepPercent: Double,
    isTestnet: Boolean,"""
)

content = content.replace(
"""    onUpdateStepPercent: (Double) -> Unit,""",
"""    onUpdateStepPercent: (Double, Double) -> Unit,"""
)

content = content.replace(
"""    var stepSlider by remember(currentStepPercent) { mutableFloatStateOf(currentStepPercent.toFloat()) }
    var currentBybitSymbol by remember(bybitSymbol) { mutableStateOf(bybitSymbol) }""",
"""    var stepSlider by remember(currentStepPercent) { mutableFloatStateOf(currentStepPercent.toFloat()) }
    var okxStepSlider by remember(okxStepPercent) { mutableFloatStateOf(okxStepPercent.toFloat()) }
    var currentBybitSymbol by remember(bybitSymbol) { mutableStateOf(bybitSymbol) }"""
)

# Replace the single Step Percent Card
card_pattern = r'                // Step Percent Card.*?                \}\n\n                Spacer\(modifier = Modifier\.height\(18\.dp\)\)\n\n                // Symbols Card'
# Wait, I need to match carefully. Let's just use string replace.
