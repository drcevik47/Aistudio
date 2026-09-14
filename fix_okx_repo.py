import re

with open("app/src/main/java/com/example/data/repository/OkxRepository.kt", "r") as f:
    content = f.read()

content = content.replace(
    """    private fun createApiService(): OkxApiService {
        val apiKey = preferences.okxApiKey
        val apiSecret = preferences.okxApiSecret
        val passphrase = preferences.okxApiPassphrase""",
    """    private fun createApiService(): OkxApiService {
        val apiKey = preferences.okxApiKey.trim()
        val apiSecret = preferences.okxApiSecret.trim()
        val passphrase = preferences.okxApiPassphrase.trim()"""
)

with open("app/src/main/java/com/example/data/repository/OkxRepository.kt", "w") as f:
    f.write(content)

print("Done")
