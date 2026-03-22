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
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.smartexpense.tracker.MainActivity
import com.smartexpense.tracker.SmartExpenseApp
import com.smartexpense.tracker.data.model.TransactionType
import java.util.concurrent.TimeUnit

/**
 * WorkManager [CoroutineWorker] that generates a weekly spending summary
 * notification showing total spent, top category, and transaction count.
 */
class WeeklySummaryWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "WeeklySummaryWorker"
        private const val CHANNEL_ID = "weekly_summary"
        private const val CHANNEL_NAME = "Weekly Summary"
        private const val CHANNEL_DESC = "Weekly spending summary notifications"
        private const val WORK_NAME = "weekly_spending_summary"
        private const val NOTIF_ID = 3002

        /**
         * Schedules the weekly summary worker with a 7-day interval.
         * Creates the notification channel if it does not already exist.
         */
        fun schedule(context: Context) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = CHANNEL_DESC
                }
            )

            val request = PeriodicWorkRequestBuilder<WeeklySummaryWorker>(7, TimeUnit.DAYS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Log.d(TAG, "Weekly summary worker scheduled (7-day interval)")
        }

        /**
         * Cancels the weekly summary worker.
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Weekly summary worker cancelled")
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val app = applicationContext as? SmartExpenseApp ?: return Result.failure()
            val repository = app.repository
            repository.awaitInitialization()

            val now = System.currentTimeMillis()
            val oneWeekAgo = now - TimeUnit.DAYS.toMillis(7)

            val weekTransactions = repository.getTransactionsByDateRange(oneWeekAgo, now)
            val expenses = weekTransactions.filter { it.type == TransactionType.EXPENSE }

            if (expenses.isEmpty()) {
                Log.d(TAG, "No expenses this week, skipping summary notification")
                return Result.success()
            }

            val totalSpent = expenses.sumOf { it.amount }
            val transactionCount = expenses.size
            val topCategory = expenses.groupBy { it.category }
                .maxByOrNull { (_, txns) -> txns.sumOf { it.amount } }
                ?.key ?: "Unknown"

            val settings = repository.appData.value.settings
            val currencySymbol = settings.currency

            sendSummaryNotification(
                totalSpent = totalSpent,
                topCategory = topCategory,
                transactionCount = transactionCount,
                currencySymbol = currencySymbol
            )

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate weekly summary", e)
            Result.retry()
        }
    }

    private fun sendSummaryNotification(
        totalSpent: Double,
        topCategory: String,
        transactionCount: Int,
        currencySymbol: String
    ) {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val summaryText = "You spent $currencySymbol${String.format("%.2f", totalSpent)} " +
            "across $transactionCount transaction${if (transactionCount != 1) "s" else ""}. " +
            "Top category: $topCategory."

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Your weekly spending summary")
            .setContentText(summaryText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summaryText))
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
