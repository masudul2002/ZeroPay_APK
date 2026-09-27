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

        // Step A: Check if Sender is whitelisted in SharedPreferences. If not -> drop completely.
        if (!repository.isSenderAllowed(sender)) {
            Log.d(TAG, "Step A: Sender '$sender' is not whitelisted in filter list. Dropping completely.")
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

        // Step B: Pass body to isTransactionSms().
        val isTx = isTransactionSms(fullBody)

        val pendingResult = goAsync()
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "ZeroPay:SmsDispatchWakeLock"
        )
        wakeLock?.acquire(60_000L) // 60 seconds max to accommodate network timeouts

        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!isTx) {
                    // Step C: If false, do NOT forward to webhook. Log locally in Room DB as "Status: Ignored (Promotional/OTP)".
                    Log.d(TAG, "Step C: SMS from '$sender' is not a transaction SMS. Logging as Status: Ignored (Promotional/OTP).")
                    repository.logIgnoredPromoOrOtp(
                        sender = sender,
                        messageBody = fullBody,
                        simSlot = simSlot
                    )
                } else {
                    // Step D: If true, verify deviceToken and forward via Webhook POST request with Authorization: Bearer <deviceToken>
                    Log.d(TAG, "Step D: Valid transaction SMS detected from '$sender'. Verifying deviceToken and forwarding to webhook.")
                    val result = repository.processIncomingSms(
                        sender = sender,
                        messageBody = fullBody,
                        simSlot = simSlot
                    )

                    withContext(Dispatchers.Main) {
                        when (result) {
                            is DispatchResult.Success -> {
                                Log.d(TAG, "SMS forwarded successfully to Zero Pay webhook!")
                                ZeroPayApp.showForwardSuccessNotification(
                                    context = context,
                                    sender = sender,
                                    code = result.code
                                )
                            }
                            is DispatchResult.Failure -> {
                                Log.w(TAG, "SMS dispatch result: ${result.errorMessage}")
                                if (result.errorMessage != "Forwarding paused") {
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

    /**
     * Helper function: checks if messageBody contains transaction and financial keywords.
     */
    private fun isTransactionSms(messageBody: String): Boolean {
        return checkIsTransactionSms(messageBody)
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
