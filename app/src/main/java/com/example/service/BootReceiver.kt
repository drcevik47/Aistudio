package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.BybitBotApp

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val app = context.applicationContext as? BybitBotApp ?: return
            val preferences = app.preferences

            if (preferences.isBotActive && preferences.apiKey.isNotBlank()) {
                Log.d("BootReceiver", "Device rebooted or app updated. Restoring 24/7 TradingBotService...")
                val serviceIntent = Intent(context, TradingBotService::class.java).apply {
                    this.action = TradingBotService.ACTION_START_BOT
                }
                try {
                    ContextCompat.startForegroundService(context, serviceIntent)
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Failed to auto-restart bot service on boot", e)
                }
            }
        }
    }
}
