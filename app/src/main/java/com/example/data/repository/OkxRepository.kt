package com.example.data.repository

import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.BotPreferences
import com.example.data.local.entity.ExchangeTradeEntity
import com.example.data.local.entity.LogEntity
import com.example.data.local.entity.LogLevel
import com.example.data.local.entity.OrderEntity
import com.example.data.remote.okx.OkxApiService
import com.example.data.remote.okx.OkxAuthInterceptor
import com.example.data.remote.okx.model.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

class OkxRepository(
    private val preferences: BotPreferences,
    private val database: AppDatabase
) {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private fun createApiService(): OkxApiService {
        val apiKey = preferences.okxApiKey.trim()
        val apiSecret = preferences.okxApiSecret.trim()
        val passphrase = preferences.okxApiPassphrase.trim()
        val isTestnet = preferences.isTestnet 

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY 
        }

        val authInterceptor = OkxAuthInterceptor(apiKey, apiSecret, passphrase, isTestnet)

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor(authInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        val baseUrl = "https://tr.okx.com"

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(OkxApiService::class.java)
    }


    suspend fun getTicker(): Result<OkxTicker> {
        return withContext(Dispatchers.IO) {
            try {
                val api = createApiService()
                val response = api.getTicker(preferences.okxSymbol)
                if (response.code == "0" && response.data.isNotEmpty()) {
                    Result.success(response.data.first())
                } else {
                    Result.failure(Exception(response.msg))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getWalletBalance(): Result<Map<String, Double>> {
        return withContext(Dispatchers.IO) {
            try {
                val api = createApiService()
                val map = mutableMapOf<String, Double>()
                
                val ccyParam = "${preferences.okxBaseCoin},USDT"
                var fetchSuccess = false
                var errorMsg = ""
                
                // 1. Try account/balance (Trading / Unified account)
                try {
                    val tradeRes = api.getBalance(ccyParam)
                    if (tradeRes.code == "0" && tradeRes.data.isNotEmpty()) {
                        tradeRes.data.first().details.forEach { detail ->
                            val avail = detail.availEq.toDoubleOrNull() ?: detail.availBal.toDoubleOrNull() ?: 0.0
                            map[detail.ccy] = (map[detail.ccy] ?: 0.0) + avail
                        }
                        fetchSuccess = true
                        // Removed spammy log
                    } else {
                        errorMsg += "[account/balance: ${tradeRes.code} - ${tradeRes.msg}] "
                    }
                } catch(e: Exception) {
                    errorMsg += "[account/balance Ağ Hatası: ${e.message}] "
                }

                // 2. Try asset/balances (Funding account)
                if (!fetchSuccess || map.values.all { it == 0.0 }) {
                    try {
                        val fundRes = api.getAssetBalances(ccyParam)
                        if (fundRes.code == "0" && fundRes.data.isNotEmpty()) {
                            fundRes.data.forEach { asset ->
                                val avail = asset.availBal.toDoubleOrNull() ?: 0.0
                                map[asset.ccy] = (map[asset.ccy] ?: 0.0) + avail
                            }
                            fetchSuccess = true
                            // Removed spammy log
                        } else {
                            errorMsg += "[asset/balances: ${fundRes.code} - ${fundRes.msg}] "
                        }
                    } catch(e: Exception) {
                        errorMsg += "[asset/balances Ağ Hatası: ${e.message}] "
                    }
                }
                
                if (fetchSuccess) {
                    Result.success(map)
                } else {
                    log(LogLevel.ERROR, "OKX_API", "Bakiye API Hatası: $errorMsg")
                    Result.failure(Exception(errorMsg))
                }
            } catch (e: Exception) {
                log(LogLevel.ERROR, "OKX_API", "Bakiye Kritik Ağ Hatası: ${e.message}")
                Result.failure(e)
            }
        }
    }

    suspend fun log(level: LogLevel, tag: String, message: String, details: String = "") {
        withContext(Dispatchers.IO) {
            database.logDao().insertLog(
                LogEntity(
                    exchange = "OKX",
                    level = level.name,
                    tag = tag,
                    message = message,
                    details = details
                )
            )
        }
    }
}
