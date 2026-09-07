package com.example.data.local

import android.content.Context
import android.content.SharedPreferences

class BotPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("bybit_bot_prefs", Context.MODE_PRIVATE)

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_API_KEY, value.trim()).apply()

    var apiSecret: String
        get() = prefs.getString(KEY_API_SECRET, "") ?: ""
        set(value) = prefs.edit().putString(KEY_API_SECRET, value.trim()).apply()

    var isTestnet: Boolean
        get() = prefs.getBoolean(KEY_IS_TESTNET, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_TESTNET, value).apply()

    var isConfigured: Boolean
        get() = prefs.getBoolean(KEY_IS_CONFIGURED, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_CONFIGURED, value).apply()

    var stepPercent: Double
        get() = prefs.getFloat(KEY_STEP_PERCENT, 2.0f).toDouble()
        set(value) = prefs.edit().putFloat(KEY_STEP_PERCENT, value.toFloat()).apply()

    var isBotActive: Boolean
        get() = prefs.getBoolean(KEY_IS_BOT_ACTIVE, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_BOT_ACTIVE, value).apply()

    var activeBuyOrderId: String
        get() = prefs.getString(KEY_ACTIVE_BUY_ORDER_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_ACTIVE_BUY_ORDER_ID, value).apply()

    var activeSellOrderId: String
        get() = prefs.getString(KEY_ACTIVE_SELL_ORDER_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_ACTIVE_SELL_ORDER_ID, value).apply()

    var lastRebalancePrice: Double
        get() = prefs.getFloat(KEY_LAST_REBALANCE_PRICE, 0f).toDouble()
        set(value) = prefs.edit().putFloat(KEY_LAST_REBALANCE_PRICE, value.toFloat()).apply()

    fun saveCredentials(key: String, secret: String, testnet: Boolean) {
        prefs.edit()
            .putString(KEY_API_KEY, key.trim())
            .putString(KEY_API_SECRET, secret.trim())
            .putBoolean(KEY_IS_TESTNET, testnet)
            .putBoolean(KEY_IS_CONFIGURED, key.isNotBlank() && secret.isNotBlank())
            .apply()
    }

    fun clearCredentials() {
        prefs.edit()
            .remove(KEY_API_KEY)
            .remove(KEY_API_SECRET)
            .putBoolean(KEY_IS_CONFIGURED, false)
            .putBoolean(KEY_IS_BOT_ACTIVE, false)
            .apply()
    }

    companion object {
        private const val KEY_API_KEY = "bybit_api_key"
        private const val KEY_API_SECRET = "bybit_api_secret"
        private const val KEY_IS_TESTNET = "bybit_is_testnet"
        private const val KEY_IS_CONFIGURED = "bybit_is_configured"
        private const val KEY_STEP_PERCENT = "bybit_step_percent"
        private const val KEY_IS_BOT_ACTIVE = "bybit_is_bot_active"
        private const val KEY_ACTIVE_BUY_ORDER_ID = "active_buy_order_id"
        private const val KEY_ACTIVE_SELL_ORDER_ID = "active_sell_order_id"
        private const val KEY_LAST_REBALANCE_PRICE = "last_rebalance_price"
    }
}
