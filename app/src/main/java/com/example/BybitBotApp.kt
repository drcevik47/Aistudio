package com.example

import android.app.Application
import com.example.data.local.AppDatabase
import com.example.data.local.BotPreferences
import com.example.data.repository.BybitRepository

class BybitBotApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var preferences: BotPreferences
        private set

    lateinit var repository: BybitRepository
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
    }
}
