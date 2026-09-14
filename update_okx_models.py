import re
with open("app/src/main/java/com/example/data/remote/okx/model/OkxModels.kt", "r") as f:
    content = f.read()

content = content.replace(
    '@Json(name = "cashBal") val cashBal: String = "0"',
    '@Json(name = "cashBal") val cashBal: String = "0",\n    @Json(name = "availBal") val availBal: String = "0"'
)

with open("app/src/main/java/com/example/data/remote/okx/model/OkxModels.kt", "w") as f:
    f.write(content)
print("Done")
