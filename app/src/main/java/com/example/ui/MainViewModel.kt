package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.SmsLogEntity
import com.example.data.model.ConfigData
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

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    fun clearLastError() {
        _lastError.value = null
    }

    fun onQrScanned(rawJson: String): Result<ConfigData> {
        val result = repository.parseAndSaveQrJson(rawJson)
        if (result.isSuccess) {
            _testStatus.value = "QR Code successfully configured!"
        }
        return result
    }

    fun saveConfigManual(webhookUrl: String, deviceSecret: String, deviceId: String) {
        repository.saveConfig(webhookUrl, deviceSecret, deviceId)
        _testStatus.value = "Settings saved successfully"
    }

    fun clearConfig() {
        repository.clearConfig()
        _testStatus.value = "Configuration cleared"
    }

    fun toggleSender(sender: String, enabled: Boolean) {
        repository.toggleSender(sender, enabled)
    }

    fun addCustomSender(sender: String): Boolean {
        return repository.addSender(sender)
    }

    fun removeSender(sender: String) {
        repository.removeSender(sender)
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
                        _testStatus.value = "Webhook verified successfully! (HTTP ${result.code})"
                        _lastError.value = null
                    }
                    is DispatchResult.Failure -> {
                        val errMsg = "Webhook failed: ${result.errorMessage ?: "Unknown error"}${result.code?.let { " (HTTP $it)" } ?: ""}"
                        _testStatus.value = errMsg
                        _lastError.value = errMsg
                    }
                }
            } catch (e: Exception) {
                val errMsg = "Error testing connection: ${e.message}"
                _testStatus.value = errMsg
                _lastError.value = errMsg
            } finally {
                _isTesting.value = false
            }
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
}
