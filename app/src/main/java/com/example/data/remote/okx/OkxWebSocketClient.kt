package com.example.data.remote.okx

import android.util.Base64
import android.util.Log
import com.example.data.remote.okx.model.OkxOrderDetails
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
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
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class OkxWebSocketClient(
    private val scope: CoroutineScope,
    initialApiKey: String = "",
    initialApiSecret: String = "",
    initialPassphrase: String = "",
    initialIsTestnet: Boolean = false,
    initialActiveSymbol: String = "BTC-USDT"
) {
    private var apiKey: String = initialApiKey
    private var apiSecret: String = initialApiSecret
    private var passphrase: String = initialPassphrase
    private var isTestnet: Boolean = initialIsTestnet
    private var activeSymbol: String = initialActiveSymbol

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private var publicWs: WebSocket? = null
    private var privateWs: WebSocket? = null
    
    private val _priceUpdates = MutableSharedFlow<Double>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val priceUpdates: SharedFlow<Double> = _priceUpdates

    private val _orderUpdates = MutableSharedFlow<OkxOrderDetails>(
        replay = 10,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val orderUpdates: SharedFlow<OkxOrderDetails> = _orderUpdates

    private val _connectionStatus = MutableSharedFlow<Pair<Boolean, String>>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val connectionStatus: SharedFlow<Pair<Boolean, String>> = _connectionStatus

    @Volatile
    private var isIntentionalDisconnect = false
    @Volatile
    private var lastActivityTimeMs = 0L
    private var isRunning = false
    private var reconnectJob: Job? = null
    private var reconnectAttempts = 0
    private var pingJob: Job? = null

    val isConnected: Boolean
        get() = isRunning && (publicWs != null || privateWs != null)

    val isPrivateConnected: Boolean
        get() = isRunning && privateWs != null

    // Deduplication cache for filled order events (stores orderId -> timestamp)
    private val recentlyEmittedFilledOrders = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private fun shouldEmitFilledOrder(orderId: String): Boolean {
        if (orderId.isBlank()) return true
        val now = System.currentTimeMillis()
        recentlyEmittedFilledOrders.entries.removeIf { now - it.value > 60_000L }
        val prev = recentlyEmittedFilledOrders.putIfAbsent(orderId, now)
        return prev == null
    }

    private fun getPublicWsUrl(): String {
        return if (isTestnet) "wss://wspap.okx.com:8443/ws/v5/public?brokerId=9999"
        else "wss://ws.okx.com:8443/ws/v5/public"
    }

    private fun getPrivateWsUrl(): String {
        return if (isTestnet) "wss://wspap.okx.com:8443/ws/v5/private?brokerId=9999"
        else "wss://ws.okx.com:8443/ws/v5/private"
    }

    fun connect(
        key: String = apiKey,
        secret: String = apiSecret,
        passphrase: String = this.passphrase,
        testnet: Boolean = isTestnet,
        symbol: String = activeSymbol
    ) {
        this.apiKey = key
        this.apiSecret = secret
        this.passphrase = passphrase
        this.isTestnet = testnet
        this.activeSymbol = symbol
        this.isRunning = true
        this.isIntentionalDisconnect = false
        this.reconnectAttempts = 0
        Log.d("OkxWS", "Starting OKX WebSockets for symbol: $activeSymbol")
        disconnectInternal()
        startPublicWs()
        if (apiKey.isNotBlank() && apiSecret.isNotBlank() && this.passphrase.isNotBlank()) {
            startPrivateWs()
        }
        startPingLoop()
    }

    fun start() {
        connect()
    }

    private fun startPublicWs() {
        val request = Request.Builder().url(getPublicWsUrl()).build()
        val ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (webSocket !== publicWs) return
                Log.d("OkxWS", "Public WS Connected, subscribing to tickers: $activeSymbol")
                reconnectAttempts = 0
                lastActivityTimeMs = System.currentTimeMillis()
                val subMsg = JSONObject().apply {
                    put("op", "subscribe")
                    put("args", JSONArray().apply {
                        put(JSONObject().apply {
                            put("channel", "tickers")
                            put("instId", activeSymbol)
                        })
                    })
                }
                webSocket.send(subMsg.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (webSocket !== publicWs) return
                lastActivityTimeMs = System.currentTimeMillis()
                try {
                    if (text == "pong") return
                    val json = JSONObject(text)
                    val arg = json.optJSONObject("arg")
                    if (arg != null && arg.optString("channel") == "tickers") {
                        val dataArray = json.optJSONArray("data")
                        if (dataArray != null && dataArray.length() > 0) {
                            val data = dataArray.getJSONObject(0)
                            val lastPrice = data.optString("last", "0").toDoubleOrNull() ?: 0.0
                            if (lastPrice > 0.0) {
                                _priceUpdates.tryEmit(lastPrice)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("OkxWS", "Error parsing public ws message", e)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (isIntentionalDisconnect || !isRunning || webSocket !== publicWs) return
                Log.e("OkxWS", "Public WS Failure: ${t.message}")
                scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (isIntentionalDisconnect || !isRunning || webSocket !== publicWs) return
                scheduleReconnect()
            }
        })
        publicWs = ws
    }

    private fun startPrivateWs() {
        val request = Request.Builder().url(getPrivateWsUrl()).build()
        val ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (webSocket !== privateWs) return
                Log.d("OkxWS", "Private WS Connected, authenticating...")
                val timestamp = (System.currentTimeMillis() / 1000).toString()
                val signMessage = timestamp + "GET" + "/users/self/verify"
                val signature = generateSignature(apiSecret, signMessage)
                
                val authMsg = JSONObject().apply {
                    put("op", "login")
                    put("args", JSONArray().apply {
                        put(JSONObject().apply {
                            put("apiKey", apiKey)
                            put("passphrase", passphrase)
                            put("timestamp", timestamp)
                            put("sign", signature)
                        })
                    })
                }
                webSocket.send(authMsg.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (webSocket !== privateWs) return
                lastActivityTimeMs = System.currentTimeMillis()
                try {
                    if (text == "pong") return
                    val json = JSONObject(text)
                    
                    val event = json.optString("event", "")
                    if (event == "login") {
                        val code = json.optString("code", "")
                        if (code == "0") {
                            Log.d("OkxWS", "Private WS Authenticated successfully! Subscribing to orders...")
                            _connectionStatus.tryEmit(Pair(true, "OKX Özel hesap veri akışı aktif"))
                            
                            val subMsg = JSONObject().apply {
                                put("op", "subscribe")
                                put("args", JSONArray().apply {
                                    put(JSONObject().apply {
                                        put("channel", "orders")
                                        put("instType", "ANY")
                                        put("instId", activeSymbol)
                                    })
                                })
                            }
                            webSocket.send(subMsg.toString())
                        } else {
                            val msg = json.optString("msg", "Auth failed")
                            Log.e("OkxWS", "Private WS Auth failed: $msg")
                            _connectionStatus.tryEmit(Pair(false, "OKX Doğrulama Hatası: $msg"))
                        }
                    }
                    
                    val arg = json.optJSONObject("arg")
                    if (arg != null && arg.optString("channel") == "orders") {
                        val dataArray = json.optJSONArray("data")
                        if (dataArray != null) {
                            for (i in 0 until dataArray.length()) {
                                val item = dataArray.getJSONObject(i)
                                val order = OkxOrderDetails(
                                    instId = item.optString("instId", ""),
                                    ordId = item.optString("ordId", ""),
                                    clOrdId = item.optString("clOrdId", ""),
                                    state = item.optString("state", ""),
                                    side = item.optString("side", ""),
                                    px = item.optString("px", ""),
                                    sz = item.optString("sz", ""),
                                    accFillSz = item.optString("accFillSz", ""),
                                    avgPx = item.optString("avgPx", ""),
                                    cTime = item.optString("cTime", ""),
                                    uTime = item.optString("uTime", ""),
                                    fee = item.optString("fee", ""),
                                    feeCcy = item.optString("feeCcy", "")
                                )
                                if (!order.state.equals("filled", ignoreCase = true) || shouldEmitFilledOrder(order.ordId)) {
                                    _orderUpdates.tryEmit(order)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("OkxWS", "Error parsing private ws message", e)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (isIntentionalDisconnect || !isRunning || webSocket !== privateWs) return
                Log.e("OkxWS", "Private WS Failure: ${t.message}")
                scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (isIntentionalDisconnect || !isRunning || webSocket !== privateWs) return
                scheduleReconnect()
            }
        })
        privateWs = ws
    }

    private fun startPingLoop() {
        pingJob?.cancel()
        pingJob = scope.launch(Dispatchers.IO) {
            while (isActive && isRunning) {
                delay(15000)
                try {
                    val pubSent = publicWs?.send("ping") ?: false
                    val privSent = if (privateWs != null) (privateWs?.send("ping") ?: false) else true

                    val now = System.currentTimeMillis()
                    val isZombie = (lastActivityTimeMs > 0 && (now - lastActivityTimeMs > 45_000L)) ||
                            (!pubSent && publicWs != null) ||
                            (!privSent && privateWs != null)

                    if (isZombie) {
                        Log.w("OkxWS", "Zombie or dead socket detected (no activity for ${now - lastActivityTimeMs}ms or ping send failed). Reconnecting...")
                        scheduleReconnect()
                    }
                } catch (e: Exception) {
                    scheduleReconnect()
                }
            }
        }
    }

    private fun scheduleReconnect() {
        if (!isRunning || isIntentionalDisconnect) return
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch(Dispatchers.IO) {
            val backoffMs = (2000L * Math.pow(1.5, reconnectAttempts.coerceAtMost(8).toDouble()).toLong())
                .coerceIn(2000L, 30_000L)
            reconnectAttempts++
            delay(backoffMs)
            if (isRunning && !isIntentionalDisconnect) {
                Log.d("OkxWS", "Reconnecting OKX WebSockets (attempt $reconnectAttempts after ${backoffMs}ms)...")
                disconnectInternal()
                startPublicWs()
                if (apiKey.isNotBlank() && apiSecret.isNotBlank() && passphrase.isNotBlank()) {
                    startPrivateWs()
                }
            }
        }
    }

    fun reconnectNow() {
        if (!isRunning || isIntentionalDisconnect) return
        scope.launch(Dispatchers.IO) {
            Log.d("OkxWS", "Forced reconnectNow requested...")
            reconnectJob?.cancel()
            reconnectAttempts = 0
            disconnectInternal()
            startPublicWs()
            if (apiKey.isNotBlank() && apiSecret.isNotBlank() && passphrase.isNotBlank()) {
                startPrivateWs()
            }
        }
    }

    fun disconnect() {
        isIntentionalDisconnect = true
        disconnectInternal()
    }

    private fun disconnectInternal() {
        val wsPublic = publicWs
        val wsPrivate = privateWs
        publicWs = null
        privateWs = null
        try {
            wsPublic?.close(1000, "App closed")
            wsPrivate?.close(1000, "App closed")
        } catch (e: Exception) {
            // Ignore
        }
    }

    fun stop() {
        isRunning = false
        isIntentionalDisconnect = true
        pingJob?.cancel()
        reconnectJob?.cancel()
        disconnectInternal()
    }

    private fun generateSignature(secret: String, message: String): String {
        val hmacSha256 = "HmacSHA256"
        val secretKeySpec = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), hmacSha256)
        val mac = Mac.getInstance(hmacSha256)
        mac.init(secretKeySpec)
        val rawHmac = mac.doFinal(message.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(rawHmac, Base64.NO_WRAP)
    }
}
