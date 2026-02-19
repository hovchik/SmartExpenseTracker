package com.smartexpense.tracker.data.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * ISO-8601 datetime formatter used for the dateTime field.
 */
private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)

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
    /** ISO-8601 datetime string (e.g. "2026-02-17T14:30:00") for human-readable storage and grouping. */
    val dateTime: String = isoDateFormat.format(Date(System.currentTimeMillis())),
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
 * Metadata for a supported currency.
 */
data class CurrencyInfo(
    val code: String,       // ISO-4217 code, e.g. "USD"
    val symbol: String,     // Display symbol, e.g. "$"
    val name: String,       // Human-readable name, e.g. "US Dollar"
    val locale: String      // BCP-47 locale tag for number formatting, e.g. "en-US"
)

/** All currencies the app supports for selection and formatting. */
val SUPPORTED_CURRENCIES: List<CurrencyInfo> = listOf(
    CurrencyInfo("USD", "$",  "US Dollar",        "en-US"),
    CurrencyInfo("EUR", "€",  "Euro",             "de-DE"),
    CurrencyInfo("GBP", "£",  "British Pound",    "en-GB"),
    CurrencyInfo("AMD", "֏",  "Armenian Dram",    "hy-AM"),
    CurrencyInfo("INR", "₹",  "Indian Rupee",     "en-IN"),
    CurrencyInfo("JPY", "¥",  "Japanese Yen",     "ja-JP"),
    CurrencyInfo("CNY", "¥",  "Chinese Yuan",     "zh-CN"),
    CurrencyInfo("CAD", "CA$","Canadian Dollar",  "en-CA"),
    CurrencyInfo("AUD", "A$", "Australian Dollar","en-AU"),
    CurrencyInfo("CHF", "Fr", "Swiss Franc",      "de-CH"),
    CurrencyInfo("RUB", "₽",  "Russian Ruble",    "ru-RU"),
    CurrencyInfo("TRY", "₺",  "Turkish Lira",     "tr-TR"),
    CurrencyInfo("BRL", "R$", "Brazilian Real",   "pt-BR"),
    CurrencyInfo("MXN", "MX$","Mexican Peso",     "es-MX"),
    CurrencyInfo("KRW", "₩",  "South Korean Won", "ko-KR"),
    CurrencyInfo("AED", "د.إ","UAE Dirham",       "ar-AE"),
    CurrencyInfo("SGD", "S$", "Singapore Dollar", "en-SG"),
    CurrencyInfo("HKD", "HK$","Hong Kong Dollar", "en-HK"),
    CurrencyInfo("NOK", "kr", "Norwegian Krone",  "nb-NO"),
    CurrencyInfo("SEK", "kr", "Swedish Krona",    "sv-SE")
)

fun currencyInfoFor(code: String): CurrencyInfo =
    SUPPORTED_CURRENCIES.firstOrNull { it.code == code }
        ?: CurrencyInfo(code, code, code, "en-US")

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
    val transactionCount: Int = 0,
    /** Transactions grouped by date string (e.g. "2026-02-17"). */
    val transactionsByDate: Map<String, List<Transaction>> = emptyMap()
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

/** Default income keywords including Armenian terms. */
val DEFAULT_INCOME_KEYWORDS: List<String> = listOf(
    // English
    "credited", "received", "deposit", "refund", "cashback",
    "salary", "income", "reward", "reversed", "transfer to your", "added to",
    // Armenian
    "\u0574\u0578\u0582\u057F\u0584",          // income/deposit
    "\u056C\u056B\u0581\u0584\u0561\u057E\u0578\u0580\u0578\u0582\u0574", // top-up
    "\u0570\u0561\u0574\u0561\u056C\u0580\u0578\u0582\u0574"  // replenishment
)

/** Default expense keywords including Armenian terms. */
val DEFAULT_EXPENSE_KEYWORDS: List<String> = listOf(
    // English
    "purchase", "debited", "spent", "paid", "charged", "withdrawal",
    "sent", "debit", "withdrawn", "payment of", "used at",
    "atm cash", "atm", "mail order", "pos", "e-commerce", "online purchase",
    // Armenian
    "\u0565\u056C\u0584"  // expense/withdrawal
)

data class AppSettings(
    /** Legacy single-char symbol kept for backward-compat. Use currencyCode instead. */
    val currency: String = "$",
    /** ISO-4217 currency code (e.g. "USD", "AMD"). Drives all formatting and OCR parsing. */
    val currencyCode: String = "USD",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Language code for the app UI (e.g. "en", "ru", "hy", "zh", "es") or "system". */
    val language: String = "system",
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
    ),
    /** User-customizable keywords that indicate an income notification. */
    val notificationIncomeKeywords: List<String> = DEFAULT_INCOME_KEYWORDS,
    /** User-customizable keywords that indicate an expense notification. */
    val notificationExpenseKeywords: List<String> = DEFAULT_EXPENSE_KEYWORDS
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
