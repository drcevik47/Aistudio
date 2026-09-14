import re

with open("app/src/main/java/com/example/data/repository/OkxRepository.kt", "r") as f:
    content = f.read()

# Remove the INFO logs for successful balance fetches
content = content.replace('log(LogLevel.INFO, "OKX_API", "account/balance Başarılı. OKX TR Al-Sat bakiye çekildi.")', '// Removed spammy log')
content = content.replace('log(LogLevel.INFO, "OKX_API", "asset/balances Başarılı. OKX TR Fonlama bakiye çekildi.")', '// Removed spammy log')

with open("app/src/main/java/com/example/data/repository/OkxRepository.kt", "w") as f:
    f.write(content)
print("Done")
