package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.entity.LogEntity
import com.example.data.local.entity.OrderEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    private lateinit var db: AppDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun readStringFromContext() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Bybit MNT Bot", appName)
    }

    @Test
    fun testRoomDatabase_InsertAndRetrieveOrder() = runBlocking {
        val orderDao = db.orderDao()
        val order = OrderEntity(
            orderId = "bybit_order_12345",
            symbol = "MNTUSDT",
            side = "Buy",
            orderType = "Limit",
            price = 0.98,
            qty = 100.0,
            status = "Filled",
            triggerReason = "GridStepDownBuy",
            timestamp = System.currentTimeMillis()
        )
        orderDao.insertOrder(order)

        val orders = orderDao.getAllOrders().first()
        assertEquals(1, orders.size)
        assertEquals("bybit_order_12345", orders[0].orderId)
        assertEquals("Buy", orders[0].side)
    }

    @Test
    fun testRoomDatabase_InsertAndRetrieveLog() = runBlocking {
        val logDao = db.logDao()
        val log = LogEntity(
            level = "ERROR",
            tag = "BybitWebSocket",
            message = "Bağlantı koptu",
            details = "Timeout exception",
            timestamp = System.currentTimeMillis()
        )
        logDao.insertLog(log)

        val logs = logDao.getAllLogs().first()
        assertEquals(1, logs.size)
        assertEquals("ERROR", logs[0].level)
        assertEquals("Bağlantı koptu", logs[0].message)
    }
}
