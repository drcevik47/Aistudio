import re

with open("app/src/main/java/com/example/ui/components/SettingsDialog.kt", "r") as f:
    content = f.read()

# Update function signature
old_sig = """@Composable
fun SettingsDialog(
    currentStepPercent: Double,
    isTestnet: Boolean,
    bybitSymbol: String,
    okxSymbol: String,
    onUpdateStepPercent: (Double) -> Unit,"""
new_sig = """@Composable
fun SettingsDialog(
    currentStepPercent: Double,
    okxStepPercent: Double,
    isTestnet: Boolean,
    bybitSymbol: String,
    okxSymbol: String,
    onUpdateStepPercent: (Double, Double) -> Unit,"""
content = content.replace(old_sig, new_sig)

# Update states inside
old_states = """    var stepSlider by remember(currentStepPercent) { mutableFloatStateOf(currentStepPercent.toFloat()) }
    var currentBybitSymbol by remember(bybitSymbol) { mutableStateOf(bybitSymbol) }"""
new_states = """    var stepSlider by remember(currentStepPercent) { mutableFloatStateOf(currentStepPercent.toFloat()) }
    var okxStepSlider by remember(okxStepPercent) { mutableFloatStateOf(okxStepPercent.toFloat()) }
    var currentBybitSymbol by remember(bybitSymbol) { mutableStateOf(bybitSymbol) }"""
content = content.replace(old_states, new_states)

# Replace the single Step Percent Card with two, one for Bybit and one for OKX
old_card_start = """                // Step Percent Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MinimalSurfaceElevated),"""
# Let's write a python script to replace the entire Step Percent Card
