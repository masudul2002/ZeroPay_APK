package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
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
}
