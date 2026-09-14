import re

with open("app/src/main/java/com/example/ui/components/SettingsDialog.kt", "r") as f:
    content = f.read()

content = content.replace(
    "import androidx.compose.ui.platform.LocalContext",
    "import androidx.compose.ui.platform.LocalContext\nimport androidx.compose.ui.platform.LocalConfiguration"
)

content = content.replace(
    """        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .testTag("settings_dialog"),""",
    """        val configuration = LocalConfiguration.current
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = (configuration.screenHeightDp * 0.85).dp)
                .testTag("settings_dialog"),"""
)

content = content.replace(
    "import androidx.compose.foundation.layout.height\n",
    "import androidx.compose.foundation.layout.height\nimport androidx.compose.foundation.layout.heightIn\n"
)

with open("app/src/main/java/com/example/ui/components/SettingsDialog.kt", "w") as f:
    f.write(content)

print("Done")
