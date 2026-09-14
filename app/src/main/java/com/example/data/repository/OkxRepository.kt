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
        val apiKey = preferences.okxApiKey
        val apiSecret = preferences.okxApiSecret
        val passphrase = preferences.okxApiPassphrase
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

        val baseUrl = "https://www.okx.com"

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
                val response = api.getBalance("${preferences.okxBaseCoin},USDT")
                if (response.code == "0" && response.data.isNotEmpty()) {
                    val map = mutableMapOf<String, Double>()
                    response.data.first().details.forEach { detail ->
                        map[detail.ccy] = detail.availEq.toDoubleOrNull() ?: 0.0
                    }
                    Result.success(map)
                } else {
                    Result.failure(Exception(response.msg))
                }
            } catch (e: Exception) {
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
