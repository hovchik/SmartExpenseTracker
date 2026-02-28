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
 * GPS coordinates captured at the moment a transaction is created.
 */
data class GeoLocation(
    val lat: Double,
    val lng: Double
)

/**
 * Represents a single financial transaction (expense or income).
 */
data class Transaction(
    val id: String = UUID.randomUUID().toString(),
    /** Amount in the app's main currency (e.g. AMD). For foreign-currency transactions this is the converted value. */
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
    val isRecurring: Boolean = false,
    /** Device GPS location captured at transaction time; null if unavailable. */
    val location: GeoLocation? = null,
    // Legacy fields kept for backward-compatible JSON deserialization; prefer [location].
    @Deprecated("Use location.lat", ReplaceWith("location?.lat"))
    val latitude: Double? = null,
    @Deprecated("Use location.lng", ReplaceWith("location?.lng"))
    val longitude: Double? = null,
    /** ISO-4217 currency code the [amount] is denominated in. Empty = app default at time of creation. */
    val currencyCode: String = "",
    /** Original amount in the foreign currency before conversion. 0.0 = no conversion was applied. */
    val originalAmount: Double = 0.0,
    /** ISO-4217 code of the original foreign currency (e.g. "USD", "RUB"). Empty = same as app currency. */
    val originalCurrencyCode: String = "",
    /** Exchange rate used at conversion time: 1 [originalCurrencyCode] = [exchangeRate] [currencyCode]. */
    val exchangeRate: Double = 0.0
) {
    /** Resolved latitude: prefers [location], falls back to legacy [latitude] field. */
    val resolvedLat: Double? get() = location?.lat ?: @Suppress("DEPRECATION") latitude

    /** Resolved longitude: prefers [location], falls back to legacy [longitude] field. */
    val resolvedLng: Double? get() = location?.lng ?: @Suppress("DEPRECATION") longitude

    /** True when this transaction carries a GPS fix. */
    val hasLocation: Boolean get() = resolvedLat != null && resolvedLng != null
}

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
    DAILY, WEEKLY, MONTHLY, CUSTOM
}

/**
 * Type of in-app notification shown in the bell panel.
 */
enum class InAppNotificationType {
    TRANSACTION_DETECTED,  // SMS or notification auto-detected a transaction
    CATEGORY_CREATED,      // A new category was auto-created during parsing
    SMS_PARSED,            // Explicit SMS scan found transactions
    BUDGET_ALERT           // Spending nearing budget limit
}

/**
 * In-app notification shown in the bell panel (top-right corner).
 * Persisted in JSON so it survives app restarts.
 */
data class InAppNotification(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val message: String,
    val type: InAppNotificationType,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    /** Transaction this notification is related to (if any). */
    val relatedTransactionId: String? = null,
    /** Category name that was auto-created (for CATEGORY_CREATED type). */
    val suggestedCategoryName: String? = null
)

/**
 * App-wide data container stored as JSON.
 */
data class AppData(
    val transactions: List<Transaction> = emptyList(),
    val categories: List<Category> = defaultCategories(),
    val budgets: List<Budget> = emptyList(),
    val suggestions: List<AiSuggestion> = emptyList(),
    val inAppNotifications: List<InAppNotification> = emptyList(),
    val storeLocations: List<StoreLocation> = emptyList(),
    /** Exchange rates: current (index 0) and previous (index 1). Max 2 entries. */
    val rateHistory: List<RateHistoryEntry> = emptyList(),
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

/**
 * Which on-device AI engine to use for categorization and insights.
 */
enum class AiEnginePreference {
    /** Automatically detect the best available engine. */
    AUTO,
    /** Pure rule-based engine (always available, no model download). */
    RULE_BASED,
    /** Gemini Nano via Google AICore or Samsung Galaxy AI. */
    GEMINI_NANO,
    /** MediaPipe LLM Inference with a downloaded model. */
    MEDIAPIPE_LLM,
    /** Ollama – on-device or local-network LLM server. */
    OLLAMA
}

/**
 * Source for fetching currency exchange rates.
 */
enum class RateSource {
    /** Open Exchange Rate APIs (open.er-api.com / exchangerate-api.com). */
    OPEN_API,
    /** rate.am – Armenian bank exchange rates (AMD-centric). */
    RATE_AM
}

/**
 * How often to automatically refresh exchange rates.
 */
enum class RateUpdateFrequency(val minutes: Int, val label: String) {
    EVERY_30_MIN(30, "Every 30 min"),
    EVERY_HOUR(60, "Every hour"),
    EVERY_3_HOURS(180, "Every 3 hours"),
    EVERY_6_HOURS(360, "Every 6 hours"),
    DAILY(1440, "Once a day"),
    MANUAL(0, "Manual only")
}

/**
 * A snapshot of exchange rates at a specific point in time.
 * [AppData.rateHistory] keeps at most 2 entries: current (index 0)
 * and previous (index 1).
 */
data class RateHistoryEntry(
    /** Timestamp when rates were fetched. */
    val timestamp: Long = System.currentTimeMillis(),
    /** Which source was used (OPEN_API / RATE_AM). */
    val source: String = "",
    /** USD-based rate map, e.g. {"AMD" → 388.5, "EUR" → 0.92, …}. */
    val rates: Map<String, Double> = emptyMap()
)

/**
 * Dashboard sections that can be reordered via drag-and-drop.
 */
enum class DashboardSection {
    BALANCE_SUMMARY,
    QUICK_STATS,
    WEEKLY_CHART,
    AI_INSIGHTS,
    CATEGORY_BREAKDOWN,
    RECENT_TRANSACTIONS
}

/**
 * A store/merchant location pinned on the map.
 * Links a merchant name to geographic coordinates so the Store Map screen
 * can show where shopping happens and aggregate transactions per location.
 */
data class StoreLocation(
    val id: String = UUID.randomUUID().toString(),
    val merchantName: String,
    val latitude: Double,
    val longitude: Double,
    val address: String = ""
)

/**
 * A recurring expense (e.g. loan payment, subscription) that fires a reminder
 * notification on the last working day (Mon–Fri) before the payment day.
 */
data class ScheduledExpense(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val amount: Double = 0.0,
    /** Day-of-month (1–31) when the payment is due. */
    val dayOfMonth: Int = 1,
    val enabled: Boolean = true
)

data class AppSettings(
    /** Legacy single-char symbol kept for backward-compat. Use currencyCode instead. */
    val currency: String = "֏",
    /** ISO-4217 currency code (e.g. "USD", "AMD"). Drives all formatting and OCR parsing. */
    val currencyCode: String = "AMD",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val notificationListenerEnabled: Boolean = false,
    val smsParsingEnabled: Boolean = false,
    val autoCategorizationEnabled: Boolean = true,
    /**
     * When true, the app will attempt to use the selected AI engine
     * for categorization and report insights.
     */
    val localAiEnabled: Boolean = false,
    /** Which on-device AI engine to use. Defaults to AUTO (best available). */
    val aiEnginePreference: AiEnginePreference = AiEnginePreference.AUTO,
    /** Path to a MediaPipe-compatible model file (e.g. Gemma .task file). */
    val mediapipeModelPath: String = "",
    /** HuggingFace API token for downloading gated models (e.g. Gemma). */
    val huggingFaceToken: String = "",
    /** Ollama server base URL (e.g. "http://localhost:11434"). */
    val ollamaHost: String = "http://localhost:11434",
    /** Selected Ollama model name (e.g. "llama3.2:1b", "gemma2:2b"). */
    val ollamaModel: String = "",
    /** Source for currency exchange rates. */
    val rateSource: RateSource = RateSource.OPEN_API,
    /** Keywords used when scanning for banking/payment apps on the device. */
    val scanKeywords: List<String> = listOf("bank", "payment", "wallet"),
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
    // ── Transaction type detection keywords ────────────────────────
    /** Keywords in notification/SMS text that indicate an income transaction. */
    val incomeKeywords: List<String> = listOf(
        "credit account", "credited", "received", "deposit", "refund",
        "cashback", "transfer to your", "added to", "reversed",
        "salary", "income", "reward",
        // Armenian: mutq (deposit), licqavorum (top-up), hamalrum (replenishment)
        "\u0574\u0578\u0582\u057F\u0584", "\u056C\u056B\u0581\u0584\u0561\u057E\u0578\u0580\u0578\u0582\u0574", "\u0570\u0561\u0574\u0561\u056C\u0580\u0578\u0582\u0574",
        // Russian (CIS banks)
        "зачисление", "пополнение"
    ),
    /** Keywords in notification/SMS text that indicate an expense transaction. */
    val expenseKeywords: List<String> = listOf(
        "purchase", "atm cash", "atm", "mail order", "pos",
        "charged", "debited", "spent", "paid", "withdrawal",
        "sent", "debit", "withdrawn", "payment of", "used at",
        "debit account", "e-commerce", "online purchase",
        // Armenian: elq (outgoing/expense)
        "\u0565\u056C\u0584",
        // Russian (CIS banks)
        "списание", "оплата", "покупка", "снятие"
    ),
    // ── Monthly expense threshold ───────────────────────────────────
    /** 0 = disabled. When monthly expenses exceed this, a system notification is posted. */
    val monthlyExpenseLimit: Double = 0.0,
    /** Tracks which month (yyyy-MM) was last alerted so we don't spam per transaction. */
    val lastThresholdAlertMonth: String = "",
    // ── Salary scheduler ───────────────────────────────────────────
    val scheduledSalaryEnabled: Boolean = false,
    val scheduledSalaryAmount: Double = 0.0,
    /** Day-of-month (1–31) to add the salary transaction automatically each month. */
    val scheduledSalaryDayOfMonth: Int = 1,
    val scheduledSalaryDescription: String = "Monthly Salary",
    /** Persisted order of dashboard sections (stored as enum names). Empty = default order. */
    val dashboardSectionOrder: List<String> = emptyList(),
    // ── Scheduled expenses (loans, subscriptions) ─────────────────
    val scheduledExpenses: List<ScheduledExpense> = emptyList(),
    // ── Exchange rate update frequency ─────────────────────────────
    /** How often to auto-refresh exchange rates. */
    val rateUpdateFrequency: RateUpdateFrequency = RateUpdateFrequency.EVERY_HOUR,
    /** Timestamp of the last successful rate fetch (epoch millis). 0 = never. */
    val lastRateUpdateTimestamp: Long = 0
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
