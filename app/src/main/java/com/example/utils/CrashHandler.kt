package com.example.utils

import android.content.Context
import android.util.Log
import com.example.data.repository.BybitRepository
import com.example.data.local.entity.LogLevel
import java.io.PrintWriter
import java.io.StringWriter

class CrashHandler(
    private val context: Context,
    private val repository: BybitRepository
) : Thread.UncaughtExceptionHandler {

    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, exception: Throwable) {
        try {
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            exception.printStackTrace(pw)
            val stackTrace = sw.toString()
            
            val message = "Kritik Hata (Crash): ${exception.javaClass.simpleName} - ${exception.message}\n$stackTrace"
            Log.e("CrashHandler", message)
            
            // Try to log to the database using runBlocking since we are crashing
            kotlinx.coroutines.runBlocking {
                try {
                    repository.log(LogLevel.ERROR, "SystemCrash", message.take(1000))
                } catch (e: Exception) {
                    Log.e("CrashHandler", "Log veritabanına yazılamadı", e)
                }
            }
        } catch (e: Exception) {
            Log.e("CrashHandler", "Error in crash handler", e)
        } finally {
            defaultHandler?.uncaughtException(thread, exception)
        }
    }
}
