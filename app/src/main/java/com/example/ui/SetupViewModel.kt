package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.data.local.SecurePreferencesManager
import com.example.data.model.ConfigData
import com.example.data.network.DispatchResult
import com.example.data.repository.SmsRepository
import com.example.util.QrCodeAnalyzer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SetupViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SmsRepository.getInstance(application)
    private val securePrefs = SecurePreferencesManager(application)

    val config: StateFlow<ConfigData> = securePrefs.configState

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    private val _connectionError = MutableStateFlow<String?>(null)
    val connectionError: StateFlow<String?> = _connectionError.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    /**
     * Parses any raw QR code or manual JSON string infallibly into ConfigData.
     */
    fun parsePayload(payload: String): Result<ConfigData> {
        return QrCodeAnalyzer.parseConfigurationPayload(payload, securePrefs.readConfig())
    }

    /**
     * Triggers an immediate HTTP handshake (PING) with the Zero Pay backend before persisting.
     */
    suspend fun connectAndHandshake(
        webhookUrl: String,
        deviceToken: String,
        deviceId: String = ""
    ): DispatchResult {
        _isConnecting.value = true
        _connectionError.value = null
        try {
            val result = repository.verifyAndConnect(webhookUrl, deviceToken, deviceId)
            when (result) {
                is DispatchResult.Success -> {
                    _statusMessage.value = "Paired successfully with Zero Pay! (HTTP ${result.code})"
                    _connectionError.value = null
                }
                is DispatchResult.Failure -> {
                    _statusMessage.value = result.errorMessage
                    _connectionError.value = result.errorMessage
                }
            }
            return result
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Connection error"
            _connectionError.value = msg
            return DispatchResult.Failure(code = null, errorMessage = msg)
        } finally {
            _isConnecting.value = false
        }
    }

    /**
     * Parses a payload and immediately connects and synchronizes pairing state.
     */
    suspend fun connectAndHandshakePayload(rawPayload: String): DispatchResult {
        _isConnecting.value = true
        _connectionError.value = null
        try {
            val parseResult = parsePayload(rawPayload)
            if (parseResult.isFailure) {
                val err = parseResult.exceptionOrNull()?.message ?: "Invalid QR or JSON configuration"
                _connectionError.value = err
                return DispatchResult.Failure(code = null, errorMessage = err)
            }
            val config = parseResult.getOrThrow()
            return connectAndHandshake(config.webhookUrl, config.deviceToken, config.deviceId)
        } finally {
            _isConnecting.value = false
        }
    }

    fun clearConnectionError() {
        _connectionError.value = null
    }
}
