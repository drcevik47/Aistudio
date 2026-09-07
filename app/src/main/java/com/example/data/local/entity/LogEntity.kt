package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class LogLevel {
    INFO,
    SUCCESS,
    WARN,
    ERROR
}

@Entity(tableName = "system_logs")
data class LogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val level: String, // INFO, SUCCESS, WARN, ERROR
    val tag: String,
    val message: String,
    val details: String = ""
)
