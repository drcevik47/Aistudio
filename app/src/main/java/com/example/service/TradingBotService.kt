package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
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
    private var wifiLock: WifiManager.WifiLock? = null
    private lateinit var preferences: BotPreferences
    private lateinit var repository: BybitRepository
    private lateinit var okxRepository: com.example.data.repository.OkxRepository
    private lateinit var wsClient: BybitWebSocketClient
    private lateinit var database: com.example.data.local.AppDatabase
    private var pollingJob: Job? = null
    private var isBotLoopRunning = AtomicBoolean(false)
    private var currentBasePrice: Double = 0.0
    private var lastUsdtBalance: Double = 0.0
    private var lastBaseBalance: Double = 0.0

    override fun onCreate() {
        super.onCreate()
        val app = application as BybitBotApp
        preferences = app.preferences
        repository = app.repository
        okxRepository = app.okxRepository
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
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(NOTIFICATION_ID, buildForegroundNotification("Bybit Bot Başlatılıyor..."), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                    } else {
                        startForeground(NOTIFICATION_ID, buildForegroundNotification("Bybit Bot Başlatılıyor..."))
                    }
                } catch (e: Exception) {
                    Log.e("TradingBotService", "startForeground failed", e)
                }
                startBot()
                scheduleKeepAliveAlarm()
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
            wakeLock?.acquire(24 * 60 * 60 * 1000L)

            if (wifiLock == null) {
                val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                @Suppress("DEPRECATION")
                wifiLock = wifiManager?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "BybitBot::TradingWifiLock")
                wifiLock?.setReferenceCounted(false)
            }
            wifiLock?.acquire()
        } catch (e: Exception) {
            Log.e("TradingBotService", "WakeLock/WifiLock acquire error", e)
        }
    }

    private fun startBot() {
        if (isBotLoopRunning.getAndSet(true)) return
        preferences.isBotActive = true

        serviceScope.launch {
            val offset = repository.syncServerTime(preferences.isTestnet)
            repository.log(LogLevel.INFO, "BotService", "7/24 Bybit Ticaret Botu başlatıldı (Zaman farkı: $offset ms)")

            wsClient.connect(
                key = preferences.apiKey,
                secret = preferences.apiSecret,
                testnet = preferences.isTestnet,
                timeOffsetMs = offset
            )

            launch {
                wsClient.priceUpdates.collect { price ->
                    if (price > 0.0 && price != currentBasePrice) {
                        currentBasePrice = price
                    }
                }
            }

            launch {
                wsClient.orderUpdates.collect { order ->
                    handleOrderUpdate(order)
                }
            }

            launch {
                while (isActive && isBotLoopRunning.get()) {
                    delay(30000)
                    try {
                        repository.syncUnfilledOrdersWithExchange()
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            }

            startWatchdogLoop()
        }
    }

    private fun startWatchdogLoop() {
        pollingJob?.cancel()
        pollingJob = serviceScope.launch {
            var cycleCount = 0
            while (isActive && isBotLoopRunning.get()) {
                try {
                    // --- BYBIT RECONCILIATION ---
                    if (preferences.isBotActive) {
                        if (currentBasePrice <= 0.0 || cycleCount % 3 == 0) {
                            val tickerRes = repository.getTicker()
                            tickerRes.onSuccess { ticker ->
                                currentBasePrice = ticker.currentPrice
                            }
                        }

                        if (cycleCount % 3 == 0 || lastUsdtBalance <= 0.0) {
                            val balanceRes = repository.getWalletBalance()
                            balanceRes.onSuccess { balances ->
                                lastUsdtBalance = balances["USDT"] ?: 0.0
                                lastBaseBalance = balances["${preferences.bybitBaseCoin}"] ?: 0.0
                                updateNotification()
                            }
                        }
                    }

                    // --- OKX RECONCILIATION ---
                    if (preferences.isOkxBotActive) {
                        if (cycleCount % 3 == 0) {
                            okxRepository.reconcileGridOrders(callerTag = "Watchdog-OKX")
                        }
                    }

                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("Watchdog", "Error in watchdog loop", e)
                }
                cycleCount++
                delay(4000)
            }
        }
    }

    private suspend fun handleOrderUpdate(order: BybitOrderDto) {
        if (order.orderStatus == "Filled") {
            val orderId = order.orderId
            val avgPrice = order.avgPrice.toDoubleOrNull() ?: 0.0
            val fillPrice = if (avgPrice > 0.0) avgPrice else (order.priceValue)
            val execQty = order.cumExecQty.toDoubleOrNull() ?: 0.0
            val fillQty = if (execQty > 0.0) execQty else order.qtyValue

            repository.recordOrderFilled(
                orderId = orderId,
                side = order.side,
                price = fillPrice,
                qty = fillQty,
                triggerReason = if (order.side.equals("Buy", ignoreCase = true)) "GridStepDownBuy" else "GridStepUpSell",
                fillTime = order.updatedTime.toLongOrNull() ?: 0L
            )
            repository.reconcileGridOrders(callerTag = "WebSocket")

            val notifId = ALERT_NOTIFICATION_ID_BASE + (orderId.hashCode() and 0x7FFFFFFF) % 500
            sendAlertNotification(
                "${order.side} Limit Emri Gerçekleşti!",
                "${order.side} ${order.cumExecQty.toDoubleOrNull() ?: 0.0} ${preferences.bybitBaseCoin} @ ${order.avgPrice.toDoubleOrNull() ?: 0.0} USDT. Karşı emir iptal edildi ve yeni ızgara kuruldu.",
                false,
                notificationId = notifId
            )
        }
    }

    private fun updateNotification() {
        val title = "Bybit 7/24 Rebalance Bot: AKTİF"
        val baseVal = lastBaseBalance * currentBasePrice
        val total = lastUsdtBalance + baseVal
        val uPct = if (total > 0) (lastUsdtBalance / total * 100).toInt() else 50
        val bPct = if (total > 0) (baseVal / total * 100).toInt() else 50
        val content = "${preferences.bybitBaseCoin}: $${RebalanceEngine.format4(currentBasePrice)} | Portföy: %$uPct USDT / %$bPct ${preferences.bybitBaseCoin} ($${RebalanceEngine.format2(total)})"
        
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
        cancelKeepAliveAlarm()
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

    private fun scheduleKeepAliveAlarm() {
        try {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(this, TradingBotService::class.java).apply {
                action = ACTION_START_BOT
            }
            val pendingIntent = PendingIntent.getService(
                this, 1001, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Schedule alarm to fire roughly every 15 minutes to restart service if killed
            alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 15 * 60 * 1000L,
                15 * 60 * 1000L,
                pendingIntent
            )
        } catch (e: Exception) {
            Log.e("TradingBotService", "Failed to schedule keep-alive alarm", e)
        }
    }

    private fun cancelKeepAliveAlarm() {
        try {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(this, TradingBotService::class.java).apply {
                action = ACTION_START_BOT
            }
            val pendingIntent = PendingIntent.getService(
                this, 1001, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        } catch (e: Exception) {
            Log.e("TradingBotService", "Failed to cancel keep-alive alarm", e)
        }
    }

    override fun onDestroy() {
        stopBot()
        serviceScope.cancel()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wifiLock?.let {
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
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    try {
                        context.startForegroundService(intent)
                    } catch(e: Exception) {
                        if (e is Exception && (e.javaClass.simpleName == "ForegroundServiceStartNotAllowedException" || e.message?.contains("ForegroundServiceStartNotAllowedException") == true || e.cause?.javaClass?.simpleName == "ForegroundServiceStartNotAllowedException")) {
                            Log.w("TradingBotService", "Foreground service start not allowed, starting normally.")
                            context.startService(intent)
                        } else {
                            Log.e("TradingBotService", "Foreground service failed", e)
                        }
                    }
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e("TradingBotService", "Failed to start foreground service: ${e.message}", e)
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
