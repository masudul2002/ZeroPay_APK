package com.example

import com.example.data.model.PredefinedSenders
import com.example.receiver.SmsReceiver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testPredefinedSendersDefaultState() {
    val defaults = PredefinedSenders.DEFAULT_ENABLED_IDS
    assertTrue(defaults.contains("bKash"))
    assertTrue(defaults.contains("Nagad"))
    assertEquals(2, defaults.size)

    assertFalse(defaults.contains("16216"))
    assertFalse(defaults.contains("BRACBANK"))
    assertFalse(defaults.contains("TallyKhata"))
  }

  @Test
  fun testPredefinedSendersMatching() {
    val enabled = setOf("bKash", "Nagad", "16216", "BRACBANK")

    // bKash matches both alphanumeric and shortcode
    assertTrue(PredefinedSenders.matchesSender("bKash", enabled))
    assertTrue(PredefinedSenders.matchesSender("BKASH", enabled))
    assertTrue(PredefinedSenders.matchesSender("16247", enabled))

    // Nagad matches both alphanumeric and shortcode
    assertTrue(PredefinedSenders.matchesSender("Nagad", enabled))
    assertTrue(PredefinedSenders.matchesSender("16167", enabled))

    // Rocket matches both 16216 and ROCKET
    assertTrue(PredefinedSenders.matchesSender("16216", enabled))
    assertTrue(PredefinedSenders.matchesSender("ROCKET", enabled))

    // BRACBANK
    assertTrue(PredefinedSenders.matchesSender("BRACBANK", enabled))
    assertTrue(PredefinedSenders.matchesSender("BracBank", enabled))

    // Disabled senders must return false
    assertFalse(PredefinedSenders.matchesSender("Cellfin", enabled))
    assertFalse(PredefinedSenders.matchesSender("CITY BANK", enabled))
    assertFalse(PredefinedSenders.matchesSender("TallyKhata", enabled))
    assertFalse(PredefinedSenders.matchesSender("SpamPromo", enabled))
  }

  @Test
  fun testSmartTransactionValidation_ValidTransactions() {
    val bkashReceived = "You have received Tk 2,500.00 from 01711000000. Fee Tk 0.00. Balance Tk 12,500.00. TrxID BL049281 at 25/09/2026 18:30"
    assertTrue(SmsReceiver.checkIsTransactionSms(bkashReceived))

    val nagadCashIn = "Cash In Tk 1,200.00 successful. Fee Tk 0.00. Balance Tk 4,500.00. TxnId: 7B90K23 at 25/09/2026 19:15"
    assertTrue(SmsReceiver.checkIsTransactionSms(nagadCashIn))

    val rocketPayment = "Payment received Tk 850.00 to merchant 01900000000. Balance BDT 5,600. Txn: 894012"
    assertTrue(SmsReceiver.checkIsTransactionSms(rocketPayment))

    val bankDeposit = "Your A/C ...1234 has been credited with BDT 50,000.00. Available Balance BDT 75,000.00. Transaction ID: TXN9948210"
    assertTrue(SmsReceiver.checkIsTransactionSms(bankDeposit))

    // Sent keyword with Trx
    val bkashSent = "You have sent Tk 500.00 to 01800000000. Fee Tk 5.00. Balance Tk 1,200.00. Trx 998877"
    assertTrue(SmsReceiver.checkIsTransactionSms(bkashSent))

    // Amount keyword with TxnId
    val amountTxn = "Transaction successful. Amount: 1,500.00 Tk. TxnId: 445566"
    assertTrue(SmsReceiver.checkIsTransactionSms(amountTxn))
  }

  @Test
  fun testSmartTransactionValidation_PromoAndOtpDropped() {
    // OTP message should return false (no TrxID/TxnId)
    val otpSms = "Your bKash verification code is 849201. Never share your OTP or PIN with anyone."
    assertFalse(SmsReceiver.checkIsTransactionSms(otpSms))

    // Promotional message should return false (no TrxID/TxnId)
    val promoSms = "Recharge Tk 50 from Nagad and get 1GB free internet! Offer valid till midnight."
    assertFalse(SmsReceiver.checkIsTransactionSms(promoSms))

    // Promotional offer without financial keywords should return false
    val promoWithoutFinancial = "TrxID updated on new terms and privacy policy. Click link to view."
    assertFalse(SmsReceiver.checkIsTransactionSms(promoWithoutFinancial))

    // Blank message
    assertFalse(SmsReceiver.checkIsTransactionSms(""))
    assertFalse(SmsReceiver.checkIsTransactionSms("   "))
  }

  @Test
  fun testQrCodeJsonParsingStrict() {
    val samplePayload = """{"webhookUrl": "https://zeropay-dev.vercel.app/api/v1/webhook", "deviceId": "dev-01", "deviceToken": "token-xyz-123"}"""
    
    fun extractField(json: String, key: String): String {
      val regex = Regex("""\"$key\"\s*:\s*\"([^\"]+)\"""")
      return regex.find(json)?.groupValues?.getOrNull(1).orEmpty()
    }

    val webhookUrl = extractField(samplePayload, "webhookUrl")
    val deviceToken = extractField(samplePayload, "deviceToken")
    val deviceId = extractField(samplePayload, "deviceId")

    assertTrue(webhookUrl.isNotBlank())
    assertTrue(deviceToken.isNotBlank())
    assertTrue(deviceId.isNotBlank())
    assertEquals("https://zeropay-dev.vercel.app/api/v1/webhook", webhookUrl)
    assertEquals("token-xyz-123", deviceToken)
    assertEquals("dev-01", deviceId)
  }

  @Test
  fun testMissingDeviceTokenValidation() {
    val samplePayloadWithoutToken = """{"webhookUrl": "https://zeropay-dev.vercel.app/api/v1/webhook", "deviceId": "dev-01"}"""

    fun extractField(json: String, key: String): String {
      val regex = Regex("""\"$key\"\s*:\s*\"([^\"]+)\"""")
      return regex.find(json)?.groupValues?.getOrNull(1).orEmpty()
    }

    val deviceToken = extractField(samplePayloadWithoutToken, "deviceToken")
    assertTrue("deviceToken should be empty when omitted", deviceToken.isBlank())
  }
}
