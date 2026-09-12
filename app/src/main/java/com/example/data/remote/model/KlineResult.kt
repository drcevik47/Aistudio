package com.example.data.remote.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class KlineResult(
    @Json(name = "symbol") val symbol: String,
    @Json(name = "category") val category: String,
    @Json(name = "list") val list: List<List<String>>
)
