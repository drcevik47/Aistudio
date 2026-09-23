package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class BotPreferences(context: Context) {
    private val standardPrefs: SharedPreferences = context.getSharedPreferences("bybit_bot_prefs", Context.MODE_PRIVATE)

    val isSecureStorageAvailable: Boolean
        get() = securePrefs != null

    private val securePrefs: SharedPreferences? = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "bybit_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Log.e("BotPreferences", "EncryptedSharedPreferences oluşturulamadı. Güvenlik gereği şifresiz düz metin saklama engellendi: ${e.message}")
        null
    }

    init {
        // Automatically migrate legacy plaintext credentials from standardPrefs to securePrefs if available
        try {
            val sp = securePrefs
            if (sp != null) {
                val legacyKeys = listOf(
                    KEY_API_KEY,
                    KEY_API_SECRET,
                    KEY_OKX_API_KEY,
                    KEY_OKX_API_SECRET,
                    KEY_OKX_API_PASSPHRASE
                )
                var needsMigration = false
                val secureEditor = sp.edit()
                val standardEditor = standardPrefs.edit()

                for (key in legacyKeys) {
                    val oldVal = standardPrefs.getString(key, null)
                    if (!oldVal.isNullOrBlank()) {
                        if (sp.getString(key, null).isNullOrBlank()) {
                            secureEditor.putString(key, oldVal)
                            needsMigration = true
                        }
                        // Remove plaintext value from standardPrefs
                        standardEditor.remove(key)
                    }
                }
                if (needsMigration) {
                    secureEditor.apply()
                }
                standardEditor.apply()
            }
        } catch (e: Exception) {
            Log.e("BotPreferences", "Credential migration error: ${e.message}")
        }
    }

    private val prefs: SharedPreferences
        get() = standardPrefs

    var apiKey: String
        get() = securePrefs?.getString(KEY_API_KEY, "") ?: ""
        set(value) {
            securePrefs?.edit()?.putString(KEY_API_KEY, value.trim())?.apply()
                ?: Log.e("BotPreferences", "GÜVENLİK: Şifreli depolama mevcut değil, apiKey kaydedilmedi.")
        }

    var apiSecret: String
        get() = securePrefs?.getString(KEY_API_SECRET, "") ?: ""
        set(value) {
            securePrefs?.edit()?.putString(KEY_API_SECRET, value.trim())?.apply()
                ?: Log.e("BotPreferences", "GÜVENLİK: Şifreli depolama mevcut değil, apiSecret kaydedilmedi.")
        }

    var okxApiKey: String
        get() = securePrefs?.getString(KEY_OKX_API_KEY, "") ?: ""
        set(value) {
            securePrefs?.edit()?.putString(KEY_OKX_API_KEY, value.trim())?.apply()
                ?: Log.e("BotPreferences", "GÜVENLİK: Şifreli depolama mevcut değil, okxApiKey kaydedilmedi.")
        }

    var okxApiSecret: String
        get() = securePrefs?.getString(KEY_OKX_API_SECRET, "") ?: ""
        set(value) {
            securePrefs?.edit()?.putString(KEY_OKX_API_SECRET, value.trim())?.apply()
                ?: Log.e("BotPreferences", "GÜVENLİK: Şifreli depolama mevcut değil, okxApiSecret kaydedilmedi.")
        }

    var okxApiPassphrase: String
        get() = securePrefs?.getString(KEY_OKX_API_PASSPHRASE, "") ?: ""
        set(value) {
            securePrefs?.edit()?.putString(KEY_OKX_API_PASSPHRASE, value.trim())?.apply()
                ?: Log.e("BotPreferences", "GÜVENLİK: Şifreli depolama mevcut değil, okxApiPassphrase kaydedilmedi.")
        }

    var bybitSymbol: String
        get() = prefs.getString(KEY_BYBIT_SYMBOL, "MNTUSDT") ?: "MNTUSDT"
        set(value) = prefs.edit().putString(KEY_BYBIT_SYMBOL, value.trim().uppercase()).apply()

    var bybitBaseCoin: String
        get() = prefs.getString(KEY_BYBIT_BASE_COIN, "MNT") ?: "MNT"
        set(value) = prefs.edit().putString(KEY_BYBIT_BASE_COIN, value.trim().uppercase()).apply()

    var okxSymbol: String
        get() = prefs.getString(KEY_OKX_SYMBOL, "BTC-USDT") ?: "BTC-USDT"
        set(value) = prefs.edit().putString(KEY_OKX_SYMBOL, value.trim().uppercase()).apply()

    var activeExchange: String
        get() = prefs.getString(KEY_ACTIVE_EXCHANGE, "BYBIT") ?: "BYBIT"
        set(value) = prefs.edit().putString(KEY_ACTIVE_EXCHANGE, value).apply()

    var okxBaseCoin: String
        get() = prefs.getString(KEY_OKX_BASE_COIN, "BTC") ?: "BTC"
        set(value) = prefs.edit().putString(KEY_OKX_BASE_COIN, value.trim().uppercase()).apply()

    var isTestnet: Boolean
        get() = prefs.getBoolean(KEY_IS_TESTNET, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_TESTNET, value).apply()

    var isConfigured: Boolean
        get() = prefs.getBoolean(KEY_IS_CONFIGURED, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_CONFIGURED, value).apply()

    var stepPercent: Double
        get() = prefs.getFloat(KEY_STEP_PERCENT, 2.0f).toDouble()
        set(value) = prefs.edit().putFloat(KEY_STEP_PERCENT, value.toFloat()).apply()

    var okxStepPercent: Double
        get() = prefs.getFloat(KEY_OKX_STEP_PERCENT, 2.0f).toDouble()
        set(value) = prefs.edit().putFloat(KEY_OKX_STEP_PERCENT, value.toFloat()).apply()

    var isBotActive: Boolean
        get() = prefs.getBoolean(KEY_IS_BOT_ACTIVE, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_BOT_ACTIVE, value).apply()

    var activeBuyOrderId: String
        get() = prefs.getString(KEY_ACTIVE_BUY_ORDER_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_ACTIVE_BUY_ORDER_ID, value).apply()

    var activeSellOrderId: String
        get() = prefs.getString(KEY_ACTIVE_SELL_ORDER_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_ACTIVE_SELL_ORDER_ID, value).apply()

    var isOkxBotActive: Boolean
        get() = prefs.getBoolean("okx_is_bot_active", false)
        set(value) = prefs.edit().putBoolean("okx_is_bot_active", value).apply()

    var okxActiveBuyOrderId: String
        get() = prefs.getString("okx_active_buy_id", "") ?: ""
        set(value) = prefs.edit().putString("okx_active_buy_id", value).apply()

    var okxActiveSellOrderId: String
        get() = prefs.getString("okx_active_sell_id", "") ?: ""
        set(value) = prefs.edit().putString("okx_active_sell_id", value).apply()

    var okxLastRebalancePrice: Double
        get() {
            val str = prefs.getString("okx_last_rebalance_price_str", null)
            if (str != null) {
                return str.toDoubleOrNull() ?: 0.0
            }
            return try {
                prefs.getFloat("okx_last_rebalance_price", 0.0f).toDouble()
            } catch (e: Exception) {
                0.0
            }
        }
        set(value) {
            prefs.edit()
                .putString("okx_last_rebalance_price_str", value.toString())
                .putFloat("okx_last_rebalance_price", value.toFloat())
                .apply()
        }

    var lastRebalancePrice: Double
        get() {
            val str = prefs.getString(KEY_LAST_REBALANCE_PRICE_STR, null)
            if (str != null) {
                return str.toDoubleOrNull() ?: 0.0
            }
            // Backwards compatibility with legacy float storage
            return try {
                prefs.getFloat(KEY_LAST_REBALANCE_PRICE, 0f).toDouble()
            } catch (e: Exception) {
                0.0
            }
        }
        set(value) {
            prefs.edit()
                .putString(KEY_LAST_REBALANCE_PRICE_STR, value.toString())
                .putFloat(KEY_LAST_REBALANCE_PRICE, value.toFloat())
                .apply()
        }

    fun saveCredentials(key: String, secret: String, testnet: Boolean) {
        val sp = securePrefs
        if (sp == null) {
            Log.e("BotPreferences", "GÜVENLİK HATASI: Şifreli depolama mevcut değil, kimlik bilgileri kaydedilemez.")
            return
        }
        sp.edit()
            .putString(KEY_API_KEY, key.trim())
            .putString(KEY_API_SECRET, secret.trim())
            .apply()
        prefs.edit()
            .remove(KEY_API_KEY)
            .remove(KEY_API_SECRET)
            .putBoolean(KEY_IS_TESTNET, testnet)
            .putBoolean(KEY_IS_CONFIGURED, key.isNotBlank() && secret.isNotBlank())
            .apply()
    }

    fun clearCredentials() {
        securePrefs?.edit()
            ?.remove(KEY_API_KEY)
            ?.remove(KEY_API_SECRET)
            ?.apply()
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
        private const val KEY_OKX_API_KEY = "okx_api_key"
        private const val KEY_OKX_API_SECRET = "okx_api_secret"
        private const val KEY_OKX_API_PASSPHRASE = "okx_api_passphrase"
        private const val KEY_BYBIT_SYMBOL = "bybit_symbol"
        private const val KEY_BYBIT_BASE_COIN = "bybit_base_coin"
        private const val KEY_OKX_SYMBOL = "okx_symbol"
        private const val KEY_OKX_BASE_COIN = "okx_base_coin"
        private const val KEY_IS_TESTNET = "bybit_is_testnet"
        private const val KEY_IS_CONFIGURED = "bybit_is_configured"
        private const val KEY_STEP_PERCENT = "bybit_step_percent"
        private const val KEY_OKX_STEP_PERCENT = "okx_step_percent"
        private const val KEY_IS_BOT_ACTIVE = "bybit_is_bot_active"
        private const val KEY_ACTIVE_BUY_ORDER_ID = "active_buy_order_id"
        private const val KEY_ACTIVE_SELL_ORDER_ID = "active_sell_order_id"
        private const val KEY_LAST_REBALANCE_PRICE = "last_rebalance_price"
        private const val KEY_LAST_REBALANCE_PRICE_STR = "last_rebalance_price_str"
        private const val KEY_ACTIVE_EXCHANGE = "active_exchange"
    }
}
