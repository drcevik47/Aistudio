package com.example

import android.app.Application
import com.example.data.local.AppDatabase
import com.example.data.local.BotPreferences
import com.example.data.repository.BybitRepository
import com.example.data.repository.OkxRepository

class BybitBotApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var preferences: BotPreferences
        private set

    lateinit var repository: BybitRepository
        private set
    lateinit var okxRepository: OkxRepository
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getDatabase(this)
        preferences = BotPreferences(this)
        repository = BybitRepository(
            preferences = preferences,
            database = database,
            orderDao = database.orderDao(),
            logDao = database.logDao(),
            exchangeTradeDao = database.exchangeTradeDao()
        )
        okxRepository = OkxRepository(
            preferences = preferences,
            database = database
        )
        Thread.setDefaultUncaughtExceptionHandler(com.example.utils.CrashHandler(this, repository))
    }
}
