package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.SmsLogEntity
import com.example.data.model.ConfigData
import com.example.data.model.CustomFilterRule
import com.example.data.network.DispatchResult
import com.example.data.repository.SmsRepository
import com.example.service.SmsForwarderService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SmsRepository.getInstance(application)

    val config: StateFlow<ConfigData> = repository.configState
    val allowedSenders: StateFlow<Set<String>> = repository.allowedSenders
    val isForwardingActive: StateFlow<Boolean> = repository.isForwardingActive
    val isWhitelistEnabled: StateFlow<Boolean> = repository.isWhitelistEnabled
    val ignoreKeywords: StateFlow<Set<String>> = repository.ignoreKeywords
    val customFilterRules: StateFlow<List<CustomFilterRule>> = repository.customFilterRules

    val logs: StateFlow<List<SmsLogEntity>> = repository.allLogs.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val successCount: StateFlow<Int> = repository.successCount.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    val failedCount: StateFlow<Int> = repository.failedCount.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    private val _testStatus = MutableStateFlow<String?>(null)
    val testStatus: StateFlow<String?> = _testStatus.asStateFlow()

    private val _isTesting = MutableStateFlow(false)
    val isTesting: StateFlow<Boolean> = _isTesting.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    private val _connectionError = MutableStateFlow<String?>(null)
    val connectionError: StateFlow<String?> = _connectionError.asStateFlow()

    fun clearConnectionError() {
        _connectionError.value = null
    }

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    fun clearLastError() {
        _lastError.value = null
        _connectionError.value = null
    }

    private val _lastScannedPayload = MutableStateFlow(repository.readLastScannedPayload())
    val lastScannedPayload: StateFlow<String> = _lastScannedPayload.asStateFlow()

    fun setScannedPayload(payload: String) {
        val trimmed = payload.trim()
        _lastScannedPayload.value = trimmed
        repository.saveLastScannedPayload(trimmed)
    }

    fun clearScannedPayload() {
        _lastScannedPayload.value = ""
        repository.saveLastScannedPayload("")
    }

    fun onQrScanned(rawJson: String): Result<ConfigData> {
        val trimmed = rawJson.trim()
        setScannedPayload(trimmed)
        val result = repository.parseAndSaveQrJson(trimmed)
        if (result.isSuccess) {
            _testStatus.value = "QR Code successfully captured and configured!"
        }
        return result
    }

    suspend fun connectAndHandshake(
        webhookUrl: String,
        deviceToken: String,
        deviceId: String
    ): DispatchResult {
        _isConnecting.value = true
        _connectionError.value = null
        _testStatus.value = "Verifying pairing with server..."
        try {
            val result = repository.verifyAndConnect(webhookUrl, deviceToken, deviceId)
            when (result) {
                is DispatchResult.Success -> {
                    _testStatus.value = "Paired successfully with Zero Pay! (HTTP ${result.code})"
                    _lastError.value = null
                    _connectionError.value = null
                }
                is DispatchResult.Failure -> {
                    val errorMsg = result.errorMessage
                    _testStatus.value = errorMsg
                    _lastError.value = errorMsg
                    _connectionError.value = errorMsg
                }
            }
            return result
        } catch (e: Exception) {
            val errorMsg = e.localizedMessage ?: "Connection error"
            _testStatus.value = errorMsg
            _lastError.value = errorMsg
            _connectionError.value = errorMsg
            return DispatchResult.Failure(code = null, errorMessage = errorMsg)
        } finally {
            _isConnecting.value = false
        }
    }

    suspend fun connectAndHandshakePayload(rawPayload: String): DispatchResult {
        val trimmed = rawPayload.trim()
        setScannedPayload(trimmed)
        _isConnecting.value = true
        _connectionError.value = null
        _testStatus.value = "Verifying pairing with server..."
        try {
            val result = repository.verifyAndConnectPayload(trimmed)
            when (result) {
                is DispatchResult.Success -> {
                    _testStatus.value = "Paired successfully with Zero Pay! (HTTP ${result.code})"
                    _lastError.value = null
                    _connectionError.value = null
                }
                is DispatchResult.Failure -> {
                    val errorMsg = result.errorMessage
                    _testStatus.value = errorMsg
                    _lastError.value = errorMsg
                    _connectionError.value = errorMsg
                }
            }
            return result
        } catch (e: Exception) {
            val errorMsg = e.localizedMessage ?: "Connection error"
            _testStatus.value = errorMsg
            _lastError.value = errorMsg
            _connectionError.value = errorMsg
            return DispatchResult.Failure(code = null, errorMessage = errorMsg)
        } finally {
            _isConnecting.value = false
        }
    }

    fun saveConfigManual(webhookUrl: String, deviceToken: String, deviceId: String) {
        repository.saveConfig(webhookUrl, deviceToken, deviceId)
        _testStatus.value = "Settings saved successfully"
    }

    fun clearConfig() {
        repository.clearConfig()
        _testStatus.value = "Configuration cleared"
    }

    fun toggleSender(sender: String, enabled: Boolean) {
        repository.toggleSender(sender, enabled)
    }

    fun setAllSenders(senders: Set<String>) {
        repository.setAllSenders(senders)
    }

    fun addCustomSender(sender: String): Boolean {
        return repository.addSender(sender)
    }

    fun removeSender(sender: String) {
        repository.removeSender(sender)
    }

    fun addCustomFilterRule(rule: CustomFilterRule) {
        repository.addCustomFilterRule(rule)
    }

    fun updateCustomFilterRule(rule: CustomFilterRule) {
        repository.updateCustomFilterRule(rule)
    }

    fun deleteCustomFilterRule(ruleId: String) {
        repository.deleteCustomFilterRule(ruleId)
    }

    fun toggleCustomFilterRule(ruleId: String, enabled: Boolean) {
        repository.toggleCustomFilterRule(ruleId, enabled)
    }

    fun setWhitelistEnabled(enabled: Boolean) {
        repository.setWhitelistEnabled(enabled)
    }

    fun addIgnoreKeyword(keyword: String): Boolean {
        return repository.addIgnoreKeyword(keyword)
    }

    fun removeIgnoreKeyword(keyword: String) {
        repository.removeIgnoreKeyword(keyword)
    }

    fun setForwardingActive(active: Boolean) {
        repository.setForwardingActive(active)
        if (active) {
            SmsForwarderService.start(getApplication())
        } else {
            SmsForwarderService.stop(getApplication())
        }
    }

    fun sendTestWebhook() {
        viewModelScope.launch {
            _isTesting.value = true
            _testStatus.value = "Testing webhook connection..."
            try {
                when (val result = repository.testConnection()) {
                    is DispatchResult.Success -> {
                        _testStatus.value = "Synced successfully! (HTTP ${result.code})"
                        _lastError.value = null
                    }
                    is DispatchResult.Failure -> {
                        val errMsg = "Sync failed: ${result.errorMessage ?: "Unknown error"}${result.code?.let { " (HTTP $it)" } ?: ""}"
                        _testStatus.value = errMsg
                        _lastError.value = errMsg
                    }
                }
            } catch (e: Exception) {
                val errMsg = "Sync error: ${e.message}"
                _testStatus.value = errMsg
                _lastError.value = errMsg
            } finally {
                _isTesting.value = false
            }
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            _isTesting.value = true
            _testStatus.value = "Syncing SMS Transactions..."
            val startTime = System.currentTimeMillis()
            try {
                val retriedCount = repository.retryAllFailed()
                val result = repository.testConnection()
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed < 2500L) {
                    kotlinx.coroutines.delay(2500L - elapsed)
                }
                when (result) {
                    is DispatchResult.Success -> {
                        val retrySuffix = if (retriedCount > 0) " ($retriedCount pending SMS forwarded)" else ""
                        _testStatus.value = "Synced successfully!$retrySuffix (HTTP ${result.code})"
                        _lastError.value = null
                    }
                    is DispatchResult.Failure -> {
                        val errMsg = "Sync finished: ${result.errorMessage ?: "Up to date"}"
                        _testStatus.value = errMsg
                        _lastError.value = null
                    }
                }
            } catch (e: Exception) {
                val errMsg = "Sync error: ${e.message}"
                _testStatus.value = errMsg
                _lastError.value = errMsg
            } finally {
                _isTesting.value = false
            }
        }
    }

    fun cleanOldLogs(olderThanDays: Int) {
        viewModelScope.launch {
            val count = repository.cleanOldLogs(olderThanDays)
            _testStatus.value = "Cleaned $count logs older than $olderThanDays days"
        }
    }

    fun simulateIncomingSms(sender: String, messageBody: String) {
        viewModelScope.launch {
            _isTesting.value = true
            _testStatus.value = "Simulating incoming SMS from $sender..."
            try {
                val result = repository.processIncomingSms(
                    sender = sender,
                    messageBody = messageBody,
                    simSlot = "SIM_1"
                )
                when (result) {
                    is DispatchResult.Success -> {
                        _testStatus.value = "Simulation forwarded successfully (HTTP ${result.code})"
                    }
                    is DispatchResult.Failure -> {
                        _testStatus.value = "Simulation result: ${result.errorMessage}"
                    }
                }
            } catch (e: Exception) {
                _testStatus.value = "Simulation error: ${e.message}"
            } finally {
                _isTesting.value = false
            }
        }
    }

    fun clearTestStatus() {
        _testStatus.value = null
    }

    fun clearLogs() {
        viewModelScope.launch {
            repository.clearLogs()
        }
    }

    fun deleteLog(id: Long) {
        viewModelScope.launch {
            repository.deleteLog(id)
        }
    }

    fun retryLog(log: SmsLogEntity) {
        viewModelScope.launch {
            _testStatus.value = "Retrying webhook dispatch..."
            try {
                when (val result = repository.retryLog(log)) {
                    is DispatchResult.Success -> {
                        _testStatus.value = "Retried successfully! (HTTP ${result.code})"
                        _lastError.value = null
                    }
                    is DispatchResult.Failure -> {
                        val errMsg = "Retry failed: ${result.errorMessage ?: "Unknown error"}${result.code?.let { " (HTTP $it)" } ?: ""}"
                        _testStatus.value = errMsg
                        _lastError.value = errMsg
                    }
                }
            } catch (e: Exception) {
                val errMsg = "Retry error: ${e.message}"
                _testStatus.value = errMsg
                _lastError.value = errMsg
            }
        }
    }

    fun retryAllFailed() {
        viewModelScope.launch {
            _testStatus.value = "Retrying all failed dispatches..."
            try {
                val count = repository.retryAllFailed()
                _testStatus.value = "Retried failed logs. $count succeeded."
            } catch (e: Exception) {
                _testStatus.value = "Retry all error: ${e.message}"
            }
        }
    }
}
