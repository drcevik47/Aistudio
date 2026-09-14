package com.example.data.remote.okx.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class OkxResponse<T>(
    @Json(name = "code") val code: String = "",
    @Json(name = "msg") val msg: String = "",
    @Json(name = "data") val data: List<T> = emptyList()
)

@JsonClass(generateAdapter = true)
data class OkxTicker(
    @Json(name = "instId") val instId: String = "",
    @Json(name = "last") val last: String = "",
    @Json(name = "askPx") val askPx: String = "",
    @Json(name = "bidPx") val bidPx: String = "",
    @Json(name = "ts") val ts: String = ""
)

@JsonClass(generateAdapter = true)
data class OkxAccountBalance(
    @Json(name = "details") val details: List<OkxBalanceDetail> = emptyList()
)

@JsonClass(generateAdapter = true)
data class OkxBalanceDetail(
    @Json(name = "ccy") val ccy: String = "",
    @Json(name = "availEq") val availEq: String = "0",
    @Json(name = "cashBal") val cashBal: String = "0",
    @Json(name = "availBal") val availBal: String = "0"
)

@JsonClass(generateAdapter = true)
data class OkxOrderRequest(
    @Json(name = "instId") val instId: String,
    @Json(name = "tdMode") val tdMode: String = "cash",
    @Json(name = "side") val side: String, // "buy" or "sell"
    @Json(name = "ordType") val ordType: String = "limit",
    @Json(name = "sz") val sz: String,
    @Json(name = "px") val px: String? = null,
    @Json(name = "tgtCcy") val tgtCcy: String? = null,
    @Json(name = "clOrdId") val clOrdId: String? = null
)

@JsonClass(generateAdapter = true)
data class OkxOrderResponse(
    @Json(name = "ordId") val ordId: String = "",
    @Json(name = "clOrdId") val clOrdId: String = "",
    @Json(name = "tag") val tag: String = "",
    @Json(name = "sCode") val sCode: String = "",
    @Json(name = "sMsg") val sMsg: String = ""
)

@JsonClass(generateAdapter = true)
data class OkxCancelOrderRequest(
    @Json(name = "instId") val instId: String,
    @Json(name = "ordId") val ordId: String? = null,
    @Json(name = "clOrdId") val clOrdId: String? = null
)

@JsonClass(generateAdapter = true)
data class OkxOrderDetails(
    @Json(name = "instId") val instId: String = "",
    @Json(name = "ordId") val ordId: String = "",
    @Json(name = "clOrdId") val clOrdId: String = "",
    @Json(name = "state") val state: String = "", // canceled, live, partially_filled, filled
    @Json(name = "side") val side: String = "",
    @Json(name = "px") val px: String = "",
    @Json(name = "sz") val sz: String = "",
    @Json(name = "accFillSz") val accFillSz: String = "",
    @Json(name = "avgPx") val avgPx: String = "",
    @Json(name = "cTime") val cTime: String = "",
    @Json(name = "uTime") val uTime: String = "",
    @Json(name = "fee") val fee: String = "",
    @Json(name = "feeCcy") val feeCcy: String = ""
)

@JsonClass(generateAdapter = true)
data class OkxFill(
    @Json(name = "instId") val instId: String = "",
    @Json(name = "ordId") val ordId: String = "",
    @Json(name = "billId") val billId: String = "",
    @Json(name = "fillPx") val fillPx: String = "",
    @Json(name = "fillSz") val fillSz: String = "",
    @Json(name = "side") val side: String = "",
    @Json(name = "fee") val fee: String = "",
    @Json(name = "feeCcy") val feeCcy: String = "",
    @Json(name = "ts") val ts: String = "",
    @Json(name = "execType") val execType: String = "" // T (Taker) or M (Maker)
)


@JsonClass(generateAdapter = true)
data class OkxAssetBalance(
    @Json(name = "ccy") val ccy: String = "",
    @Json(name = "availBal") val availBal: String = "0"
)
