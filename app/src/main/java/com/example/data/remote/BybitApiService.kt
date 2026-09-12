package com.example.data.remote

import com.example.data.remote.model.BybitApiResponse
import com.example.data.remote.model.CancelOrderResult
import com.example.data.remote.model.CreateOrderResult
import com.example.data.remote.model.ExecutionListResult
import com.example.data.remote.model.OpenOrdersResult
import com.example.data.remote.model.ServerTimeResult
import com.example.data.remote.model.TickersResult
import com.example.data.remote.model.WalletBalanceResult
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HeaderMap
import retrofit2.http.POST
import retrofit2.http.Query

interface BybitApiService {

    @GET("/v5/market/time")
    suspend fun getServerTime(): Response<BybitApiResponse<ServerTimeResult>>

    @GET("/v5/account/wallet-balance")
    suspend fun getWalletBalance(
        @HeaderMap headers: Map<String, String>,
        @Query("accountType") accountType: String = "UNIFIED"
    ): Response<BybitApiResponse<WalletBalanceResult>>

    @GET("/v5/market/tickers")
    suspend fun getTickers(
        @Query("category") category: String = "spot",
        @Query("symbol") symbol: String = "MNTUSDT"
    ): Response<BybitApiResponse<TickersResult>>

    @POST("/v5/order/create")
    suspend fun createOrder(
        @HeaderMap headers: Map<String, String>,
        @Body request: RequestBody
    ): Response<BybitApiResponse<CreateOrderResult>>

    @POST("/v5/order/cancel")
    suspend fun cancelOrder(
        @HeaderMap headers: Map<String, String>,
        @Body request: RequestBody
    ): Response<BybitApiResponse<CancelOrderResult>>

    @POST("/v5/order/cancel-all")
    suspend fun cancelAllOrders(
        @HeaderMap headers: Map<String, String>,
        @Body request: RequestBody
    ): Response<BybitApiResponse<CancelOrderResult>>

    @GET("/v5/order/realtime")
    suspend fun getOpenOrders(
        @HeaderMap headers: Map<String, String>,
        @Query("category") category: String = "spot",
        @Query("symbol") symbol: String = "MNTUSDT"
    ): Response<BybitApiResponse<OpenOrdersResult>>

    @GET("/v5/order/history")
    suspend fun getOrderHistory(
        @HeaderMap headers: Map<String, String>,
        @Query("category") category: String = "spot",
        @Query("symbol") symbol: String = "MNTUSDT",
        @Query("orderId") orderId: String? = null
    ): Response<BybitApiResponse<OpenOrdersResult>>

    @GET("/v5/order/history")
    suspend fun getOrderHistoryList(
        @HeaderMap headers: Map<String, String>,
        @Query("category") category: String = "spot",
        @Query("symbol") symbol: String? = null,
        @Query("startTime") startTime: Long? = null,
        @Query("endTime") endTime: Long? = null,
        @Query("limit") limit: Int = 50,
        @Query(value = "cursor", encoded = true) cursor: String? = null
    ): Response<BybitApiResponse<OpenOrdersResult>>

    @GET("/v5/execution/list")
    suspend fun getExecutionList(
        @HeaderMap headers: Map<String, String>,
        @Query("category") category: String = "spot",
        @Query("symbol") symbol: String? = null,
        @Query("startTime") startTime: Long? = null,
        @Query("endTime") endTime: Long? = null,
        @Query("limit") limit: Int = 100,
        @Query(value = "cursor", encoded = true) cursor: String? = null
    ): Response<BybitApiResponse<ExecutionListResult>>
    @GET("/v5/market/kline")
    suspend fun getKlines(
        @Query("category") category: String = "spot",
        @Query("symbol") symbol: String,
        @Query("interval") interval: String,
        @Query("start") start: Long? = null,
        @Query("end") end: Long? = null,
        @Query("limit") limit: Int = 1000
    ): Response<BybitApiResponse<com.example.data.remote.model.KlineResult>>
}
