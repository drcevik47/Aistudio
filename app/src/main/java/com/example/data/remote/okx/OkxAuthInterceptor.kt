package com.example.data.remote.okx

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import android.util.Base64

class OkxAuthInterceptor(
    private val apiKey: String,
    private val apiSecret: String,
    private val passphrase: String,
    private val isTestnet: Boolean
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        if (apiKey.isBlank() || apiSecret.isBlank() || passphrase.isBlank()) {
            return chain.proceed(originalRequest)
        }

        val timestamp = getIso8601Timestamp()
        val method = originalRequest.method
        val requestPath = originalRequest.url.encodedPath + 
                          if (originalRequest.url.encodedQuery != null) "?" + originalRequest.url.encodedQuery else ""
        
        val bodyString = getBodyAsString(originalRequest)
        
        val signMessage = timestamp + method + requestPath + bodyString
        val signature = generateSignature(apiSecret, signMessage)

        val requestBuilder = originalRequest.newBuilder()
            .header("OK-ACCESS-KEY", apiKey)
            .header("OK-ACCESS-SIGN", signature)
            .header("OK-ACCESS-TIMESTAMP", timestamp)
            .header("OK-ACCESS-PASSPHRASE", passphrase)
            .header("Content-Type", "application/json")
            
        if (isTestnet) {
            requestBuilder.header("x-simulated-trading", "1")
        }

        return chain.proceed(requestBuilder.build())
    }

    private fun getIso8601Timestamp(): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return sdf.format(java.util.Date())
    }

    private fun getBodyAsString(request: Request): String {
        val copy = request.newBuilder().build()
        val buffer = Buffer()
        copy.body?.writeTo(buffer)
        return buffer.readUtf8()
    }

    private fun generateSignature(secret: String, message: String): String {
        val hmacSha256 = "HmacSHA256"
        val secretKeySpec = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), hmacSha256)
        val mac = Mac.getInstance(hmacSha256)
        mac.init(secretKeySpec)
        val rawHmac = mac.doFinal(message.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(rawHmac, Base64.NO_WRAP)
    }
}
