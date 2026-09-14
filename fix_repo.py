import re

with open("app/src/main/java/com/example/data/repository/OkxRepository.kt", "r") as f:
    content = f.read()

# Let's cleanly replace the duplicate part
bad_part = """    suspend fun getWalletBalance(): Result<Map<String, Double>> {
        return withContext(Dispatchers.IO) {
            try {
                val api = createApiService()
                val map = mutableMapOf<String, Double>()
                
                // Fetch Trading Account
                val tradeRes = api.getBalance("${preferences.okxBaseCoin},USDT")
                if (tradeRes.code == "0" && tradeRes.data.isNotEmpty()) {
                    tradeRes.data.first().details.forEach { detail ->
                        val avail = detail.availEq.toDoubleOrNull() ?: detail.availBal.toDoubleOrNull() ?: 0.0
                        map[detail.ccy] = (map[detail.ccy] ?: 0.0) + avail
                    }
                }

                // Fetch Funding Account
                try {
                    val fundRes = api.getAssetBalances("${preferences.okxBaseCoin},USDT")
                    if (fundRes.code == "0" && fundRes.data.isNotEmpty()) {
                        // funding account might return list of balances directly in data or inside details.
                        // Actually asset/balances returns data as list of balances.
                        // So fundRes.data is List<OkxAccountBalance>? No, asset/balances returns data: [ { ccy: "BTC", availBal: "1" } ]
                        // Let's just catch it if it fails parsing.
                    }
                } catch(e: Exception) {
                    // ignore funding parsing errors
                }

                Result.success(map)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
                    Result.success(map)
                } else {
                    Result.failure(Exception(response.msg))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }"""

good_part = """    suspend fun getWalletBalance(): Result<Map<String, Double>> {
        return withContext(Dispatchers.IO) {
            try {
                val api = createApiService()
                val map = mutableMapOf<String, Double>()
                
                // Fetch Trading Account
                val tradeRes = api.getBalance("${preferences.okxBaseCoin},USDT")
                if (tradeRes.code == "0" && tradeRes.data.isNotEmpty()) {
                    tradeRes.data.first().details.forEach { detail ->
                        val avail = detail.availEq.toDoubleOrNull() ?: detail.availBal.toDoubleOrNull() ?: 0.0
                        map[detail.ccy] = (map[detail.ccy] ?: 0.0) + avail
                    }
                }
                
                Result.success(map)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }"""

content = content.replace(bad_part, good_part)
with open("app/src/main/java/com/example/data/repository/OkxRepository.kt", "w") as f:
    f.write(content)
print("Done")
