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
    val isWhitelistEnabled: StateFlow<Boolean> = securePreferencesManager.isWhitelistEnabled
    val ignoreKeywords: StateFlow<Set<String>> = securePreferencesManager.ignoreKeywords

    val allLogs: Flow<List<SmsLogEntity>> = database.smsLogDao().getAllLogs()
    val successCount: Flow<Int> = database.smsLogDao().getSuccessCount()
    val failedCount: Flow<Int> = database.smsLogDao().getFailedCount()

    fun getConfig(): ConfigData = securePreferencesManager.readConfig()

    fun saveConfig(webhookUrl: String, deviceToken: String, deviceId: String) {
        securePreferencesManager.saveConfig(webhookUrl, deviceToken, deviceId)
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

    fun setAllSenders(senders: Set<String>) {
        securePreferencesManager.setAllSenders(senders)
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

    fun setWhitelistEnabled(enabled: Boolean) {
        securePreferencesManager.setWhitelistEnabled(enabled)
    }

    fun addIgnoreKeyword(keyword: String): Boolean {
        return securePreferencesManager.addIgnoreKeyword(keyword)
    }

    fun removeIgnoreKeyword(keyword: String) {
        securePreferencesManager.removeIgnoreKeyword(keyword)
    }

    fun isSenderAllowed(sender: String): Boolean {
        return securePreferencesManager.isSenderAllowed(sender)
    }

    suspend fun cleanOldLogs(olderThanDays: Int): Int {
        val cutoffMillis = System.currentTimeMillis() - (olderThanDays.toLong() * 24L * 60L * 60L * 1000L)
        return database.smsLogDao().deleteLogsOlderThan(cutoffMillis)
    }

    suspend fun clearLogs() {
        database.smsLogDao().clearAll()
    }

    suspend fun deleteLog(id: Long) {
        database.smsLogDao().deleteById(id)
    }

    suspend fun testConnection(): DispatchResult {
        val config = securePreferencesManager.readConfig()
        return webhookDispatcher.testConnection(config)
    }

    suspend fun logIgnoredPromoOrOtp(
        sender: String,
        messageBody: String,
        simSlot: String = "SIM_1"
    ): SmsLogEntity {
        val isoTimestamp = WebhookDispatcher.getCurrentIsoTimestamp()
        val config = securePreferencesManager.readConfig()
        val ignoredLog = SmsLogEntity(
            sender = sender,
            messageBody = messageBody,
            timestamp = isoTimestamp,
            simSlot = simSlot,
            deviceId = config.deviceId.ifBlank { "NOT_SET" },
            status = "Status: Ignored (Promotional/OTP)",
            httpCode = null,
            errorMessage = "Filtered: Not a valid financial transaction (Promotional/OTP)"
        )
        database.smsLogDao().insertLog(ignoredLog)
        return ignoredLog
    }

    suspend fun processIncomingSms(
        sender: String,
        messageBody: String,
        simSlot: String = "SIM_1"
    ): DispatchResult {
        val isoTimestamp = WebhookDispatcher.getCurrentIsoTimestamp()
        val config = securePreferencesManager.readConfig()

        // 1. Verify if deviceToken exists before forwarding
        if (config.deviceToken.isBlank()) {
            Log.w(TAG, "Device token missing. Logging as Failed: Not Paired")
            val unpairedLog = SmsLogEntity(
                sender = sender,
                messageBody = messageBody,
                timestamp = isoTimestamp,
                simSlot = simSlot,
                deviceId = config.deviceId.ifBlank { "NOT_PAIRED" },
                status = "Failed: Not Paired",
                httpCode = null,
                errorMessage = "Failed: Not Paired"
            )
            database.smsLogDao().insertLog(unpairedLog)
            return DispatchResult.Failure(code = null, errorMessage = "Failed: Not Paired")
        }

        // 2. Check if message contains any ignore keyword
        if (securePreferencesManager.containsIgnoreKeyword(messageBody)) {
            Log.d(TAG, "SMS contains ignored keyword, logging filtered: $sender")
            val ignoredLog = SmsLogEntity(
                sender = sender,
                messageBody = messageBody,
                timestamp = isoTimestamp,
                simSlot = simSlot,
                deviceId = config.deviceId,
                status = "FILTERED",
                errorMessage = "Message contains ignored keyword"
            )
            database.smsLogDao().insertLog(ignoredLog)
            return DispatchResult.Failure(code = null, errorMessage = "Contains ignored keyword")
        }

        // 3. Check if forwarding service is paused
        val isForwarding = securePreferencesManager.isForwardingActive()
        if (!isForwarding) {
            Log.d(TAG, "Forwarding paused by user, ignoring SMS from: $sender")
            val pausedLog = SmsLogEntity(
                sender = sender,
                messageBody = messageBody,
                timestamp = isoTimestamp,
                simSlot = simSlot,
                deviceId = config.deviceId,
                status = "PAUSED",
                errorMessage = "Forwarding is turned off in app settings"
            )
            database.smsLogDao().insertLog(pausedLog)
            return DispatchResult.Failure(code = null, errorMessage = "Forwarding paused")
        }

        // 4. Check if sender matches whitelist validation
        val isAllowed = securePreferencesManager.isSenderAllowed(sender)
        if (!isAllowed) {
            Log.d(TAG, "Sender '$sender' not in allowed senders list. Logging filtered.")
            val filteredLog = SmsLogEntity(
                sender = sender,
                messageBody = messageBody,
                timestamp = isoTimestamp,
                simSlot = simSlot,
                deviceId = config.deviceId,
                status = "FILTERED",
                errorMessage = "Sender not in allowed senders list"
            )
            database.smsLogDao().insertLog(filteredLog)
            return DispatchResult.Failure(code = null, errorMessage = "Sender not allowed")
        }

        if (!config.isConfigured) {
            val unconfiguredLog = SmsLogEntity(
                sender = sender,
                messageBody = messageBody,
                timestamp = isoTimestamp,
                simSlot = simSlot,
                deviceId = "UNCONFIGURED",
                status = "Failed: Not Paired",
                errorMessage = "Zero Pay webhook not configured yet"
            )
            database.smsLogDao().insertLog(unconfiguredLog)
            return DispatchResult.Failure(code = null, errorMessage = "Failed: Not Paired")
        }

        // 5. Dispatch to webhook with Authorization: Bearer <deviceToken>
        val result = webhookDispatcher.dispatchSms(
            config = config,
            sender = sender,
            messageBody = messageBody,
            simSlot = simSlot,
            isoTimestamp = isoTimestamp
        )

        // 6. Log result into Room database
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
                status = if (result.errorMessage == "Failed: Not Paired") "Failed: Not Paired" else "FAILED",
                httpCode = result.code,
                errorMessage = result.errorMessage
            )
        }
        database.smsLogDao().insertLog(logEntity)

        return result
    }

    companion object {
        private const val TAG = "ZeroPaySmsRepository"

        @Volatile
        private var INSTANCE: SmsRepository? = null

        fun getInstance(context: Context): SmsRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    val appContext = context.applicationContext
                    val database = AppDatabase.getInstance(appContext)
                    val securePrefs = SecurePreferencesManager(appContext)
                    val dispatcher = WebhookDispatcher()
                    SmsRepository(securePrefs, database, dispatcher).also { INSTANCE = it }
                }
            }
        }
    }
}
