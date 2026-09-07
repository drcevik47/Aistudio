package com.example.data.remote

import android.util.Log
import com.example.data.remote.model.BybitOrderDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class BybitWebSocketClient(
    private val scope: CoroutineScope
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private var publicWs: WebSocket? = null
    private var privateWs: WebSocket? = null
    private var pingJob: Job? = null
    private var reconnectJob: Job? = null
    private var reconnectAttempts = 0

    private var apiKey: String = ""
    private var apiSecret: String = ""
    private var isTestnet: Boolean = false
    private var isRunning: Boolean = false
    @Volatile
    private var isIntentionalDisconnect: Boolean = false
    var serverTimeOffsetMs: Long = 0L

    private val _orderUpdates = MutableSharedFlow<BybitOrderDto>(extraBufferCapacity = 64)
    val orderUpdates: SharedFlow<BybitOrderDto> = _orderUpdates.asSharedFlow()

    private val _priceUpdates = MutableSharedFlow<Double>(extraBufferCapacity = 64)
    val priceUpdates: SharedFlow<Double> = _priceUpdates.asSharedFlow()

    private val _connectionStatus = MutableSharedFlow<Pair<Boolean, String?>>(extraBufferCapacity = 16)
    val connectionStatus: SharedFlow<Pair<Boolean, String?>> = _connectionStatus.asSharedFlow()

    // Deduplication cache for filled order events (stores orderId -> timestamp)
    private val recentlyEmittedFilledOrders = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private fun shouldEmitFilledOrder(orderId: String): Boolean {
        if (orderId.isBlank()) return true
        val now = System.currentTimeMillis()
        // Clean entries older than 15 seconds
        recentlyEmittedFilledOrders.entries.removeIf { now - it.value > 15_000L }
        val prev = recentlyEmittedFilledOrders.putIfAbsent(orderId, now)
        return prev == null || (now - prev > 5_000L)
    }

    fun connect(key: String, secret: String, testnet: Boolean, timeOffsetMs: Long = 0L) {
        this.apiKey = key
        this.apiSecret = secret
        this.isTestnet = testnet
        this.serverTimeOffsetMs = timeOffsetMs
        this.isRunning = true

        disconnect()
        startPublicWs()
        if (key.isNotBlank() && secret.isNotBlank()) {
            startPrivateWs()
        }
        startPingLoop()
    }

    private fun getPublicWsUrl(): String {
        return if (isTestnet) "wss://stream-testnet.bybit.com/v5/public/spot"
        else "wss://stream.bybit.com/v5/public/spot"
    }

    private fun getPrivateWsUrl(): String {
        return if (isTestnet) "wss://stream-testnet.bybit.com/v5/private"
        else "wss://stream.bybit.com/v5/private"
    }

    private fun startPublicWs() {
        val request = Request.Builder().url(getPublicWsUrl()).build()
        publicWs = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("BybitWS", "Public WS Connected")
                reconnectAttempts = 0
                val subMsg = JSONObject().apply {
                    put("op", "subscribe")
                    put("args", JSONArray().put("tickers.MNTUSDT"))
                }
                webSocket.send(subMsg.toString())
                _connectionStatus.tryEmit(Pair(true, "Canlı piyasa veri akışı aktif"))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    val op = json.optString("op", "")
                    if (op == "pong" || json.optString("ret_msg") == "pong") {
                        // Healthy heartbeat pong from Bybit
                        return
                    }

                    val topic = json.optString("topic", "")
                    if (topic == "tickers.MNTUSDT") {
                        val dataObj = json.optJSONObject("data")
                        if (dataObj != null) {
                            val lastPrice = dataObj.optDouble("lastPrice", 0.0)
                            if (lastPrice > 0.0) {
                                _priceUpdates.tryEmit(lastPrice)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("BybitWS", "Error parsing public ws message", e)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("BybitWS", "Public WS Failure: ${t.message}")
                _connectionStatus.tryEmit(Pair(false, t.message ?: "Bağlantı koptu"))
                scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("BybitWS", "Public WS Closed: $reason")
                if (isRunning && !isIntentionalDisconnect) {
                    scheduleReconnect()
                }
            }
        })
    }

    private fun startPrivateWs() {
        val request = Request.Builder().url(getPrivateWsUrl()).build()
        privateWs = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("BybitWS", "Private WS Connected, authenticating...")
                val expires = System.currentTimeMillis() + serverTimeOffsetMs + 10000
                val signature = BybitSigner.signWebSocket(expires, apiSecret)
                val authMsg = JSONObject().apply {
                    put("op", "auth")
                    put("args", JSONArray().apply {
                        put(apiKey)
                        put(expires)
                        put(signature)
                    })
                }
                webSocket.send(authMsg.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    val op = json.optString("op", "")
                    if (op == "pong" || json.optString("ret_msg") == "pong") {
                        return
                    }

                    val success = json.optBoolean("success", false)

                    if (op == "auth") {
                        if (success) {
                            Log.d("BybitWS", "Private WS Authenticated successfully! Subscribing to order and execution...")
                            _connectionStatus.tryEmit(Pair(true, "Özel hesap veri akışı aktif"))
                            val subMsg = JSONObject().apply {
                                put("op", "subscribe")
                                put("args", JSONArray().apply {
                                    put("order")
                                    put("execution")
                                })
                            }
                            webSocket.send(subMsg.toString())
                        } else {
                            val retMsg = json.optString("ret_msg", "Auth failed")
                            Log.e("BybitWS", "Private WS Auth failed: $retMsg")
                            _connectionStatus.tryEmit(Pair(false, "Özel WS Doğrulama Hatası: $retMsg"))
                        }
                    }

                    val topic = json.optString("topic", "")
                    if (topic == "order") {
                        val dataArray = json.optJSONArray("data")
                        if (dataArray != null) {
                            for (i in 0 until dataArray.length()) {
                                val item = dataArray.getJSONObject(i)
                                val order = BybitOrderDto(
                                    orderId = item.optString("orderId", ""),
                                    orderLinkId = item.optString("orderLinkId", ""),
                                    symbol = item.optString("symbol", ""),
                                    price = item.optString("price", "0"),
                                    qty = item.optString("qty", "0"),
                                    side = item.optString("side", ""),
                                    orderType = item.optString("orderType", ""),
                                    orderStatus = item.optString("orderStatus", ""),
                                    cumExecQty = item.optString("cumExecQty", "0"),
                                    cumExecValue = item.optString("cumExecValue", "0"),
                                    avgPrice = item.optString("avgPrice", "0"),
                                    createdTime = item.optString("createdTime", "0"),
                                    updatedTime = item.optString("updatedTime", "0")
                                )
                                if (!order.isFilled || shouldEmitFilledOrder(order.orderId)) {
                                    _orderUpdates.tryEmit(order)
                                }
                            }
                        }
                    } else if (topic == "execution") {
                        val dataArray = json.optJSONArray("data")
                        if (dataArray != null) {
                            for (i in 0 until dataArray.length()) {
                                val item = dataArray.getJSONObject(i)
                                val orderId = item.optString("orderId", "")
                                if (shouldEmitFilledOrder(orderId)) {
                                    val order = BybitOrderDto(
                                        orderId = orderId,
                                        orderLinkId = item.optString("orderLinkId", ""),
                                        symbol = item.optString("symbol", ""),
                                        price = item.optString("execPrice", "0"),
                                        qty = item.optString("execQty", "0"),
                                        side = item.optString("side", ""),
                                        orderType = item.optString("orderType", ""),
                                        orderStatus = "Filled",
                                        cumExecQty = item.optString("execQty", "0"),
                                        cumExecValue = item.optString("execValue", "0"),
                                        avgPrice = item.optString("execPrice", "0"),
                                        createdTime = item.optString("execTime", "0"),
                                        updatedTime = item.optString("execTime", "0")
                                    )
                                    _orderUpdates.tryEmit(order)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("BybitWS", "Error parsing private ws message", e)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("BybitWS", "Private WS Failure: ${t.message}")
                scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("BybitWS", "Private WS Closed: $reason")
                if (isRunning && !isIntentionalDisconnect) {
                    scheduleReconnect()
                }
            }
        })
    }

    private fun startPingLoop() {
        pingJob?.cancel()
        pingJob = scope.launch(Dispatchers.IO) {
            while (isActive && isRunning) {
                delay(15000) // Send ping every 15s to keep within Bybit's 20s timeout
                try {
                    val ping = JSONObject().put("op", "ping").toString()
                    publicWs?.send(ping)
                    privateWs?.send(ping)
                } catch (e: Exception) {
                    // Ignore ping failures
                }
            }
        }
    }

    private fun scheduleReconnect() {
        if (!isRunning) return
        if (reconnectJob?.isActive == true) return // Already reconnecting
        reconnectJob = scope.launch(Dispatchers.IO) {
            val backoffMs = (reconnectAttempts * 2000L).coerceIn(2000L, 8000L)
            reconnectAttempts++
            delay(backoffMs)
            if (isRunning) {
                Log.d("BybitWS", "Reconnecting WebSockets (attempt $reconnectAttempts after ${backoffMs}ms)...")
                disconnect()
                startPublicWs()
                if (apiKey.isNotBlank() && apiSecret.isNotBlank()) {
                    startPrivateWs()
                }
            }
        }
    }

    fun disconnect() {
        isIntentionalDisconnect = true
        try {
            publicWs?.close(1000, "App closed")
            privateWs?.close(1000, "App closed")
        } catch (e: Exception) {
            // Ignore
        }
        publicWs = null
        privateWs = null
        isIntentionalDisconnect = false
    }

    fun stop() {
        isRunning = false
        pingJob?.cancel()
        reconnectJob?.cancel()
        disconnect()
    }
}
