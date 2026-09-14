import re

with open("app/src/main/java/com/example/data/remote/okx/OkxAuthInterceptor.kt", "r") as f:
    content = f.read()

new_timestamp_method = """    private fun getIso8601Timestamp(): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return sdf.format(java.util.Date())
    }"""

content = re.sub(r'    private fun getIso8601Timestamp\(\): String \{.*?    \}', new_timestamp_method, content, flags=re.DOTALL)

with open("app/src/main/java/com/example/data/remote/okx/OkxAuthInterceptor.kt", "w") as f:
    f.write(content)

with open("app/src/main/java/com/example/data/remote/okx/OkxApiService.kt", "r") as f:
    content_api = f.read()

content_api = content_api.replace('@Query("ccy")', '@Query("ccy", encoded = true)')
with open("app/src/main/java/com/example/data/remote/okx/OkxApiService.kt", "w") as f:
    f.write(content_api)

print("Done")
