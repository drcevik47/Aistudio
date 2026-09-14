import re

with open("app/src/main/java/com/example/data/local/BotPreferences.kt", "r") as f:
    content = f.read()

# Add okxStepPercent after stepPercent
new_step_percent = """    var stepPercent: Double
        get() = prefs.getFloat(KEY_STEP_PERCENT, 2.0f).toDouble()
        set(value) = prefs.edit().putFloat(KEY_STEP_PERCENT, value.toFloat()).apply()

    var okxStepPercent: Double
        get() = prefs.getFloat(KEY_OKX_STEP_PERCENT, 2.0f).toDouble()
        set(value) = prefs.edit().putFloat(KEY_OKX_STEP_PERCENT, value.toFloat()).apply()"""

content = re.sub(r'    var stepPercent: Double\n        get\(\) = prefs\.getFloat\(KEY_STEP_PERCENT, 2\.0f\)\.toDouble\(\)\n        set\(value\) = prefs\.edit\(\)\.putFloat\(KEY_STEP_PERCENT, value\.toFloat\(\)\)\.apply\(\)', new_step_percent, content)

# Add KEY_OKX_STEP_PERCENT to companion object
new_companion = """        private const val KEY_STEP_PERCENT = "bybit_step_percent"
        private const val KEY_OKX_STEP_PERCENT = "okx_step_percent\""""
content = re.sub(r'        private const val KEY_STEP_PERCENT = "bybit_step_percent"', new_companion, content)

with open("app/src/main/java/com/example/data/local/BotPreferences.kt", "w") as f:
    f.write(content)

print("Done")
