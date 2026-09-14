import re

with open("app/src/main/java/com/example/data/remote/okx/model/OkxModels.kt", "r") as f:
    content = f.read()

if "data class OkxAssetBalance" not in content:
    content += """

@JsonClass(generateAdapter = true)
data class OkxAssetBalance(
    @Json(name = "ccy") val ccy: String = "",
    @Json(name = "availBal") val availBal: String = "0"
)
"""
    with open("app/src/main/java/com/example/data/remote/okx/model/OkxModels.kt", "w") as f:
        f.write(content)

with open("app/src/main/java/com/example/data/remote/okx/OkxApiService.kt", "r") as f:
    content2 = f.read()

content2 = content2.replace(
    """    @GET("/api/v5/asset/balances")
    suspend fun getAssetBalances(
        @Query("ccy") ccy: String? = null
    ): OkxResponse<OkxAccountBalance>""",
    """    @GET("/api/v5/asset/balances")
    suspend fun getAssetBalances(
        @Query("ccy") ccy: String? = null
    ): OkxResponse<OkxAssetBalance>"""
)

with open("app/src/main/java/com/example/data/remote/okx/OkxApiService.kt", "w") as f:
    f.write(content2)

print("Done")
