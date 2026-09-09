package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.BybitBotApp
import com.example.MainActivity
import com.example.R
import com.example.bot.RebalanceEngine
import com.example.data.local.BotPreferences
import com.example.data.local.entity.LogLevel
import com.example.data.remote.BybitWebSocketClient
import com.example.data.remote.model.BybitOrderDto
import com.example.data.repository.BybitRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class TradingBotService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wakeLock: PowerManager.WakeLock? = null

    private lateinit var preferences: BotPreferences
    private lateinit var repository: BybitRepository
    private lateinit var wsClient: BybitWebSocketClient
    private lateinit var database: com.example.data.local.AppDatabase

    private var pollingJob: Job? = null
    private var isBotLoopRunning = AtomicBoolean(false)
    private var currentMntPrice: Double = 0.0
    private var lastUsdtBalance: Double = 0.0
    private var lastMntBalance: Double = 0.0
    private val notifiedFilledOrderIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    override fun onCreate() {
        super.onCreate()
        val app = application as BybitBotApp
        preferences = app.preferences
        repository = app.repository
        database = app.database
        wsClient = BybitWebSocketClient(serviceScope)

        createNotificationChannels()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_STOP_BOT -> {
                stopBot()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START_BOT, null -> {
                startForeground(NOTIFICATION_ID, buildForegroundNotification("Bybit Bot Başlatılıyor..."))
                startBot()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BybitBot::TradingWakeLock")
                wakeLock?.setReferenceCounted(false)
            }
            // Acquire / Refresh with 24 hours duration
            wakeLock?.acquire(24 * 60 * 60 * 1000L)
        } catch (e: Exception) {
            Log.e("TradingBotService", "WakeLock acquire error", e)
        }
    }

    private fun startBot() {
        if (isBotLoopRunning.getAndSet(true)) return
        preferences.isBotActive = true

        serviceScope.launch {
            val offset = repository.syncServerTime(preferences.isTestnet)
            repository.log(LogLevel.INFO, "BotService", "7/24 Bybit Ticaret Botu başlatıldı (Zaman farkı: $offset ms)")

            // Connect WebSocket
            wsClient.connect(
                key = preferences.apiKey,
                secret = preferences.apiSecret,
                testnet = preferences.isTestnet,
                timeOffsetMs = offset
            )

            // Listen to live price updates
            launch {
                wsClient.priceUpdates.collect { price ->
                    if (price > 0.0 && price != currentMntPrice) {
                        currentMntPrice = price
                        updateNotification()
                    }
                }
            }

            // Listen to real-time order fill updates
            launch {
                wsClient.orderUpdates.collect { order ->
                    handleOrderUpdate(order)
                }
            }

            // Listen to connection status
            launch {
                wsClient.connectionStatus.collect { (connected, message) ->
                    if (connected && message != null) {
                        repository.log(LogLevel.SUCCESS, "WebSocket", message)
                    } else if (!connected && message != null) {
                        repository.log(LogLevel.WARN, "WebSocket", "Bağlantı koptu, yeniden bağlanılıyor: $message")
                    }
                }
            }

            // Start Watchdog Polling Loop
            startWatchdogLoop()
        }
    }

    private fun startWatchdogLoop() {
        pollingJob?.cancel()
        pollingJob = serviceScope.launch {
            var cycleCount = 0
            while (isActive && isBotLoopRunning.get()) {
                try {
                    // 1. Fetch current price if missing or periodically
                    if (currentMntPrice <= 0.0 || cycleCount % 3 == 0) {
                        val tickerRes = repository.getMntTicker()
                        tickerRes.onSuccess { ticker ->
                            currentMntPrice = ticker.currentPrice
                        }
                    }

                    // 2. Fetch balances periodically (every 12 seconds)
                    if (cycleCount % 3 == 0 || lastUsdtBalance <= 0.0) {
                        val balanceRes = repository.getWalletBalance()
                        balanceRes.onSuccess { balances ->
                            lastUsdtBalance = balances["USDT"] ?: 0.0
                            lastMntBalance = balances["MNT"] ?: 0.0
                            updateNotification()
                        }.onFailure { err ->
                            repository.log(LogLevel.ERROR, "Watchdog", "Bakiye alınamadı: ${err.message}")
                        }
                    }

                    // 3. Check active grid limit orders
                    checkActiveOrdersStatus()

                    // Periodic heartbeat log, WakeLock refresh, and log pruning every 5 minutes (~75 cycles)
                    if (cycleCount % 75 == 0) {
                        acquireWakeLock() // Refresh WakeLock for 24/7 background operation
                        repository.pruneLogs()
                        if (currentMntPrice > 0.0) {
                            repository.log(
                                LogLevel.INFO,
                                "Watchdog",
                                "7/24 Bot aktif ve çalışıyor (Anlık MNT: $${RebalanceEngine.format4(currentMntPrice)})"
                            )
                        }
                    }

                    cycleCount++
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    repository.log(LogLevel.ERROR, "Watchdog", "Döngü hatası: ${e.message}")
                }
                delay(4000) // Poll every 4 seconds
            }
        }
    }

    private suspend fun checkActiveOrdersStatus() {
        if (!preferences.isBotActive) return

        val res = repository.reconcileGridOrders(callerTag = "Watchdog")
        res.onSuccess { rec ->
            if (rec.executedOrderFound) {
                val execId = rec.executedOrderId.ifBlank { "exec_${rec.executedSide}_${rec.executedPrice}" }
                if (execId.isNotBlank() && notifiedFilledOrderIds.add(execId)) {
                    val notifId = ALERT_NOTIFICATION_ID_BASE + (execId.hashCode() and 0x7FFFFFFF) % 500
                    sendAlertNotification(
                        "${rec.executedSide ?: "Grid"} Emri Gerçekleşti!",
                        "${rec.message} Baz Fiyat: $${RebalanceEngine.format4(rec.newBasePrice)}",
                        false,
                        notificationId = notifId
                    )
                }
            }
        }
    }

    private suspend fun ensureGridOrdersPlaced() {
        if (!preferences.isBotActive) return

        val tickerRes = repository.getMntTicker()
        val basePrice = tickerRes.getOrNull()?.currentPrice ?: currentMntPrice
        if (basePrice <= 0.0) return

        val balanceRes = repository.getWalletBalance()
        val balances = balanceRes.getOrNull() ?: return
        lastUsdtBalance = balances["USDT"] ?: 0.0
        lastMntBalance = balances["MNT"] ?: 0.0

        val plan = RebalanceEngine.calculateGridOrders(
            usdtBalance = lastUsdtBalance,
            mntBalance = lastMntBalance,
            basePrice = basePrice,
            stepPercent = preferences.stepPercent
        )

        if (plan.isValid) {
            repository.log(
                LogLevel.INFO,
                "GridEngine",
                "Açık emir bulunamadı, ızgara limit emirleri açılıyor (Baz: ${RebalanceEngine.format4(basePrice)})"
            )

            val sellRes = repository.createOrder(
                side = "Sell",
                orderType = "Limit",
                qty = plan.sellMntQty,
                price = plan.sellLimitPrice,
                triggerReason = "GridStepUpSell"
            )
            sellRes.onSuccess { sellId ->
                preferences.activeSellOrderId = sellId
            }

            val buyRes = repository.createOrder(
                side = "Buy",
                orderType = "Limit",
                qty = plan.buyMntQty,
                price = plan.buyLimitPrice,
                triggerReason = "GridStepDownBuy"
            )
            buyRes.onSuccess { buyId ->
                preferences.activeBuyOrderId = buyId
            }

            preferences.lastRebalancePrice = basePrice
            updateNotification()
        }
    }

    private suspend fun handleOrderUpdate(order: BybitOrderDto) {
        if (order.isFilled) {
            val orderId = order.orderId
            if (orderId.isNotBlank() && !notifiedFilledOrderIds.add(orderId)) {
                // Already notified and processed for this exact orderId! Prevent duplicate alerts.
                return
            }

            repository.log(
                LogLevel.SUCCESS,
                "WebSocket",
                "WS Bildirimi: ${order.side} emri DOLDU! Fiyat: ${order.avgPrice} MntQty: ${order.cumExecQty} ($orderId)"
            )

            // Immediately mark order as Filled in Room DB so UI updates instantly
            val fillTime = if (order.updatedTimeMillis > 0L) order.updatedTimeMillis else System.currentTimeMillis()
            val fillPrice = if (order.avgPriceValue > 0.0) order.avgPriceValue else order.priceValue
            val fillQty = if (order.filledQtyValue > 0.0) order.filledQtyValue else order.qtyValue
            repository.recordOrderFilled(
                orderId = orderId,
                side = order.side,
                price = fillPrice,
                qty = fillQty,
                triggerReason = if (order.side.equals("Buy", ignoreCase = true)) "GridStepDownBuy" else "GridStepUpSell",
                fillTime = fillTime
            )

            repository.reconcileGridOrders(callerTag = "WebSocket")

            val notifId = ALERT_NOTIFICATION_ID_BASE + (orderId.hashCode() and 0x7FFFFFFF) % 500
            sendAlertNotification(
                "${order.side} Limit Emri Gerçekleşti!",
                "${order.side} ${order.cumExecQty} MNT @ ${order.avgPrice} USDT. Karşı emir iptal edildi ve yeni ızgara kuruldu.",
                false,
                notificationId = notifId
            )
        }
    }

    private fun updateNotification() {
        val title = "Bybit 7/24 Rebalance Bot: AKTİF"
        val mntVal = lastMntBalance * currentMntPrice
        val total = lastUsdtBalance + mntVal
        val uPct = if (total > 0) (lastUsdtBalance / total * 100).toInt() else 50
        val mPct = if (total > 0) (mntVal / total * 100).toInt() else 50

        val content = "MNT: $${RebalanceEngine.format4(currentMntPrice)} | Portföy: %$uPct USDT / %$mPct MNT ($${RebalanceEngine.format2(total)})"
        val notification = buildForegroundNotification(content)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildForegroundNotification(contentText: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, TradingBotService::class.java).apply {
            action = ACTION_STOP_BOT
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_BOT_STATUS)
            .setContentTitle("Bybit 7/24 Dengeleme Botu")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(R.drawable.ic_launcher_foreground, "Botu Durdur", stopPendingIntent)
            .build()
    }

    private fun sendAlertNotification(
        title: String,
        message: String,
        isError: Boolean,
        notificationId: Int = ALERT_NOTIFICATION_ID_BASE
    ) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ALERTS)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(if (isError) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setDefaults(Notification.DEFAULT_ALL)
            .build()

        manager.notify(notificationId, notification)
    }

    private fun stopBot() {
        isBotLoopRunning.set(false)
        preferences.isBotActive = false
        pollingJob?.cancel()
        wsClient.stop()
        serviceScope.launch {
            repository.log(LogLevel.INFO, "BotService", "Bot durduruldu")
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val statusChannel = NotificationChannel(
                CHANNEL_BOT_STATUS,
                "Bybit Bot Durum Bildirimi",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Botun 7/24 arka planda sürekli çalışmasını sağlayan bildirim"
            }

            val alertChannel = NotificationChannel(
                CHANNEL_ALERTS,
                "Bybit Bot Emir & Hata Bildirimleri",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Gerçekleşen emirler ve kritik hata uyarıları"
                enableVibration(true)
            }

            manager.createNotificationChannel(statusChannel)
            manager.createNotificationChannel(alertChannel)
        }
    }

    override fun onDestroy() {
        stopBot()
        serviceScope.cancel()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        super.onDestroy()
    }

    companion object {
        const val ACTION_START_BOT = "com.example.bybit.action.START_BOT"
        const val ACTION_STOP_BOT = "com.example.bybit.action.STOP_BOT"

        private const val NOTIFICATION_ID = 1001
        private const val ALERT_NOTIFICATION_ID_BASE = 2000
        private const val CHANNEL_BOT_STATUS = "bybit_bot_status_channel"
        private const val CHANNEL_ALERTS = "bybit_bot_alerts_channel"

        fun start(context: Context) {
            val intent = Intent(context, TradingBotService::class.java).apply {
                action = ACTION_START_BOT
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, TradingBotService::class.java).apply {
                action = ACTION_STOP_BOT
            }
            context.startService(intent)
        }
    }
}
