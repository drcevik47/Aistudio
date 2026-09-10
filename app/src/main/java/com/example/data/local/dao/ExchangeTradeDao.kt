package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.ExchangeTradeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExchangeTradeDao {

    @Query("SELECT * FROM exchange_trades ORDER BY timeMillis DESC")
    fun getAllTrades(): Flow<List<ExchangeTradeEntity>>

    @Query("SELECT * FROM exchange_trades WHERE symbol = :symbol ORDER BY timeMillis DESC")
    fun getTradesBySymbol(symbol: String): Flow<List<ExchangeTradeEntity>>

    @Query("SELECT * FROM exchange_trades ORDER BY timeMillis DESC")
    suspend fun getAllTradesSync(): List<ExchangeTradeEntity>

    @Query("SELECT * FROM exchange_trades WHERE symbol = :symbol ORDER BY timeMillis DESC")
    suspend fun getTradesBySymbolSync(symbol: String): List<ExchangeTradeEntity>

    @Query("SELECT DISTINCT symbol FROM exchange_trades WHERE symbol IS NOT NULL AND symbol != '' ORDER BY symbol ASC")
    fun getAllDistinctSymbols(): Flow<List<String>>

    @Query("SELECT DISTINCT symbol FROM exchange_trades WHERE symbol IS NOT NULL AND symbol != '' ORDER BY symbol ASC")
    suspend fun getAllDistinctSymbolsSync(): List<String>

    @Query("SELECT execId FROM exchange_trades")
    suspend fun getAllExecIds(): List<String>

    @Query("SELECT COUNT(*) FROM exchange_trades")
    fun getTradeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM exchange_trades")
    suspend fun getTradeCountSync(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM exchange_trades WHERE orderId = :orderId LIMIT 1)")
    suspend fun hasTradeForOrder(orderId: String): Boolean

    @Query("SELECT * FROM exchange_trades WHERE orderId = :orderId LIMIT 1")
    suspend fun getTradeByOrderId(orderId: String): ExchangeTradeEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTrades(trades: List<ExchangeTradeEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTrade(trade: ExchangeTradeEntity): Long

    @Query("DELETE FROM exchange_trades WHERE orderId = :orderId")
    suspend fun deleteTradeByOrderId(orderId: String)

    @Query("DELETE FROM exchange_trades")
    suspend fun clearAllTrades()

    @Query("DELETE FROM exchange_trades WHERE symbol = :symbol")
    suspend fun clearTradesBySymbol(symbol: String)
}
