import re

with open("app/src/main/java/com/example/ui/components/SettingsDialog.kt", "r") as f:
    content = f.read()

content = content.replace(
    "import androidx.compose.foundation.background",
    "import androidx.compose.foundation.rememberScrollState\nimport androidx.compose.foundation.verticalScroll\nimport androidx.compose.foundation.background"
)

content = content.replace(
    """                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
            ) {""",
    """                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {"""
)

with open("app/src/main/java/com/example/ui/components/SettingsDialog.kt", "w") as f:
    f.write(content)

print("Done")
