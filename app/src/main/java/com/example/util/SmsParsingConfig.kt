package com.example.util

import java.security.MessageDigest

/**
 * Group mapping index definitions for regex extractions.
 * Index 0 indicates the field is not captured by the regex or absent in the SMS format.
 */
data class ParsingGroupMapping(
    val amountGroup: Int,
    val trxGroup: Int = 0,
    val phoneGroup: Int = 0,
    val accountGroup: Int = 0,
    val sourceGroup: Int = 0
)

/**
 * Representation of a specific Bank or MFS provider SMS parsing rule.
 */
data class ProviderParsingRule(
    val id: String,
    val providerName: String,
    val primaryRegex: Regex,
    val groupMapping: ParsingGroupMapping,
    val sampleSms: String = "",
    val fallbackPatterns: List<Pair<Regex, ParsingGroupMapping>> = emptyList()
)

object SmsParsingConfig {

    /**
     * Generates a deterministic fallback reference ID for bank receipts or credits that lack a traditional TrxID.
     * Starts with "REF-" followed by a 12-character uppercase MD5 hex string derived deterministically from the transaction data.
     */
    fun generateDeterministicReference(
        provider: String,
        amount: Double,
        identifier: String?,
        rawBody: String
    ): String {
        return try {
            val key = "$provider:$amount:${identifier.orEmpty().trim()}:${rawBody.trim()}"
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(key.toByteArray(Charsets.UTF_8))
            val hex = digest.joinToString("") { "%02X".format(it) }
            "REF-${hex.take(12)}"
        } catch (_: Exception) {
            "REF-${System.currentTimeMillis()}"
        }
    }

    /**
     * Exact 14 Bank & MFS Provider Rules:
     * 1. Trust Bank (ATM / Card)
     * 2. Trust Bank (Account/Card Received)
     * 3. Nagad Bangla QR
     * 4. Nagad Personal Received
     * 5. Nagad Add Money from Bank
     * 6. Upay Bangla QR
     * 7. Upay Personal Received
     * 8. BRAC Bank Limited
     * 9. Tally Pay QR
     * 10. Islami Bank (IBBL - ATM / General Credit)
     * 11. BRAC Bank Direct Credit
     * 12. Midland Bank
     * 13. Islami Bank (IBBL Deposit Slip)
     * 14. Sonali Bank NPSB
     */
    val SUPPORTED_PROVIDER_RULES: List<ProviderParsingRule> = listOf(
        // 1. Trust Bank (ATM / Card)
        // Sample: "ATM CASH Txn \n TK 5945.00 CREDIT \n AC No 046***328"
        // Groups: Amt Group: 2, Trx Group: 0, Phone Group: 0, Account Group: 3
        ProviderParsingRule(
            id = "trust_bank_atm",
            providerName = "Trust Bank",
            primaryRegex = Regex(
                """(?i)\b(CREDIT)\b[\s\S]*?(?:TK|Tk|BDT)\s*([0-9.,]+)[\s\S]*?(?:AC No|A/C)\s*([A-Za-z0-9*]+)"""
            ),
            groupMapping = ParsingGroupMapping(
                amountGroup = 2,
                trxGroup = 0,
                phoneGroup = 0,
                accountGroup = 3
            ),
            sampleSms = "ATM CASH Txn \n TK 5945.00 CREDIT \n AC No 046***328",
            fallbackPatterns = listOf(
                // Handles order where TK amount appears before CREDIT in sample SMS
                Regex(
                    """(?i)(?:TK|Tk|BDT)\s*([0-9.,]+)[\s\S]*?\b(CREDIT)\b[\s\S]*?(?:AC No|A/C)\s*([A-Za-z0-9*]+)"""
                ) to ParsingGroupMapping(
                    amountGroup = 1,
                    trxGroup = 0,
                    phoneGroup = 0,
                    accountGroup = 3
                )
            )
        ),

        // 2. Trust Bank (Account/Card Received)
        // Sample: "Tk1,400.01 received from Ac./Card:017****0251 Fee: Tk.00... TxnId: 6914682052"
        // Groups: Amt Group: 1, Trx Group: 3, Phone Group: 2
        ProviderParsingRule(
            id = "trust_bank_card_received",
            providerName = "Trust Bank",
            primaryRegex = Regex(
                """(?i)(?:received from|Tk)\s*([0-9.,]+)[\s\S]*?(?:Ac\./Card:?|A/C:?)\s*([0-9*]+)[\s\S]*?TxnId:\s*([A-Za-z0-9]+)"""
            ),
            groupMapping = ParsingGroupMapping(
                amountGroup = 1,
                trxGroup = 3,
                phoneGroup = 2
            ),
            sampleSms = "Tk1,400.01 received from Ac./Card:017****0251 Fee: Tk.00... TxnId: 6914682052",
            fallbackPatterns = listOf(
                Regex(
                    """(?i)(?:received from|Tk)\s*([0-9.,]+)[\s\S]*?(?:Ac\./Card|A/C:)\s*([0-9*]+)[\s\S]*?TxnId:\s*([A-Za-z0-9]+)"""
                ) to ParsingGroupMapping(
                    amountGroup = 1,
                    trxGroup = 3,
                    phoneGroup = 2
                )
            )
        ),

        // 3. Nagad Bangla QR
        // Sample: "Payment - Bangla QR Successfully Received. \n Amount: Tk 1.00 \n Txn ID: 0001U735"
        // Groups: Amt Group: 1, Trx Group: 2, Phone Group: 0
        ProviderParsingRule(
            id = "nagad_bangla_qr",
            providerName = "Nagad",
            primaryRegex = Regex(
                """(?i)(?:Successfully Received\.?|Amount:)\s*(?:Tk|৳)?\s*([0-9]+[0-9.,]*)[\s\S]*?Txn ID:\s*([A-Za-z0-9]+)"""
            ),
            groupMapping = ParsingGroupMapping(
                amountGroup = 1,
                trxGroup = 2,
                phoneGroup = 0
            ),
            sampleSms = "Payment - Bangla QR Successfully Received. \n Amount: Tk 1.00 \n Txn ID: 0001U735",
            fallbackPatterns = listOf(
                Regex(
                    """(?i)(?:Successfully Received|Amount:)\s*(?:Tk|৳)?\s*([0-9.,]+)[\s\S]*?Txn ID:\s*([A-Za-z0-9]+)"""
                ) to ParsingGroupMapping(
                    amountGroup = 1,
                    trxGroup = 2,
                    phoneGroup = 0
                )
            )
        ),

        // 4. Nagad Personal Received
        // Sample: "Payment Received. \n Amount: Tk 1.00 \n Customer: 01797924838 \n TxnID: 75L7Z1Q1"
        // Groups: Amt Group: 1, Trx Group: 3, Phone Group: 2
        ProviderParsingRule(
            id = "nagad_personal_received",
            providerName = "Nagad",
            primaryRegex = Regex(
                """(?i)Payment Received[\s\S]*?(?:Tk|৳)\s*([0-9.,]+)[\s\S]*?Customer:\s*([0-9]+)[\s\S]*?TxnID:\s*([A-Za-z0-9]+)"""
            ),
            groupMapping = ParsingGroupMapping(
                amountGroup = 1,
                trxGroup = 3,
                phoneGroup = 2
            ),
            sampleSms = "Payment Received. \n Amount: Tk 1.00 \n Customer: 01797924838 \n TxnID: 75L7Z1Q1"
        ),

        // 5. Nagad Add Money from Bank
        // Sample: "Add Money from Bank is Successful. \n From: IBBL \n Amount: Tk 2520.0 \n TxnID: 760VVGGW"
        // Groups: Amt Group: 2, Trx Group: 3, Source Group: 1
        ProviderParsingRule(
            id = "nagad_add_money_bank",
            providerName = "Nagad",
            primaryRegex = Regex(
                """(?i)Add Money from Bank[\s\S]*?From:\s*([A-Za-z0-9 ]+?)\s*(?:\n|Amount:)[\s\S]*?(?:Tk|৳)\s*([0-9.,]+)[\s\S]*?TxnID:\s*([A-Za-z0-9]+)"""
            ),
            groupMapping = ParsingGroupMapping(
                amountGroup = 2,
                trxGroup = 3,
                sourceGroup = 1
            ),
            sampleSms = "Add Money from Bank is Successful. \n From: IBBL \n Amount: Tk 2520.0 \n TxnID: 760VVGGW",
            fallbackPatterns = listOf(
                Regex(
                    """(?i)Add Money from Bank[\s\S]*?From:\s*([A-Za-z0-9\s]+)[\s\S]*?(?:Tk|৳)\s*([0-9.,]+)[\s\S]*?TxnID:\s*([A-Za-z0-9]+)"""
                ) to ParsingGroupMapping(
                    amountGroup = 2,
                    trxGroup = 3,
                    sourceGroup = 1
                )
            )
        ),

        // 6. Upay Bangla QR
        // Sample: "Received Payment of Tk.1.00 from 01797924838... TrxID 01M3V635E3"
        // Groups: Amt Group: 1, Trx Group: 3, Phone Group: 2
        ProviderParsingRule(
            id = "upay_bangla_qr",
            providerName = "Upay",
            primaryRegex = Regex(
                """(?i)Received Payment of\s*(?:Tk\.?|৳)\s*([0-9.,]+)\s*from\s*([0-9]+)[\s\S]*?TrxID\s*([A-Za-z0-9]+)"""
            ),
            groupMapping = ParsingGroupMapping(
                amountGroup = 1,
                trxGroup = 3,
                phoneGroup = 2
            ),
            sampleSms = "Received Payment of Tk.1.00 from 01797924838... TrxID 01M3V635E3"
        ),

        // 7. Upay Personal Received
        // Sample: "Tk. 254.90 has been received from 01300366626... TrxID 01JP2W61EN"
        // Groups: Amt Group: 1, Trx Group: 3, Phone Group: 2
        ProviderParsingRule(
            id = "upay_personal_received",
            providerName = "Upay",
            primaryRegex = Regex(
                """(?i)(?:Tk\.?|৳)\s*([0-9.,]+)\s*has been received from\s*([0-9]+)[\s\S]*?TrxID\s*([A-Za-z0-9]+)"""
            ),
            groupMapping = ParsingGroupMapping(
                amountGroup = 1,
                trxGroup = 3,
                phoneGroup = 2
            ),
            sampleSms = "Tk. 254.90 has been received from 01300366626... TrxID 01JP2W61EN"
        ),

        // 8. BRAC Bank Limited
        // Sample: "Tk.150.00 has been received from BRAC Bank Limited... TrxID 01K6MZYRK4"
        // Groups: Amt Group: 1, Trx Group: 3, Source Group: 2
        ProviderParsingRule(
            id = "brac_bank_received",
            providerName = "BRAC Bank",
            primaryRegex = Regex(
                """(?i)(?:Tk\.?|৳)\s*([0-9.,]+)\s*has been received from\s*([A-Za-z0-9\s]+)[\s\S]*?TrxID\s*([A-Za-z0-9]+)"""
            ),
            groupMapping = ParsingGroupMapping(
                amountGroup = 1,
                trxGroup = 3,
                sourceGroup = 2
            ),
            sampleSms = "Tk.150.00 has been received from BRAC Bank Limited... TrxID 01K6MZYRK4"
        ),

        // 9. Tally Pay QR
        // Sample: "Tk 1,898.57 received from 017***4838 via TallyPay QR... Txn ID: YY1Z85HTP"
        // Groups: Amt Group: 1, Trx Group: 3, Phone Group: 2
        ProviderParsingRule(
            id = "tallypay_qr",
            providerName = "TallyPay",
            primaryRegex = Regex(
                """(?i)(?:Tk|৳)\s*([0-9.,]+)\s*received from\s*([0-9*]+)[\s\S]*?Txn ID:\s*([A-Za-z0-9]+)"""
            ),
            groupMapping = ParsingGroupMapping(
                amountGroup = 1,
                trxGroup = 3,
                phoneGroup = 2
            ),
            sampleSms = "Tk 1,898.57 received from 017***4838 via TallyPay QR... Txn ID: YY1Z85HTP"
        ),

        // 10. Islami Bank (IBBL - ATM / General Credit)
        // Sample: "Dear Customer ATM Tk 3647 has been credited to A/C777**05092965"
        // Groups: Amt Group: 1, Trx Group: 0, Account Group: 2
        ProviderParsingRule(
            id = "ibbl_atm_credit",
            providerName = "IBBL",
            primaryRegex = Regex(
                """(?i)(?:credited|Tk\.?|৳)\s*([0-9.,]+)[\s\S]*?A/C\s*([0-9*]+)"""
            ),
            groupMapping = ParsingGroupMapping(
                amountGroup = 1,
                trxGroup = 0,
                accountGroup = 2
            ),
            sampleSms = "Dear Customer ATM Tk 3647 has been credited to A/C777**05092965"
        ),

        // 11. BRAC Bank Direct Credit
        // Sample: "TK 2,020.00 has been credited to your A/C# 10609**0001 on 22-08-26."
        // Groups: Amt Group: 1, Trx Group: 0, Account Group: 2
        ProviderParsingRule(
            id = "brac_bank_direct_credit",
            providerName = "BRAC Bank",
            primaryRegex = Regex(
                """(?i)(?:TK|Tk|BDT)\s*([0-9.,]+)\s*has been credited to your A/C#\s*([0-9*]+)"""
            ),
            groupMapping = ParsingGroupMapping(
                amountGroup = 1,
                trxGroup = 0,
                accountGroup = 2
            ),
            sampleSms = "TK 2,020.00 has been credited to your A/C# 10609**0001 on 22-08-26."
        ),

        // 12. Midland Bank
        // Sample: "BDT 3999.99 was deposited to A/c No ***03118 by ATM transaction"
        // Groups: Amt Group: 1, Trx Group: 0, Account Group: 2
        ProviderParsingRule(
            id = "midland_bank_deposit",
            providerName = "Midland Bank",
            primaryRegex = Regex(
                """(?i)(?:BDT|Tk)\s*([0-9.,]+)\s*was deposited to A/c No\s*([A-Za-z0-9*]+)"""
            ),
            groupMapping = ParsingGroupMapping(
                amountGroup = 1,
                trxGroup = 0,
                accountGroup = 2
            ),
            sampleSms = "BDT 3999.99 was deposited to A/c No ***03118 by ATM transaction"
        ),

        // 13. Islami Bank (IBBL Deposit Slip)
        // Sample: "TrxID: 58260928000006866 \n Acc: 20507776705092965 \n Deposit Amount: 9,000.00"
        // Groups: Trx Group: 1, Account Group: 2, Amt Group: 3
        ProviderParsingRule(
            id = "ibbl_deposit_slip",
            providerName = "IBBL",
            primaryRegex = Regex(
                """(?i)TrxID:\s*([0-9]+)[\s\S]*?Acc:\s*([0-9]+)[\s\S]*?Deposit Amount:\s*([0-9.,]+)"""
            ),
            groupMapping = ParsingGroupMapping(
                amountGroup = 3,
                trxGroup = 1,
                accountGroup = 2
            ),
            sampleSms = "TrxID: 58260928000006866 \n Acc: 20507776705092965 \n Deposit Amount: 9,000.00"
        ),

        // 14. Sonali Bank NPSB
        // Sample: "Your account 5912*****1613 has been credited through NPSB for BDT 9,900.00"
        // Groups: Account Group: 1, Amt Group: 2, Trx Group: 0
        ProviderParsingRule(
            id = "sonali_bank_npsb",
            providerName = "Sonali Bank",
            primaryRegex = Regex(
                """(?i)account\s*([0-9*]+)[\s\S]*?(?:BDT|Tk)\s*([0-9.,]+)"""
            ),
            groupMapping = ParsingGroupMapping(
                amountGroup = 2,
                trxGroup = 0,
                accountGroup = 1
            ),
            sampleSms = "Your account 5912*****1613 has been credited through NPSB for BDT 9,900.00"
        )
    )

    /**
     * Executes the provider parsing rule against the normalized SMS body.
     */
    fun matchAndExtract(
        rule: ProviderParsingRule,
        normalizedBody: String,
        timestamp: String,
        rawText: String,
        sender: String = ""
    ): ExtractedTransactionData? {
        val patternsToTry = mutableListOf(rule.primaryRegex to rule.groupMapping)
        patternsToTry.addAll(rule.fallbackPatterns)

        for ((regex, mapping) in patternsToTry) {
            val match = regex.find(normalizedBody) ?: continue
            val groups = match.groups

            val rawAmtStr = if (mapping.amountGroup in 1 until groups.size) {
                groups[mapping.amountGroup]?.value?.replace(",", "")?.trim()
            } else null

            val amount = rawAmtStr?.toDoubleOrNull() ?: continue
            if (amount <= 0.0) continue

            val rawTrx = if (mapping.trxGroup in 1 until groups.size) {
                groups[mapping.trxGroup]?.value?.trim()
            } else null

            val phone = if (mapping.phoneGroup in 1 until groups.size) {
                groups[mapping.phoneGroup]?.value?.trim()
            } else null

            val account = if (mapping.accountGroup in 1 until groups.size) {
                groups[mapping.accountGroup]?.value?.trim()
            } else null

            val source = if (mapping.sourceGroup in 1 until groups.size) {
                groups[mapping.sourceGroup]?.value?.trim()
            } else null

            val senderNumber = phone ?: account ?: source ?: sender.trim().ifBlank { null }

            val finalTrxId = if (!rawTrx.isNullOrBlank()) {
                rawTrx.uppercase()
            } else {
                generateDeterministicReference(
                    provider = rule.providerName,
                    amount = amount,
                    identifier = senderNumber,
                    rawBody = normalizedBody
                )
            }

            return ExtractedTransactionData(
                gateway = rule.providerName,
                amount = amount,
                senderNumber = senderNumber,
                trxId = finalTrxId,
                timestamp = timestamp,
                rawText = rawText
            )
        }

        return null
    }
}
