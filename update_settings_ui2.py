import re

with open("app/src/main/java/com/example/ui/components/SettingsDialog.kt", "r") as f:
    content = f.read()

card_regex = re.compile(r'                // Step Percent Card.*?                \}\n\n                Spacer\(modifier = Modifier\.height\(18\.dp\)\)\n\n                // Symbols Card', re.DOTALL)

# Let's write the replacement. We will call the new composable twice.
new_ui = """                // Bybit Step Percent Card
                StepPercentCard(
                    title = "Bybit Grid Step",
                    sliderValue = stepSlider,
                    onValueChange = { stepSlider = it },
                    tag = "bybit_step_percent_slider"
                )

                Spacer(modifier = Modifier.height(18.dp))

                // OKX TR Step Percent Card
                StepPercentCard(
                    title = "OKX TR Grid Step",
                    sliderValue = okxStepSlider,
                    onValueChange = { okxStepSlider = it },
                    tag = "okx_step_percent_slider"
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Symbols Card"""
                
content = card_regex.sub(new_ui, content)

# Now, we also need to append the new composable function at the end of the file.
new_composable = """
@Composable
fun StepPercentCard(
    title: String,
    sliderValue: Float,
    onValueChange: (Float) -> Unit,
    tag: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MinimalSurfaceElevated),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MinimalSurfaceBorderLight)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Percent, contentDescription = null, tint = MinimalPrimary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MinimalTextPrimary
                    )
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MinimalPrimaryLight
                ) {
                    Text(
                        text = "±${RebalanceEngine.format2(sliderValue.toDouble())}%",
                        fontWeight = FontWeight.Bold,
                        color = MinimalPrimaryDark,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Default: 2.00% (Orders placed at +2% sell and -2% buy to maintain balance)",
                fontSize = 11.sp,
                color = MinimalTextSecondary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Slider(
                value = sliderValue,
                onValueChange = onValueChange,
                valueRange = 0.5f..10.0f,
                steps = 18,
                colors = SliderDefaults.colors(
                    thumbColor = MinimalPrimary,
                    activeTrackColor = MinimalPrimary,
                    inactiveTrackColor = MinimalSurfaceBorder
                ),
                modifier = Modifier.testTag(tag)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(1.0f, 1.5f, 2.0f, 3.0f, 5.0f).forEach { preset ->
                    FilterChip(
                        selected = kotlin.math.abs(sliderValue - preset) < 0.05f,
                        onClick = { onValueChange(preset) },
                        shape = RoundedCornerShape(999.dp),
                        label = { Text("${preset.toInt()}%", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MinimalPrimary,
                            selectedLabelColor = Color.White,
                            containerColor = MinimalSurface,
                            labelColor = MinimalTextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = kotlin.math.abs(sliderValue - preset) < 0.05f,
                            borderColor = MinimalSurfaceBorder,
                            selectedBorderColor = Color.Transparent
                        )
                    )
                }
            }
        }
    }
}
"""
content += new_composable

# Also replace the 'onDismissRequest' on save
content = content.replace(
"""                                    onUpdateStepPercent(stepSlider.toDouble())
                                    onUpdateSymbols(currentBybitSymbol, currentOkxSymbol)
                                    onDismiss()""",
"""                                    onUpdateStepPercent(stepSlider.toDouble(), okxStepSlider.toDouble())
                                    onUpdateSymbols(currentBybitSymbol, currentOkxSymbol)
                                    onDismiss()"""
)

with open("app/src/main/java/com/example/ui/components/SettingsDialog.kt", "w") as f:
    f.write(content)
print("Done")
