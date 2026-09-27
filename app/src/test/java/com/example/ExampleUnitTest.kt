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

  @Test
  fun testWebhookUrlPayloadAutoDetection() {
    val webhookUrlWithParams = "https://zeropay-dev.vercel.app/api/v1/webhook?deviceToken=sec_tok_999&deviceId=device_01"
    val uri = java.net.URI(webhookUrlWithParams)
    val query = uri.query
    val queryMap = query.split("&").associate {
      val parts = it.split("=")
      parts[0] to (parts.getOrNull(1) ?: "")
    }

    assertEquals("https", uri.scheme)
    assertEquals("zeropay-dev.vercel.app", uri.host)
    assertEquals("/api/v1/webhook", uri.path)
    assertEquals("sec_tok_999", queryMap["deviceToken"])
    assertEquals("device_01", queryMap["deviceId"])
  }

  @Test
  fun testDynamicPaymentStringAutoDetection() {
    val emvcoPayload = "00020101021229300012com.bkash.qr01080170000053030505802BD"
    val isDynamicPayment = emvcoPayload.startsWith("000201") || emvcoPayload.contains("://")
    assertTrue("Should detect EMVCo / Bangla QR dynamic payment string", isDynamicPayment)

    val uriPayload = "bkash://payment?merchant=01700000000&amount=500"
    assertTrue("Should detect deep link payment URI", uriPayload.contains("://"))
  }

  @Test
  fun testStrictOtpAndSecurityDrop() {
    val bKashOtp = "Your bKash verification code is 493021. Do not share your OTP or PIN with anyone."
    val eval1 = com.example.util.SmsFilterAndParser.evaluateSms("bKash", bKashOtp)
    assertTrue("OTP must be ignored", eval1 is com.example.util.FilterEvaluation.Ignored)
    assertEquals("Dropped: OTP or verification code", (eval1 as com.example.util.FilterEvaluation.Ignored).reason)

    val nagadPin = "Your Nagad PIN reset security code is 881290. Keep it secret."
    val eval2 = com.example.util.SmsFilterAndParser.evaluateSms("Nagad", nagadPin)
    assertTrue("Security code must be ignored", eval2 is com.example.util.FilterEvaluation.Ignored)
  }

  @Test
  fun testStrictDebitAndStatementDrop() {
    val bracDebit = "Your A/C ...1234 has been debited by BDT 2,500.00 at ATM Cash Withdrawal on 27-SEP-26. Avail Bal: BDT 5,000.00."
    val eval1 = com.example.util.SmsFilterAndParser.evaluateSms("BRACBANK", bracDebit)
    assertTrue("Debit alert must be ignored", eval1 is com.example.util.FilterEvaluation.Ignored)
    assertEquals("Dropped: Outgoing debit or statement notice", (eval1 as com.example.util.FilterEvaluation.Ignored).reason)

    val statementSms = "Mini statement for A/C ...999: Available balance is BDT 15,200.00. Thank you."
    val eval2 = com.example.util.SmsFilterAndParser.evaluateSms("CITY BANK", statementSms)
    assertTrue("Statement notice must be ignored", eval2 is com.example.util.FilterEvaluation.Ignored)
  }

  @Test
  fun testStrictPromoWithoutTrxDrop() {
    val promoSms = "Special offer! Recharge Tk 50 now and win up to 100% bonus cashback! Dial *247#."
    val eval = com.example.util.SmsFilterAndParser.evaluateSms("bKash", promoSms)
    assertTrue("Promo without TrxID must be ignored", eval is com.example.util.FilterEvaluation.Ignored)
  }

  @Test
  fun testBkashIncomingPaymentParsing() {
    val sms = "You have received payment Tk 1,250.00 from 01712345678. Ref 01. Fee Tk 0.00. Balance Tk 5,420.50. TrxID 9K3M88219 at 27/09/2026 12:45"
    val eval = com.example.util.SmsFilterAndParser.evaluateSms("bKash", sms)
    assertTrue("bKash payment receipt must be allowed", eval is com.example.util.FilterEvaluation.Allowed)
    val data = (eval as com.example.util.FilterEvaluation.Allowed).data
    assertEquals("bKash", data.gateway)
    assertEquals(1250.0, data.amount, 0.001)
    assertEquals("01712345678", data.senderNumber)
    assertEquals("9K3M88219", data.trxId)
    assertEquals(0.0, data.fee ?: 0.0, 0.001)
    assertEquals(5420.50, data.balance ?: 0.0, 0.001)
  }

  @Test
  fun testNagadIncomingPaymentParsing() {
    val sms = "Payment Received. Amount: Tk 800.00. Sender: 01812345678. TxnID: 72J8KL91. Balance: Tk 1,200.00. 27/09/2026 13:10."
    val eval = com.example.util.SmsFilterAndParser.evaluateSms("Nagad", sms)
    assertTrue("Nagad payment receipt must be allowed", eval is com.example.util.FilterEvaluation.Allowed)
    val data = (eval as com.example.util.FilterEvaluation.Allowed).data
    assertEquals("Nagad", data.gateway)
    assertEquals(800.0, data.amount, 0.001)
    assertEquals("01812345678", data.senderNumber)
    assertEquals("72J8KL91", data.trxId)
    assertEquals(1200.0, data.balance ?: 0.0, 0.001)
  }

  @Test
  fun testRocketAndCellfinParsing() {
    val rocketSms = "Received Tk 500.00 from 01912345678. TxnId: 99887766. Balance: Tk 1,500.00."
    val evalRocket = com.example.util.SmsFilterAndParser.evaluateSms("16216", rocketSms)
    assertTrue("Rocket receipt must be allowed", evalRocket is com.example.util.FilterEvaluation.Allowed)
    val rocketData = (evalRocket as com.example.util.FilterEvaluation.Allowed).data
    assertEquals("Rocket", rocketData.gateway)
    assertEquals(500.0, rocketData.amount, 0.001)
    assertEquals("99887766", rocketData.trxId)

    val cellfinSms = "Your CellFin has been credited with Tk 3,500.00 from 01512345678. TrxID: CF223344. Balance Tk 4,000.00."
    val evalCellfin = com.example.util.SmsFilterAndParser.evaluateSms("Cellfin", cellfinSms)
    assertTrue("CellFin receipt must be allowed", evalCellfin is com.example.util.FilterEvaluation.Allowed)
    val cellfinData = (evalCellfin as com.example.util.FilterEvaluation.Allowed).data
    assertEquals("Cellfin", cellfinData.gateway)
    assertEquals(3500.0, cellfinData.amount, 0.001)
    assertEquals("CF223344", cellfinData.trxId)
  }

  @Test
  fun testBankCreditReceiptParsing() {
    val bracSms = "Your A/C ...456 has been credited by BDT 10,000.00 on 27-SEP-26. Ref/Trx: FT26270001 from 01711223344. Avail Bal: BDT 25,000.00."
    val evalBrac = com.example.util.SmsFilterAndParser.evaluateSms("BRACBANK", bracSms)
    assertTrue("BRAC Bank credit receipt must be allowed", evalBrac is com.example.util.FilterEvaluation.Allowed)
    val bracData = (evalBrac as com.example.util.FilterEvaluation.Allowed).data
    assertEquals("BRACBANK", bracData.gateway)
    assertEquals(10000.0, bracData.amount, 0.001)
    assertEquals("FT26270001", bracData.trxId)

    val ibblSms = "Dear Customer, A/C ...789 credited with Tk 2,500.00 by Transfer. TrxID: IBBL987654 from 019XXXXXXXX."
    val evalIbbl = com.example.util.SmsFilterAndParser.evaluateSms("IBBL", ibblSms)
    assertTrue("IBBL credit receipt must be allowed", evalIbbl is com.example.util.FilterEvaluation.Allowed)
    val ibblData = (evalIbbl as com.example.util.FilterEvaluation.Allowed).data
    assertEquals("IBBL", ibblData.gateway)
    assertEquals(2500.0, ibblData.amount, 0.001)
    assertEquals("IBBL987654", ibblData.trxId)
  }

  @Test
  fun testCustomFilterRuleDynamicParsing() {
    val customRule = com.example.data.model.CustomFilterRule(
        name = "Midland Custom",
        senderPattern = "MDB, MIDLANDBANK",
        bodyRegex = """(?i)A/C\s*\S+\s*credited\s*by\s*Tk\.?\s*([0-9,.]+)\s*on\s*\S+\.?\s*Ref[:\s]*([A-Za-z0-9]+)\s*from\s*([0-9+]+)""",
        amountGroup = 1,
        trxIdGroup = 2,
        senderGroup = 3,
        enabled = true
    )

    val sms = "A/C ...012 credited by Tk 4,500.00 on 27-09-2026. Ref: MDB445566 from 01612345678. Available Bal Tk 8,000.00."
    val eval = com.example.util.SmsFilterAndParser.evaluateSms(
        sender = "MDB",
        messageBody = sms,
        customRules = listOf(customRule)
    )

    assertTrue("Custom rule should successfully match and extract details", eval is com.example.util.FilterEvaluation.Allowed)
    val data = (eval as com.example.util.FilterEvaluation.Allowed).data
    assertEquals("Midland Custom", data.gateway)
    assertEquals(4500.0, data.amount, 0.001)
    assertEquals("MDB445566", data.trxId)
    assertEquals("01612345678", data.senderNumber)
  }

  @Test
  fun testBypassFalseNotPairedWithValidTokenAndUrl() {
    // When deviceId is missing or empty, config is still configured as long as webhookUrl and deviceToken are present
    val configWithoutDeviceId = com.example.data.model.ConfigData(
      webhookUrl = "https://zeropay.example.com/api/v1/webhook",
      deviceToken = "secret_tok_123",
      deviceId = ""
    )

    assertTrue("App must be considered configured even if deviceId is empty", configWithoutDeviceId.isConfigured)
    assertEquals("sim-gateway-01", configWithoutDeviceId.effectiveDeviceId)

    // With blank spaces in deviceId
    val configWithWhitespaceDeviceId = com.example.data.model.ConfigData(
      webhookUrl = "https://zeropay.example.com/api/v1/webhook",
      deviceToken = "secret_tok_123",
      deviceId = "   "
    )
    assertTrue("App must be considered configured with whitespace deviceId", configWithWhitespaceDeviceId.isConfigured)
    assertEquals("sim-gateway-01", configWithWhitespaceDeviceId.effectiveDeviceId)

    // With explicit deviceId
    val configWithDeviceId = com.example.data.model.ConfigData(
      webhookUrl = "https://zeropay.example.com/api/v1/webhook",
      deviceToken = "secret_tok_123",
      deviceId = "phone-pos-99"
    )
    assertTrue(configWithDeviceId.isConfigured)
    assertEquals("phone-pos-99", configWithDeviceId.effectiveDeviceId)

    // When token is absent, should not be configured
    val unconfigured = com.example.data.model.ConfigData(
      webhookUrl = "https://zeropay.example.com/api/v1/webhook",
      deviceToken = "",
      deviceId = "phone-pos-99"
    )
    assertFalse("Must not be configured if deviceToken is blank", unconfigured.isConfigured)
  }

  @Test
  fun testDeviceTokenSecretEquivalence() {
    val config = com.example.data.model.ConfigData(
      webhookUrl = "https://zeropay.example.com/webhook",
      deviceToken = "tok_abc"
    )
    assertEquals("tok_abc", config.deviceSecret)
    assertEquals("tok_abc", config.deviceToken)
  }
}
