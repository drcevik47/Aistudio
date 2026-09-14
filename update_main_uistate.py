import re

with open("app/src/main/java/com/example/ui/MainViewModel.kt", "r") as f:
    content = f.read()

# Add okxStepPercent to MainUiState
new_uistate = """    val activeOrders: List<BybitOrderDto> = emptyList(),
    val stepPercent: Double = 2.0,
    val okxStepPercent: Double = 2.0,"""
content = re.sub(r'    val activeOrders: List<BybitOrderDto> = emptyList\(\),\n    val stepPercent: Double = 2\.0,', new_uistate, content)

# Initialize it in MutableStateFlow
new_init = """            isBotActive = preferences.isBotActive,
            stepPercent = preferences.stepPercent,
            okxStepPercent = preferences.okxStepPercent,"""
content = re.sub(r'            isBotActive = preferences\.isBotActive,\n            stepPercent = preferences\.stepPercent,', new_init, content)

# Update updateStepPercent to handle OKX
new_update_func = """    fun updateStepPercent(bybitStep: Double, okxStep: Double) {
        preferences.stepPercent = bybitStep
        preferences.okxStepPercent = okxStep
        _uiState.update { it.copy(stepPercent = bybitStep, okxStepPercent = okxStep) }
        viewModelScope.launch {
            repository.log(LogLevel.INFO, "Settings", "Rebalance Grid Step güncellendi: Bybit: $bybitStep%, OKX: $okxStep%")
            refreshData()
        }
    }"""
content = re.sub(r'    fun updateStepPercent\(step: Double\) \{.*?\n    \}', new_update_func, content, flags=re.DOTALL)

with open("app/src/main/java/com/example/ui/MainViewModel.kt", "w") as f:
    f.write(content)

print("Done")
