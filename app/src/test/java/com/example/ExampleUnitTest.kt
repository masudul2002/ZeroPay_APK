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

  @Test
  fun testInfallibleQrAndJsonPayloadParsing() {
    // 1. Standard configuration JSON matching the production/dev zero pay format
    val standardJson = """
      {"webhookUrl":"https://zeropay-dev.vercel.app/api/v1/webhook","deviceId":"dev_ec7afd5636d8a76c","deviceToken":"sec_live_mock_token_123"}
    """.trimIndent()
    val res1 = com.example.util.QrCodeAnalyzer.parseConfigurationPayload(standardJson)
    assertTrue("Standard JSON must parse successfully", res1.isSuccess)
    val config1 = res1.getOrThrow()
    assertEquals("https://zeropay-dev.vercel.app/api/v1/webhook", config1.webhookUrl)
    assertEquals("dev_ec7afd5636d8a76c", config1.deviceId)
    assertEquals("sec_live_mock_token_123", config1.deviceToken)

    // 2. Escaped JSON string
    val escapedJson = "\"{\\\"webhookUrl\\\":\\\"https://zeropay-dev.vercel.app/api/v1/webhook\\\",\\\"deviceId\\\":\\\"dev_ec7afd5636d8a76c\\\",\\\"deviceToken\\\":\\\"sec_live_mock_token_123\\\"}\""
    val res2 = com.example.util.QrCodeAnalyzer.parseConfigurationPayload(escapedJson)
    assertTrue("Escaped JSON must parse successfully", res2.isSuccess)
    val config2 = res2.getOrThrow()
    assertEquals("https://zeropay-dev.vercel.app/api/v1/webhook", config2.webhookUrl)
    assertEquals("dev_ec7afd5636d8a76c", config2.deviceId)
    assertEquals("sec_live_mock_token_123", config2.deviceToken)

    // 3. Markdown code-fenced JSON
    val markdownJson = """
      ```json
      {
        "webhook_url": "https://zeropay-dev.vercel.app/api/v1/webhook",
        "device_id": "dev_ec7afd5636d8a76c",
        "device_token": "sec_live_mock_token_123"
      }
      ```
    """.trimIndent()
    val res3 = com.example.util.QrCodeAnalyzer.parseConfigurationPayload(markdownJson)
    assertTrue("Markdown JSON must parse successfully", res3.isSuccess)
    val config3 = res3.getOrThrow()
    assertEquals("https://zeropay-dev.vercel.app/api/v1/webhook", config3.webhookUrl)
    assertEquals("dev_ec7afd5636d8a76c", config3.deviceId)
    assertEquals("sec_live_mock_token_123", config3.deviceToken)

    // 4. URL with query parameters
    val urlWithQuery = "https://zeropay-dev.vercel.app/api/v1/webhook?deviceToken=sec_live_mock_token_123&deviceId=dev_ec7afd5636d8a76c"
    val res4 = com.example.util.QrCodeAnalyzer.parseConfigurationPayload(urlWithQuery)
    assertTrue("URL with query parameters must parse successfully", res4.isSuccess)
    val config4 = res4.getOrThrow()
    assertEquals("https://zeropay-dev.vercel.app/api/v1/webhook", config4.webhookUrl)
    assertEquals("dev_ec7afd5636d8a76c", config4.deviceId)
    assertEquals("sec_live_mock_token_123", config4.deviceToken)

    // 5. Malformed JSON with trailing comma (rescued by Regex fallback)
    val malformedJson = "{ webhookUrl: 'https://zeropay-dev.vercel.app/api/v1/webhook', deviceId: 'dev_ec7afd5636d8a76c', deviceToken: 'sec_live_mock_token_123', }"
    val res5 = com.example.util.QrCodeAnalyzer.parseConfigurationPayload(malformedJson)
    assertTrue("Malformed JSON with trailing comma must parse via regex fallback", res5.isSuccess)
    val config5 = res5.getOrThrow()
    assertEquals("https://zeropay-dev.vercel.app/api/v1/webhook", config5.webhookUrl)
    assertEquals("dev_ec7afd5636d8a76c", config5.deviceId)
    assertEquals("sec_live_mock_token_123", config5.deviceToken)
  }

  @Test
  fun testAll14ProviderRules() {
    // 1. Trust Bank (ATM / Card)
    val trustAtm = "ATM CASH Txn \n TK 5945.00 CREDIT \n AC No 046***328"
    val eval1 = com.example.util.SmsFilterAndParser.evaluateSms("Trust Bank", trustAtm)
    assertTrue("Trust Bank ATM must be allowed", eval1 is com.example.util.FilterEvaluation.Allowed)
    val d1 = (eval1 as com.example.util.FilterEvaluation.Allowed).data
    assertEquals("Trust Bank", d1.gateway)
    assertEquals(5945.0, d1.amount, 0.001)
    assertEquals("046***328", d1.senderNumber)
    assertTrue("TrxID must be deterministic ref starting with REF-", d1.trxId.startsWith("REF-"))

    // 2. Trust Bank (Account/Card Received)
    val trustCard = "Tk1,400.01 received from Ac./Card:017****0251 Fee: Tk.00... TxnId: 6914682052"
    val eval2 = com.example.util.SmsFilterAndParser.evaluateSms("Trust Bank", trustCard)
    assertTrue("Trust Bank card received must be allowed", eval2 is com.example.util.FilterEvaluation.Allowed)
    val d2 = (eval2 as com.example.util.FilterEvaluation.Allowed).data
    assertEquals("Trust Bank", d2.gateway)
    assertEquals(1400.01, d2.amount, 0.001)
    assertEquals("017****0251", d2.senderNumber)
    assertEquals("6914682052", d2.trxId)

    // 3. Nagad Bangla QR
    val nagadBQr = "Payment - Bangla QR Successfully Received. \n Amount: Tk 1.00 \n Txn ID: 0001U735"
    val eval3 = com.example.util.SmsFilterAndParser.evaluateSms("Nagad", nagadBQr)
    assertTrue("Nagad Bangla QR must be allowed", eval3 is com.example.util.FilterEvaluation.Allowed)
    val d3 = (eval3 as com.example.util.FilterEvaluation.Allowed).data
    assertEquals("Nagad", d3.gateway)
    assertEquals(1.0, d3.amount, 0.001)
    assertEquals("0001U735", d3.trxId)

    // 4. Nagad Personal Received
    val nagadPersonal = "Payment Received. \n Amount: Tk 1.00 \n Customer: 01797924838 \n TxnID: 75L7Z1Q1"
    val eval4 = com.example.util.SmsFilterAndParser.evaluateSms("Nagad", nagadPersonal)
    assertTrue("Nagad personal received must be allowed", eval4 is com.example.util.FilterEvaluation.Allowed)
    val d4 = (eval4 as com.example.util.FilterEvaluation.Allowed).data
    assertEquals("Nagad", d4.gateway)
    assertEquals(1.0, d4.amount, 0.001)
    assertEquals("01797924838", d4.senderNumber)
    assertEquals("75L7Z1Q1", d4.trxId)

    // 5. Nagad Add Money from Bank
    val nagadAddMoney = "Add Money from Bank is Successful. \n From: IBBL \n Amount: Tk 2520.0 \n TxnID: 760VVGGW"
    val eval5 = com.example.util.SmsFilterAndParser.evaluateSms("Nagad", nagadAddMoney)
    assertTrue("Nagad Add Money from Bank must be allowed", eval5 is com.example.util.FilterEvaluation.Allowed)
    val d5 = (eval5 as com.example.util.FilterEvaluation.Allowed).data
    assertEquals("Nagad", d5.gateway)
    assertEquals(2520.0, d5.amount, 0.001)
    assertEquals("IBBL", d5.senderNumber)
    assertEquals("760VVGGW", d5.trxId)

    // 6. Upay Bangla QR
    val upayBQr = "Received Payment of Tk.1.00 from 01797924838... TrxID 01M3V635E3"
    val eval6 = com.example.util.SmsFilterAndParser.evaluateSms("Upay", upayBQr)
    assertTrue("Upay Bangla QR must be allowed", eval6 is com.example.util.FilterEvaluation.Allowed)
    val d6 = (eval6 as com.example.util.FilterEvaluation.Allowed).data
    assertEquals("Upay", d6.gateway)
    assertEquals(1.0, d6.amount, 0.001)
    assertEquals("01797924838", d6.senderNumber)
    assertEquals("01M3V635E3", d6.trxId)

    // 7. Upay Personal Received
    val upayPersonal = "Tk. 254.90 has been received from 01300366626... TrxID 01JP2W61EN"
    val eval7 = com.example.util.SmsFilterAndParser.evaluateSms("Upay", upayPersonal)
    assertTrue("Upay Personal Received must be allowed", eval7 is com.example.util.FilterEvaluation.Allowed)
    val d7 = (eval7 as com.example.util.FilterEvaluation.Allowed).data
    assertEquals("Upay", d7.gateway)
    assertEquals(254.90, d7.amount, 0.001)
    assertEquals("01300366626", d7.senderNumber)
    assertEquals("01JP2W61EN", d7.trxId)

    // 8. BRAC Bank Limited
    val bracReceived = "Tk.150.00 has been received from BRAC Bank Limited... TrxID 01K6MZYRK4"
    val eval8 = com.example.util.SmsFilterAndParser.evaluateSms("BRAC Bank", bracReceived)
    assertTrue("BRAC Bank Limited receipt must be allowed", eval8 is com.example.util.FilterEvaluation.Allowed)
    val d8 = (eval8 as com.example.util.FilterEvaluation.Allowed).data
    assertEquals("BRAC Bank", d8.gateway)
    assertEquals(150.0, d8.amount, 0.001)
    assertEquals("BRAC Bank Limited", d8.senderNumber)
    assertEquals("01K6MZYRK4", d8.trxId)

    // 9. Tally Pay QR
    val tallyQr = "Tk 1,898.57 received from 017***4838 via TallyPay QR... Txn ID: YY1Z85HTP"
    val eval9 = com.example.util.SmsFilterAndParser.evaluateSms("TallyPay", tallyQr)
    assertTrue("Tally Pay QR must be allowed", eval9 is com.example.util.FilterEvaluation.Allowed)
    val d9 = (eval9 as com.example.util.FilterEvaluation.Allowed).data
    assertEquals("TallyPay", d9.gateway)
    assertEquals(1898.57, d9.amount, 0.001)
    assertEquals("017***4838", d9.senderNumber)
    assertEquals("YY1Z85HTP", d9.trxId)

    // 10. Islami Bank (IBBL - ATM / General Credit)
    val ibblAtm = "Dear Customer ATM Tk 3647 has been credited to A/C777**05092965"
    val eval10 = com.example.util.SmsFilterAndParser.evaluateSms("IBBL", ibblAtm)
    assertTrue("IBBL ATM must be allowed", eval10 is com.example.util.FilterEvaluation.Allowed)
    val d10 = (eval10 as com.example.util.FilterEvaluation.Allowed).data
    assertEquals("IBBL", d10.gateway)
    assertEquals(3647.0, d10.amount, 0.001)
    assertEquals("777**05092965", d10.senderNumber)
    assertTrue("IBBL ATM Trx must start with REF-", d10.trxId.startsWith("REF-"))

    // 11. BRAC Bank Direct Credit
    val bracCredit = "TK 2,020.00 has been credited to your A/C# 10609**0001 on 22-08-26."
    val eval11 = com.example.util.SmsFilterAndParser.evaluateSms("BRAC Bank", bracCredit)
    assertTrue("BRAC Bank Direct Credit must be allowed", eval11 is com.example.util.FilterEvaluation.Allowed)
    val d11 = (eval11 as com.example.util.FilterEvaluation.Allowed).data
    assertEquals("BRAC Bank", d11.gateway)
    assertEquals(2020.0, d11.amount, 0.001)
    assertEquals("10609**0001", d11.senderNumber)
    assertTrue("BRAC Direct Credit Trx must start with REF-", d11.trxId.startsWith("REF-"))

    // 12. Midland Bank
    val midlandDeposit = "BDT 3999.99 was deposited to A/c No ***03118 by ATM transaction"
    val eval12 = com.example.util.SmsFilterAndParser.evaluateSms("Midland Bank", midlandDeposit)
    assertTrue("Midland Bank deposit must be allowed", eval12 is com.example.util.FilterEvaluation.Allowed)
    val d12 = (eval12 as com.example.util.FilterEvaluation.Allowed).data
    assertEquals("Midland Bank", d12.gateway)
    assertEquals(3999.99, d12.amount, 0.001)
    assertEquals("***03118", d12.senderNumber)
    assertTrue("Midland Bank Trx must start with REF-", d12.trxId.startsWith("REF-"))

    // 13. Islami Bank (IBBL Deposit Slip)
    val ibblDeposit = "TrxID: 58260928000006866 \n Acc: 20507776705092965 \n Deposit Amount: 9,000.00"
    val eval13 = com.example.util.SmsFilterAndParser.evaluateSms("IBBL", ibblDeposit)
    assertTrue("IBBL Deposit Slip must be allowed", eval13 is com.example.util.FilterEvaluation.Allowed)
    val d13 = (eval13 as com.example.util.FilterEvaluation.Allowed).data
    assertEquals("IBBL", d13.gateway)
    assertEquals(9000.0, d13.amount, 0.001)
    assertEquals("58260928000006866", d13.trxId)
    assertEquals("20507776705092965", d13.senderNumber)

    // 14. Sonali Bank NPSB
    val sonaliNpsb = "Your account 5912*****1613 has been credited through NPSB for BDT 9,900.00"
    val eval14 = com.example.util.SmsFilterAndParser.evaluateSms("Sonali Bank", sonaliNpsb)
    assertTrue("Sonali Bank NPSB must be allowed", eval14 is com.example.util.FilterEvaluation.Allowed)
    val d14 = (eval14 as com.example.util.FilterEvaluation.Allowed).data
    assertEquals("Sonali Bank", d14.gateway)
    assertEquals(9900.0, d14.amount, 0.001)
    assertEquals("5912*****1613", d14.senderNumber)
    assertTrue("Sonali Bank Trx must start with REF-", d14.trxId.startsWith("REF-"))
  }

  @Test
  fun testDeterministicReferenceGeneration() {
    val ref1 = com.example.util.SmsParsingConfig.generateDeterministicReference("Sonali Bank", 500.0, "1234", "Body text")
    val ref2 = com.example.util.SmsParsingConfig.generateDeterministicReference("Sonali Bank", 500.0, "1234", "Body text")
    assertEquals(ref1, ref2)
    assertTrue(ref1.startsWith("REF-"))
    assertEquals(16, ref1.length) // "REF-" (4) + 12 hex chars = 16

    val ref3 = com.example.util.SmsParsingConfig.generateDeterministicReference("Sonali Bank", 600.0, "1234", "Body text")
    org.junit.Assert.assertNotEquals(ref1, ref3)
  }

  @Test
  fun testUniversalFallbackParser() {
    // 1. Unlisted Bank SMS with explicit TrxID
    val unlistedBankSms = "Dear Customer, your A/C 9876**5432 has been credited with BDT 15,000.00 on 01-Oct-2026. Ref: TR77665544"
    val eval1 = com.example.util.SmsFilterAndParser.evaluateSms("GlobalIslamicBank", unlistedBankSms)
    assertTrue("Universal fallback should parse unlisted bank SMS", eval1 is com.example.util.FilterEvaluation.Allowed)
    val d1 = (eval1 as com.example.util.FilterEvaluation.Allowed).data
    assertEquals("GlobalIslamicBank", d1.gateway)
    assertEquals(15000.0, d1.amount, 0.001)
    assertEquals("TR77665544", d1.trxId)
    assertEquals("9876**5432", d1.senderNumber)

    // 2. Unlisted MFS without direct TrxID (should generate deterministic reference)
    val unlistedMfsSms = "Received payment BDT 750.50 from 01799887766 for merchant checkout."
    val eval2 = com.example.util.SmsFilterAndParser.evaluateSms("FastPay", unlistedMfsSms)
    assertTrue("Universal fallback should parse unlisted MFS SMS", eval2 is com.example.util.FilterEvaluation.Allowed)
    val d2 = (eval2 as com.example.util.FilterEvaluation.Allowed).data
    assertEquals("FastPay", d2.gateway)
    assertEquals(750.50, d2.amount, 0.001)
    assertEquals("01799887766", d2.senderNumber)
    assertTrue(d2.trxId.startsWith("REF-"))

    // 3. Fallback on Bengali Taka symbol
    val bengaliCurrencySms = "Account 12345 credited with ৳ 3,200.00 successfully."
    val eval3 = com.example.util.SmsFilterAndParser.evaluateSms("CommunityBank", bengaliCurrencySms)
    assertTrue("Universal fallback should parse Bengali Taka symbol", eval3 is com.example.util.FilterEvaluation.Allowed)
    val d3 = (eval3 as com.example.util.FilterEvaluation.Allowed).data
    assertEquals(3200.0, d3.amount, 0.001)
    assertEquals("12345", d3.senderNumber)
  }

  @Test
  fun testProductionWebhookUrlDefault() {
    val blankConfig = com.example.data.model.ConfigData(
        webhookUrl = "",
        deviceToken = "test_token_123",
        deviceId = "dev_01"
    )
    assertEquals("https://www.zero-pay.tech/api/v1/webhook", blankConfig.effectiveWebhookUrl)
    assertTrue(blankConfig.isConfigured)

    val customConfig = com.example.data.model.ConfigData(
        webhookUrl = "https://custom.site/api/webhook",
        deviceToken = "test_token_123"
    )
    assertEquals("https://custom.site/api/webhook", customConfig.effectiveWebhookUrl)
  }
}
