package com.smartexpense.tracker.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.smartexpense.tracker.R
import com.smartexpense.tracker.SmartExpenseApp
import com.smartexpense.tracker.data.model.TransactionType
import com.smartexpense.tracker.util.CurrencyUtils
import java.util.Calendar

/**
 * AppWidget provider that displays the current month's balance,
 * income total, and expense total on the home screen.
 */
class BalanceWidgetProvider : AppWidgetProvider() {

    companion object {
        /** Minimum widget width in dp. */
        const val MIN_WIDTH_DP = 250
        /** Minimum widget height in dp. */
        const val MIN_HEIGHT_DP = 110
        /** Update interval: 30 minutes in milliseconds. */
        const val UPDATE_PERIOD_MS = 1_800_000L
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_balance)

        // Read current data from the repository
        try {
            val repository = SmartExpenseApp.instance.repository
            val appData = repository.appData.value
            val currencyCode = appData.settings.currencyCode.ifEmpty { "USD" }

            // Calculate current month range
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.DAY_OF_MONTH, 1)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val monthStart = calendar.timeInMillis

            calendar.add(Calendar.MONTH, 1)
            val monthEnd = calendar.timeInMillis

            val monthTransactions = appData.transactions.filter {
                it.timestamp in monthStart until monthEnd
            }

            val income = monthTransactions
                .filter { it.type == TransactionType.INCOME }
                .sumOf { it.amount }

            val expenses = monthTransactions
                .filter { it.type == TransactionType.EXPENSE }
                .sumOf { it.amount }

            val balance = income - expenses

            views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_balance_title))
            views.setTextViewText(R.id.widget_balance, CurrencyUtils.format(balance, currencyCode))
            views.setTextViewText(R.id.widget_income_label, context.getString(R.string.widget_income_label))
            views.setTextViewText(R.id.widget_income_amount, CurrencyUtils.format(income, currencyCode))
            views.setTextViewText(R.id.widget_expense_label, context.getString(R.string.widget_expense_label))
            views.setTextViewText(R.id.widget_expense_amount, CurrencyUtils.format(expenses, currencyCode))
        } catch (_: Exception) {
            // App not yet initialized — show placeholder values
            views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_balance_title))
            views.setTextViewText(R.id.widget_balance, "--")
            views.setTextViewText(R.id.widget_income_label, context.getString(R.string.widget_income_label))
            views.setTextViewText(R.id.widget_income_amount, "--")
            views.setTextViewText(R.id.widget_expense_label, context.getString(R.string.widget_expense_label))
            views.setTextViewText(R.id.widget_expense_amount, "--")
        }

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
