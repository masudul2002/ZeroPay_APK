package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.data.model.ConfigData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

class SecurePreferencesManager(private val context: Context) {

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val securePrefs: SharedPreferences by lazy {
        try {
            EncryptedSharedPreferences.create(
                context.applicationContext,
                PREFS_SECURE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "EncryptedSharedPreferences init failed, falling back to private prefs", e)
            context.applicationContext.getSharedPreferences(PREFS_FALLBACK_NAME, Context.MODE_PRIVATE)
        }
    }

    private val generalPrefs: SharedPreferences by lazy {
        context.applicationContext.getSharedPreferences(PREFS_GENERAL_NAME, Context.MODE_PRIVATE)
    }

    private val _configState = MutableStateFlow(readConfig())
    val configState: StateFlow<ConfigData> = _configState.asStateFlow()

    private val _allowedSenders = MutableStateFlow(readAllowedSenders())
    val allowedSenders: StateFlow<Set<String>> = _allowedSenders.asStateFlow()

    private val _isForwardingActive = MutableStateFlow(isForwardingActive())
    val isForwardingActive: StateFlow<Boolean> = _isForwardingActive.asStateFlow()

    fun readConfig(): ConfigData {
        val webhookUrl = securePrefs.getString(KEY_WEBHOOK_URL, "").orEmpty()
        val deviceSecret = securePrefs.getString(KEY_DEVICE_SECRET, "").orEmpty()
        val deviceId = securePrefs.getString(KEY_DEVICE_ID, "").orEmpty()
        return ConfigData(webhookUrl, deviceSecret, deviceId)
    }

    fun saveConfig(webhookUrl: String, deviceSecret: String, deviceId: String) {
        securePrefs.edit()
            .putString(KEY_WEBHOOK_URL, webhookUrl.trim())
            .putString(KEY_DEVICE_SECRET, deviceSecret.trim())
            .putString(KEY_DEVICE_ID, deviceId.trim())
            .apply()
        _configState.value = ConfigData(webhookUrl.trim(), deviceSecret.trim(), deviceId.trim())
    }

    fun parseAndSaveQrJson(qrJsonString: String): Result<ConfigData> {
        return try {
            val json = JSONObject(qrJsonString.trim())
            val webhookUrl = json.optString("webhookUrl", "").trim()
            val deviceSecret = json.optString("deviceSecret", "").trim()
            val deviceId = json.optString("deviceId", "").trim()

            if (webhookUrl.isBlank()) {
                return Result.failure(IllegalArgumentException("Missing or empty 'webhookUrl' in QR code"))
            }
            if (deviceSecret.isBlank()) {
                return Result.failure(IllegalArgumentException("Missing or empty 'deviceSecret' in QR code"))
            }
            if (deviceId.isBlank()) {
                return Result.failure(IllegalArgumentException("Missing or empty 'deviceId' in QR code"))
            }

            saveConfig(webhookUrl, deviceSecret, deviceId)
            Result.success(ConfigData(webhookUrl, deviceSecret, deviceId))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse QR JSON: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun clearConfig() {
        securePrefs.edit().clear().apply()
        _configState.value = ConfigData("", "", "")
    }

    fun readAllowedSenders(): Set<String> {
        val saved = generalPrefs.getStringSet(KEY_ALLOWED_SENDERS, null)
        return if (saved == null) {
            // Initial defaults: Bangladeshi payment providers and common shortcodes
            val defaults = DEFAULT_SENDERS
            generalPrefs.edit().putStringSet(KEY_ALLOWED_SENDERS, defaults).apply()
            defaults
        } else {
            saved
        }
    }

    fun toggleSender(sender: String, enabled: Boolean) {
        val current = _allowedSenders.value.toMutableSet()
        if (enabled) {
            current.add(sender.trim())
        } else {
            current.remove(sender.trim())
        }
        generalPrefs.edit().putStringSet(KEY_ALLOWED_SENDERS, current).apply()
        _allowedSenders.value = current
    }

    fun addSender(sender: String): Boolean {
        val trimmed = sender.trim()
        if (trimmed.isBlank()) return false
        val current = _allowedSenders.value.toMutableSet()
        val added = current.add(trimmed)
        if (added) {
            generalPrefs.edit().putStringSet(KEY_ALLOWED_SENDERS, current).apply()
            _allowedSenders.value = current
        }
        return added
    }

    fun removeSender(sender: String) {
        val current = _allowedSenders.value.toMutableSet()
        current.remove(sender.trim())
        generalPrefs.edit().putStringSet(KEY_ALLOWED_SENDERS, current).apply()
        _allowedSenders.value = current
    }

    fun isSenderAllowed(rawSender: String): Boolean {
        val normalized = rawSender.trim().uppercase()
        val senders = _allowedSenders.value
        return senders.any { it.trim().uppercase() == normalized }
    }

    fun isForwardingActive(): Boolean {
        return generalPrefs.getBoolean(KEY_FORWARDING_ACTIVE, true)
    }

    fun setForwardingActive(active: Boolean) {
        generalPrefs.edit().putBoolean(KEY_FORWARDING_ACTIVE, active).apply()
        _isForwardingActive.value = active
    }

    companion object {
        private const val TAG = "ZeroPaySecurePrefs"
        private const val PREFS_SECURE_NAME = "zeropay_secure_prefs"
        private const val PREFS_FALLBACK_NAME = "zeropay_prefs_fallback"
        private const val PREFS_GENERAL_NAME = "zeropay_general_settings"

        private const val KEY_WEBHOOK_URL = "webhook_url"
        private const val KEY_DEVICE_SECRET = "device_secret"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_ALLOWED_SENDERS = "allowed_senders_list"
        private const val KEY_FORWARDING_ACTIVE = "forwarding_active"

        val DEFAULT_SENDERS = setOf(
            "bKash",
            "Nagad",
            "16216",
            "16167",
            "ROCKET",
            "UPAY"
        )
    }
}
