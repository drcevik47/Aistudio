import re

with open("app/src/main/java/com/example/ui/components/SettingsDialog.kt", "r") as f:
    content = f.read()

# I will find the exact bounds using indices
start_idx = content.find("                // Step Percent Card")
end_idx = content.find("                // Symbol Configuration")

if start_idx != -1 and end_idx != -1:
    old_card = content[start_idx:end_idx]
    
    new_card = """                // Bybit Step Percent Card
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

                Spacer(modifier = Modifier.height(12.dp))

"""
    content = content[:start_idx] + new_card + content[end_idx:]
    with open("app/src/main/java/com/example/ui/components/SettingsDialog.kt", "w") as f:
        f.write(content)
    print("Replaced!")
else:
    print("Not found")

