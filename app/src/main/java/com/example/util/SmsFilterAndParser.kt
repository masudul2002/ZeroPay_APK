package com.example.util

import android.util.Log
import com.example.data.model.CustomFilterRule
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Data structure holding clean, normalized financial fields extracted from incoming SMS.
 */
data class ExtractedTransactionData(
    val gateway: String,
    val amount: Double,
    val senderNumber: String?,
    val trxId: String,
    val timestamp: String,
    val fee: Double? = 0.0,
    val balance: Double? = null,
    val rawText: String = ""
)

/**
 * Result evaluation of strict SMS filtering.
 */
sealed class FilterEvaluation {
    data class Allowed(val data: ExtractedTransactionData) : FilterEvaluation()
    data class Ignored(val reason: String) : FilterEvaluation()
}

/**
 * Intelligent SMS Filter and Multi-Gateway Parser.
 *
 * Implements strict blacklist detection (dropping OTPs, security PINs, promotional spam,
 * debit alerts, ATM withdrawals, and non-transaction balance notices), 14 Bank/MFS provider rules,
 * custom dynamic rules, and a Universal Fallback SMS Parser for unexpected or unlisted SMS receipts.
 */
object SmsFilterAndParser {

    private const val TAG = "SmsFilterAndParser"

    // ---------------------------------------------------------------------------------------------
    // STRICT BLACKLIST PATTERNS
    // ---------------------------------------------------------------------------------------------

    // OTP, verification codes, device registration, security codes
    private val OTP_REGEX = Regex(
        """(?i)\b(otp|verification\s*code|security\s*code|one\s*time\s*password|one-time\s*password|login\s*pin|security\s*pin|device\s*registration|device\s*activation|secret\s*code|do\s*not\s*share|change\s*pin|password\s*reset|auth\s*code|temporary\s*password|pin\s*reset|sms\s*code|validation\s*code)\b"""
    )

    // Promotional spam, discounts, recharge offers, cashbacks without transaction receipt
    private val PROMO_REGEX = Regex(
        """(?i)\b(special\s*offer|exclusive\s*offer|win\s*up\s*to|dial\s*\*|recharge\s*offer|bundle\s*offer|promo\s*code|discount\s*code|bonus\s*points|earn\s*reward|congratulations\s*you\s*have\s*won|participate\s*in|cashback\s*offer)\b"""
    )

    // Statements, balance inquiries, debit transactions, ATM cash withdrawals, card purchases
    private val DEBIT_AND_STATEMENT_REGEX = Regex(
        """(?i)\b(balance\s*inquiry|mini\s*statement|statement\s*for|available\s*balance\s*is|debited\s*by|debited\s*from|debited\s*for|debited\s*with|account\s*debited|withdrawn\s*at\s*atm|atm\s*withdrawal|pos\s*purchase|card\s*purchase|bill\s*payment\s*successful|fund\s*transferred\s*to|sent\s*money\s*to|payment\s*made\s*to|your\s*card\s*has\s*been\s*used|standing\s*instruction\s*executed)\b"""
    )

    // Generic transaction ID matcher
    private val TRX_ID_REGEX = Regex(
        """(?i)\b(?:TrxID|TxnId|Txn\s*ID|Trx\s*ID|Transaction\s*ID|Trx|Txn|Ref|Ref\s*No|Reference)[:#\s.\-]*([A-Za-z0-9_\-]{6,35})\b"""
    )

    // Generic amount matcher
    private val AMOUNT_REGEX = Regex(
        """(?i)(?:Tk\.?|BDT|Tk|৳)\s*([0-9]+(?:,[0-9]+)*(?:\.[0-9]{1,2})?)"""
    )

    // Flexible amount matcher (handles currency symbol before or after numbers)
    private val AMOUNT_FLEXIBLE_REGEX = Regex(
        """(?i)(?:TK\.?|Tk|BDT|৳)\s*([0-9]+(?:,[0-9]+)*(?:\.[0-9]{1,2})?)|([0-9]+(?:,[0-9]+)*(?:\.[0-9]{1,2})?)\s*(?:TK\.?|Tk|BDT|৳)"""
    )

    // Universal incoming transaction indicators
    private val UNIVERSAL_INCOMING_REGEX = Regex(
        """(?i)\b(received|credited|deposit|deposited|cash\s*in|add\s*money|money\s*received|payment\s*received|transferred\s*from|credit\s*alert|credit\s*txn|credited\s*to|credited\s*with|credited\s*by|credited\s*through)\b"""
    )

    // Phone number pattern (013-019 Bangladesh mobile or masked phone)
    private val PHONE_NUMBER_REGEX = Regex(
        """\b(01[3-9][0-9*]{8,9})\b"""
    )

    // Account snippet pattern
    private val ACCOUNT_SNIPPET_REGEX = Regex(
        """(?i)\b(?:A/C#?|Ac\.|Account|Acc|AC No)[:#\s]*([A-Za-z0-9*]{4,25})"""
    )

    // Party pattern
    private val PARTY_FROM_REGEX = Regex(
        """(?i)\b(?:from|sender|customer|by)[:\s]+([+0-9A-Za-z*.\-\s]{3,30}?)(?=\s+(?:Fee|Bal|Balance|Trx|Txn|at|on|\.|$))"""
    )

    /**
     * Strictly evaluates an incoming SMS.
     * Returns [FilterEvaluation.Allowed] with clean extracted fields if it's a genuine incoming receipt,
     * or [FilterEvaluation.Ignored] with a specific drop reason otherwise.
     */
    fun evaluateSms(
        sender: String,
        messageBody: String,
        customRules: List<CustomFilterRule> = emptyList()
    ): FilterEvaluation {
        val trimmedBody = messageBody.trim()
        val normalizedBody = convertBengaliDigits(trimmedBody)
        val normalizedSender = sender.trim()

        if (normalizedBody.isBlank()) {
            return FilterEvaluation.Ignored("Dropped: Empty message body")
        }

        // 1. Strict Blacklist: OTP & Security PINs
        if (OTP_REGEX.containsMatchIn(normalizedBody)) {
            safeLogD(TAG, "Dropped OTP/Security message from $normalizedSender")
            return FilterEvaluation.Ignored("Dropped: OTP or verification code")
        }

        // 2. Strict Blacklist: Outgoing debit alerts & statements
        // Exception: Explicit credit/deposit notices (e.g. ATM CASH Txn CREDIT or deposited by ATM)
        val isExplicitCreditAlert = normalizedBody.contains("CREDIT", ignoreCase = true) ||
                normalizedBody.contains("deposited", ignoreCase = true) ||
                normalizedBody.contains("credited", ignoreCase = true)
        if (DEBIT_AND_STATEMENT_REGEX.containsMatchIn(normalizedBody) && !isExplicitCreditAlert) {
            safeLogD(TAG, "Dropped debit/statement notification from $normalizedSender")
            return FilterEvaluation.Ignored("Dropped: Outgoing debit or statement notice")
        }

        // 3. Strict Blacklist: Promotional alerts without explicit TrxID
        if (PROMO_REGEX.containsMatchIn(normalizedBody) && !TRX_ID_REGEX.containsMatchIn(normalizedBody)) {
            safeLogD(TAG, "Dropped promotional alert from $normalizedSender")
            return FilterEvaluation.Ignored("Dropped: Promotional alert or spam")
        }

        val nowIso = getCurrentIsoTimestamp()

        // 4. Dynamic Custom Regex Rules (Administrator Defined)
        for (rule in customRules) {
            if (!rule.enabled) continue
            if (matchesCustomSender(normalizedSender, rule.senderPattern)) {
                try {
                    val customRegex = Regex(rule.bodyRegex, RegexOption.IGNORE_CASE)
                    val match = customRegex.find(normalizedBody)
                    if (match != null) {
                        val amountStr = match.groups[rule.amountGroup]?.value?.replace(",", "")
                        val amount = amountStr?.toDoubleOrNull()
                        val trxId = match.groups[rule.trxIdGroup]?.value?.trim()
                        val senderNum = if (rule.senderGroup in 1 until match.groups.size) {
                            match.groups[rule.senderGroup]?.value?.trim()
                        } else null

                        if (amount != null && amount > 0 && !trxId.isNullOrBlank()) {
                            return FilterEvaluation.Allowed(
                                ExtractedTransactionData(
                                    gateway = rule.name,
                                    amount = amount,
                                    senderNumber = senderNum,
                                    trxId = trxId,
                                    timestamp = nowIso,
                                    rawText = trimmedBody
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    safeLogW(TAG, "Custom rule '${rule.name}' evaluation error: ${e.message}")
                }
            }
        }

        // 5. Exact 14 Bank & MFS Provider Rules
        // If a known sender is provided, prioritize rules matching that provider
        val prioritizedRules = if (normalizedSender.isNotBlank()) {
            val (preferred, remaining) = SmsParsingConfig.SUPPORTED_PROVIDER_RULES.partition { rule ->
                normalizedSender.contains(rule.providerName, ignoreCase = true) ||
                rule.providerName.contains(normalizedSender, ignoreCase = true) ||
                (normalizedSender.contains("trust", ignoreCase = true) && rule.providerName.contains("trust", ignoreCase = true)) ||
                (normalizedSender.contains("nagad", ignoreCase = true) && rule.providerName.contains("nagad", ignoreCase = true)) ||
                (normalizedSender.contains("upay", ignoreCase = true) && rule.providerName.contains("upay", ignoreCase = true)) ||
                (normalizedSender.contains("brac", ignoreCase = true) && rule.providerName.contains("brac", ignoreCase = true)) ||
                (normalizedSender.contains("ibbl", ignoreCase = true) && rule.providerName.contains("ibbl", ignoreCase = true)) ||
                (normalizedSender.contains("midland", ignoreCase = true) && rule.providerName.contains("midland", ignoreCase = true)) ||
                (normalizedSender.contains("sonali", ignoreCase = true) && rule.providerName.contains("sonali", ignoreCase = true)) ||
                (normalizedSender.contains("tally", ignoreCase = true) && rule.providerName.contains("tally", ignoreCase = true))
            }
            preferred + remaining
        } else {
            SmsParsingConfig.SUPPORTED_PROVIDER_RULES
        }

        for (providerRule in prioritizedRules) {
            val extracted = SmsParsingConfig.matchAndExtract(
                rule = providerRule,
                normalizedBody = normalizedBody,
                timestamp = nowIso,
                rawText = trimmedBody,
                sender = normalizedSender
            )
            if (extracted != null) {
                return FilterEvaluation.Allowed(extracted)
            }
        }

        // 6. Built-in Multi-Gateway Parsers
        val senderLower = normalizedSender.lowercase()

        // --- bKash ---
        if (senderLower.contains("bkash") || senderLower.contains("16247") || normalizedBody.contains("bKash", ignoreCase = true)) {
            val bkashResult = parseBkash(normalizedBody, nowIso, trimmedBody)
            if (bkashResult != null) return FilterEvaluation.Allowed(bkashResult)
        }

        // --- Nagad ---
        if (senderLower.contains("nagad") || senderLower.contains("16167") || normalizedBody.contains("Nagad", ignoreCase = true)) {
            val nagadResult = parseNagad(normalizedBody, nowIso, trimmedBody)
            if (nagadResult != null) return FilterEvaluation.Allowed(nagadResult)
        }

        // --- Rocket ---
        if (senderLower.contains("16216") || senderLower.contains("rocket") || normalizedBody.contains("Rocket", ignoreCase = true)) {
            val rocketResult = parseRocket(normalizedBody, nowIso, trimmedBody)
            if (rocketResult != null) return FilterEvaluation.Allowed(rocketResult)
        }

        // --- CellFin ---
        if (senderLower.contains("cellfin") || senderLower.contains("16259") || normalizedBody.contains("CellFin", ignoreCase = true)) {
            val cellfinResult = parseCellfin(normalizedBody, nowIso, trimmedBody)
            if (cellfinResult != null) return FilterEvaluation.Allowed(cellfinResult)
        }

        // --- Upay ---
        if (senderLower.contains("upay") || senderLower.contains("16268") || normalizedBody.contains("Upay", ignoreCase = true)) {
            val upayResult = parseUpay(normalizedBody, nowIso, trimmedBody)
            if (upayResult != null) return FilterEvaluation.Allowed(upayResult)
        }

        // --- Bangladesh Banks (BRAC, IBBL, Midland, Trust, City, DBBL, EBL, etc.) ---
        val bankResult = parseBankCreditReceipt(normalizedSender, normalizedBody, nowIso, trimmedBody)
        if (bankResult != null) {
            return FilterEvaluation.Allowed(bankResult)
        }

        // 7. Universal Fallback SMS Parser
        // Dynamically parses unlisted or unexpected SMS messages
        val fallbackResult = parseUniversalFallback(normalizedSender, normalizedBody, nowIso, trimmedBody)
        if (fallbackResult != null) {
            return FilterEvaluation.Allowed(fallbackResult)
        }

        // 8. Generic Fallback for whitelisted incoming financial transactions
        val genericResult = parseGenericReceipt(normalizedSender, normalizedBody, nowIso, trimmedBody)
        if (genericResult != null) {
            return FilterEvaluation.Allowed(genericResult)
        }

        return FilterEvaluation.Ignored("Dropped: Not a valid incoming financial transaction receipt")
    }

    // ---------------------------------------------------------------------------------------------
    // GATEWAY PARSER IMPLEMENTATIONS
    // ---------------------------------------------------------------------------------------------

    private fun parseBkash(body: String, timestamp: String, raw: String): ExtractedTransactionData? {
        val isReceipt = body.contains("received payment", ignoreCase = true) ||
                body.contains("received money", ignoreCase = true) ||
                body.contains("you have received", ignoreCase = true) ||
                body.contains("cash in", ignoreCase = true)

        if (!isReceipt) return null

        val trxMatch = Regex("""(?i)\b(?:TrxID|TrxId|TxnID)[:\s]*([A-Za-z0-9_\-]+)""").find(body)
            ?: return null
        val trxId = trxMatch.groupValues[1].uppercase()

        val amountMatch = Regex(
            """(?i)(?:received(?:\s+payment|\s+money)?|cash\s*in|amount)[:\s]*(?:Tk\.?|BDT)?\s*([0-9,]+(?:\.[0-9]{1,2})?)"""
        ).find(body) ?: AMOUNT_REGEX.find(body) ?: return null

        val amount = amountMatch.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        if (amount <= 0) return null

        val senderMatch = Regex("""(?i)from\s+([+0-9A-Za-z\-]{10,18})""").find(body)
        val senderNum = senderMatch?.groupValues?.get(1)

        val feeMatch = Regex("""(?i)Fee\s+(?:Tk\.?|BDT)?\s*([0-9,]+(?:\.[0-9]{1,2})?)""").find(body)
        val fee = feeMatch?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull() ?: 0.0

        val balanceMatch = Regex("""(?i)Balance\s+(?:Tk\.?|BDT)?\s*([0-9,]+(?:\.[0-9]{1,2})?)""").find(body)
        val balance = balanceMatch?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()

        return ExtractedTransactionData(
            gateway = "bKash",
            amount = amount,
            senderNumber = senderNum,
            trxId = trxId,
            timestamp = timestamp,
            fee = fee,
            balance = balance,
            rawText = raw
        )
    }

    private fun parseNagad(body: String, timestamp: String, raw: String): ExtractedTransactionData? {
        val isReceipt = body.contains("Payment Received", ignoreCase = true) ||
                body.contains("Money Received", ignoreCase = true) ||
                body.contains("Received Tk", ignoreCase = true) ||
                body.contains("Cash In", ignoreCase = true)

        if (!isReceipt) return null

        val trxMatch = Regex("""(?i)\b(?:TxnID|TxnId|TrxID|TrxId)[:\s]*([A-Za-z0-9_\-]+)""").find(body)
            ?: return null
        val trxId = trxMatch.groupValues[1].uppercase()

        val amountMatch = Regex(
            """(?i)(?:Amount[:\s]+)?(?:Tk\.?|BDT)?\s*([0-9,]+(?:\.[0-9]{1,2})?)"""
        ).find(body) ?: AMOUNT_REGEX.find(body) ?: return null

        val amount = amountMatch.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        if (amount <= 0) return null

        val senderMatch = Regex("""(?i)(?:from|sender)[:\s]+([+0-9A-Za-z\-]{10,18})""").find(body)
        val senderNum = senderMatch?.groupValues?.get(1)

        val feeMatch = Regex("""(?i)Fee[:\s]+(?:Tk\.?|BDT)?\s*([0-9,]+(?:\.[0-9]{1,2})?)""").find(body)
        val fee = feeMatch?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull() ?: 0.0

        val balanceMatch = Regex("""(?i)Balance[:\s]+(?:Tk\.?|BDT)?\s*([0-9,]+(?:\.[0-9]{1,2})?)""").find(body)
        val balance = balanceMatch?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()

        return ExtractedTransactionData(
            gateway = "Nagad",
            amount = amount,
            senderNumber = senderNum,
            trxId = trxId,
            timestamp = timestamp,
            fee = fee,
            balance = balance,
            rawText = raw
        )
    }

    private fun parseRocket(body: String, timestamp: String, raw: String): ExtractedTransactionData? {
        val isReceipt = body.contains("Received Tk", ignoreCase = true) ||
                body.contains("cash in", ignoreCase = true) ||
                body.contains("money received", ignoreCase = true)

        if (!isReceipt) return null

        val trxMatch = Regex("""(?i)\b(?:TxnId|TxnID|TrxId|TrxID)[:\s]*([0-9A-Za-z_\-]+)""").find(body)
            ?: return null
        val trxId = trxMatch.groupValues[1].uppercase()

        val amountMatch = Regex(
            """(?i)(?:Received\s+(?:money\s+)?(?:Tk\.?|BDT)?|Amount[:\s]+)\s*([0-9,]+(?:\.[0-9]{1,2})?)"""
        ).find(body) ?: AMOUNT_REGEX.find(body) ?: return null

        val amount = amountMatch.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        if (amount <= 0) return null

        val senderMatch = Regex("""(?i)(?:from|sender)[:\s]+([+0-9A-Za-z\-]{10,18})""").find(body)
        val senderNum = senderMatch?.groupValues?.get(1)

        val balanceMatch = Regex("""(?i)Balance[:\s]+(?:Tk\.?|BDT)?\s*([0-9,]+(?:\.[0-9]{1,2})?)""").find(body)
        val balance = balanceMatch?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()

        return ExtractedTransactionData(
            gateway = "Rocket",
            amount = amount,
            senderNumber = senderNum,
            trxId = trxId,
            timestamp = timestamp,
            balance = balance,
            rawText = raw
        )
    }

    private fun parseCellfin(body: String, timestamp: String, raw: String): ExtractedTransactionData? {
        val isReceipt = body.contains("credited with", ignoreCase = true) ||
                body.contains("received", ignoreCase = true) ||
                body.contains("credit alert", ignoreCase = true)

        if (!isReceipt) return null

        val trxMatch = Regex("""(?i)\b(?:TrxID|TrxId|TxnID|TxnId)[:\s]*([A-Za-z0-9_\-]+)""").find(body)
            ?: return null
        val trxId = trxMatch.groupValues[1].uppercase()

        val amountMatch = Regex(
            """(?i)(?:credited\s+with|received)?\s*(?:Tk\.?|BDT)\s*([0-9,]+(?:\.[0-9]{1,2})?)"""
        ).find(body) ?: AMOUNT_REGEX.find(body) ?: return null

        val amount = amountMatch.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        if (amount <= 0) return null

        val senderMatch = Regex("""(?i)from\s+([+0-9A-Za-z\-]{10,18})""").find(body)
        val senderNum = senderMatch?.groupValues?.get(1)

        val balanceMatch = Regex("""(?i)Balance\s+(?:Tk\.?|BDT)?\s*([0-9,]+(?:\.[0-9]{1,2})?)""").find(body)
        val balance = balanceMatch?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()

        return ExtractedTransactionData(
            gateway = "Cellfin",
            amount = amount,
            senderNumber = senderNum,
            trxId = trxId,
            timestamp = timestamp,
            balance = balance,
            rawText = raw
        )
    }

    private fun parseUpay(body: String, timestamp: String, raw: String): ExtractedTransactionData? {
        val isReceipt = body.contains("received", ignoreCase = true) ||
                body.contains("cash in", ignoreCase = true) ||
                body.contains("payment", ignoreCase = true)

        if (!isReceipt) return null

        val trxMatch = Regex("""(?i)\b(?:TrxID|TrxId|TxnID|TxnId)[:\s]*([A-Za-z0-9_\-]+)""").find(body)
            ?: return null
        val trxId = trxMatch.groupValues[1].uppercase()

        val amountMatch = AMOUNT_REGEX.find(body) ?: return null
        val amount = amountMatch.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        if (amount <= 0) return null

        val senderMatch = Regex("""(?i)from\s+([+0-9A-Za-z\-]{10,18})""").find(body)
        val senderNum = senderMatch?.groupValues?.get(1)

        return ExtractedTransactionData(
            gateway = "upay",
            amount = amount,
            senderNumber = senderNum,
            trxId = trxId,
            timestamp = timestamp,
            rawText = raw
        )
    }

    private fun parseBankCreditReceipt(
        sender: String,
        body: String,
        timestamp: String,
        raw: String
    ): ExtractedTransactionData? {
        val isCredit = body.contains("credited by", ignoreCase = true) ||
                body.contains("credited with", ignoreCase = true) ||
                body.contains("credited for", ignoreCase = true) ||
                body.contains("credited to", ignoreCase = true) ||
                body.contains("credit alert", ignoreCase = true) ||
                body.contains("deposit received", ignoreCase = true) ||
                body.contains("deposit successful", ignoreCase = true)

        if (!isCredit) return null

        val trxMatch = TRX_ID_REGEX.find(body) ?: return null
        val trxId = trxMatch.groupValues[1].uppercase()

        val amountMatch = AMOUNT_REGEX.find(body) ?: return null
        val amount = amountMatch.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        if (amount <= 0) return null

        val senderMatch = Regex("""(?i)(?:from|by)\s+([+0-9A-Za-z\-/]{6,20})""").find(body)
        val accountMatch = ACCOUNT_SNIPPET_REGEX.find(body)
        val senderNum = senderMatch?.groupValues?.get(1) ?: accountMatch?.groupValues?.get(1)

        val balanceMatch = Regex("""(?i)(?:Avail\s*Bal|Available\s*Balance|Balance)[:\s]+(?:Tk\.?|BDT)?\s*([0-9,]+(?:\.[0-9]{1,2})?)""").find(body)
        val balance = balanceMatch?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()

        val normalizedSenderName = resolveGatewayName(sender, defaultGateway = "Bank Transfer")

        return ExtractedTransactionData(
            gateway = normalizedSenderName,
            amount = amount,
            senderNumber = senderNum,
            trxId = trxId,
            timestamp = timestamp,
            balance = balance,
            rawText = raw
        )
    }

    /**
     * Universal Fallback SMS Parser.
     * Executes dynamically if all specific provider matches fail.
     * Extracts Amount, Sender/Account/Phone, and TrxID (or generates a secure deterministic reference).
     */
    fun parseUniversalFallback(
        sender: String,
        normalizedBody: String,
        timestamp: String,
        rawText: String
    ): ExtractedTransactionData? {
        val hasIncoming = UNIVERSAL_INCOMING_REGEX.containsMatchIn(normalizedBody)
        if (!hasIncoming) {
            return null
        }

        // 1. Amount Extraction
        val amtMatch = AMOUNT_FLEXIBLE_REGEX.find(normalizedBody) ?: return null
        val rawAmtStr = (amtMatch.groups[1]?.value ?: amtMatch.groups[2]?.value)?.replace(",", "")?.trim()
        val amount = rawAmtStr?.toDoubleOrNull() ?: return null
        if (amount <= 0.0) return null

        // 2. TrxID Extraction
        val trxMatch = TRX_ID_REGEX.find(normalizedBody)
        val rawTrxId = trxMatch?.groups?.get(1)?.value?.trim()

        // 3. Sender / Phone / Account / Source Extraction
        val phoneMatch = PHONE_NUMBER_REGEX.find(normalizedBody)
        val accountMatch = ACCOUNT_SNIPPET_REGEX.find(normalizedBody)
        val partyMatch = PARTY_FROM_REGEX.find(normalizedBody)

        val senderNumber = phoneMatch?.groups?.get(1)?.value?.trim()
            ?: accountMatch?.groups?.get(1)?.value?.trim()
            ?: partyMatch?.groups?.get(1)?.value?.trim()
            ?: sender.trim().ifBlank { null }

        val gateway = resolveGatewayName(sender, defaultGateway = "Universal Gateway")

        val finalTrxId = if (!rawTrxId.isNullOrBlank()) {
            rawTrxId.uppercase()
        } else {
            SmsParsingConfig.generateDeterministicReference(
                provider = gateway,
                amount = amount,
                identifier = senderNumber,
                rawBody = normalizedBody
            )
        }

        return ExtractedTransactionData(
            gateway = gateway,
            amount = amount,
            senderNumber = senderNumber,
            trxId = finalTrxId,
            timestamp = timestamp,
            rawText = rawText
        )
    }

    private fun parseGenericReceipt(
        sender: String,
        body: String,
        timestamp: String,
        raw: String
    ): ExtractedTransactionData? {
        val hasIncoming = body.contains("received", ignoreCase = true) ||
                body.contains("credited", ignoreCase = true) ||
                body.contains("deposit", ignoreCase = true) ||
                body.contains("payment", ignoreCase = true)

        if (!hasIncoming) return null

        val trxMatch = TRX_ID_REGEX.find(body) ?: return null
        val trxId = trxMatch.groupValues[1].uppercase()

        val amountMatch = AMOUNT_REGEX.find(body) ?: return null
        val amount = amountMatch.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        if (amount <= 0) return null

        val senderMatch = Regex("""(?i)from\s+([+0-9A-Za-z\-]{8,20})""").find(body)

        return ExtractedTransactionData(
            gateway = resolveGatewayName(sender, defaultGateway = "Unknown Gateway"),
            amount = amount,
            senderNumber = senderMatch?.groupValues?.get(1),
            trxId = trxId,
            timestamp = timestamp,
            rawText = raw
        )
    }

    // ---------------------------------------------------------------------------------------------
    // HELPERS
    // ---------------------------------------------------------------------------------------------

    fun resolveGatewayName(sender: String, defaultGateway: String = ""): String {
        val s = sender.trim()
        val sLower = s.lowercase()
        return when {
            sLower.contains("bkash") || sLower.contains("16247") -> "bKash"
            sLower.contains("nagad") || sLower.contains("16167") -> "Nagad"
            sLower.contains("rocket") || sLower.contains("16216") -> "Rocket"
            sLower.contains("cellfin") || sLower.contains("16259") -> "Cellfin"
            sLower.contains("upay") || sLower.contains("16268") -> "Upay"
            sLower.contains("tally") -> "TallyPay"
            sLower.contains("brac") -> "BRACBANK"
            sLower.contains("ibbl") || sLower.contains("islami bank") || sLower == "islamibank" -> "IBBL"
            sLower.contains("trust") -> "Trust Bank"
            sLower.contains("midland") || sLower.contains("mdb") -> "MidlandBank"
            sLower.contains("sonali") -> "Sonali Bank"
            sLower.contains("city") -> "CITY BANK"
            sLower.contains("dbbl") || sLower.contains("dutch") -> "DBBL"
            sLower.contains("ebl") || sLower.contains("eastern") -> "EBL"
            sLower.contains("asia") -> "BANK ASIA"
            sLower.contains("abb") || sLower.contains("ab bank") -> "AB BANK"
            sLower.contains("mtb") -> "MTB"
            sLower.contains("prime") -> "Prime Bank"
            sLower.contains("dhaka") -> "Dhaka Bank"
            sLower.contains("nrb") -> "NRB Bank"
            s.isNotBlank() -> s
            defaultGateway.isNotBlank() -> defaultGateway
            else -> "Bank Transfer"
        }
    }

    private fun matchesCustomSender(sender: String, patternStr: String): Boolean {
        if (patternStr.isBlank()) return true
        val patterns = patternStr.split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }
        val s = sender.lowercase()
        return patterns.any { s.contains(it) || it == s }
    }

    fun convertBengaliDigits(input: String): String {
        val bengaliDigits = "০১২৩৪৫৬৭৮৯"
        val asciiDigits = "0123456789"
        val sb = StringBuilder(input.length)
        for (char in input) {
            val idx = bengaliDigits.indexOf(char)
            if (idx != -1) {
                sb.append(asciiDigits[idx])
            } else {
                sb.append(char)
            }
        }
        return sb.toString()
    }

    fun getCurrentIsoTimestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    private fun safeLogD(tag: String, msg: String) {
        try {
            Log.d(tag, msg)
        } catch (_: Throwable) {}
    }

    private fun safeLogW(tag: String, msg: String) {
        try {
            Log.w(tag, msg)
        } catch (_: Throwable) {}
    }
}
