import re

with open("app/src/main/java/com/example/ui/MainViewModel.kt", "r") as f:
    content = f.read()

old_func = """    fun updateStepPercent(percent: Double) {
        preferences.stepPercent = percent
        _uiState.update { it.copy(stepPercent = percent) }
        recalculateGridPlan()
    }"""
new_func = """    fun updateStepPercent(bybitStep: Double, okxStep: Double) {
        preferences.stepPercent = bybitStep
        preferences.okxStepPercent = okxStep
        _uiState.update { it.copy(stepPercent = bybitStep, okxStepPercent = okxStep) }
        recalculateGridPlan()
    }"""

content = content.replace(old_func, new_func)

with open("app/src/main/java/com/example/ui/MainViewModel.kt", "w") as f:
    f.write(content)

print("Done")
