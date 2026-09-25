package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
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

        // Extract SIM slot
        val simSlot = detectSimSlot(intent)

        Log.d(TAG, "SMS received from: '$sender' (simSlot: $simSlot, length: ${fullBody.length})")

        val repository = SmsRepository.getInstance(context)

        // Quick check before background wake if sender is not allowed
        if (!repository.isSenderAllowed(sender)) {
            Log.d(TAG, "Ignored SMS from '$sender' (not in allowed senders list)")
            return
        }

        // Use goAsync to process webhook network call reliably
        val pendingResult = goAsync()
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "ZeroPay:SmsDispatchWakeLock"
        )
        wakeLock?.acquire(15_000L) // 15 seconds max

        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Processing allowed SMS from: $sender")
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
                            Log.w(TAG, "Failed forwarding SMS: ${result.errorMessage}")
                            ZeroPayApp.showForwardFailureNotification(
                                context = context,
                                sender = sender,
                                error = result.errorMessage
                            )
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
    }
}
