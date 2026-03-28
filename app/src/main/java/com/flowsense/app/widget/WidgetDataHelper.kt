package com.flowsense.app.widget

import android.content.Context
import com.flowsense.app.FlowSenseApp
import com.flowsense.app.data.model.TransactionType
import com.flowsense.app.data.model.PaceStatus
import com.flowsense.app.util.CurrencyUtils
import com.flowsense.app.util.DateUtils
import java.util.Calendar

/**
 * Snapshot of data needed by the home-screen widget.
 */
data class WidgetData(
    val currencyCode: String = "USD",
    val monthLabel: String = "",
    val totalIncome: Double = 0.0,
    val totalExpenses: Double = 0.0,
    val balance: Double = 0.0,
    val todayExpenses: Double = 0.0,
    val transactionCount: Int = 0,
    val topCategories: List<CategorySpend> = emptyList(),
    /** Worst budget pace (most over-budget category), if any. */
    val worstPace: BudgetPaceSnapshot? = null
)

data class CategorySpend(
    val name: String,
    val amount: Double
)

data class BudgetPaceSnapshot(
    val categoryName: String,
    val spent: Double,
    val limit: Double,
    val status: PaceStatus
)

/**
 * Reads current month's summary from the repository synchronously.
 * Must be called from a coroutine (widget update runs on a background thread).
 */
suspend fun loadWidgetData(context: Context): WidgetData {
    val app = context.applicationContext as? FlowSenseApp ?: return WidgetData()
    val repo = app.repository
    repo.awaitInitialization(5_000)

    val appData = repo.appData.value
    val settings = appData.settings
    val currencyCode = settings.currencyCode

    val now = System.currentTimeMillis()
    val monthStart = DateUtils.getStartOfMonth(now)
    val monthEnd = DateUtils.getEndOfMonth(now)
    val dayStart = DateUtils.getStartOfDay(now)
    val dayEnd = DateUtils.getEndOfDay(now)

    val monthLabel = DateUtils.formatMonth(now)

    // Filter active (non-deleted) transactions for current month
    val monthTransactions = appData.transactions.filter { t ->
        t.timestamp in monthStart..monthEnd && !t.isDeleted
    }

    val totalIncome = monthTransactions
        .filter { it.type == TransactionType.INCOME }
        .sumOf { it.amount }

    val totalExpenses = monthTransactions
        .filter { it.type == TransactionType.EXPENSE }
        .sumOf { it.amount }

    val todayExpenses = monthTransactions
        .filter { it.type == TransactionType.EXPENSE && it.timestamp in dayStart..dayEnd }
        .sumOf { it.amount }

    // Top 3 expense categories
    val topCategories = monthTransactions
        .filter { it.type == TransactionType.EXPENSE }
        .groupBy { it.category }
        .mapValues { (_, txns) -> txns.sumOf { it.amount } }
        .entries
        .sortedByDescending { it.value }
        .take(3)
        .map { CategorySpend(it.key, it.value) }

    // Budget pace — find the worst category
    val cal = Calendar.getInstance()
    val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

    var worstPace: BudgetPaceSnapshot? = null
    for (budget in appData.budgets) {
        val catName = appData.categories.firstOrNull { it.id == budget.categoryId }?.name
            ?: budget.categoryId
        val spent = monthTransactions
            .filter { it.type == TransactionType.EXPENSE && it.category == catName }
            .sumOf { it.amount }
        if (dayOfMonth > 0) {
            val linearPace = budget.monthlyLimit * dayOfMonth / daysInMonth
            val status = when {
                spent > budget.monthlyLimit -> PaceStatus.OVER
                spent > linearPace * 1.10 -> PaceStatus.OVER
                spent >= linearPace * 0.90 -> PaceStatus.ON_TRACK
                else -> PaceStatus.UNDER
            }
            if (status == PaceStatus.OVER && budget.monthlyLimit > 0 && (worstPace == null || worstPace.limit <= 0 || spent / budget.monthlyLimit > worstPace.spent / worstPace.limit)) {
                worstPace = BudgetPaceSnapshot(catName, spent, budget.monthlyLimit, status)
            }
        }
    }

    return WidgetData(
        currencyCode = currencyCode,
        monthLabel = monthLabel,
        totalIncome = totalIncome,
        totalExpenses = totalExpenses,
        balance = totalIncome - totalExpenses,
        todayExpenses = todayExpenses,
        transactionCount = monthTransactions.size,
        topCategories = topCategories,
        worstPace = worstPace
    )
}
