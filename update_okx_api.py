with open("app/src/main/java/com/example/data/remote/okx/OkxApiService.kt", "r") as f:
    content = f.read()

new_endpoints = """    @GET("/api/v5/account/balance")
    suspend fun getBalance(
        @Query("ccy") ccy: String? = null
    ): OkxResponse<OkxAccountBalance>

    @GET("/api/v5/asset/balances")
    suspend fun getAssetBalances(
        @Query("ccy") ccy: String? = null
    ): OkxResponse<OkxAccountBalance>"""

content = content.replace('    @GET("/api/v5/account/balance")\n    suspend fun getBalance(\n        @Query("ccy") ccy: String? = null\n    ): OkxResponse<OkxAccountBalance>', new_endpoints)

with open("app/src/main/java/com/example/data/remote/okx/OkxApiService.kt", "w") as f:
    f.write(content)
print("Done")
