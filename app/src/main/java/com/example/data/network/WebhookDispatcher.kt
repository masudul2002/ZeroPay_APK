package com.example.data.network

import android.util.Log
import com.example.data.model.ConfigData
import com.example.data.model.WebhookPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

sealed class DispatchResult {
    data class Success(val code: Int, val body: String) : DispatchResult()
    data class Failure(val code: Int?, val errorMessage: String) : DispatchResult()
}

class WebhookDispatcher(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
) {

    suspend fun dispatchSms(
        config: ConfigData,
        sender: String,
        messageBody: String,
        simSlot: String = "SIM_1",
        isoTimestamp: String = getCurrentIsoTimestamp()
    ): DispatchResult = withContext(Dispatchers.IO) {
        if (!config.isConfigured) {
            return@withContext DispatchResult.Failure(
                code = null,
                errorMessage = "Device not configured. Webhook URL, secret, or device ID is missing."
            )
        }

        try {
            // Strict JSON Payload Format specified in requirements:
            // {
            //   "sender": "<extracted_sender>",
            //   "messageBody": "<extracted_message_body>",
            //   "timestamp": "<current_iso_8601_timestamp>",
            //   "simSlot": "SIM_1",
            //   "deviceId": "<saved_deviceId>"
            // }
            val jsonObject = JSONObject().apply {
                put("sender", sender)
                put("messageBody", messageBody)
                put("timestamp", isoTimestamp)
                put("simSlot", simSlot)
                put("deviceId", config.deviceId)
            }

            val jsonString = jsonObject.toString()
            Log.d(TAG, "Dispatching to ${config.webhookUrl}: $jsonString")

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = jsonString.toRequestBody(mediaType)

            val request = Request.Builder()
                .url(config.webhookUrl)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer ${config.deviceSecret}")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                val code = response.code

                if (response.isSuccessful) {
                    Log.d(TAG, "Webhook dispatched successfully! Code: $code")
                    DispatchResult.Success(code = code, body = responseBody)
                } else {
                    val errorMsg = "HTTP $code: ${response.message.ifBlank { responseBody.take(120) }}"
                    Log.w(TAG, "Webhook response not successful: $errorMsg")
                    DispatchResult.Failure(code = code, errorMessage = errorMsg)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dispatch webhook: ${e.message}", e)
            DispatchResult.Failure(
                code = null,
                errorMessage = e.localizedMessage ?: "Network connection error"
            )
        }
    }

    suspend fun testConnection(config: ConfigData): DispatchResult {
        return dispatchSms(
            config = config,
            sender = "TEST_PING",
            messageBody = "Zero Pay test ping message to verify webhook connectivity.",
            simSlot = "SIM_1"
        )
    }

    companion object {
        private const val TAG = "ZeroPayDispatcher"

        fun getCurrentIsoTimestamp(): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            return sdf.format(Date())
        }
    }
}
