package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import android.widget.Toast
import com.example.ZeroPayApp
import com.example.data.network.DispatchResult
import com.example.data.repository.SmsRepository
import com.example.util.ExtractedTransactionData
import com.example.util.FilterEvaluation
import com.example.util.SmsFilterAndParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        val messages: Array<SmsMessage>? = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) {
            Log.w(TAG, "SMS_RECEIVED intent received with empty messages")
            return
        }

        // Extract sender and concatenate full message body for multi-part SMS
        val firstMessage = messages[0]
        val sender = firstMessage.displayOriginatingAddress
            ?: firstMessage.originatingAddress
            ?: ""

        val fullBodyBuilder = StringBuilder()
        for (part in messages) {
            val body = part.displayMessageBody ?: part.messageBody
            if (!body.isNullOrEmpty()) {
                fullBodyBuilder.append(body)
            }
        }
        val fullBody = fullBodyBuilder.toString()
        val simSlot = detectSimSlot(intent)

        Log.d(TAG, "SMS received from: '$sender' (simSlot: $simSlot, length: ${fullBody.length})")

        val repository = SmsRepository.getInstance(context)

        // Step A: Strict Transaction Evaluation & Gateway Parsing
        val customRules = repository.getCustomFilterRules()
        val evaluation = SmsFilterAndParser.evaluateSms(sender, fullBody, customRules)

        // If the SMS is not a verified transaction and sender is not whitelisted, drop completely
        if (evaluation is FilterEvaluation.Ignored && !repository.isSenderAllowed(sender)) {
            Log.d(TAG, "SMS from unlisted sender '$sender' is not a payment transaction (${evaluation.reason}). Dropping completely.")
            return
        }

        // Show Toast: "SMS intercepted: [Sender]"
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(
                context.applicationContext,
                "SMS intercepted: $sender",
                Toast.LENGTH_SHORT
            ).show()
        }

        val pendingResult = goAsync()
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "ZeroPay:SmsDispatchWakeLock"
        )
        wakeLock?.acquire(60_000L) // 60 seconds max to accommodate network timeouts

        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (evaluation) {
                    is FilterEvaluation.Ignored -> {
                        // Step C: Drop OTPs, promotional alerts, debit notices, and statements. Log in Room DB.
                        Log.d(TAG, "Step C: SMS from '$sender' ignored (${evaluation.reason}). Logging locally.")
                        repository.logIgnoredSms(
                            sender = sender,
                            messageBody = fullBody,
                            simSlot = simSlot,
                            reason = evaluation.reason
                        )
                    }
                    is FilterEvaluation.Allowed -> {
                        // Step D: Valid incoming payment receipt. Forward clean payload to webhook.
                        val cleanData = evaluation.data
                        Log.d(TAG, "Step D: Valid ${cleanData.gateway} payment receipt detected from '$sender' (Amount: ${cleanData.amount}, TrxID: ${cleanData.trxId}). Forwarding clean payload.")
                        val result = repository.processIncomingSms(
                            sender = sender,
                            messageBody = fullBody,
                            simSlot = simSlot,
                            cleanData = cleanData
                        )

                        withContext(Dispatchers.Main) {
                            when (result) {
                                is DispatchResult.Success -> {
                                    Log.d(TAG, "SMS forwarded successfully to Zero Pay webhook!")
                                    ZeroPayApp.showForwardSuccessNotification(
                                        context = context,
                                        sender = "${cleanData.gateway} (${cleanData.trxId})",
                                        code = result.code
                                    )
                                }
                                is DispatchResult.Failure -> {
                                    Log.w(TAG, "SMS dispatch result: ${result.errorMessage}")
                                    if (result.errorMessage != "Forwarding paused") {
                                        if (result.errorMessage.contains("queued", ignoreCase = true) || result.errorMessage.contains("retry", ignoreCase = true)) {
                                            ZeroPayApp.showForwardQueuedNotification(
                                                context = context,
                                                sender = "${cleanData.gateway} (${cleanData.trxId})",
                                                note = result.errorMessage
                                            )
                                        } else {
                                            ZeroPayApp.showForwardFailureNotification(
                                                context = context,
                                                sender = sender,
                                                error = result.errorMessage
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in SmsReceiver dispatch coroutine", e)
            } finally {
                try {
                    if (wakeLock?.isHeld == true) {
                        wakeLock.release()
                    }
                } catch (ignored: Exception) {}
                pendingResult.finish()
            }
        }
    }

    private fun isTransactionSms(messageBody: String, sender: String = ""): Boolean {
        return SmsFilterAndParser.evaluateSms(sender, messageBody) is FilterEvaluation.Allowed
    }

    private fun detectSimSlot(intent: Intent): String {
        return try {
            val extras = intent.extras ?: return "SIM_1"
            val slot = when {
                extras.containsKey("slot") -> extras.getInt("slot", 0)
                extras.containsKey("simSlot") -> extras.getInt("simSlot", 0)
                extras.containsKey("simId") -> extras.getInt("simId", 0)
                extras.containsKey("phone") -> extras.getInt("phone", 0)
                extras.containsKey("subscription") -> {
                    val subId = extras.getInt("subscription", 0)
                    if (subId > 1) 1 else 0
                }
                else -> 0
            }
            if (slot == 1) "SIM_2" else "SIM_1"
        } catch (e: Exception) {
            "SIM_1"
        }
    }

    companion object {
        private const val TAG = "ZeroPaySmsReceiver"

        // Transaction ID regex: matches TrxID, TxnId, Trx, Transaction ID, Txn
        private val TRANSACTION_ID_REGEX = Regex(
            """(?i)\b(TrxID|TxnId|Transaction\s*ID|Trx\s*ID|Txn\s*ID|Trx|Txn)(\b|[:#\s\d]|$)"""
        )

        // Financial keywords regex: matches Tk, BDT, Amount, Balance, Received, Sent, Fee
        private val FINANCIAL_REGEX = Regex(
            """(?i)\b(Tk|BDT|Amount|Balance|Received|Sent|Fee)(\b|[:#.\s\d]|$)"""
        )

        /**
         * Validates if the message is a genuine transaction SMS:
         * The message body MUST contain at least one Transaction ID keyword
         * (e.g., "TrxID", "TxnId", "Trx", "Transaction ID", "Txn")
         * AND at least one Financial keyword
         * (e.g., "Tk", "BDT", "Amount", "Balance", "Received", "Sent", "Fee").
         */
        fun checkIsTransactionSms(messageBody: String): Boolean {
            if (messageBody.isBlank()) return false
            val hasTransactionKeyword = TRANSACTION_ID_REGEX.containsMatchIn(messageBody)
            val hasFinancialKeyword = FINANCIAL_REGEX.containsMatchIn(messageBody)
            return hasTransactionKeyword && hasFinancialKeyword
        }
    }
}
