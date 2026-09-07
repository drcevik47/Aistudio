package com.example.data.remote

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object BybitSigner {

    fun signRest(
        timestamp: Long,
        apiKey: String,
        recvWindow: String,
        paramStr: String,
        apiSecret: String
    ): String {
        val payload = "$timestamp$apiKey$recvWindow$paramStr"
        return hmacSha256(payload, apiSecret)
    }

    fun signWebSocket(expires: Long, apiSecret: String): String {
        val payload = "GET/realtime$expires"
        return hmacSha256(payload, apiSecret)
    }

    private fun hmacSha256(data: String, key: String): String {
        return try {
            val sha256Hmac = Mac.getInstance("HmacSHA256")
            val secretKey = SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256")
            sha256Hmac.init(secretKey)
            val bytes = sha256Hmac.doFinal(data.toByteArray(Charsets.UTF_8))
            bytesToHex(bytes)
        } catch (e: Exception) {
            ""
        }
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        val hexArray = "0123456789abcdef".toCharArray()
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            hexChars[i * 2] = hexArray[v ushr 4]
            hexChars[i * 2 + 1] = hexArray[v and 0x0F]
        }
        return String(hexChars)
    }
}
