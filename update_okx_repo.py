import re

with open("app/src/main/java/com/example/data/repository/OkxRepository.kt", "r") as f:
    content = f.read()

new_wallet = """    suspend fun getWalletBalance(): Result<Map<String, Double>> {
        return withContext(Dispatchers.IO) {
            try {
                val api = createApiService()
                val map = mutableMapOf<String, Double>()
                
                // Fetch Trading Account
                val ccyParam = "${preferences.okxBaseCoin},USDT"
                val tradeRes = api.getBalance(ccyParam)
                if (tradeRes.code == "0" && tradeRes.data.isNotEmpty()) {
                    tradeRes.data.first().details.forEach { detail ->
                        val avail = detail.availEq.toDoubleOrNull() ?: detail.availBal.toDoubleOrNull() ?: 0.0
                        map[detail.ccy] = (map[detail.ccy] ?: 0.0) + avail
                    }
                    log(LogLevel.INFO, "OKX_API", "Bakiye çekildi. Çekilen Data: ${tradeRes.data}")
                } else {
                    val errMsg = "Bakiye API Hatası: Kod=${tradeRes.code} Mesaj=${tradeRes.msg}"
                    log(LogLevel.ERROR, "OKX_API", errMsg)
                    return@withContext Result.failure(Exception(errMsg))
                }
                
                Result.success(map)
            } catch (e: Exception) {
                log(LogLevel.ERROR, "OKX_API", "Bakiye Ağ Hatası: ${e.message}")
                Result.failure(e)
            }
        }
    }"""

content = re.sub(r'    suspend fun getWalletBalance\(\): Result<Map<String, Double>> \{.*?        \}\n    \}', new_wallet, content, flags=re.DOTALL)

with open("app/src/main/java/com/example/data/repository/OkxRepository.kt", "w") as f:
    f.write(content)
print("Done")
