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
import com.example.data.remote.okx.OkxWebSocketClient
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
    private lateinit var okxWsClient: OkxWebSocketClient
    private lateinit var database: com.example.data.local.AppDatabase
    private var pollingJob: Job? = null
    private var isBotLoopRunning = AtomicBoolean(false)
    private var currentBasePrice: Double = 0.0
    private var lastUsdtBalance: Double = 0.0
    private var lastBaseBalance: Double = 0.0

    private var currentOkxBasePrice: Double = 0.0
    private var lastOkxUsdtBalance: Double = 0.0
    private var lastOkxBaseBalance: Double = 0.0

    override fun onCreate() {
        super.onCreate()
        val app = application as BybitBotApp
        preferences = app.preferences
        repository = app.repository
        okxRepository = app.okxRepository
        database = app.database
        wsClient = BybitWebSocketClient(serviceScope)
        okxWsClient = OkxWebSocketClient(
            scope = serviceScope,
            initialApiKey = preferences.okxApiKey,
            initialApiSecret = preferences.okxApiSecret,
            initialPassphrase = preferences.okxApiPassphrase,
            initialIsTestnet = preferences.isTestnet,
            initialActiveSymbol = preferences.okxSymbol
        )
        setupSocketListeners()
        createNotificationChannels()
        acquireWakeLock()
    }

    private fun setupSocketListeners() {
        serviceScope.launch {
            wsClient.priceUpdates.collect { price ->
                if (price > 0.0 && price != currentBasePrice) {
                    currentBasePrice = price
                }
            }
        }
        serviceScope.launch {
            okxWsClient.priceUpdates.collect { price ->
                if (price > 0.0 && price != currentOkxBasePrice) {
                    currentOkxBasePrice = price
                }
            }
        }
        serviceScope.launch {
            wsClient.orderUpdates.collect { order ->
                handleOrderUpdate(order)
            }
        }
        serviceScope.launch {
            okxWsClient.orderUpdates.collect { order ->
                handleOkxOrderUpdate(order)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_STOP_BOT -> {
                val targetExchange = intent?.getStringExtra(EXTRA_EXCHANGE) ?: EXTRA_EXCHANGE_ALL
                when (targetExchange) {
                    EXTRA_EXCHANGE_BYBIT -> {
                        preferences.isBotActive = false
                        wsClient.stop()
                        serviceScope.launch {
                            repository.log(LogLevel.INFO, "BotService", "Bybit Bot durduruldu")
                        }
                    }
                    EXTRA_EXCHANGE_OKX -> {
                        preferences.isOkxBotActive = false
                        okxWsClient.stop()
                        serviceScope.launch {
                            repository.log(LogLevel.INFO, "BotService", "OKX Bot durduruldu")
                        }
                    }
                    else -> {
                        preferences.isBotActive = false
                        preferences.isOkxBotActive = false
                    }
                }

                if (!preferences.isBotActive && !preferences.isOkxBotActive) {
                    stopBot()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return START_NOT_STICKY
                } else {
                    updateNotification()
                }
            }
            ACTION_START_BOT, null -> {
                try {
                    if (Build.VERSION.SDK_INT >= 34) {
                        startForeground(NOTIFICATION_ID, buildForegroundNotification("BITBALANCE Başlatılıyor..."), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(NOTIFICATION_ID, buildForegroundNotification("BITBALANCE Başlatılıyor..."), 0)
                    } else {
                        startForeground(NOTIFICATION_ID, buildForegroundNotification("BITBALANCE Başlatılıyor..."))
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
        serviceScope.launch {
            if (preferences.isBotActive) {
                val offset = repository.syncServerTime(preferences.isTestnet)
                repository.log(LogLevel.INFO, "BotService", "Bybit Bot veri akışı başlatılıyor (Zaman farkı: $offset ms)")
                wsClient.connect(
                    key = preferences.apiKey,
                    secret = preferences.apiSecret,
                    testnet = preferences.isTestnet,
                    timeOffsetMs = offset,
                    symbol = preferences.bybitSymbol
                )
            }

            if (preferences.isOkxBotActive) {
                repository.log(LogLevel.INFO, "BotService", "OKX Bot veri akışı başlatılıyor (${preferences.okxSymbol})")
                okxWsClient.connect(
                    key = preferences.okxApiKey,
                    secret = preferences.okxApiSecret,
                    passphrase = preferences.okxApiPassphrase,
                    testnet = preferences.isTestnet,
                    symbol = preferences.okxSymbol
                )
            }

            if (isBotLoopRunning.compareAndSet(false, true)) {
                launch {
                    while (isActive && isBotLoopRunning.get()) {
                        delay(30000)
                        try {
                            if (preferences.isBotActive) {
                                repository.syncUnfilledOrdersWithExchange()
                            }
                            if (preferences.isOkxBotActive) {
                                okxRepository.getPendingOrders()
                            }
                        } catch (e: Exception) {
                            // ignore
                        }
                    }
                }
                startWatchdogLoop()
            }
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
                                lastUsdtBalance = balances["USDT"]?.quantity ?: 0.0
                                lastBaseBalance = balances["${preferences.bybitBaseCoin}"]?.quantity ?: 0.0
                                updateNotification()
                            }
                        }

                        if (cycleCount % 3 == 0) {
                            repository.reconcileGridOrders(callerTag = "Watchdog-Bybit")
                        }
                    }

                    // --- OKX RECONCILIATION ---
                    if (preferences.isOkxBotActive) {
                        if (currentOkxBasePrice <= 0.0 || cycleCount % 3 == 0) {
                            val okxTickerRes = okxRepository.getTicker()
                            okxTickerRes.onSuccess { ticker ->
                                val p = ticker.last.toDoubleOrNull() ?: 0.0
                                if (p > 0.0) currentOkxBasePrice = p
                            }
                        }

                        if (cycleCount % 3 == 0 || lastOkxUsdtBalance <= 0.0) {
                            val okxBalRes = okxRepository.getWalletBalance()
                            okxBalRes.onSuccess { balances ->
                                lastOkxUsdtBalance = balances["USDT"]?.quantity ?: 0.0
                                lastOkxBaseBalance = balances[preferences.okxBaseCoin]?.quantity ?: 0.0
                                updateNotification()
                            }
                        }

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

    private suspend fun handleOkxOrderUpdate(order: com.example.data.remote.okx.model.OkxOrderDetails) {
        if (preferences.isOkxBotActive && order.state.equals("filled", ignoreCase = true)) {
            val fillPrice = order.avgPx.toDoubleOrNull() ?: order.px.toDoubleOrNull() ?: 0.0
            val fillQty = order.accFillSz.toDoubleOrNull() ?: order.sz.toDoubleOrNull() ?: 0.0
            val fillTime = order.uTime.toLongOrNull() ?: System.currentTimeMillis()
            
            okxRepository.recordOrderFilled(
                orderId = order.ordId,
                side = order.side.replaceFirstChar { it.uppercase() },
                price = fillPrice,
                qty = fillQty,
                triggerReason = if (order.side.equals("buy", ignoreCase = true)) "GridStepDownBuy" else "GridStepUpSell",
                fillTime = fillTime
            )
            
            okxRepository.reconcileGridOrders(callerTag = "OkxWebSocket")
            
            val notifId = ALERT_NOTIFICATION_ID_BASE + 500 + (order.ordId.hashCode() and 0x3FFFFFFF) % 500
            sendAlertNotification(
                "OKX ${order.side.uppercase()} Limit Emri Gerçekleşti!",
                "${order.side.uppercase()} ${RebalanceEngine.format4(fillQty)} ${preferences.okxBaseCoin} @ $fillPrice USDT. Yeni OKX ızgara kuruldu.",
                false,
                notificationId = notifId
            )
        }
    }

    private suspend fun handleOrderUpdate(order: BybitOrderDto) {
        if (order.orderStatus.equals("Filled", ignoreCase = true)) {
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
                "${order.side} ${RebalanceEngine.format4(fillQty)} ${preferences.bybitBaseCoin} @ ${RebalanceEngine.format4(fillPrice)} USDT. Karşı emir iptal edildi ve yeni ızgara kuruldu.",
                false,
                notificationId = notifId
            )
        }
    }

    private fun updateNotification() {
        val content = when {
            preferences.isBotActive && preferences.isOkxBotActive -> {
                "Bybit: $${RebalanceEngine.format4(currentBasePrice)} | OKX: $${RebalanceEngine.format4(currentOkxBasePrice)}"
            }
            preferences.isOkxBotActive -> {
                val okxBaseVal = lastOkxBaseBalance * currentOkxBasePrice
                val total = lastOkxUsdtBalance + okxBaseVal
                val uPct = if (total > 0) (lastOkxUsdtBalance / total * 100).toInt() else 50
                val bPct = if (total > 0) (okxBaseVal / total * 100).toInt() else 50
                "${preferences.okxBaseCoin}: $${RebalanceEngine.format4(currentOkxBasePrice)} | Portföy: %$uPct USDT / %$bPct ${preferences.okxBaseCoin} ($${RebalanceEngine.format2(total)})"
            }
            else -> {
                val baseVal = lastBaseBalance * currentBasePrice
                val total = lastUsdtBalance + baseVal
                val uPct = if (total > 0) (lastUsdtBalance / total * 100).toInt() else 50
                val bPct = if (total > 0) (baseVal / total * 100).toInt() else 50
                "${preferences.bybitBaseCoin}: $${RebalanceEngine.format4(currentBasePrice)} | Portföy: %$uPct USDT / %$bPct ${preferences.bybitBaseCoin} ($${RebalanceEngine.format2(total)})"
            }
        }
        
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
            putExtra(EXTRA_EXCHANGE, EXTRA_EXCHANGE_ALL)
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = when {
            preferences.isBotActive && preferences.isOkxBotActive -> "BITBALANCE (Bybit + OKX): AKTİF"
            preferences.isOkxBotActive -> "OKX 7/24 Dengeleme Botu: AKTİF"
            else -> "Bybit 7/24 Dengeleme Botu: AKTİF"
        }

        return NotificationCompat.Builder(this, CHANNEL_BOT_STATUS)
            .setContentTitle(title)
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
        if (!preferences.isBotActive && !preferences.isOkxBotActive) {
            isBotLoopRunning.set(false)
            pollingJob?.cancel()
            cancelKeepAliveAlarm()
            wsClient.stop()
            okxWsClient.stop()
            serviceScope.launch {
                repository.log(LogLevel.INFO, "BotService", "Tüm botlar durduruldu")
            }
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            val statusChannel = NotificationChannel(
                CHANNEL_BOT_STATUS,
                "BITBALANCE Durum Bildirimi",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Botun 7/24 arka planda sürekli çalışmasını sağlayan bildirim"
            }
            
            val alertChannel = NotificationChannel(
                CHANNEL_ALERTS,
                "BITBALANCE Emir & Hata Bildirimleri",
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
        const val EXTRA_EXCHANGE = "extra_exchange"
        const val EXTRA_EXCHANGE_ALL = "all"
        const val EXTRA_EXCHANGE_BYBIT = "bybit"
        const val EXTRA_EXCHANGE_OKX = "okx"

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

        fun stop(context: Context, exchange: String = EXTRA_EXCHANGE_ALL) {
            val intent = Intent(context, TradingBotService::class.java).apply {
                action = ACTION_STOP_BOT
                putExtra(EXTRA_EXCHANGE, exchange)
            }
            context.startService(intent)
        }
    }
}
