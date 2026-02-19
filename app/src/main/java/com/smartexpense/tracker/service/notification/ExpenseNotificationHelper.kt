package com.smartexpense.tracker.service.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.smartexpense.tracker.MainActivity

object ExpenseNotificationHelper {

    private const val CHANNEL_ID_BUDGET = "budget_alerts"
    private const val CHANNEL_NAME_BUDGET = "Budget Alerts"
    private const val CHANNEL_DESC_BUDGET = "Notifications when monthly expense limits are reached"

    private const val CHANNEL_ID_SALARY = "salary_scheduler"
    private const val CHANNEL_NAME_SALARY = "Salary Scheduler"
    private const val CHANNEL_DESC_SALARY = "Confirmation when a scheduled salary is added"

    private const val NOTIF_ID_BUDGET = 1001
    private const val NOTIF_ID_SALARY = 1002

    fun createChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID_BUDGET, CHANNEL_NAME_BUDGET, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = CHANNEL_DESC_BUDGET
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID_SALARY, CHANNEL_NAME_SALARY, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = CHANNEL_DESC_SALARY
            }
        )
    }

    fun postBudgetExceededNotification(
        context: Context,
        spent: Double,
        limit: Double,
        currencySymbol: String
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pi = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_BUDGET)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Monthly expense limit reached")
            .setContentText(
                "You've spent $currencySymbol${String.format("%.2f", spent)} " +
                    "— your $currencySymbol${String.format("%.2f", limit)} limit has been exceeded."
            )
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "You've spent $currencySymbol${String.format("%.2f", spent)} this month, " +
                        "exceeding your set limit of $currencySymbol${String.format("%.2f", limit)}. " +
                        "Tap to review your expenses."
                )
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID_BUDGET, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS permission not granted — notification silently skipped
        }
    }

    fun postSalaryAddedNotification(
        context: Context,
        amount: Double,
        currencySymbol: String,
        description: String
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pi = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_SALARY)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Salary added automatically")
            .setContentText("$description: $currencySymbol${String.format("%.2f", amount)} has been recorded.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID_SALARY, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS permission not granted
        }
    }
}
