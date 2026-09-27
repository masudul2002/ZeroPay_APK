package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.SecurePreferencesManager
import com.example.data.local.entity.SmsLogEntity
import com.example.data.model.ConfigData
import com.example.data.model.CustomFilterRule
import com.example.data.network.DispatchResult
import com.example.data.network.WebhookDispatcher
import com.example.util.ExtractedTransactionData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

class SmsRepository(
    private val securePreferencesManager: SecurePreferencesManager,
    private val database: AppDatabase,
    private val webhookDispatcher: WebhookDispatcher = WebhookDispatcher(),
    private val context: Context? = null
) {
    val configState: StateFlow<ConfigData> = securePreferencesManager.configState
    val allowedSenders: StateFlow<Set<String>> = securePreferencesManager.allowedSenders
    val isForwardingActive: StateFlow<Boolean> = securePreferencesManager.isForwardingActive
    val isWhitelistEnabled: StateFlow<Boolean> = securePreferencesManager.isWhitelistEnabled
    val ignoreKeywords: StateFlow<Set<String>> = securePreferencesManager.ignoreKeywords
    val customFilterRules: StateFlow<List<CustomFilterRule>> = securePreferencesManager.customFilterRules

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

    fun readLastScannedPayload(): String = securePreferencesManager.readLastScannedPayload()

    fun saveLastScannedPayload(payload: String) {
        securePreferencesManager.saveLastScannedPayload(payload)
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

    fun getCustomFilterRules(): List<CustomFilterRule> = securePreferencesManager.getCustomFilterRules()
    fun saveCustomFilterRules(rules: List<CustomFilterRule>) = securePreferencesManager.saveCustomFilterRules(rules)
    fun addCustomFilterRule(rule: CustomFilterRule) = securePreferencesManager.addCustomFilterRule(rule)
    fun updateCustomFilterRule(rule: CustomFilterRule) = securePreferencesManager.updateCustomFilterRule(rule)
    fun deleteCustomFilterRule(ruleId: String) = securePreferencesManager.deleteCustomFilterRule(ruleId)
    fun toggleCustomFilterRule(ruleId: String, enabled: Boolean) = securePreferencesManager.toggleCustomFilterRule(ruleId, enabled)

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

    suspend fun logIgnoredSms(
        sender: String,
        messageBody: String,
        simSlot: String = "SIM_1",
        reason: String = "Filtered: Not a valid financial transaction (Promotional/OTP)"
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
            errorMessage = reason
        )
        database.smsLogDao().insertLog(ignoredLog)
        return ignoredLog
    }

    suspend fun logIgnoredPromoOrOtp(
        sender: String,
        messageBody: String,
        simSlot: String = "SIM_1"
    ): SmsLogEntity {
        return logIgnoredSms(sender, messageBody, simSlot, "Filtered: Not a valid financial transaction (Promotional/OTP)")
    }

    fun isNetworkConnected(): Boolean {
        val ctx = context ?: return true
        return try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                val network = cm?.activeNetwork ?: return false
                val caps = cm.getNetworkCapabilities(network) ?: return false
                caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } else {
                @Suppress("DEPRECATION")
                cm?.activeNetworkInfo?.isConnected == true
            }
        } catch (_: Exception) {
            true
        }
    }

    suspend fun performSilentHandshake(config: ConfigData): Boolean {
        return try {
            Log.d(TAG, "Auto-Handshake: verifying lightweight connectivity for device ${config.effectiveDeviceId}")
            val pingResult = webhookDispatcher.testConnection(config)
            pingResult is DispatchResult.Success
        } catch (e: Exception) {
            Log.w(TAG, "Silent auto-handshake ping non-fatal notice: ${e.message}")
            false
        }
    }

    suspend fun processIncomingSms(
        sender: String,
        messageBody: String,
        simSlot: String = "SIM_1",
        cleanData: ExtractedTransactionData? = null
    ): DispatchResult {
        val isoTimestamp = WebhookDispatcher.getCurrentIsoTimestamp()
        var config = securePreferencesManager.readConfig()
        val effectiveDeviceId = config.effectiveDeviceId

        // Auto-Repair pairing state: if credentials exist but deviceId is blank, synchronize it
        if (config.deviceId.isBlank() && config.isConfigured) {
            Log.d(TAG, "Auto-repairing pairing state with effective deviceId: $effectiveDeviceId")
            securePreferencesManager.saveConfig(config.webhookUrl, config.deviceToken, effectiveDeviceId)
            config = securePreferencesManager.readConfig()
        }

        // 1. Bypass False "Not Paired" Blocking:
        // Only if BOTH webhookUrl and deviceToken are completely absent is it truly not paired
        if (config.deviceToken.isBlank() && config.webhookUrl.isBlank()) {
            Log.w(TAG, "No credentials configured. Logging as Failed: Not Paired")
            val unpairedLog = SmsLogEntity(
                sender = sender,
                messageBody = messageBody,
                timestamp = isoTimestamp,
                simSlot = simSlot,
                deviceId = "NOT_PAIRED",
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
                deviceId = effectiveDeviceId,
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
                deviceId = effectiveDeviceId,
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
                deviceId = effectiveDeviceId,
                status = "FILTERED",
                errorMessage = "Sender not in allowed senders list"
            )
            database.smsLogDao().insertLog(filteredLog)
            return DispatchResult.Failure(code = null, errorMessage = "Sender not allowed")
        }

        // 5. If webhookUrl is missing but token exists, fallback to queued retry state rather than dropping
        if (config.webhookUrl.isBlank()) {
            Log.w(TAG, "Webhook URL missing but device token exists. Queuing SMS for retry.")
            val queuedLog = SmsLogEntity(
                sender = sender,
                messageBody = messageBody,
                timestamp = isoTimestamp,
                simSlot = simSlot,
                deviceId = effectiveDeviceId,
                status = "RETRY",
                errorMessage = "Webhook URL not configured - SMS queued for retry"
            )
            database.smsLogDao().insertLog(queuedLog)
            return DispatchResult.Failure(code = null, errorMessage = "Webhook URL missing - queued for retry")
        }

        // 6. Connectivity Verification: If offline, immediately queue for retry rather than hanging or dropping
        if (!isNetworkConnected()) {
            Log.w(TAG, "Device is offline. Queuing incoming transaction SMS for background retry.")
            val offlineLog = SmsLogEntity(
                sender = sender,
                messageBody = messageBody,
                timestamp = isoTimestamp,
                simSlot = simSlot,
                deviceId = effectiveDeviceId,
                status = "RETRY",
                errorMessage = "Device offline (No internet connection) - SMS queued for retry"
            )
            database.smsLogDao().insertLog(offlineLog)
            return DispatchResult.Failure(code = null, errorMessage = "Device offline - queued for retry")
        }

        // 7. Auto-Handshake / Silent Re-Pairing:
        // Lightweight token validation attempt; does not block core forwarding if handshake server ping varies
        performSilentHandshake(config)

        // 8. Dispatch to webhook with Authorization: Bearer <deviceToken>
        val result = webhookDispatcher.dispatchSms(
            config = config,
            sender = sender,
            messageBody = messageBody,
            simSlot = simSlot,
            isoTimestamp = isoTimestamp,
            cleanData = cleanData
        )

        // 9. Log result into Room database
        val logEntity = when (result) {
            is DispatchResult.Success -> SmsLogEntity(
                sender = sender,
                messageBody = messageBody,
                timestamp = isoTimestamp,
                simSlot = simSlot,
                deviceId = effectiveDeviceId,
                status = "SUCCESS",
                httpCode = result.code,
                errorMessage = null
            )
            is DispatchResult.Failure -> {
                // If network failure / connection error, fallback to queued retry state
                val isNetworkError = result.code == null
                val status = if (isNetworkError) "RETRY" else "FAILED"
                SmsLogEntity(
                    sender = sender,
                    messageBody = messageBody,
                    timestamp = isoTimestamp,
                    simSlot = simSlot,
                    deviceId = effectiveDeviceId,
                    status = status,
                    httpCode = result.code,
                    errorMessage = if (isNetworkError) "Network error (queued for retry): ${result.errorMessage}" else result.errorMessage
                )
            }
        }
        database.smsLogDao().insertLog(logEntity)

        return result
    }

    suspend fun retryLog(log: SmsLogEntity): DispatchResult {
        val config = securePreferencesManager.readConfig()
        if (!config.isConfigured) {
            return DispatchResult.Failure(code = null, errorMessage = "Zero Pay webhook not configured yet")
        }

        val result = webhookDispatcher.dispatchSms(
            config = config,
            sender = log.sender,
            messageBody = log.messageBody,
            simSlot = log.simSlot,
            isoTimestamp = log.timestamp
        )

        when (result) {
            is DispatchResult.Success -> {
                database.smsLogDao().updateLogStatus(log.id, "SUCCESS", result.code, null)
            }
            is DispatchResult.Failure -> {
                val newStatus = if (result.code == null) "RETRY" else "FAILED"
                database.smsLogDao().updateLogStatus(log.id, newStatus, result.code, result.errorMessage)
            }
        }

        return result
    }

    suspend fun retryAllFailed(): Int {
        val failed = database.smsLogDao().getFailedLogsList()
        var successCount = 0
        for (log in failed) {
            val res = retryLog(log)
            if (res is DispatchResult.Success) {
                successCount++
            }
        }
        return successCount
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
                    SmsRepository(securePrefs, database, dispatcher, appContext).also { INSTANCE = it }
                }
            }
        }
    }
}
