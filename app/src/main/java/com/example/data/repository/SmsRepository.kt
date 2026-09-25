package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.SecurePreferencesManager
import com.example.data.local.entity.SmsLogEntity
import com.example.data.model.ConfigData
import com.example.data.network.DispatchResult
import com.example.data.network.WebhookDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

class SmsRepository(
    private val securePreferencesManager: SecurePreferencesManager,
    private val database: AppDatabase,
    private val webhookDispatcher: WebhookDispatcher = WebhookDispatcher()
) {
    val configState: StateFlow<ConfigData> = securePreferencesManager.configState
    val allowedSenders: StateFlow<Set<String>> = securePreferencesManager.allowedSenders
    val isForwardingActive: StateFlow<Boolean> = securePreferencesManager.isForwardingActive

    val allLogs: Flow<List<SmsLogEntity>> = database.smsLogDao().getAllLogs()
    val successCount: Flow<Int> = database.smsLogDao().getSuccessCount()
    val failedCount: Flow<Int> = database.smsLogDao().getFailedCount()

    fun getConfig(): ConfigData = securePreferencesManager.readConfig()

    fun saveConfig(webhookUrl: String, deviceSecret: String, deviceId: String) {
        securePreferencesManager.saveConfig(webhookUrl, deviceSecret, deviceId)
    }

    fun parseAndSaveQrJson(qrJson: String): Result<ConfigData> {
        return securePreferencesManager.parseAndSaveQrJson(qrJson)
    }

    fun clearConfig() {
        securePreferencesManager.clearConfig()
    }

    fun toggleSender(sender: String, enabled: Boolean) {
        securePreferencesManager.toggleSender(sender, enabled)
    }

    fun addSender(sender: String): Boolean {
        return securePreferencesManager.addSender(sender)
    }

    fun removeSender(sender: String) {
        securePreferencesManager.removeSender(sender)
    }

    fun setForwardingActive(active: Boolean) {
        securePreferencesManager.setForwardingActive(active)
    }

    fun isSenderAllowed(sender: String): Boolean {
        return securePreferencesManager.isSenderAllowed(sender)
    }

    suspend fun processIncomingSms(
        sender: String,
        messageBody: String,
        simSlot: String = "SIM_1"
    ): DispatchResult {
        val isForwarding = securePreferencesManager.isForwardingActive()
        val isoTimestamp = WebhookDispatcher.getCurrentIsoTimestamp()
        val config = securePreferencesManager.readConfig()

        if (!isForwarding) {
            Log.d(TAG, "Forwarding paused by user, ignoring SMS from: $sender")
            val ignoredLog = SmsLogEntity(
                sender = sender,
                messageBody = messageBody,
                timestamp = isoTimestamp,
                simSlot = simSlot,
                deviceId = config.deviceId.ifBlank { "NOT_SET" },
                status = "PAUSED",
                errorMessage = "Forwarding is turned off in app settings"
            )
            database.smsLogDao().insertLog(ignoredLog)
            return DispatchResult.Failure(code = null, errorMessage = "Forwarding paused")
        }

        // Check if sender matches any allowed sender
        val isAllowed = securePreferencesManager.isSenderAllowed(sender)
        if (!isAllowed) {
            Log.d(TAG, "Sender '$sender' not in allowed senders list. Ignoring.")
            val ignoredLog = SmsLogEntity(
                sender = sender,
                messageBody = messageBody,
                timestamp = isoTimestamp,
                simSlot = simSlot,
                deviceId = config.deviceId.ifBlank { "NOT_SET" },
                status = "FILTERED",
                errorMessage = "Sender not in allowed senders list"
            )
            database.smsLogDao().insertLog(ignoredLog)
            return DispatchResult.Failure(code = null, errorMessage = "Sender not allowed")
        }

        if (!config.isConfigured) {
            val unconfiguredLog = SmsLogEntity(
                sender = sender,
                messageBody = messageBody,
                timestamp = isoTimestamp,
                simSlot = simSlot,
                deviceId = "UNCONFIGURED",
                status = "FAILED",
                errorMessage = "Zero Pay webhook not configured yet"
            )
            database.smsLogDao().insertLog(unconfiguredLog)
            return DispatchResult.Failure(code = null, errorMessage = "Not configured")
        }

        // Dispatch to webhook
        val result = webhookDispatcher.dispatchSms(
            config = config,
            sender = sender,
            messageBody = messageBody,
            simSlot = simSlot,
            isoTimestamp = isoTimestamp
        )

        // Log result into Room database
        val logEntity = when (result) {
            is DispatchResult.Success -> SmsLogEntity(
                sender = sender,
                messageBody = messageBody,
                timestamp = isoTimestamp,
                simSlot = simSlot,
                deviceId = config.deviceId,
                status = "SUCCESS",
                httpCode = result.code,
                errorMessage = null
            )
            is DispatchResult.Failure -> SmsLogEntity(
                sender = sender,
                messageBody = messageBody,
                timestamp = isoTimestamp,
                simSlot = simSlot,
                deviceId = config.deviceId,
                status = "FAILED",
                httpCode = result.code,
                errorMessage = result.errorMessage
            )
        }
        database.smsLogDao().insertLog(logEntity)

        return result
    }

    suspend fun testConnection(): DispatchResult {
        val config = securePreferencesManager.readConfig()
        return webhookDispatcher.testConnection(config)
    }

    suspend fun clearLogs() {
        database.smsLogDao().clearAll()
    }

    suspend fun deleteLog(id: Long) {
        database.smsLogDao().deleteById(id)
    }

    companion object {
        private const val TAG = "ZeroPayRepository"

        @Volatile
        private var INSTANCE: SmsRepository? = null

        fun getInstance(context: Context): SmsRepository {
            return INSTANCE ?: synchronized(this) {
                val db = AppDatabase.getInstance(context)
                val securePrefs = SecurePreferencesManager(context)
                val instance = SmsRepository(securePrefs, db)
                INSTANCE = instance
                instance
            }
        }
    }
}
