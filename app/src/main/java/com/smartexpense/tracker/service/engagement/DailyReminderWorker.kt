package com.smartexpense.tracker.service.engagement

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.Worker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.smartexpense.tracker.MainActivity
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * WorkManager [Worker] that sends a daily notification reminding the user
 * to log their expenses.
 */
class DailyReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {

    companion object {
        private const val TAG = "DailyReminderWorker"
        private const val CHANNEL_ID = "daily_reminders"
        private const val CHANNEL_NAME = "Daily Reminders"
        private const val CHANNEL_DESC = "Daily reminders to log your expenses"
        private const val WORK_NAME = "daily_expense_reminder"
        private const val NOTIF_ID = 3001

        /**
         * Schedules the daily reminder at the specified [hour] and [minute].
         * Creates the notification channel if it does not already exist.
         */
        fun schedule(context: Context, hour: Int, minute: Int) {
            // Create notification channel
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = CHANNEL_DESC
                }
            )

            // Calculate initial delay to the target time
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (before(now)) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            val initialDelay = target.timeInMillis - now.timeInMillis

            val request = PeriodicWorkRequestBuilder<DailyReminderWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
            Log.d(TAG, "Daily reminder scheduled for $hour:${minute.toString().padStart(2, '0')}")
        }

        /**
         * Cancels the daily reminder.
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Daily reminder cancelled")
        }
    }

    override fun doWork(): Result {
        return try {
            sendReminderNotification()
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send daily reminder", e)
            Result.retry()
        }
    }

    private fun sendReminderNotification() {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Log your expenses")
            .setContentText("Don't forget to record today's spending. Tap to open the app.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(applicationContext).notify(NOTIF_ID, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS permission not granted
        }
    }
}
