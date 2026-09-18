package com.example.data.remote.okx

import com.example.data.remote.okx.model.*
import retrofit2.http.*

interface OkxApiService {
    
    // https://www.okx.com/api/v5/public/instruments?instType=SPOT&instId=MNT-USDT
    @GET("/api/v5/public/instruments")
    suspend fun getInstruments(
        @Query("instType") instType: String = "SPOT",
        @Query("instId") instId: String? = null
    ): OkxResponse<OkxInstrument>

    // https://www.okx.com/api/v5/market/ticker?instId=MNT-USDT
    @GET("/api/v5/market/ticker")
    suspend fun getTicker(
        @Query("instId") instId: String
    ): OkxResponse<OkxTicker>

    // https://www.okx.com/api/v5/account/balance?ccy=MNT,USDT
    @GET("/api/v5/account/balance")
    suspend fun getBalance(
        @Query("ccy", encoded = true) ccy: String? = null
    ): OkxResponse<OkxAccountBalance>

    @GET("/api/v5/asset/balances")
    suspend fun getAssetBalances(
        @Query("ccy", encoded = true) ccy: String? = null
    ): OkxResponse<OkxAssetBalance>

    // https://www.okx.com/api/v5/trade/order
    @POST("/api/v5/trade/order")
    suspend fun placeOrder(
        @Body request: OkxOrderRequest
    ): OkxResponse<OkxOrderResponse>

    // https://www.okx.com/api/v5/trade/cancel-order
    @POST("/api/v5/trade/cancel-order")
    suspend fun cancelOrder(
        @Body request: OkxCancelOrderRequest
    ): OkxResponse<OkxOrderResponse>

    // https://www.okx.com/api/v5/trade/orders-pending
    @GET("/api/v5/trade/orders-pending")
    suspend fun getPendingOrders(
        @Query("instType") instType: String = "SPOT",
        @Query("instId") instId: String? = null,
        @Query("ordType") ordType: String? = null
    ): OkxResponse<OkxOrderDetails>

    // https://www.okx.com/api/v5/trade/orders-history-archive
    @GET("/api/v5/trade/orders-history-archive")
    suspend fun getOrderHistoryArchive(
        @Query("instType") instType: String = "SPOT",
        @Query("instId") instId: String,
        @Query("limit") limit: Int = 100,
        @Query("begin") begin: Long? = null,
        @Query("end") end: Long? = null
    ): OkxResponse<OkxOrderDetails>

    // https://www.okx.com/api/v5/trade/fills (Recent 3 days fills)
    @GET("/api/v5/trade/fills")
    suspend fun getFills(
        @Query("instType") instType: String = "SPOT",
        @Query("instId") instId: String? = null,
        @Query("limit") limit: Int = 100,
        @Query("begin") begin: Long? = null,
        @Query("end") end: Long? = null
    ): OkxResponse<OkxFill>

    // https://www.okx.com/api/v5/trade/fills-history (Historical fills older than 1-2 hours)
    @GET("/api/v5/trade/fills-history")
    suspend fun getFillsHistory(
        @Query("instType") instType: String = "SPOT",
        @Query("instId") instId: String,
        @Query("limit") limit: Int = 100,
        @Query("begin") begin: Long? = null,
        @Query("end") end: Long? = null
    ): OkxResponse<OkxFill>
}
