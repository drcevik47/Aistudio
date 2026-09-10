package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.OrderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderDao {
    @Query("SELECT * FROM orders ORDER BY timestamp DESC")
    fun getAllOrders(): Flow<List<OrderEntity>>

    @Query("SELECT DISTINCT symbol FROM orders WHERE symbol IS NOT NULL AND symbol != '' ORDER BY symbol ASC")
    fun getAllDistinctSymbols(): Flow<List<String>>

    @Query("SELECT DISTINCT symbol FROM orders WHERE symbol IS NOT NULL AND symbol != '' ORDER BY symbol ASC")
    suspend fun getAllDistinctSymbolsSync(): List<String>

    @Query("SELECT * FROM orders WHERE status = 'Filled' ORDER BY timestamp DESC")
    fun getFilledOrders(): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE status = 'Filled' ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastFilledOrder(): OrderEntity?

    @Query("SELECT * FROM orders WHERE status = 'Filled' ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentFilledOrdersList(limit: Int = 50): List<OrderEntity>

    @Query("SELECT * FROM orders WHERE orderId = :orderId LIMIT 1")
    suspend fun getOrderByOrderId(orderId: String): OrderEntity?

    @Query("SELECT * FROM orders WHERE status = 'New' OR status = 'PartiallyFilled'")
    suspend fun getOpenOrdersFromDb(): List<OrderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrder(order: OrderEntity): Long

    @Update
    suspend fun updateOrder(order: OrderEntity)

    @Query("UPDATE orders SET status = :status, filledQty = :filledQty, avgPrice = :avgPrice, timestamp = :fillTime WHERE orderId = :orderId")
    suspend fun updateOrderStatus(orderId: String, status: String, filledQty: Double, avgPrice: Double, fillTime: Long = System.currentTimeMillis())

    @Query("DELETE FROM orders WHERE orderId = :orderId")
    suspend fun deleteOrder(orderId: String)

    @Query("DELETE FROM orders WHERE status != 'Filled'")
    suspend fun deleteUnfilledOrders()

    @Query("DELETE FROM orders WHERE status = 'Filled'")
    suspend fun deleteFilledOrders()

    @Query("DELETE FROM orders")
    suspend fun clearAllOrders()
}
