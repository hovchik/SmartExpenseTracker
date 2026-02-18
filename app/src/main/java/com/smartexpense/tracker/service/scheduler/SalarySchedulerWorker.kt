package com.smartexpense.tracker.service.scheduler

import android.content.Context
import android.util.Log
import androidx.work.*
import com.smartexpense.tracker.SmartExpenseApp
import com.smartexpense.tracker.data.model.Transaction
import com.smartexpense.tracker.data.model.TransactionSource
import com.smartexpense.tracker.data.model.TransactionType
import com.smartexpense.tracker.data.model.InAppNotification
import com.smartexpense.tracker.data.model.InAppNotificationType
import com.smartexpense.tracker.data.model.currencyInfoFor
import com.smartexpense.tracker.service.notification.ExpenseNotificationHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that runs daily and adds a salary income transaction on the
 * configured day of the month. Uses a unique periodic work tag so there is always
 * at most one scheduled instance.
 */
class SalarySchedulerWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "SalarySchedulerWorker"
        const val WORK_NAME = "salary_scheduler_daily"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SalarySchedulerWorker>(1, TimeUnit.DAYS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Log.d(TAG, "Salary scheduler enqueued (daily)")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Salary scheduler cancelled")
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val app = applicationContext as? SmartExpenseApp
                ?: return Result.failure()

            val repo = app.repository
            val settings = repo.appData.value.settings

            if (!settings.scheduledSalaryEnabled) return Result.success()
            if (settings.scheduledSalaryAmount <= 0) return Result.success()

            val today = Calendar.getInstance()
            val todayDayOfMonth = today.get(Calendar.DAY_OF_MONTH)
            if (todayDayOfMonth != settings.scheduledSalaryDayOfMonth) return Result.success()

            // Check if we've already added salary for this month
            val monthKey = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
            val alreadyAdded = repo.appData.value.transactions.any { tx ->
                tx.source == TransactionSource.MANUAL &&
                tx.type == TransactionType.INCOME &&
                tx.notes.contains("salary_scheduler:$monthKey")
            }
            if (alreadyAdded) {
                Log.d(TAG, "Salary already added for $monthKey, skipping")
                return Result.success()
            }

            val dtFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            val now = System.currentTimeMillis()
            val transaction = Transaction(
                amount = settings.scheduledSalaryAmount,
                description = settings.scheduledSalaryDescription,
                category = "Salary",
                type = TransactionType.INCOME,
                source = TransactionSource.MANUAL,
                timestamp = now,
                dateTime = dtFormatter.format(Date(now)),
                notes = "salary_scheduler:$monthKey",
                isRecurring = true
            )

            repo.ensureCategoryExists("Salary")
            repo.addTransaction(transaction)

            val currencySymbol = currencyInfoFor(settings.currencyCode).symbol
            repo.addInAppNotification(
                InAppNotification(
                    title = "Salary added automatically",
                    message = "${settings.scheduledSalaryDescription}: " +
                        "$currencySymbol${String.format("%.2f", settings.scheduledSalaryAmount)} " +
                        "recorded for $monthKey.",
                    type = InAppNotificationType.TRANSACTION_DETECTED,
                    relatedTransactionId = transaction.id
                )
            )

            ExpenseNotificationHelper.postSalaryAddedNotification(
                context = applicationContext,
                amount = settings.scheduledSalaryAmount,
                currencySymbol = currencySymbol,
                description = settings.scheduledSalaryDescription
            )

            Log.d(TAG, "Salary transaction added: ${settings.scheduledSalaryAmount} for $monthKey")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add salary transaction", e)
            Result.retry()
        }
    }
}
