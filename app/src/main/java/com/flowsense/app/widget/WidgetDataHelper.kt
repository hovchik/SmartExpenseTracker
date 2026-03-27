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
    val worstPace: BudgetPaceSnapshot? = null,
    /** When true, all monetary amounts are masked for privacy. */
    val hideAmounts: Boolean = false
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
 * Smart category suggestion for the Quick Add widget.
 * Ranked by recent usage frequency and time-of-day patterns.
 */
data class SmartCategory(
    val name: String,
    val icon: String,
    val isExpense: Boolean = true
)

/**
 * Snapshot of data needed by the Quick Add widget.
 */
data class QuickAddWidgetData(
    val currencyCode: String = "USD",
    /** Top suggested categories based on usage frequency and time of day. */
    val suggestedCategories: List<SmartCategory> = emptyList(),
    /** The user's most recent transaction description (for context). */
    val lastDescription: String = ""
)

/**
 * Loads smart suggestions for the Quick Add widget.
 * Analyzes transaction history to suggest the most relevant categories
 * based on time of day and recent usage patterns.
 */
suspend fun loadQuickAddData(context: Context): QuickAddWidgetData {
    val app = context.applicationContext as? FlowSenseApp ?: return QuickAddWidgetData()
    val repo = app.repository
    repo.awaitInitialization(5_000)

    val appData = repo.appData.value
    val settings = appData.settings
    val categories = appData.categories

    val now = System.currentTimeMillis()
    val cal = Calendar.getInstance()
    val currentHour = cal.get(Calendar.HOUR_OF_DAY)

    // Look at last 90 days of transactions for usage patterns
    val ninetyDaysAgo = now - 90L * 24 * 60 * 60 * 1000
    val recentTransactions = appData.transactions.filter { t ->
        t.timestamp > ninetyDaysAgo && !t.isDeleted
    }

    // Time-of-day buckets: morning (6-11), afternoon (12-17), evening (18-23), night (0-5)
    val timeBucket = when (currentHour) {
        in 6..11 -> "morning"
        in 12..17 -> "afternoon"
        in 18..23 -> "evening"
        else -> "night"
    }

    // Score categories: weight recent usage + time-of-day match
    val categoryScores = mutableMapOf<String, Double>()
    for (tx in recentTransactions) {
        val txCal = Calendar.getInstance().apply { timeInMillis = tx.timestamp }
        val txHour = txCal.get(Calendar.HOUR_OF_DAY)
        val txBucket = when (txHour) {
            in 6..11 -> "morning"
            in 12..17 -> "afternoon"
            in 18..23 -> "evening"
            else -> "night"
        }
        // Base score: 1 point per transaction
        var score = 1.0
        // Recency bonus: transactions in the last 7 days get 2x weight
        if (tx.timestamp > now - 7L * 24 * 60 * 60 * 1000) score += 1.0
        // Time-of-day bonus: same time bucket gets 1.5x weight
        if (txBucket == timeBucket) score += 1.5

        categoryScores[tx.category] = (categoryScores[tx.category] ?: 0.0) + score
    }

    // Pick top 4 categories, preferring expense categories
    val topCategoryNames = categoryScores.entries
        .sortedByDescending { it.value }
        .take(6)
        .map { it.key }

    val suggested = topCategoryNames.mapNotNull { name ->
        val cat = categories.firstOrNull { it.name == name }
        if (cat != null) {
            // Determine if it's typically an expense or income
            val expenseCount = recentTransactions.count { it.category == name && it.type == com.flowsense.app.data.model.TransactionType.EXPENSE }
            val incomeCount = recentTransactions.count { it.category == name && it.type == com.flowsense.app.data.model.TransactionType.INCOME }
            SmartCategory(cat.name, cat.icon, isExpense = expenseCount >= incomeCount)
        } else {
            SmartCategory(name, "category")
        }
    }.take(4)

    // Fallback: if no history, suggest time-appropriate defaults
    val finalSuggestions = if (suggested.isEmpty()) {
        when (timeBucket) {
            "morning" -> listOf(
                SmartCategory("Food & Dining", "restaurant"),
                SmartCategory("Transportation", "directions_car"),
                SmartCategory("Groceries", "local_grocery_store"),
                SmartCategory("Other", "more_horiz")
            )
            "afternoon" -> listOf(
                SmartCategory("Food & Dining", "restaurant"),
                SmartCategory("Shopping", "shopping_bag"),
                SmartCategory("Transportation", "directions_car"),
                SmartCategory("Other", "more_horiz")
            )
            "evening" -> listOf(
                SmartCategory("Entertainment", "movie"),
                SmartCategory("Food & Dining", "restaurant"),
                SmartCategory("Shopping", "shopping_bag"),
                SmartCategory("Other", "more_horiz")
            )
            else -> listOf(
                SmartCategory("Entertainment", "movie"),
                SmartCategory("Transportation", "directions_car"),
                SmartCategory("Food & Dining", "restaurant"),
                SmartCategory("Other", "more_horiz")
            )
        }
    } else suggested

    val lastDesc = recentTransactions
        .maxByOrNull { it.timestamp }
        ?.description ?: ""

    return QuickAddWidgetData(
        currencyCode = settings.currencyCode,
        suggestedCategories = finalSuggestions,
        lastDescription = lastDesc
    )
}

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
            if (status == PaceStatus.OVER && (worstPace == null || spent / budget.monthlyLimit > (worstPace.spent / worstPace.limit))) {
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
        worstPace = worstPace,
        hideAmounts = settings.widgetHideAmounts
    )
}
