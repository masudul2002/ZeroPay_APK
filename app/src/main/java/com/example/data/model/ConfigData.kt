package com.example.data.model

data class ConfigData(
    val webhookUrl: String = "",
    val deviceToken: String = "",
    val deviceId: String = ""
) {
    val deviceSecret: String
        get() = deviceToken

    val effectiveDeviceId: String
        get() = deviceId.trim().ifBlank { "sim-gateway-01" }

    // Bypass false "Not Paired" blocking: as long as a valid webhookUrl and deviceToken exist,
    // the app is considered configured and ready to forward payment SMS.
    val isConfigured: Boolean
        get() = webhookUrl.isNotBlank() && deviceToken.isNotBlank()
}

data class WebhookPayload(
    val sender: String,
    val messageBody: String,
    val timestamp: String,
    val simSlot: String = "SIM_1",
    val deviceId: String
)
