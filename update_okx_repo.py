with open("app/src/main/java/com/example/data/repository/OkxRepository.kt", "r") as f:
    content = f.read()

new_methods = """
    suspend fun getTicker(): Result<OkxTicker> {
        return withContext(Dispatchers.IO) {
            try {
                val api = createApiService()
                val response = api.getTicker(preferences.okxSymbol)
                if (response.code == "0" && response.data.isNotEmpty()) {
                    Result.success(response.data.first())
                } else {
                    Result.failure(Exception(response.msg))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getWalletBalance(): Result<Map<String, Double>> {
        return withContext(Dispatchers.IO) {
            try {
                val api = createApiService()
                val response = api.getBalance("${preferences.okxBaseCoin},USDT")
                if (response.code == "0" && response.data.isNotEmpty()) {
                    val map = mutableMapOf<String, Double>()
                    response.data.first().details.forEach { detail ->
                        map[detail.ccy] = detail.availEq.toDoubleOrNull() ?: 0.0
                    }
                    Result.success(map)
                } else {
                    Result.failure(Exception(response.msg))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
"""

content = content.replace("    suspend fun log", new_methods + "\n    suspend fun log")

with open("app/src/main/java/com/example/data/repository/OkxRepository.kt", "w") as f:
    f.write(content)

print("Done")
