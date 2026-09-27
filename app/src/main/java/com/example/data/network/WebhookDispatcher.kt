package com.example.data.network

import android.util.Log
import com.example.data.model.ConfigData
import com.example.data.model.WebhookPayload
import com.example.util.ExtractedTransactionData
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
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
) {

    suspend fun dispatchSms(
        config: ConfigData,
        sender: String,
        messageBody: String,
        simSlot: String = "SIM_1",
        isoTimestamp: String = getCurrentIsoTimestamp(),
        cleanData: ExtractedTransactionData? = null
    ): DispatchResult = withContext(Dispatchers.IO) {
        if (config.deviceToken.isBlank() || config.deviceId.isBlank()) {
            return@withContext DispatchResult.Failure(
                code = null,
                errorMessage = "Failed: Not Paired"
            )
        }

        if (config.webhookUrl.isBlank()) {
            return@withContext DispatchResult.Failure(
                code = null,
                errorMessage = "Missing webhook URL"
            )
        }

        try {
            // Clean Payload Dispatch Format:
            // {
            //   "gateway": "bKash",
            //   "amount": 500.0,
            //   "senderNumber": "017XXXXXXXX",
            //   "trxId": "BLA123XYZ",
            //   "timestamp": "<current_iso_8601_timestamp>",
            //   "sender": "<extracted_sender>",
            //   "messageBody": "<extracted_message_body>",
            //   "simSlot": "SIM_1",
            //   "deviceId": "<saved_deviceId>"
            // }
            val jsonObject = JSONObject().apply {
                if (cleanData != null) {
                    put("gateway", cleanData.gateway)
                    put("amount", cleanData.amount)
                    put("trxId", cleanData.trxId)
                    put("senderNumber", cleanData.senderNumber.orEmpty())
                    cleanData.fee?.let { put("fee", it) }
                    cleanData.balance?.let { put("balance", it) }
                }
                put("sender", sender)
                put("messageBody", messageBody)
                put("timestamp", cleanData?.timestamp ?: isoTimestamp)
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
                .addHeader("Authorization", "Bearer ${config.deviceToken}")
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

    suspend fun testConnection(config: ConfigData): DispatchResult = withContext(Dispatchers.IO) {
        if (config.deviceToken.isBlank()) {
            return@withContext DispatchResult.Failure(code = null, errorMessage = "Failed: Not Paired")
        }

        if (config.webhookUrl.isBlank()) {
            return@withContext DispatchResult.Failure(code = null, errorMessage = "Missing webhook URL")
        }

        try {
            // Pairing sync "PING" request
            val jsonObject = JSONObject().apply {
                put("action", "PING")
                put("deviceId", config.deviceId)
                put("deviceToken", config.deviceToken)
                put("timestamp", getCurrentIsoTimestamp())
            }

            val jsonString = jsonObject.toString()
            Log.d(TAG, "Sending PING to ${config.webhookUrl}: $jsonString")

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = jsonString.toRequestBody(mediaType)

            val request = Request.Builder()
                .url(config.webhookUrl)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer ${config.deviceToken}")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                val code = response.code

                if (response.isSuccessful) {
                    Log.d(TAG, "PING dispatched successfully! Code: $code, Body: $responseBody")
                    DispatchResult.Success(code = code, body = responseBody)
                } else {
                    val errorMsg = "HTTP $code: ${response.message.ifBlank { responseBody.take(120) }}"
                    Log.w(TAG, "PING response failed: $errorMsg")
                    DispatchResult.Failure(code = code, errorMessage = errorMsg)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send PING: ${e.message}", e)
            DispatchResult.Failure(
                code = null,
                errorMessage = e.localizedMessage ?: "Network connection error"
            )
        }
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
