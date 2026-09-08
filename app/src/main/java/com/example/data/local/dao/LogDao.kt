package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.LogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LogDao {
    @Query("SELECT * FROM system_logs ORDER BY timestamp DESC LIMIT 10000")
    fun getAllLogs(): Flow<List<LogEntity>>

    @Query("SELECT * FROM system_logs WHERE level = :level ORDER BY timestamp DESC LIMIT 10000")
    fun getLogsByLevel(level: String): Flow<List<LogEntity>>

    @Query("SELECT * FROM system_logs ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentLogs(limit: Int): List<LogEntity>

    @Query("SELECT COUNT(*) FROM system_logs")
    suspend fun getLogCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: LogEntity): Long

    @Query("DELETE FROM system_logs WHERE timestamp < :cutoffTimestamp")
    suspend fun pruneOldLogs(cutoffTimestamp: Long): Int

    @Query("DELETE FROM system_logs WHERE id NOT IN (SELECT id FROM system_logs ORDER BY timestamp DESC LIMIT :keepCount)")
    suspend fun pruneExcessLogs(keepCount: Int): Int

    @Query("DELETE FROM system_logs")
    suspend fun clearAllLogs()
}
