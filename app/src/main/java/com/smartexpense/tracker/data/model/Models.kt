package com.smartexpense.tracker.data.model

import java.util.UUID

/**
 * Represents a single financial transaction (expense or income).
 */
data class Transaction(
    val id: String = UUID.randomUUID().toString(),
    val amount: Double,
    val description: String,
    val category: String,
    val type: TransactionType,
    val source: TransactionSource,
    val timestamp: Long = System.currentTimeMillis(),
    val tags: List<String> = emptyList(),
    val notes: String = "",
    val merchantName: String = "",
    val isRecurring: Boolean = false
)

enum class TransactionType {
    EXPENSE, INCOME
}

enum class TransactionSource {
    MANUAL,        // User entered manually
    OCR_SCAN,      // Camera receipt scan
    SMS,           // Parsed from banking SMS
    NOTIFICATION,  // Parsed from banking app notification
    IMPORT         // Imported from file
}

/**
 * Category with optional AI-suggested grouping.
 */
data class Category(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val icon: String = "category",  // Material icon name
    val color: Long = 0xFF6200EE,
    val isDefault: Boolean = false,
    val parentCategoryId: String? = null
)

/**
 * Budget definition for a category.
 */
data class Budget(
    val id: String = UUID.randomUUID().toString(),
    val categoryId: String,
    val monthlyLimit: Double,
    val alertThreshold: Double = 0.8 // Alert at 80% by default
)

/**
 * AI-generated optimization suggestion.
 */
data class AiSuggestion(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String,
    val potentialSaving: Double,
    val category: String,
    val priority: SuggestionPriority,
    val timestamp: Long = System.currentTimeMillis(),
    val isDismissed: Boolean = false
)

enum class SuggestionPriority {
    HIGH, MEDIUM, LOW
}

/**
 * Report model for daily/weekly/monthly summaries.
 */
data class ExpenseReport(
    val periodType: ReportPeriod,
    val startDate: Long,
    val endDate: Long,
    val totalExpenses: Double,
    val totalIncome: Double,
    val netBalance: Double,
    val categoryBreakdown: Map<String, Double>,
    val topExpenses: List<Transaction>,
    val comparisonWithPrevious: Double,
    val averageDailySpend: Double,
    val topMerchants: Map<String, Double> = emptyMap(),
    val dayOfWeekSpending: Map<String, Double> = emptyMap(),
    val sourceBreakdown: Map<String, Int> = emptyMap(),
    val aiInsight: String = "",
    val transactionCount: Int = 0
)

enum class ReportPeriod {
    DAILY, WEEKLY, MONTHLY
}

/**
 * App-wide data container stored as JSON.
 */
data class AppData(
    val transactions: List<Transaction> = emptyList(),
    val categories: List<Category> = defaultCategories(),
    val budgets: List<Budget> = emptyList(),
    val suggestions: List<AiSuggestion> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val lastUpdated: Long = System.currentTimeMillis()
)

/**
 * Theme mode for the app.
 */
enum class ThemeMode {
    SYSTEM,  // Follow system setting
    LIGHT,   // Always light
    DARK     // Always dark
}

data class AppSettings(
    val currency: String = "$",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val notificationListenerEnabled: Boolean = false,
    val smsParsingEnabled: Boolean = false,
    val autoCategorizationEnabled: Boolean = true,
    val bankingAppPackages: List<String> = listOf(
        "com.chase.sig.android",
        "com.wf.wellsfargomobile",
        "com.infonow.bofa",
        "com.citi.citimobile",
        "com.konylabs.capitalone",
        "com.usaa.mobile.android.usaa",
        "com.ally.MobileBanking",
        "com.paypal.android.p2pmobile",
        "com.venmo",
        "com.squareup.cash",
        "com.zellepay.zelle"
    )
)

fun defaultCategories(): List<Category> = listOf(
    Category(name = "Food & Dining", icon = "restaurant", color = 0xFFE91E63, isDefault = true),
    Category(name = "Transportation", icon = "directions_car", color = 0xFF2196F3, isDefault = true),
    Category(name = "Shopping", icon = "shopping_bag", color = 0xFF9C27B0, isDefault = true),
    Category(name = "Entertainment", icon = "movie", color = 0xFFFF9800, isDefault = true),
    Category(name = "Bills & Utilities", icon = "receipt_long", color = 0xFF607D8B, isDefault = true),
    Category(name = "Healthcare", icon = "local_hospital", color = 0xFFF44336, isDefault = true),
    Category(name = "Education", icon = "school", color = 0xFF3F51B5, isDefault = true),
    Category(name = "Groceries", icon = "local_grocery_store", color = 0xFF4CAF50, isDefault = true),
    Category(name = "Rent & Housing", icon = "home", color = 0xFF795548, isDefault = true),
    Category(name = "Salary", icon = "payments", color = 0xFF00BCD4, isDefault = true),
    Category(name = "Freelance", icon = "work", color = 0xFF8BC34A, isDefault = true),
    Category(name = "Investment", icon = "trending_up", color = 0xFFFFEB3B, isDefault = true),
    Category(name = "Other", icon = "more_horiz", color = 0xFF9E9E9E, isDefault = true)
)
