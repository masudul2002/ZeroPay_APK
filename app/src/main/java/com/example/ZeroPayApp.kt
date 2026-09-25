package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.data.repository.SmsRepository

class ZeroPayApp : Application() {

    lateinit var repository: SmsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        repository = SmsRepository.getInstance(this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            // Status channel for background foreground service
            val statusChannel = NotificationChannel(
                CHANNEL_STATUS_ID,
                "Service Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active background status of Zero Pay SMS Forwarder"
                setShowBadge(false)
            }

            // Alerts channel for forwarded SMS confirmations & errors
            val alertsChannel = NotificationChannel(
                CHANNEL_ALERTS_ID,
                "Forwarding Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Alerts when incoming SMS is forwarded or if an error occurs"
                enableVibration(true)
            }

            notificationManager?.createNotificationChannel(statusChannel)
            notificationManager?.createNotificationChannel(alertsChannel)
        }
    }

    companion object {
        const val CHANNEL_STATUS_ID = "zeropay_status_channel"
        const val CHANNEL_ALERTS_ID = "zeropay_alerts_channel"

        private var instance: ZeroPayApp? = null
        fun get(): ZeroPayApp = instance!!

        fun showForwardSuccessNotification(context: Context, sender: String, code: Int) {
            try {
                val intent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                val pendingIntent = PendingIntent.getActivity(
                    context, 0, intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )

                val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("SMS Forwarded: $sender")
                    .setContentText("Dispatched to Zero Pay (HTTP $code)")
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .build()

                NotificationManagerCompat.from(context).notify(System.currentTimeMillis().toInt(), notification)
            } catch (ignored: SecurityException) {
                // Notification permission might not be granted yet
            }
        }

        fun showForwardFailureNotification(context: Context, sender: String, error: String) {
            try {
                val intent = Intent(context, MainActivity::class.java)
                val pendingIntent = PendingIntent.getActivity(
                    context, 0, intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )

                val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setContentTitle("Failed Forwarding SMS: $sender")
                    .setContentText(error)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .build()

                NotificationManagerCompat.from(context).notify(System.currentTimeMillis().toInt(), notification)
            } catch (ignored: SecurityException) {
                // Notification permission might not be granted
            }
        }
    }
}
