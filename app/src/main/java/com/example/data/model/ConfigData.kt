package com.example.data.model

data class ConfigData(
    val webhookUrl: String,
    val deviceSecret: String,
    val deviceId: String
) {
    val isConfigured: Boolean
        get() = webhookUrl.isNotBlank() && deviceSecret.isNotBlank() && deviceId.isNotBlank()
}

data class WebhookPayload(
    val sender: String,
    val messageBody: String,
    val timestamp: String,
    val simSlot: String = "SIM_1",
    val deviceId: String
)
