package com.smartexpense.tracker.service.scheduler

import android.content.Context
import android.util.Log
import androidx.work.*
import com.smartexpense.tracker.SmartExpenseApp
import com.smartexpense.tracker.data.model.InAppNotification
import com.smartexpense.tracker.data.model.InAppNotificationType
import com.smartexpense.tracker.data.model.ScheduledExpense
import com.smartexpense.tracker.data.model.currencyInfoFor
import com.smartexpense.tracker.service.notification.ExpenseNotificationHelper
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that runs daily and sends a reminder notification on the
 * last working day (Mon–Fri) before each scheduled expense's payment day.
 *
 * Example: if payment is on the 15th and the 15th is a Wednesday, the reminder
 * fires on the 14th (Tuesday). If the 15th is a Monday, the reminder fires on
 * the preceding Friday (the 12th).
 */
class ScheduledExpenseWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "ScheduledExpenseWorker"
        const val WORK_NAME = "scheduled_expense_reminder_daily"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ScheduledExpenseWorker>(1, TimeUnit.DAYS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Log.d(TAG, "Scheduled expense reminder worker enqueued (daily)")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Scheduled expense reminder worker cancelled")
        }

        /**
         * Returns the last working day (Mon–Fri) strictly before [paymentDay]
         * in the given month/year, as a [Calendar].
         */
        fun workingDayBefore(year: Int, month: Int, paymentDay: Int): Calendar {
            val cal = Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                // Clamp to actual last day of the month
                val maxDay = getActualMaximum(Calendar.DAY_OF_MONTH)
                set(Calendar.DAY_OF_MONTH, paymentDay.coerceAtMost(maxDay))
            }
            // Move back one day, then skip weekends
            cal.add(Calendar.DAY_OF_MONTH, -1)
            while (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY ||
                cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
            ) {
                cal.add(Calendar.DAY_OF_MONTH, -1)
            }
            return cal
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val app = applicationContext as? SmartExpenseApp ?: return Result.failure()
            val repo = app.repository
            val settings = repo.appData.value.settings
            val expenses = settings.scheduledExpenses.filter { it.enabled && it.amount > 0 }
            if (expenses.isEmpty()) return Result.success()

            val today = Calendar.getInstance()
            val todayYear = today.get(Calendar.YEAR)
            val todayMonth = today.get(Calendar.MONTH)
            val todayDay = today.get(Calendar.DAY_OF_MONTH)

            val currencySymbol = currencyInfoFor(settings.currencyCode).symbol

            expenses.forEachIndexed { index, expense ->
                val reminderDate = workingDayBefore(todayYear, todayMonth, expense.dayOfMonth)
                if (reminderDate.get(Calendar.DAY_OF_MONTH) == todayDay &&
                    reminderDate.get(Calendar.MONTH) == todayMonth &&
                    reminderDate.get(Calendar.YEAR) == todayYear
                ) {
                    notifyExpense(expense, currencySymbol, index, repo)
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check scheduled expenses", e)
            Result.retry()
        }
    }

    private suspend fun notifyExpense(
        expense: ScheduledExpense,
        currencySymbol: String,
        index: Int,
        repo: com.smartexpense.tracker.data.repository.ExpenseRepository
    ) {
        Log.d(TAG, "Sending reminder for: ${expense.name}")

        repo.addInAppNotification(
            InAppNotification(
                title = "Payment reminder: ${expense.name}",
                message = "$currencySymbol${String.format("%.2f", expense.amount)} " +
                    "is due on day ${expense.dayOfMonth}. Prepare your payment.",
                type = InAppNotificationType.BUDGET_ALERT
            )
        )

        ExpenseNotificationHelper.postScheduledExpenseReminder(
            context = applicationContext,
            expenseName = expense.name,
            amount = expense.amount,
            currencySymbol = currencySymbol,
            paymentDay = expense.dayOfMonth,
            index = index
        )
    }
}
