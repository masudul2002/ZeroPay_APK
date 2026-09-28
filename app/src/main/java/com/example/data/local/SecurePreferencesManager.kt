package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.data.model.ConfigData
import com.example.data.model.CustomFilterRule
import com.example.data.model.PredefinedSenders
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

    private val _isWhitelistEnabled = MutableStateFlow(isWhitelistEnabled())
    val isWhitelistEnabled: StateFlow<Boolean> = _isWhitelistEnabled.asStateFlow()

    private val _ignoreKeywords = MutableStateFlow(readIgnoreKeywords())
    val ignoreKeywords: StateFlow<Set<String>> = _ignoreKeywords.asStateFlow()

    private val _customFilterRules = MutableStateFlow(getCustomFilterRules())
    val customFilterRules: StateFlow<List<CustomFilterRule>> = _customFilterRules.asStateFlow()

    fun readConfig(): ConfigData {
        val webhookUrl = securePrefs.getString(KEY_WEBHOOK_URL, "").orEmpty()
        val deviceToken = (securePrefs.getString(KEY_DEVICE_TOKEN, null)
            ?: securePrefs.getString(KEY_DEVICE_SECRET, "")).orEmpty()
        var deviceId = securePrefs.getString(KEY_DEVICE_ID, "").orEmpty()
        if (deviceId.isBlank() && webhookUrl.isNotBlank() && deviceToken.isNotBlank()) {
            deviceId = "sim-gateway-01"
            securePrefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
        }
        return ConfigData(webhookUrl = webhookUrl, deviceToken = deviceToken, deviceId = deviceId)
    }

    fun saveConfig(webhookUrl: String, deviceToken: String, deviceId: String) {
        val resolvedDeviceId = deviceId.trim().ifBlank {
            if (webhookUrl.isNotBlank() && deviceToken.isNotBlank()) "sim-gateway-01" else ""
        }
        securePrefs.edit()
            .putString(KEY_WEBHOOK_URL, webhookUrl.trim())
            .putString(KEY_DEVICE_TOKEN, deviceToken.trim())
            .putString(KEY_DEVICE_SECRET, deviceToken.trim())
            .putString(KEY_DEVICE_ID, resolvedDeviceId)
            .apply()
        _configState.value = ConfigData(webhookUrl.trim(), deviceToken.trim(), resolvedDeviceId)
    }

    fun readLastScannedPayload(): String = securePrefs.getString(KEY_LAST_SCANNED_PAYLOAD, "").orEmpty()

    fun saveLastScannedPayload(payload: String) {
        securePrefs.edit().putString(KEY_LAST_SCANNED_PAYLOAD, payload.trim()).apply()
    }

    fun parseQrJson(qrJsonString: String): Result<ConfigData> {
        return com.example.util.QrCodeAnalyzer.parseConfigurationPayload(qrJsonString, readConfig())
    }

    fun parseAndSaveQrJson(qrJsonString: String): Result<ConfigData> {
        val trimmedRaw = qrJsonString.trim()
        saveLastScannedPayload(trimmedRaw)
        val parseResult = parseQrJson(trimmedRaw)
        if (parseResult.isSuccess) {
            val config = parseResult.getOrThrow()
            saveConfig(config.webhookUrl, config.deviceToken, config.deviceId)
        }
        return parseResult
    }

    fun clearConfig() {
        securePrefs.edit().clear().apply()
        _configState.value = ConfigData("", "", "")
    }

    fun readAllowedSenders(): Set<String> {
        val saved = generalPrefs.getStringSet(KEY_ALLOWED_SENDERS, null)
        return if (saved == null) {
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

    fun setAllSenders(senders: Set<String>) {
        generalPrefs.edit().putStringSet(KEY_ALLOWED_SENDERS, senders).apply()
        _allowedSenders.value = senders
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
        if (!isWhitelistEnabled()) {
            return true
        }
        if (PredefinedSenders.matchesSender(rawSender, _allowedSenders.value)) {
            return true
        }
        val s = rawSender.trim().lowercase()
        return _customFilterRules.value.any { rule ->
            if (!rule.enabled) return@any false
            val patterns = rule.senderPattern.split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }
            patterns.any { s.contains(it) || it == s }
        }
    }

    fun getCustomFilterRules(): List<CustomFilterRule> {
        val jsonStr = generalPrefs.getString(KEY_CUSTOM_FILTER_RULES, "").orEmpty()
        return CustomFilterRule.fromJsonList(jsonStr)
    }

    fun saveCustomFilterRules(rules: List<CustomFilterRule>) {
        val jsonStr = CustomFilterRule.toJsonList(rules)
        generalPrefs.edit().putString(KEY_CUSTOM_FILTER_RULES, jsonStr).apply()
        _customFilterRules.value = rules
    }

    fun addCustomFilterRule(rule: CustomFilterRule) {
        val current = getCustomFilterRules().toMutableList()
        current.removeAll { it.id == rule.id }
        current.add(0, rule)
        saveCustomFilterRules(current)
    }

    fun updateCustomFilterRule(rule: CustomFilterRule) {
        val current = getCustomFilterRules().toMutableList()
        val idx = current.indexOfFirst { it.id == rule.id }
        if (idx != -1) {
            current[idx] = rule
        } else {
            current.add(0, rule)
        }
        saveCustomFilterRules(current)
    }

    fun deleteCustomFilterRule(ruleId: String) {
        val current = getCustomFilterRules().toMutableList()
        current.removeAll { it.id == ruleId }
        saveCustomFilterRules(current)
    }

    fun toggleCustomFilterRule(ruleId: String, enabled: Boolean) {
        val current = getCustomFilterRules().toMutableList()
        val idx = current.indexOfFirst { it.id == ruleId }
        if (idx != -1) {
            current[idx] = current[idx].copy(enabled = enabled)
            saveCustomFilterRules(current)
        }
    }

    fun isForwardingActive(): Boolean {
        return generalPrefs.getBoolean(KEY_FORWARDING_ACTIVE, true)
    }

    fun setForwardingActive(active: Boolean) {
        generalPrefs.edit().putBoolean(KEY_FORWARDING_ACTIVE, active).apply()
        _isForwardingActive.value = active
    }

    fun isWhitelistEnabled(): Boolean {
        return generalPrefs.getBoolean(KEY_WHITELIST_ENABLED, true)
    }

    fun setWhitelistEnabled(enabled: Boolean) {
        generalPrefs.edit().putBoolean(KEY_WHITELIST_ENABLED, enabled).apply()
        _isWhitelistEnabled.value = enabled
    }

    fun readIgnoreKeywords(): Set<String> {
        val saved = generalPrefs.getStringSet(KEY_IGNORE_KEYWORDS, null)
        return if (saved == null) {
            val defaults = DEFAULT_IGNORE_KEYWORDS
            generalPrefs.edit().putStringSet(KEY_IGNORE_KEYWORDS, defaults).apply()
            defaults
        } else {
            saved
        }
    }

    fun addIgnoreKeyword(keyword: String): Boolean {
        val trimmed = keyword.trim().lowercase()
        if (trimmed.isBlank()) return false
        val current = _ignoreKeywords.value.toMutableSet()
        val added = current.add(trimmed)
        if (added) {
            generalPrefs.edit().putStringSet(KEY_IGNORE_KEYWORDS, current).apply()
            _ignoreKeywords.value = current
        }
        return added
    }

    fun removeIgnoreKeyword(keyword: String) {
        val current = _ignoreKeywords.value.toMutableSet()
        current.remove(keyword.trim().lowercase())
        generalPrefs.edit().putStringSet(KEY_IGNORE_KEYWORDS, current).apply()
        _ignoreKeywords.value = current
    }

    fun containsIgnoreKeyword(messageBody: String): Boolean {
        val lower = messageBody.lowercase()
        return _ignoreKeywords.value.any { kw -> kw.isNotBlank() && lower.contains(kw.lowercase()) }
    }

    companion object {
        private const val TAG = "ZeroPaySecurePrefs"
        private const val PREFS_SECURE_NAME = "zeropay_secure_prefs"
        private const val PREFS_FALLBACK_NAME = "zeropay_prefs_fallback"
        private const val PREFS_GENERAL_NAME = "zeropay_general_settings"

        private const val KEY_WEBHOOK_URL = "webhook_url"
        private const val KEY_DEVICE_TOKEN = "device_token"
        private const val KEY_DEVICE_SECRET = "device_secret"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_ALLOWED_SENDERS = "allowed_senders_list"
        private const val KEY_FORWARDING_ACTIVE = "forwarding_active"
        private const val KEY_WHITELIST_ENABLED = "whitelist_validation_enabled"
        private const val KEY_IGNORE_KEYWORDS = "ignore_keywords_list"
        private const val KEY_LAST_SCANNED_PAYLOAD = "last_scanned_payload"
        private const val KEY_CUSTOM_FILTER_RULES = "custom_filter_rules_list"

        val DEFAULT_SENDERS = PredefinedSenders.DEFAULT_ENABLED_IDS

        val DEFAULT_IGNORE_KEYWORDS = setOf(
            "otp",
            "promo",
            "offer",
            "discount"
        )
    }
}
