import re
with open("app/src/main/java/com/example/data/repository/OkxRepository.kt", "r") as f:
    content = f.read()

content = content.replace(
    'val baseUrl = "https://www.okx.com"',
    'val baseUrl = "https://tr.okx.com"'
)

with open("app/src/main/java/com/example/data/repository/OkxRepository.kt", "w") as f:
    f.write(content)
print("Done")
