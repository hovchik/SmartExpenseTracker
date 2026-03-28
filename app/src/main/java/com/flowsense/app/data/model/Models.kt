package com.flowsense.app.data.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * ISO-8601 datetime formatter used for the dateTime field.
 * ThreadLocal because SimpleDateFormat is not thread-safe and Transaction
 * default values can be evaluated from any thread.
 */
private val isoDateFormat: ThreadLocal<SimpleDateFormat> =
    ThreadLocal.withInitial { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US) }

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
    val dateTime: String = isoDateFormat.get()!!.format(Date(System.currentTimeMillis())),
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
    val exchangeRate: Double = 0.0,
    /** True when this transaction's category was assigned by an AI model rather than the user or rule-based engine. */
    val categorizedByAi: Boolean = false,
    /** URI path to a receipt photo attached to this transaction. Nullable for Gson backward compat. */
    val photoUri: String? = "",
    /** Soft-delete: when non-zero, this transaction is in the trash (epoch millis of deletion). Nullable for Gson backward compat. */
    val deletedAt: Long? = 0,
    /** Split expense: list of people sharing this cost. Empty = not split. Nullable for Gson backward compat. */
    val splitWith: List<SplitPerson>? = emptyList()
) {
    /** Resolved latitude: prefers [location], falls back to legacy [latitude] field. */
    val resolvedLat: Double? get() = location?.lat ?: @Suppress("DEPRECATION") latitude

    /** Resolved longitude: prefers [location], falls back to legacy [longitude] field. */
    val resolvedLng: Double? get() = location?.lng ?: @Suppress("DEPRECATION") longitude

    /** True when this transaction carries a GPS fix. */
    val hasLocation: Boolean get() = resolvedLat != null && resolvedLng != null

    /** True when this transaction is in the trash (soft-deleted). */
    val isDeleted: Boolean get() = (deletedAt ?: 0) > 0

    /** True when this transaction is a split expense. */
    val isSplit: Boolean get() = !splitWith.isNullOrEmpty()

    /** Each person's share of the split expense. */
    val splitAmount: Double get() = if (!splitWith.isNullOrEmpty()) amount / (splitWith!!.size + 1) else amount

    /** Safe accessor for photoUri that handles null from Gson. */
    val resolvedPhotoUri: String get() = photoUri ?: ""

    /**
     * Returns a copy with null String fields replaced by their defaults.
     * Gson bypasses Kotlin constructors via `Unsafe`, so non-null String
     * fields can be `null` at runtime when the JSON key was missing.
     */
    @Suppress("SENSELESS_COMPARISON")
    fun sanitized(): Transaction =
        if (currencyCode == null || originalCurrencyCode == null || description == null ||
            category == null || notes == null || merchantName == null || dateTime == null
        ) copy(
            description = description ?: "",
            category = category ?: "",
            dateTime = dateTime ?: isoDateFormat.get()!!.format(Date(timestamp)),
            notes = notes ?: "",
            merchantName = merchantName ?: "",
            currencyCode = currencyCode ?: "",
            originalCurrencyCode = originalCurrencyCode ?: "",
        ) else this
}

/**
 * A person participating in a split expense.
 */
data class SplitPerson(
    val name: String,
    val paid: Boolean = false
)

enum class TransactionType {
    EXPENSE, INCOME
}

enum class TransactionSource {
    MANUAL,        // User entered manually
    OCR_SCAN,      // Camera receipt scan
    SMS,           // Parsed from banking SMS
    NOTIFICATION,  // Parsed from banking app notification
    IMPORT,        // Imported from file
    VOICE          // Voice input (speech-to-text)
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
/**
 * A detected recurring transaction pattern (e.g., Netflix $15.99 monthly).
 */
data class RecurringPattern(
    val id: String = UUID.randomUUID().toString(),
    val description: String,
    val merchantName: String = "",
    val amount: Double,
    val category: String,
    /** Average interval between occurrences in days. */
    val intervalDays: Int,
    /** Number of times this pattern was observed. */
    val occurrenceCount: Int,
    /** Whether the user has confirmed/dismissed this pattern. */
    val confirmed: Boolean = false,
    val dismissed: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Spending streak and achievement tracking.
 */
data class SpendingStreak(
    /** Current consecutive days with logged expenses. */
    val currentStreak: Int = 0,
    /** Longest-ever consecutive streak. */
    val longestStreak: Int = 0,
    /** Last date a transaction was logged (yyyy-MM-dd). */
    val lastLogDate: String = "",
    /** Total number of transactions ever logged. */
    val totalTransactionsLogged: Int = 0,
    /** Achievements unlocked (achievement IDs). */
    val unlockedAchievements: List<String> = emptyList(),
    /** Months where user stayed under budget. */
    val monthsUnderBudget: Int = 0
)

/**
 * Budget pace indicator data for a single category.
 */
data class BudgetPace(
    val categoryName: String,
    val monthlyLimit: Double,
    val spent: Double,
    val dayOfMonth: Int,
    val daysInMonth: Int,
    /** Projected end-of-month spend at current pace. */
    val projectedSpend: Double,
    /** Pace status: UNDER, ON_TRACK, OVER */
    val status: PaceStatus
)

enum class PaceStatus {
    UNDER, ON_TRACK, OVER
}

data class AppData(
    val transactions: List<Transaction> = emptyList(),
    val categories: List<Category> = defaultCategories(),
    val budgets: List<Budget> = emptyList(),
    val suggestions: List<AiSuggestion> = emptyList(),
    val inAppNotifications: List<InAppNotification> = emptyList(),
    val storeLocations: List<StoreLocation> = emptyList(),
    /** Saved OCR scan sections (receipts with items and costs). */
    val ocrSections: List<OcrSection> = emptyList(),
    /** Exchange rates: current (index 0) and previous (index 1). Max 2 entries. */
    val rateHistory: List<RateHistoryEntry> = emptyList(),
    /** AI prompt/response conversation history. */
    val aiConversations: List<AiConversation> = emptyList(),
    /** Detected recurring transaction patterns. Nullable for Gson backward compat. */
    val recurringPatterns: List<RecurringPattern>? = emptyList(),
    /** Spending streak and achievement tracking. Nullable for Gson backward compat. */
    val spendingStreak: SpendingStreak? = SpendingStreak(),
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
 * High-level AI execution mode selection (new tri-mode architecture).
 */
enum class AiModePreference(val label: String, val description: String) {
    /** Automatically select the best available AI engine. */
    AUTO("Auto (recommended)", "Automatically select the best available engine"),
    /** Use Android system AI runtimes (AICore, ML Kit GenAI). */
    SYSTEM_AI("System AI", "Use built-in on-device AI when available"),
    /** Use a user-installed or downloaded local model. */
    LOCAL_MODEL("Local model", "Run a downloaded AI model on-device"),
    /** Use a cloud AI provider (Claude, Gemini, ChatGPT, DeepSeek). */
    CLOUD_AI("Cloud AI", "Use a cloud AI service via internet connection")
}

/**
 * Which cloud AI provider to use when [AiModePreference.CLOUD_AI] is selected.
 */
enum class CloudAiProviderType(val label: String, val placeholder: String) {
    CLAUDE("Claude (Anthropic)", "sk-ant-..."),
    GEMINI("Gemini (Google)", "AIza..."),
    CHATGPT("ChatGPT (OpenAI)", "sk-..."),
    DEEPSEEK("DeepSeek", "sk-...")
}

/**
 * A selectable cloud AI model: display [label] shown in the UI and [modelId] sent to the API.
 */
data class CloudAiModel(val label: String, val modelId: String)

/** Available models for each cloud AI provider. */
val CLOUD_AI_MODELS: Map<CloudAiProviderType, List<CloudAiModel>> = mapOf(
    CloudAiProviderType.CLAUDE to listOf(
        CloudAiModel("Claude Haiku 4.5", "claude-haiku-4-5-20251001"),
        CloudAiModel("Claude Sonnet 4.5", "claude-sonnet-4-5-20250514"),
        CloudAiModel("Claude Opus 4", "claude-opus-4-20250514")
    ),
    CloudAiProviderType.GEMINI to listOf(
        CloudAiModel("Gemini 3.1 Pro", "gemini-3.1-pro-preview"),
        CloudAiModel("Gemini 3 Flash", "gemini-3-flash-preview"),
        CloudAiModel("Gemini 3.1 Flash Lite", "gemini-3.1-flash-lite-preview"),
        CloudAiModel("Gemini 2.5 Pro", "gemini-2.5-pro"),
        CloudAiModel("Gemini 2.5 Flash", "gemini-2.5-flash"),
        CloudAiModel("Gemini 2.5 Flash Lite", "gemini-2.5-flash-lite")
    ),
    CloudAiProviderType.CHATGPT to listOf(
        CloudAiModel("GPT-4o mini", "gpt-4o-mini"),
        CloudAiModel("GPT-4o", "gpt-4o"),
        CloudAiModel("GPT-4.1 nano", "gpt-4.1-nano"),
        CloudAiModel("GPT-4.1 mini", "gpt-4.1-mini"),
        CloudAiModel("GPT-4.1", "gpt-4.1")
    ),
    CloudAiProviderType.DEEPSEEK to listOf(
        CloudAiModel("DeepSeek Chat", "deepseek-chat"),
        CloudAiModel("DeepSeek Reasoner", "deepseek-reasoner")
    )
)

/** Returns the default model ID for a given provider. */
fun defaultModelIdFor(provider: CloudAiProviderType): String =
    CLOUD_AI_MODELS[provider]?.firstOrNull()?.modelId ?: ""

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
    RECENT_TRANSACTIONS,
    BUDGET_PACE,
    SPENDING_STREAKS
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

// ─── OCR Scan Session & Sections ────────────────────────────────

/**
 * A single item detected by OCR on a receipt: name + price.
 */
data class OcrItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val price: Double,
    /** Optional category for this item (auto-detected or user-assigned). */
    val category: String = ""
)

/**
 * A section groups OCR-scanned items from a single receipt scan.
 * Think of it as one scanned receipt stored for future reference and reporting.
 */
data class OcrSection(
    val id: String = UUID.randomUUID().toString(),
    /** Label the user gives to this section (e.g. "Grocery 03/09", "Restaurant dinner"). */
    val label: String = "",
    /** Merchant name detected from the receipt. */
    val merchantName: String = "Unknown",
    /** ISO-4217 currency code for this scan. */
    val currencyCode: String = "AMD",
    /** Items scanned from the receipt. */
    val items: List<OcrItem> = emptyList(),
    /** Total amount (may differ from sum of items if receipt has tax/discount). */
    val totalAmount: Double = 0.0,
    /** Detected language(s) used during OCR (e.g. "Armenian, Russian"). */
    val detectedLanguages: String = "",
    /** Raw OCR text preserved for debugging / re-parsing. */
    val rawOcrText: String = "",
    /** Timestamp when the scan was performed. */
    val timestamp: Long = System.currentTimeMillis(),
    /** Optional notes from the user. */
    val notes: String = ""
) {
    /** Computed sum of item prices. */
    val itemsTotal: Double get() = items.sumOf { it.price }
}

/**
 * Aggregated info for a single item across multiple receipts — used in goods reports.
 * Example: "Cheese" bought 3 times totalling 2000 AMD.
 */
data class GoodsReportItem(
    val name: String,
    val count: Int,
    val totalSpent: Double,
    val category: String = ""
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

/**
 * A single AI conversation entry: the user's prompt and the AI's response.
 */
data class AiConversation(
    val id: String = UUID.randomUUID().toString(),
    val prompt: String,
    val response: String,
    /** Display name of the AI provider that generated this response. Nullable for GSON backward compat. */
    val aiModelName: String? = "AI",
    val timestamp: Long = System.currentTimeMillis(),
    /** Date string for grouping, e.g. "2026-03-17". */
    val dateKey: String = isoDateFormat.get()!!.format(Date(System.currentTimeMillis())).substring(0, 10)
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
    /** High-level AI execution mode (Cloud / System / Local / Auto). */
    val aiModePreference: AiModePreference = AiModePreference.AUTO,
    /** Which cloud AI provider to use (Claude, Gemini, ChatGPT, DeepSeek). */
    val cloudAiProvider: CloudAiProviderType = CloudAiProviderType.CLAUDE,
    /** Selected model ID for the active cloud AI provider. Empty = provider default. */
    val cloudAiModel: String = "",
    /** Claude API key for Cloud AI mode. */
    val claudeApiKey: String = "",
    /** Google Gemini API key. */
    val geminiApiKey: String = "",
    /** OpenAI (ChatGPT) API key. */
    val openaiApiKey: String = "",
    /** DeepSeek API key. */
    val deepseekApiKey: String = "",
    /** Active local model ID (from catalog or imported). */
    val activeLocalModelId: String = "",
    /** Whether the local AI setup wizard has been completed. */
    val localAiSetupComplete: Boolean = false,
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
    val lastRateUpdateTimestamp: Long = 0,
    /** Whether the splash/onboarding screen has been shown to the user. */
    val splashShown: Boolean = false,
    // ── Dashboard visibility toggles ──────────────────────────────
    /** Which dashboard cards are visible (by DashboardSection name). Empty = all visible. Nullable for Gson backward compat. */
    val hiddenDashboardSections: List<String>? = emptyList(),
    // ── Soft-delete retention ─────────────────────────────────────
    /** Days to keep soft-deleted transactions before permanent purge. */
    val trashRetentionDays: Int = 30,
    // ── Tag suggestions ───────────────────────────────────────────
    /** Recently used tags for auto-suggest. Nullable for Gson backward compat. */
    val recentTags: List<String>? = emptyList(),
    // ── Offline currency cache ────────────────────────────────────
    /** Cached exchange rates for offline use. Nullable for Gson backward compat. */
    val cachedExchangeRates: Map<String, Double>? = emptyMap(),
    /** Timestamp when cached rates were last updated. */
    val cachedRatesTimestamp: Long = 0
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
