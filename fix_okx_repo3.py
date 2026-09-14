import re

with open("app/src/main/java/com/example/data/repository/OkxRepository.kt", "r") as f:
    content = f.read()

new_wallet = """    suspend fun getWalletBalance(): Result<Map<String, Double>> {
        return withContext(Dispatchers.IO) {
            try {
                val api = createApiService()
                val map = mutableMapOf<String, Double>()
                
                val ccyParam = "${preferences.okxBaseCoin},USDT"
                var fetchSuccess = false
                var errorMsg = ""
                
                // 1. Try account/balance (Trading / Unified account)
                try {
                    val tradeRes = api.getBalance(ccyParam)
                    if (tradeRes.code == "0" && tradeRes.data.isNotEmpty()) {
                        tradeRes.data.first().details.forEach { detail ->
                            val avail = detail.availEq.toDoubleOrNull() ?: detail.availBal.toDoubleOrNull() ?: 0.0
                            map[detail.ccy] = (map[detail.ccy] ?: 0.0) + avail
                        }
                        fetchSuccess = true
                        log(LogLevel.INFO, "OKX_API", "account/balance Başarılı. OKX TR Al-Sat bakiye çekildi.")
                    } else {
                        errorMsg += "[account/balance: ${tradeRes.code} - ${tradeRes.msg}] "
                    }
                } catch(e: Exception) {
                    errorMsg += "[account/balance Ağ Hatası: ${e.message}] "
                }

                // 2. Try asset/balances (Funding account)
                if (!fetchSuccess || map.values.all { it == 0.0 }) {
                    try {
                        val fundRes = api.getAssetBalances(ccyParam)
                        if (fundRes.code == "0" && fundRes.data.isNotEmpty()) {
                            fundRes.data.forEach { asset ->
                                val avail = asset.availBal.toDoubleOrNull() ?: 0.0
                                map[asset.ccy] = (map[asset.ccy] ?: 0.0) + avail
                            }
                            fetchSuccess = true
                            log(LogLevel.INFO, "OKX_API", "asset/balances Başarılı. OKX TR Fonlama bakiye çekildi.")
                        } else {
                            errorMsg += "[asset/balances: ${fundRes.code} - ${fundRes.msg}] "
                        }
                    } catch(e: Exception) {
                        errorMsg += "[asset/balances Ağ Hatası: ${e.message}] "
                    }
                }
                
                if (fetchSuccess) {
                    Result.success(map)
                } else {
                    log(LogLevel.ERROR, "OKX_API", "Bakiye API Hatası: $errorMsg")
                    Result.failure(Exception(errorMsg))
                }
            } catch (e: Exception) {
                log(LogLevel.ERROR, "OKX_API", "Bakiye Kritik Ağ Hatası: ${e.message}")
                Result.failure(e)
            }
        }
    }"""

content = re.sub(r'    suspend fun getWalletBalance\(\): Result<Map<String, Double>> \{.*?        \}\n    \}', new_wallet, content, flags=re.DOTALL)

with open("app/src/main/java/com/example/data/repository/OkxRepository.kt", "w") as f:
    f.write(content)
print("Done")
