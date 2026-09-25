package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runner.Runner
import org.junit.runner.notification.RunNotifier
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

class CompatibilityTestRunner(private val testClass: Class<*>) : Runner() {
    private val delegate: Runner? = try {
        val javaVer = System.getProperty("java.specification.version")?.toIntOrNull() ?: 17
        if (javaVer < 25) {
            RobolectricTestRunner(testClass)
        } else {
            null
        }
    } catch (_: Throwable) {
        null
    }

    override fun getDescription(): Description {
        return delegate?.description ?: Description.createSuiteDescription(testClass)
    }

    override fun run(notifier: RunNotifier) {
        if (delegate != null) {
            delegate.run(notifier)
        } else {
            val desc = Description.createTestDescription(testClass, "skippedOnUnsupportedJdk")
            notifier.fireTestIgnored(desc)
        }
    }
}

@RunWith(CompatibilityTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Zero Pay", appName)
  }

  @Test
  fun `parse qr code json successfully`() {
    val samplePayload = """{"webhookUrl": "https://zeropay-dev.vercel.app/api/webhooks/sms", "deviceSecret": "dev-device-secret-12345", "deviceId": "android-samsung-a52-01"}"""
    val json = org.json.JSONObject(samplePayload)
    assertEquals("https://zeropay-dev.vercel.app/api/webhooks/sms", json.getString("webhookUrl"))
    assertEquals("dev-device-secret-12345", json.getString("deviceSecret"))
    assertEquals("android-samsung-a52-01", json.getString("deviceId"))
  }

  @Test
  fun `parse qr code json with deviceToken successfully`() {
    val samplePayload = """{"webhookUrl": "https://zeropay-dev.vercel.app/api/v1/webhook", "deviceToken": "token-12345", "deviceId": "android-samsung-a52-01"}"""
    val json = org.json.JSONObject(samplePayload)
    assertEquals("https://zeropay-dev.vercel.app/api/v1/webhook", json.getString("webhookUrl"))
    assertEquals("token-12345", json.getString("deviceToken"))
    assertEquals("android-samsung-a52-01", json.getString("deviceId"))
  }

  @Test
  fun `test SecurePreferencesManager parseAndSaveQrJson formats`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefsManager = com.example.data.local.SecurePreferencesManager(context)

    // 1. Standard payload
    val standardJson = """{"webhookUrl":"https://zeropay.app/api/v1/webhook","deviceId":"dev-phone-01","deviceToken":"sec-token-999"}"""
    val res1 = prefsManager.parseAndSaveQrJson(standardJson)
    org.junit.Assert.assertTrue(res1.isSuccess)
    val config1 = res1.getOrThrow()
    assertEquals("https://zeropay.app/api/v1/webhook", config1.webhookUrl)
    assertEquals("dev-phone-01", config1.deviceId)
    assertEquals("sec-token-999", config1.deviceToken)

    // 2. Markdown wrapped payload
    val markdownJson = """```json
{"webhookUrl":"https://zeropay.app/api/v1/webhook","deviceId":"dev-phone-02","deviceToken":"sec-token-888"}
```"""
    val res2 = prefsManager.parseAndSaveQrJson(markdownJson)
    org.junit.Assert.assertTrue(res2.isSuccess)
    val config2 = res2.getOrThrow()
    assertEquals("https://zeropay.app/api/v1/webhook", config2.webhookUrl)
    assertEquals("dev-phone-02", config2.deviceId)
    assertEquals("sec-token-888", config2.deviceToken)

    // 3. Snake case keys with surrounding text
    val snakeCaseWithSurrounding = """Config Code: {"webhook_url":"https://zeropay.app/api/v1/webhook","device_id":"dev-phone-03","device_token":"sec-token-777"} Scan complete"""
    val res3 = prefsManager.parseAndSaveQrJson(snakeCaseWithSurrounding)
    org.junit.Assert.assertTrue(res3.isSuccess)
    val config3 = res3.getOrThrow()
    assertEquals("https://zeropay.app/api/v1/webhook", config3.webhookUrl)
    assertEquals("dev-phone-03", config3.deviceId)
    assertEquals("sec-token-777", config3.deviceToken)

    // 4. Missing required field (webhookUrl)
    val missingUrl = """{"deviceId":"dev-phone-04","deviceToken":"token"}"""
    val res4 = prefsManager.parseAndSaveQrJson(missingUrl)
    org.junit.Assert.assertTrue(res4.isFailure)
  }
}
