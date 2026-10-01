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

    val effectiveWebhookUrl: String
        get() = webhookUrl.trim().ifBlank { DEFAULT_PRODUCTION_WEBHOOK_URL }

    // Bypass false "Not Paired" blocking: as long as a valid webhookUrl (or default) and deviceToken exist,
    // the app is considered configured and ready to forward payment SMS.
    val isConfigured: Boolean
        get() = effectiveWebhookUrl.isNotBlank() && deviceToken.isNotBlank()

    companion object {
        const val DEFAULT_PRODUCTION_WEBHOOK_URL = "https://www.zero-pay.tech/api/v1/webhook"
    }
}

data class WebhookPayload(
    val sender: String,
    val messageBody: String,
    val timestamp: String,
    val simSlot: String = "SIM_1",
    val deviceId: String
)
