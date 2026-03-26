package com.flowsense.app.service.ai

import com.flowsense.app.data.model.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * On-device AI engine for expense analysis, categorization, and optimization.
 * Uses rule-based heuristics + pattern matching (no cloud API needed).
 */
class AiExpenseEngine {

    // ─── Smart Categorization ──────────────────────────────────────

    /**
     * Known merchant → category mappings. Checked first for precise classification.
     * Merchant names are lowercase; matched as substring against the description.
     */
    private val merchantCategoryMap: Map<String, String> = mapOf(
        // ── Ride-hailing / Taxi ──
        "yandex.go" to "Transportation", "yandex go" to "Transportation",
        "yandex taxi" to "Transportation", "yango" to "Transportation",
        "uber" to "Transportation", "lyft" to "Transportation",
        "bolt" to "Transportation", "gett" to "Transportation",
        "grab" to "Transportation", "didi" to "Transportation",
        "ola" to "Transportation", "rapido" to "Transportation",
        "cabify" to "Transportation", "freenow" to "Transportation",
        "indriver" to "Transportation", "maxim taxi" to "Transportation",
        "gg taxi" to "Transportation",
        // ── Fuel / Gas ──
        "shell" to "Transportation", "chevron" to "Transportation",
        "bp " to "Transportation", "exxon" to "Transportation",
        "lukoil" to "Transportation", "gazprom" to "Transportation",
        "petrol" to "Transportation",
        // ── Airlines / Travel ──
        "airline" to "Transportation", "airways" to "Transportation",
        "flyone" to "Transportation", "ryanair" to "Transportation",
        "wizzair" to "Transportation", "booking.com" to "Transportation",
        "airbnb" to "Rent & Housing", "irctc" to "Transportation",
        // ── Food delivery ──
        "uber eats" to "Food & Dining", "doordash" to "Food & Dining",
        "grubhub" to "Food & Dining", "deliveroo" to "Food & Dining",
        "glovo" to "Food & Dining", "wolt" to "Food & Dining",
        "swiggy" to "Food & Dining", "zomato" to "Food & Dining",
        "yandex eda" to "Food & Dining", "yandex.eda" to "Food & Dining",
        // ── Restaurants / Coffee ──
        "mcdonald" to "Food & Dining", "starbucks" to "Food & Dining",
        "chipotle" to "Food & Dining", "subway" to "Food & Dining",
        "kfc" to "Food & Dining", "domino" to "Food & Dining",
        "pizza hut" to "Food & Dining", "burger king" to "Food & Dining",
        "dunkin" to "Food & Dining", "taco bell" to "Food & Dining",
        "panda express" to "Food & Dining", "wendy" to "Food & Dining",
        "popeyes" to "Food & Dining", "chick-fil-a" to "Food & Dining",
        "five guys" to "Food & Dining", "panera" to "Food & Dining",
        // ── Grocery chains ──
        "walmart" to "Groceries", "target" to "Groceries",
        "costco" to "Groceries", "kroger" to "Groceries",
        "whole foods" to "Groceries", "trader joe" to "Groceries",
        "safeway" to "Groceries", "aldi" to "Groceries",
        "publix" to "Groceries", "bigbasket" to "Groceries",
        "blinkit" to "Groceries", "dmart" to "Groceries",
        // ── E-commerce / Shopping ──
        "amazon" to "Shopping", "ebay" to "Shopping",
        "etsy" to "Shopping", "aliexpress" to "Shopping",
        "flipkart" to "Shopping", "myntra" to "Shopping",
        "wildberries" to "Shopping", "ozon" to "Shopping",
        "best buy" to "Shopping", "apple store" to "Shopping",
        "nike" to "Shopping", "zara" to "Shopping",
        "h&m" to "Shopping", "nordstrom" to "Shopping",
        "macy" to "Shopping", "ikea" to "Rent & Housing",
        // ── Streaming / Entertainment ──
        "netflix" to "Entertainment", "spotify" to "Entertainment",
        "hulu" to "Entertainment", "disney+" to "Entertainment",
        "disney plus" to "Entertainment", "hbo max" to "Entertainment",
        "apple tv" to "Entertainment", "prime video" to "Entertainment",
        "youtube premium" to "Entertainment", "hotstar" to "Entertainment",
        "steam" to "Entertainment", "playstation" to "Entertainment",
        "xbox" to "Entertainment", "nintendo" to "Entertainment",
        "epic games" to "Entertainment",
        // ── Telecom / Utilities ──
        "at&t" to "Bills & Utilities", "verizon" to "Bills & Utilities",
        "t-mobile" to "Bills & Utilities", "comcast" to "Bills & Utilities",
        "jio" to "Bills & Utilities", "airtel" to "Bills & Utilities",
        "vodafone" to "Bills & Utilities", "bsnl" to "Bills & Utilities",
        "beeline" to "Bills & Utilities", "ucom" to "Bills & Utilities",
        "viva-mts" to "Bills & Utilities", "team telecom" to "Bills & Utilities",
        // ── Health / Pharmacy ──
        "cvs" to "Healthcare", "walgreens" to "Healthcare",
        "apollo" to "Healthcare", "medplus" to "Healthcare",
        // ── Education ──
        "udemy" to "Education", "coursera" to "Education",
        "skillshare" to "Education", "linkedin learning" to "Education",
        // ── Yandex services ──
        "yandex.market" to "Shopping", "yandex market" to "Shopping",
        "yandex.lavka" to "Groceries", "yandex lavka" to "Groceries",
        "yandex.plus" to "Entertainment", "yandex plus" to "Entertainment"
    )

    private val categoryKeywords = mapOf(
        "Food & Dining" to listOf(
            "restaurant", "cafe", "coffee", "pizza", "burger", "sushi", "doordash",
            "uber eats", "grubhub", "mcdonald", "starbucks", "chipotle", "subway",
            "dine", "food", "eat", "lunch", "dinner", "breakfast", "taco", "bakery",
            "bar", "pub", "grill", "kitchen", "bistro", "deli", "swiggy", "zomato"
        ),
        "Groceries" to listOf(
            "grocery", "walmart", "target", "costco", "kroger", "whole foods",
            "trader joe", "safeway", "aldi", "publix", "market", "supermarket",
            "fresh", "organic", "produce", "bigbasket", "blinkit", "dmart"
        ),
        "Transportation" to listOf(
            "taxi", "cab", "ride", "gas", "fuel", "parking", "toll", "metro",
            "bus", "train", "airline", "flight", "car wash", "mechanic",
            "oil change", "tire", "transit", "shell", "chevron", "bp", "exxon",
            "rapido", "irctc"
        ),
        "Shopping" to listOf(
            "amazon", "ebay", "etsy", "mall", "store", "shop", "clothing",
            "shoes", "electronics", "best buy", "apple store", "nike", "zara",
            "h&m", "nordstrom", "macy", "outlet", "fashion", "flipkart", "myntra"
        ),
        "Entertainment" to listOf(
            "netflix", "spotify", "hulu", "disney", "movie", "theater", "concert",
            "game", "steam", "playstation", "xbox", "cinema", "ticket", "event",
            "museum", "park", "zoo", "amusement", "bowling", "arcade", "hotstar",
            "prime video", "youtube premium"
        ),
        "Bills & Utilities" to listOf(
            "electric", "water", "gas bill", "internet", "phone", "cable",
            "utility", "at&t", "verizon", "t-mobile", "comcast", "power",
            "sewage", "trash", "waste", "subscription", "insurance",
            "jio", "airtel", "vodafone", "bsnl", "broadband"
        ),
        "Healthcare" to listOf(
            "doctor", "hospital", "pharmacy", "cvs", "walgreens", "medical",
            "dental", "vision", "health", "clinic", "therapy", "prescription",
            "lab", "urgent care", "medicine", "apollo", "medplus"
        ),
        "Education" to listOf(
            "tuition", "school", "university", "college", "course", "udemy",
            "coursera", "book", "textbook", "learning", "class", "training",
            "workshop", "seminar", "certification"
        ),
        "Rent & Housing" to listOf(
            "rent", "mortgage", "apartment", "house", "landlord", "property",
            "maintenance", "repair", "plumber", "electrician", "furniture",
            "home depot", "lowes", "ikea", "cleaning"
        ),
        "Salary" to listOf(
            "salary", "payroll", "wages", "direct deposit", "paycheck", "pay"
        ),
        "Freelance" to listOf(
            "freelance", "contract", "invoice", "client payment", "consulting"
        ),
        "Investment" to listOf(
            "dividend", "interest", "investment", "stock", "return", "yield",
            "capital gain", "portfolio", "mutual fund", "sip"
        )
    )

    /**
     * Categorise a transaction description.
     * @param isExpense Pass false for income transactions — prevents income-only categories
     *   (Salary, Freelance, Investment) from matching POS / expense descriptions that happen
     *   to contain keywords like "pay" or "salary" (e.g. "payment at Paytm").
     * @param userCategoryNames Names of user-created categories. When a category name appears
     *   as a substring in the description it takes priority over hardcoded keyword scoring,
     *   so that custom categories like "ATM" match SMS text such as "ATM cash at ATM AEB …".
     */
    fun categorize(
        description: String,
        isExpense: Boolean = true,
        userCategoryNames: List<String> = emptyList()
    ): String {
        val lowerDesc = description.lowercase()
        val incomeOnlyCategories = setOf("Salary", "Freelance", "Investment")

        // Pass 0 – known merchant lookup (highest precision)
        for ((merchant, category) in merchantCategoryMap) {
            if (isExpense && category in incomeOnlyCategories) continue
            if (lowerDesc.contains(merchant)) return category
        }

        // Pass 0.5 – user-created category name matching (before hardcoded keywords)
        // Longest matching name wins (more specific). Minimum 2 chars to avoid noise.
        var bestUserMatch: String? = null
        var bestUserScore = 0
        for (name in userCategoryNames) {
            val lowerName = name.lowercase()
            if (lowerName.length >= 2 && lowerDesc.contains(lowerName)) {
                if (lowerName.length > bestUserScore) {
                    bestUserScore = lowerName.length
                    bestUserMatch = name
                }
            }
        }
        if (bestUserMatch != null) return bestUserMatch

        // Pass 1 – exact substring match, scored by keyword length (longer = more specific)
        var bestMatch: String? = null
        var bestScore = 0
        for ((category, keywords) in categoryKeywords) {
            if (isExpense && category in incomeOnlyCategories) continue
            var score = 0
            for (keyword in keywords) {
                if (lowerDesc.contains(keyword)) score += keyword.length
            }
            if (score > bestScore) { bestScore = score; bestMatch = category }
        }
        if (bestMatch != null) return bestMatch

        // Pass 2 – word-token overlap: tokenise description, check prefix matches
        val tokens = lowerDesc.split(Regex("""[\s\-_/.,;:]+""")).filter { it.length >= 3 }
        var fallbackMatch: String? = null
        var fallbackScore = 0
        for ((category, keywords) in categoryKeywords) {
            // Skip income-only categories for fallback (unlikely for POS merchants)
            if (category in listOf("Salary", "Freelance", "Investment")) continue
            var score = 0
            for (token in tokens) {
                for (keyword in keywords) {
                    val minLen = minOf(token.length, keyword.length, 5)
                    if (minLen >= 3 && (token.startsWith(keyword.take(minLen)) ||
                                        keyword.startsWith(token.take(minLen)))) {
                        score += minLen
                    }
                }
            }
            if (score > fallbackScore) { fallbackScore = score; fallbackMatch = category }
        }
        if (fallbackMatch != null && fallbackScore >= 3) return fallbackMatch

        // Pass 3 – structural heuristics for unrecognised merchants
        return when {
            // Armenian/CIS/regional store-type name patterns
            lowerDesc.any { it.code in 0x0530..0x058F } -> "Shopping" // Armenian Unicode block
            lowerDesc.matches(Regex(""".*\b(llc|ltd|inc|corp|gmbh|srl|sas|ojsc|cjsc)\b.*""")) -> "Shopping"
            lowerDesc.contains("market") || lowerDesc.contains("shop") ||
                lowerDesc.contains("store") || lowerDesc.contains("mart") -> "Shopping"
            lowerDesc.contains("fee") || lowerDesc.contains("charge") ||
                lowerDesc.contains("bill") -> "Bills & Utilities"
            else -> "Shopping"   // POS/card transactions with unknown merchant → Shopping beats Other
        }
    }

    fun detectTransactionType(description: String, amount: Double): TransactionType {
        val lowerDesc = description.lowercase()
        val incomeKeywords = listOf(
            "salary", "payroll", "deposit", "refund", "cashback", "reimbursement",
            "dividend", "interest earned", "payment received", "income", "credited",
            "freelance", "bonus", "commission", "transfer in", "received"
        )
        return if (incomeKeywords.any { lowerDesc.contains(it) } || amount < 0) {
            TransactionType.INCOME
        } else {
            TransactionType.EXPENSE
        }
    }

    // ─── Optimization Suggestions ──────────────────────────────────

    fun generateSuggestions(
        transactions: List<Transaction>,
        budgets: List<Budget>,
        currencyCode: String = "USD",
        categories: List<Category> = emptyList()
    ): List<AiSuggestion> {
        if (transactions.isEmpty()) return emptyList()
        val sym = currencyInfoFor(currencyCode).symbol
        val suggestions = mutableListOf<AiSuggestion>()
        val expenses = transactions.filter { it.type == TransactionType.EXPENSE }
        val now = System.currentTimeMillis()
        val thirtyDaysAgo = now - 30L * 24 * 60 * 60 * 1000
        val recentExpenses = expenses.filter { it.timestamp > thirtyDaysAgo }

        suggestions.addAll(detectHighSpendingCategories(recentExpenses, sym))
        suggestions.addAll(detectSubscriptionOptimizations(recentExpenses, sym))
        suggestions.addAll(detectSpendingSpikes(expenses, sym))
        suggestions.addAll(detectBudgetOverruns(recentExpenses, budgets, sym, categories))
        suggestions.addAll(detectDiningPatterns(recentExpenses, sym))
        suggestions.addAll(detectWeekendSpending(recentExpenses, sym))
        suggestions.addAll(detectSavingsPotential(recentExpenses, transactions))

        return suggestions.take(10)
    }

    private fun detectHighSpendingCategories(expenses: List<Transaction>, sym: String): List<AiSuggestion> {
        val suggestions = mutableListOf<AiSuggestion>()
        val totalSpend = expenses.sumOf { it.amount }
        if (totalSpend == 0.0) return suggestions
        val categorySpend = expenses.groupBy { it.category }
            .mapValues { it.value.sumOf { t -> t.amount } }
            .entries.sortedByDescending { it.value }
        for ((category, amount) in categorySpend) {
            val percentage = (amount / totalSpend * 100).roundToInt()
            if (percentage > 35) {
                suggestions.add(AiSuggestion(
                    title = "High spending on $category",
                    description = "You're spending $percentage% of your budget on $category " +
                            "($sym${String.format("%.2f", amount)} this month). Consider setting " +
                            "a budget limit and finding alternatives.",
                    potentialSaving = amount * 0.2, category = category,
                    priority = SuggestionPriority.HIGH
                ))
            }
        }
        return suggestions
    }

    private fun detectSubscriptionOptimizations(expenses: List<Transaction>, sym: String): List<AiSuggestion> {
        val suggestions = mutableListOf<AiSuggestion>()
        val possibleSubs = expenses.filter {
            it.category == "Entertainment" || it.category == "Bills & Utilities"
        }
        val merchantCounts = possibleSubs
            .filter { it.merchantName.isNotEmpty() }
            .groupBy { it.merchantName.lowercase() }
            .filter { it.value.size > 1 }
        if (merchantCounts.size > 3) {
            val totalSubCost = merchantCounts.values.flatten().sumOf { it.amount }
            suggestions.add(AiSuggestion(
                title = "Review your subscriptions",
                description = "You have ${merchantCounts.size} recurring subscriptions totaling " +
                        "$sym${String.format("%.2f", totalSubCost)}. Consider consolidating or canceling unused ones.",
                potentialSaving = totalSubCost * 0.25, category = "Entertainment",
                priority = SuggestionPriority.MEDIUM
            ))
        }
        return suggestions
    }

    private fun detectSpendingSpikes(expenses: List<Transaction>, sym: String): List<AiSuggestion> {
        val suggestions = mutableListOf<AiSuggestion>()
        val now = System.currentTimeMillis()
        val oneWeekAgo = now - 7L * 24 * 60 * 60 * 1000
        val twoWeeksAgo = now - 14L * 24 * 60 * 60 * 1000
        val thisWeek = expenses.filter { it.timestamp > oneWeekAgo }.sumOf { it.amount }
        val lastWeek = expenses.filter { it.timestamp in twoWeeksAgo..oneWeekAgo }.sumOf { it.amount }
        if (lastWeek > 0 && thisWeek > lastWeek * 1.5) {
            suggestions.add(AiSuggestion(
                title = "Spending spike detected",
                description = "This week's spending ($sym${String.format("%.2f", thisWeek)}) is " +
                        "${((thisWeek / lastWeek - 1) * 100).roundToInt()}% higher than last week.",
                potentialSaving = thisWeek - lastWeek, category = "General",
                priority = SuggestionPriority.HIGH
            ))
        }
        return suggestions
    }

    private fun detectBudgetOverruns(expenses: List<Transaction>, budgets: List<Budget>, sym: String, categories: List<Category> = emptyList()): List<AiSuggestion> {
        val suggestions = mutableListOf<AiSuggestion>()
        for (budget in budgets) {
            // Resolve the budget's categoryId (UUID) to a human-readable category name.
            // Transactions store category by name, so we must compare against the resolved name.
            val categoryName = categories.find { it.id == budget.categoryId }?.name ?: budget.categoryId
            val spent = expenses.filter { it.category == categoryName }.sumOf { it.amount }
            if (spent > budget.monthlyLimit * budget.alertThreshold) {
                val percentage = (spent / budget.monthlyLimit * 100).roundToInt()
                suggestions.add(AiSuggestion(
                    title = "Budget alert: $categoryName",
                    description = "You've used $percentage% of your $sym${String.format("%.2f", budget.monthlyLimit)} budget.",
                    potentialSaving = spent - budget.monthlyLimit, category = categoryName,
                    priority = if (spent > budget.monthlyLimit) SuggestionPriority.HIGH else SuggestionPriority.MEDIUM
                ))
            }
        }
        return suggestions
    }

    private fun detectDiningPatterns(expenses: List<Transaction>, sym: String): List<AiSuggestion> {
        val suggestions = mutableListOf<AiSuggestion>()
        val dining = expenses.filter { it.category == "Food & Dining" }
        if (dining.size > 10) {
            val total = dining.sumOf { it.amount }
            val avgMeal = total / dining.size
            suggestions.add(AiSuggestion(
                title = "Dining out frequency",
                description = "You've dined out ${dining.size} times recently, averaging " +
                        "$sym${String.format("%.2f", avgMeal)} per meal. Cooking at home 2 more " +
                        "days per week could save significant money.",
                potentialSaving = avgMeal * 8, category = "Food & Dining",
                priority = SuggestionPriority.MEDIUM
            ))
        }
        return suggestions
    }

    private fun detectWeekendSpending(expenses: List<Transaction>, sym: String): List<AiSuggestion> {
        val suggestions = mutableListOf<AiSuggestion>()
        val cal = Calendar.getInstance()
        val weekend = expenses.filter {
            cal.timeInMillis = it.timestamp
            cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY || cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
        }
        val weekday = expenses.filter {
            cal.timeInMillis = it.timestamp
            val d = cal.get(Calendar.DAY_OF_WEEK)
            d != Calendar.SATURDAY && d != Calendar.SUNDAY
        }
        if (weekend.isNotEmpty() && weekday.isNotEmpty()) {
            val avgWeekend = weekend.sumOf { it.amount } / (weekend.size.coerceAtLeast(1))
            val avgWeekday = weekday.sumOf { it.amount } / (weekday.size.coerceAtLeast(1))
            if (avgWeekend > avgWeekday * 2) {
                suggestions.add(AiSuggestion(
                    title = "Weekend spending is high",
                    description = "Your average weekend transaction ($sym${String.format("%.2f", avgWeekend)}) " +
                            "is ${(avgWeekend / avgWeekday).roundToInt()}x higher than weekday ($sym${String.format("%.2f", avgWeekday)}).",
                    potentialSaving = (avgWeekend - avgWeekday) * 8, category = "General",
                    priority = SuggestionPriority.LOW
                ))
            }
        }
        return suggestions
    }

    private fun detectSavingsPotential(expenses: List<Transaction>, all: List<Transaction>): List<AiSuggestion> {
        val suggestions = mutableListOf<AiSuggestion>()
        val now = System.currentTimeMillis()
        val thirtyDaysAgo = now - 30L * 24 * 60 * 60 * 1000
        val recentIncome = all.filter { it.type == TransactionType.INCOME && it.timestamp > thirtyDaysAgo }.sumOf { it.amount }
        val recentSpend = expenses.filter { it.timestamp > thirtyDaysAgo }.sumOf { it.amount }
        if (recentIncome > 0) {
            val savingsRate = (1 - recentSpend / recentIncome) * 100
            if (savingsRate < 20) {
                suggestions.add(AiSuggestion(
                    title = "Improve your savings rate",
                    description = "Your savings rate is ${savingsRate.roundToInt()}%. " +
                            "Experts recommend at least 20%. Try reducing discretionary spending.",
                    potentialSaving = recentIncome * 0.2 - (recentIncome - recentSpend),
                    category = "General",
                    priority = if (savingsRate < 10) SuggestionPriority.HIGH else SuggestionPriority.MEDIUM
                ))
            }
        }
        return suggestions
    }

    // ─── Report Generation ─────────────────────────────────────────

    fun generateReport(
        transactions: List<Transaction>,
        period: ReportPeriod,
        startDate: Long,
        endDate: Long,
        currencyCode: String = "USD"
    ): ExpenseReport {
        val periodTransactions = transactions.filter { it.timestamp in startDate..endDate }
        val expenses = periodTransactions.filter { it.type == TransactionType.EXPENSE }
        val income = periodTransactions.filter { it.type == TransactionType.INCOME }
        val totalExpenses = expenses.sumOf { it.amount }
        val totalIncome = income.sumOf { it.amount }

        val categoryBreakdown = expenses
            .groupBy { it.category }
            .mapValues { it.value.sumOf { t -> t.amount } }

        val topExpenses = expenses.sortedByDescending { it.amount }.take(5)

        // Previous period comparison
        val periodLength = endDate - startDate
        val prevStart = startDate - periodLength
        val prevEnd = startDate
        val prevExpenses = transactions
            .filter { it.type == TransactionType.EXPENSE && it.timestamp in prevStart..prevEnd }
            .sumOf { it.amount }
        val comparison = if (prevExpenses > 0) (totalExpenses - prevExpenses) / prevExpenses * 100 else 0.0

        val days = ((endDate - startDate) / (24 * 60 * 60 * 1000.0)).coerceAtLeast(1.0)
        val avgDaily = totalExpenses / days

        // Top merchants
        val topMerchants = expenses
            .filter { it.merchantName.isNotEmpty() }
            .groupBy { it.merchantName }
            .mapValues { it.value.sumOf { t -> t.amount } }
            .entries.sortedByDescending { it.value }
            .take(5)
            .associate { it.key to it.value }

        // Day-of-week spending
        val cal = Calendar.getInstance()
        val dayNames = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        val dayOfWeekSpending = mutableMapOf<String, Double>()
        for (e in expenses) {
            cal.timeInMillis = e.timestamp
            val dayName = dayNames[cal.get(Calendar.DAY_OF_WEEK) - 1]
            dayOfWeekSpending[dayName] = (dayOfWeekSpending[dayName] ?: 0.0) + e.amount
        }

        // Source breakdown
        val sourceBreakdown = periodTransactions
            .groupBy { it.source.name }
            .mapValues { it.value.size }

        // Spending trend (insight text)
        val insight = buildInsight(totalExpenses, totalIncome, comparison, categoryBreakdown, topMerchants, currencyCode)

        // Group period transactions by date string ("yyyy-MM-dd") for date-based browsing
        val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val transactionsByDate: Map<String, List<Transaction>> = periodTransactions
            .sortedByDescending { it.timestamp }
            .groupBy { dateFormatter.format(Date(it.timestamp)) }

        return ExpenseReport(
            periodType = period,
            startDate = startDate,
            endDate = endDate,
            totalExpenses = totalExpenses,
            totalIncome = totalIncome,
            netBalance = totalIncome - totalExpenses,
            categoryBreakdown = categoryBreakdown,
            topExpenses = topExpenses,
            comparisonWithPrevious = comparison,
            averageDailySpend = avgDaily,
            topMerchants = topMerchants,
            dayOfWeekSpending = dayOfWeekSpending,
            sourceBreakdown = sourceBreakdown,
            aiInsight = insight,
            transactionCount = periodTransactions.size,
            transactionsByDate = transactionsByDate
        )
    }

    private fun buildInsight(
        expenses: Double, income: Double, comparison: Double,
        categories: Map<String, Double>, merchants: Map<String, Double>,
        currencyCode: String = "USD"
    ): String {
        val sym = currencyInfoFor(currencyCode).symbol
        val parts = mutableListOf<String>()

        // Spending direction
        if (comparison != 0.0) {
            val dir = if (comparison > 0) "increased" else "decreased"
            parts.add("Spending $dir ${abs(comparison).roundToInt()}% vs previous period.")
        }

        // Savings rate
        if (income > 0) {
            val rate = ((income - expenses) / income * 100).roundToInt()
            parts.add("Savings rate: $rate%.")
        }

        // Top category
        val topCat = categories.entries.maxByOrNull { it.value }
        if (topCat != null && expenses > 0) {
            val pct = (topCat.value / expenses * 100).roundToInt()
            parts.add("Biggest category: ${topCat.key} ($pct%).")
        }

        // Top merchant
        val topMerch = merchants.entries.maxByOrNull { it.value }
        if (topMerch != null) {
            parts.add("Most spent at: ${topMerch.key} ($sym${String.format("%.2f", topMerch.value)}).")
        }

        return parts.joinToString(" ")
    }

    // ─── Deep Transaction Analysis ─────────────────────────────────

    /**
     * Produces a detailed multi-paragraph analysis of the supplied transactions.
     * When [category] is non-null only transactions in that category are examined.
     */
    fun generateAnalysis(
        transactions: List<Transaction>,
        startDate: Long,
        endDate: Long,
        currencyCode: String = "USD",
        category: String? = null
    ): String {
        val sym = currencyInfoFor(currencyCode).symbol
        val dateFormat = SimpleDateFormat("MMM dd", Locale.US)
        val rangeLabel = "${dateFormat.format(Date(startDate))} – ${dateFormat.format(Date(endDate))}"

        val filtered = transactions.filter { t ->
            t.timestamp in startDate..endDate &&
                (category == null || t.category == category)
        }

        if (filtered.isEmpty()) {
            return if (category != null)
                "No transactions found in \"$category\" for $rangeLabel."
            else
                "No transactions found for $rangeLabel."
        }

        val expenses = filtered.filter { it.type == TransactionType.EXPENSE }
        val income   = filtered.filter { it.type == TransactionType.INCOME }
        val totalExpense = expenses.sumOf { it.amount }
        val totalIncome  = income.sumOf  { it.amount }
        val days = ((endDate - startDate) / (24 * 60 * 60 * 1000.0)).coerceAtLeast(1.0)

        val sections = mutableListOf<String>()

        // ── 1. Overview ──────────────────────────────────────────
        val header = buildString {
            append("Analysis for $rangeLabel")
            if (category != null) append(" (category: $category)")
            append("\n${filtered.size} transactions found.")
        }
        sections.add(header)

        val overview = buildString {
            append("OVERVIEW\n")
            append("Total expenses: $sym${fmt(totalExpense)}\n")
            append("Total income: $sym${fmt(totalIncome)}\n")
            append("Net: $sym${fmt(totalIncome - totalExpense)}\n")
            append("Daily average spend: $sym${fmt(totalExpense / days)}")
        }
        sections.add(overview)

        // ── 2. Category breakdown (skip when already filtered to one category)
        if (category == null && expenses.isNotEmpty()) {
            val catBreakdown = expenses.groupBy { it.category }
                .mapValues { it.value.sumOf { t -> t.amount } }
                .entries.sortedByDescending { it.value }
            val sb = StringBuilder("SPENDING BY CATEGORY\n")
            for ((cat, amt) in catBreakdown) {
                val pct = if (totalExpense > 0) (amt / totalExpense * 100).roundToInt() else 0
                sb.append("  $cat: $sym${fmt(amt)} ($pct%)\n")
            }
            sections.add(sb.toString().trimEnd())
        }

        // ── 3. Top transactions ──────────────────────────────────
        val top = expenses.sortedByDescending { it.amount }.take(5)
        if (top.isNotEmpty()) {
            val sb = StringBuilder("LARGEST EXPENSES\n")
            for (t in top) {
                val d = dateFormat.format(Date(t.timestamp))
                val desc = t.description.take(30)
                sb.append("  $d  $sym${fmt(t.amount)}  $desc\n")
            }
            sections.add(sb.toString().trimEnd())
        }

        // ── 4. Spending trend (week-over-week) ───────────────────
        if (days >= 14) {
            val mid = startDate + ((endDate - startDate) / 2)
            val firstHalf = expenses.filter { it.timestamp < mid }.sumOf { it.amount }
            val secondHalf = expenses.filter { it.timestamp >= mid }.sumOf { it.amount }
            if (firstHalf > 0) {
                val change = ((secondHalf - firstHalf) / firstHalf * 100).roundToInt()
                val dir = if (change >= 0) "increased" else "decreased"
                sections.add("TREND\nSpending $dir by ${abs(change)}% in the second half of the period compared to the first half.")
            }
        }

        // ── 5. Merchant concentration ────────────────────────────
        val merchants = expenses.filter { it.merchantName.isNotEmpty() }
            .groupBy { it.merchantName }
            .mapValues { it.value.sumOf { t -> t.amount } }
            .entries.sortedByDescending { it.value }
        if (merchants.isNotEmpty()) {
            val sb = StringBuilder("TOP MERCHANTS\n")
            for ((m, amt) in merchants.take(5)) {
                sb.append("  $m: $sym${fmt(amt)}\n")
            }
            sections.add(sb.toString().trimEnd())
        }

        // ── 6. Day-of-week pattern ───────────────────────────────
        if (expenses.size >= 7) {
            val cal = Calendar.getInstance()
            val dayNames = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
            val byDay = mutableMapOf<String, Double>()
            for (e in expenses) {
                cal.timeInMillis = e.timestamp
                val name = dayNames[cal.get(Calendar.DAY_OF_WEEK) - 1]
                byDay[name] = (byDay[name] ?: 0.0) + e.amount
            }
            val peak = byDay.maxByOrNull { it.value }
            val low  = byDay.minByOrNull { it.value }
            if (peak != null && low != null && peak.key != low.key) {
                sections.add("DAY-OF-WEEK PATTERN\nHighest spending day: ${peak.key} ($sym${fmt(peak.value)}). " +
                        "Lowest: ${low.key} ($sym${fmt(low.value)}).")
            }
        }

        // ── 7. Recommendations ───────────────────────────────────
        val recs = mutableListOf<String>()
        if (category == null) {
            val topCat = expenses.groupBy { it.category }
                .mapValues { it.value.sumOf { t -> t.amount } }
                .maxByOrNull { it.value }
            if (topCat != null && totalExpense > 0) {
                val pct = (topCat.value / totalExpense * 100).roundToInt()
                if (pct > 30) recs.add("${topCat.key} dominates at $pct% — review whether all purchases were necessary.")
            }
        }
        if (totalIncome > 0) {
            val savingsRate = ((totalIncome - totalExpense) / totalIncome * 100).roundToInt()
            if (savingsRate < 20) recs.add("Savings rate is only $savingsRate%. Aim for at least 20%.")
            else recs.add("Good savings rate of $savingsRate%.")
        }
        val avgTx = if (expenses.isNotEmpty()) totalExpense / expenses.size else 0.0
        if (avgTx > 0) recs.add("Average transaction size: $sym${fmt(avgTx)}.")
        if (merchants.size >= 2) {
            val topM = merchants.first()
            val topMPct = if (totalExpense > 0) (topM.value / totalExpense * 100).roundToInt() else 0
            if (topMPct > 25) recs.add("${topM.key} accounts for $topMPct% of spending — check for alternatives or loyalty rewards.")
        }

        if (recs.isNotEmpty()) {
            val sb = StringBuilder("RECOMMENDATIONS\n")
            recs.forEachIndexed { i, r -> sb.append("  ${i + 1}. $r\n") }
            sections.add(sb.toString().trimEnd())
        }

        return sections.joinToString("\n\n")
    }

    private fun fmt(v: Double): String = String.format("%,.2f", v)

    // ─── Expense Reduction Tips ──────────────────────────────────

    /**
     * Generates actionable tips to reduce expenses based on the report data.
     * Analyzes category breakdown, spending patterns, and comparison with previous period.
     */
    fun generateExpenseReductionTips(report: ExpenseReport, currencyCode: String = "AMD"): List<String> {
        val sym = currencyInfoFor(currencyCode).symbol
        val tips = mutableListOf<String>()

        // 1. Identify top spending category and suggest reduction
        val sortedCategories = report.categoryBreakdown.entries.sortedByDescending { it.value }
        val topCat = sortedCategories.firstOrNull()
        if (topCat != null && report.totalExpenses > 0) {
            val pct = (topCat.value / report.totalExpenses * 100).roundToInt()
            if (pct > 30) {
                val tenPct = topCat.value * 0.1
                tips.add("Your biggest expense is ${topCat.key} ($pct% of total). Try reducing it by 10% to save ~$sym${String.format("%.0f", tenPct)} per period.")
            }
        }

        // 2. Dining/food spending tip
        val foodCategories = listOf("Food & Dining", "Groceries", "Restaurant", "Cafe")
        val foodSpend = report.categoryBreakdown.filter { it.key in foodCategories }.values.sum()
        if (foodSpend > 0 && report.totalExpenses > 0) {
            val foodPct = (foodSpend / report.totalExpenses * 100).roundToInt()
            if (foodPct > 25) {
                tips.add("Food expenses account for $foodPct% of your spending. Meal planning and cooking at home can reduce food costs by 30-40%.")
            }
        }

        // 3. Spending increase warning
        if (report.comparisonWithPrevious > 10) {
            tips.add("Your spending increased ${report.comparisonWithPrevious.roundToInt()}% vs the previous period. Review recent transactions to identify unexpected expenses.")
        }

        // 4. Weekend vs weekday spending
        val weekendDays = listOf("Sat", "Sun")
        val weekendSpend = report.dayOfWeekSpending.filter { it.key in weekendDays }.values.sum()
        val weekdaySpend = report.dayOfWeekSpending.filter { it.key !in weekendDays }.values.sum()
        if (weekendSpend > 0 && weekdaySpend > 0) {
            val avgWeekend = weekendSpend / 2
            val avgWeekday = weekdaySpend / 5
            if (avgWeekend > avgWeekday * 1.5) {
                tips.add("Weekend spending is ${String.format("%.0f", avgWeekend / avgWeekday)}x higher than weekdays. Setting a weekend budget can help control impulse purchases.")
            }
        }

        // 5. Multiple merchants in same category
        if (report.topMerchants.size > 3) {
            val topMerchant = report.topMerchants.entries.maxByOrNull { it.value }
            if (topMerchant != null) {
                tips.add("You frequently spend at ${topMerchant.key}. Check if loyalty programs or alternatives could save you money.")
            }
        }

        // 6. Savings rate advice
        if (report.totalIncome > 0) {
            val savingsRate = ((report.totalIncome - report.totalExpenses) / report.totalIncome * 100)
            if (savingsRate < 20) {
                val targetSaving = report.totalIncome * 0.2 - (report.totalIncome - report.totalExpenses)
                if (targetSaving > 0) {
                    tips.add("To reach the recommended 20% savings rate, try to cut ~$sym${String.format("%.0f", targetSaving)} from non-essential categories like Entertainment or Shopping.")
                }
            }
        }

        // 7. Subscription audit tip (if recurring patterns exist)
        val hasSubscriptions = report.categoryBreakdown.containsKey("Bills & Utilities") ||
            report.categoryBreakdown.containsKey("Entertainment")
        if (hasSubscriptions) {
            tips.add("Review your subscriptions and recurring bills. Canceling unused services is one of the easiest ways to cut expenses.")
        }

        // 8. General best practice
        if (tips.size < 2) {
            tips.add("Track every expense and set category budgets. People who actively track spending save 15-20% more on average.")
        }

        return tips.take(4) // Return at most 4 tips to avoid overwhelming
    }

    // ─── SMS / Notification Parsing ────────────────────────────────

    data class ParsedTransaction(
        val amount: Double,
        val description: String,
        val isExpense: Boolean,
        val merchantName: String,
        val currency: String = "",
        /** Last 4 digits of the card involved, if detected (e.g. "2968"). */
        val cardLastFour: String = ""
    )

    /**
     * Parse a banking SMS or notification text to extract transaction info.
     * Supports: US banks, Indian banks (INR/UPI), Armenian banks (AMD),
     * European banks (EUR/GBP), and generic international formats.
     */
    /**
     * Keywords that identify a pre-authorisation hold (not a real debit).
     * Pre-auth messages should be silently discarded — the actual charge
     * arrives in a separate "approved" / "completion" message.
     */
    private val preAuthKeywords = listOf(
        "pre-auth", "pre auth", "preauth", "pre-authorization", "pre authorization",
        "preauthorization", "authorisation hold", "authorization hold", "auth hold",
        "card authorised", "card authorized",
        "temporary hold", "temp hold", "pending authorization", "pending authorisation",
        // Armenian POS terminal pre-auth strings
        "Նախաէttv", "авторизация", "предавторизация"
    )

    fun parseFinancialMessage(
        message: String,
        customIncomeKeywords: List<String> = emptyList(),
        customExpenseKeywords: List<String> = emptyList()
    ): ParsedTransaction? {
        try {
            val lowerMsg = message.lowercase()

            // Reject pre-auth / authorisation-hold messages — they are not real transactions
            if (preAuthKeywords.any { lowerMsg.contains(it.lowercase()) }) {
                return null
            }

            val oneLine = message.replace(Regex("""\s*\n\s*"""), " ").trim()

            // ── CARD LAST-4 EXTRACTION ─────────────────────
            // Detect masked card patterns like "************2968", "457890******2968", "4083***1982"
            val cardLast4 = Regex("""\d{0,6}\*{2,12}(\d{4})\b""")
                .find(oneLine)?.groupValues?.get(1) ?: ""

            // ── TRY SPECIFIC BANK FORMATS FIRST ─────────────

            // 1) Armenian/CIS bank: "Purchase approved 17063.12 AMD, 457890******2968 ..."
            //    Pattern: TYPE approved AMOUNT CURRENCY, CARD DATE TIME MERCHANT authcode CODE
            val armenianPattern1 = Regex(
                """(Purchase|ATM Cash|Mail Order|POS|Online|E-commerce)\s+(?:completion\s+)?approved\s+([\d,]+\.?\d*)\s+([A-Z]{3})[,\s]+\d{4,6}\*{2,6}\d{2,6}\s+[\d.]+\s+[\d:]+\s+(.+?)\s+authcode""",
                RegexOption.IGNORE_CASE
            )
            armenianPattern1.find(oneLine)?.let { m ->
                val txType = m.groupValues[1]
                val amt = m.groupValues[2].replace(",", "").toDoubleOrNull() ?: return@let
                val cur = m.groupValues[3]
                val merchant = m.groupValues[4].trim()
                val isExpense = !txType.equals("credit", ignoreCase = true)
                return ParsedTransaction(amt, "${txType.trim()} at $merchant", isExpense, merchant, cur, cardLast4)
            }

            // 2) Armenian: "Purchase completion approved CARD DATE TIME AMOUNT AMD MERCHANT authcode"
            val armenianPattern2 = Regex(
                """(Purchase|ATM|Mail Order|POS)\s+(?:completion\s+)?approved\s+\d{4,6}\*{2,6}\d{2,6}\s+[\d.]+\s+[\d:]+\s+([\d,]+\.?\d*)\s+([A-Z]{3})\s+(.+?)\s+authcode""",
                RegexOption.IGNORE_CASE
            )
            armenianPattern2.find(oneLine)?.let { m ->
                val txType = m.groupValues[1]
                val amt = m.groupValues[2].replace(",", "").toDoubleOrNull() ?: return@let
                val cur = m.groupValues[3]
                val merchant = m.groupValues[4].trim()
                return ParsedTransaction(amt, "${txType.trim()} at $merchant", true, merchant, cur, cardLast4)
            }

            // 3) Armenian multi-line CREDIT/DEBIT ACCOUNT format:
            //    CREDIT ACCOUNT\n14,950.00 AMD\n4083***1982,\nMERCHANT\nDATE TIME\nBALANCE: ...
            val creditAccountPattern = Regex(
                """(CREDIT|DEBIT)\s+ACCOUNT\s+([\d,]+\.?\d*)\s+([A-Z]{3})\s+\d{4}\*{2,4}\d{4}[,\s]+(.+?)(?:\d{2}\.\d{2}\.\d{2,4}|\s+BALANCE)""",
                RegexOption.IGNORE_CASE
            )
            creditAccountPattern.find(oneLine)?.let { m ->
                val txType = m.groupValues[1]
                val amt = m.groupValues[2].replace(",", "").toDoubleOrNull() ?: return@let
                val cur = m.groupValues[3]
                val merchant = m.groupValues[4].trim().trimEnd(',').trim()
                val isExpense = txType.equals("DEBIT", ignoreCase = true)
                val label = if (isExpense) "Debit" else "Credit"
                return ParsedTransaction(amt, "$label from $merchant", isExpense, merchant, cur, cardLast4)
            }

            // ── GENERIC INTERNATIONAL AMOUNT EXTRACTION ─────

            // Try to find amount with explicit currency (handles AMD, USD, EUR, GBP, INR, etc.)
            var amount: Double? = null
            var currency = ""

            // Ordered from most specific to least specific
            val amountPatterns = listOf(
                // "12 000.00 AMD" — amount with space-thousands-separator followed by currency
                Regex("""([0-9][0-9\s,]*\.[0-9]{1,2})\s+([A-Z]{3})\b"""),
                // "17063.12 AMD" or "21.70 USD" — amount followed by 3-letter currency
                Regex("""([\d,]+\.\d{1,2})\s+([A-Z]{3})"""),
                // "AMD 17,063.12" or "INR 500" — currency before amount
                Regex("""([A-Z]{3})\s+([\d,]+\.?\d*)"""),
                // "$500.00" or "$ 500"
                Regex("""\$\s?([\d,]+\.?\d*)"""),
                // "Rs.500" or "₹500" or "INR 500"
                Regex("""(?:rs\.?|inr|₹)\s?([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE),
                // "EUR 20.50" or "€20.50" or "£20.50"
                Regex("""(?:eur|€|gbp|£)\s?([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE),
                // "amount: 500.00" or "amt 500"
                Regex("""(?:amount|amt|total)[:\s]*(?:[^\d])?([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE),
                // "charged 500.00" / "debited 500" / "credited 500"
                Regex("""(?:charged|debited|credited|paid|spent|received|withdrawn)[:\s]*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE),
                // Standalone decimal number (last resort): "14,950.00"
                Regex("""(?:^|\s)([\d,]+\.\d{2})(?:\s|$)""")
            )

            for (pattern in amountPatterns) {
                val match = pattern.find(message)
                if (match != null) {
                    // Figure out which group is the amount vs currency
                    val groups = match.groupValues.drop(1).filter { it.isNotEmpty() }
                    for (g in groups) {
                        val cleaned = g.replace(",", "")
                        val asNum = cleaned.toDoubleOrNull()
                        if (asNum != null && asNum > 0) {
                            amount = asNum
                        } else if (g.matches(Regex("[A-Z]{3}"))) {
                            currency = g
                        }
                    }
                    if (amount != null && amount > 0) break
                    amount = null
                }
            }

            if (amount == null || amount <= 0) return null

            // Infer ISO currency code from well-known symbols if not already captured
            if (currency.isEmpty()) {
                currency = when {
                    message.contains("$")   -> "USD"
                    message.contains("€")   -> "EUR"
                    message.contains("£")   -> "GBP"
                    message.contains("֏")   -> "AMD"
                    message.contains("₹") || message.lowercase().contains("rs.") -> "INR"
                    message.contains("₽")   -> "RUB"
                    message.contains("₺")   -> "TRY"
                    message.contains("₩")   -> "KRW"
                    message.contains("¥")   -> "JPY"
                    else -> ""
                }
            }

            // ── EXPENSE VS INCOME ───────────────────────

            val builtInExpenseWords = listOf(
                "purchase", "atm cash", "atm", "mail order", "pos",
                "charged", "debited", "spent", "paid", "withdrawal",
                "sent", "debit", "withdrawn", "payment of", "used at",
                "debit account", "e-commerce", "online purchase"
            )
            val builtInIncomeWords = listOf(
                "credit account", "credited", "received", "deposit", "refund",
                "cashback", "transfer to your", "added to", "reversed",
                "salary", "income", "reward"
            )
            // Merge custom keywords (checked first for user priority)
            val allIncomeWords = customIncomeKeywords + builtInIncomeWords
            val allExpenseWords = customExpenseKeywords + builtInExpenseWords

            val isExpense = when {
                allIncomeWords.any { it.isNotEmpty() && lowerMsg.contains(it.lowercase()) } -> false
                allExpenseWords.any { it.isNotEmpty() && lowerMsg.contains(it.lowercase()) } -> true
                lowerMsg.contains("approved") && !lowerMsg.contains("credit account") -> true
                else -> true // Default to expense
            }

            // ── MERCHANT EXTRACTION ─────────────────────

            var merchant = ""

            // Armenian/CIS: text between time (HH:MM) and "authcode"
            val armenianMerchant = Regex("""\d{2}:\d{2}\s+(.+?)\s+authcode""", RegexOption.IGNORE_CASE)
            armenianMerchant.find(oneLine)?.let {
                val m = it.groupValues[1].trim()
                // Remove amount+currency if merchant starts with them
                val cleaned = m.replace(Regex("""^[\d,]+\.?\d*\s+[A-Z]{3}\s+"""), "").trim()
                if (cleaned.length > 1) merchant = cleaned
            }

            // Armenian multi-line: merchant is after card number line
            if (merchant.isEmpty()) {
                val creditMerchant = Regex("""\d{4}\*{2,4}\d{4}[,\s]+(.+?)(?:\d{2}\.\d{2}\.\d{2,4})""")
                creditMerchant.find(oneLine)?.let {
                    val m = it.groupValues[1].trim().trimEnd(',').trim()
                    if (m.length > 1) merchant = m
                }
            }

            // Generic: "at MERCHANT", "from MERCHANT", "to MERCHANT"
            if (merchant.isEmpty()) {
                val genericMerchant = listOf(
                    Regex("""(?:at|from|to|@|towards)\s+([A-Za-z*][A-Za-z0-9\s&'.*\-]{1,35})"""),
                    Regex("""(?:merchant|payee|beneficiary)[:\s]+([A-Za-z][A-Za-z0-9\s&'.\-]{2,30})""", RegexOption.IGNORE_CASE),
                    Regex("""(?:UPI|upi|IMPS|NEFT)[:\s/-]+[^\s]*\s+([A-Za-z][A-Za-z0-9\s&'.\-]{2,25})""")
                )
                for (pattern in genericMerchant) {
                    pattern.find(message)?.let {
                        val m = it.groupValues[1].trim()
                            .replace(Regex("""(?:\s+(?:on|for|ref|txn|transaction|authcode|auth)\b).*$""", RegexOption.IGNORE_CASE), "")
                            .trim()
                        if (m.length > 1) { merchant = m; return@let }
                    }
                    if (merchant.isNotEmpty()) break
                }
            }

            // Clean merchant: remove trailing card fragments, dates, commas
            merchant = merchant
                .replace(Regex("""\d{4,6}\*{2,6}\d{2,6}"""), "")       // card numbers
                .replace(Regex("""\d{2}\.\d{2}\.\d{2,4}"""), "")       // dates
                .replace(Regex("""\s+"""), " ")
                .trim().trimEnd(',', '.', ' ')

            val currLabel = if (currency.isNotEmpty()) " $currency" else ""
            val desc = when {
                merchant.isNotEmpty() -> "${if (isExpense) "Payment" else "Received"} at $merchant"
                else -> oneLine.take(100)
            }

            return ParsedTransaction(
                amount = amount, description = desc, isExpense = isExpense,
                merchantName = merchant, currency = currency, cardLastFour = cardLast4
            )
        } catch (e: Exception) {
            return null
        }
    }

    // ─── Currency Auto-Detection ──────────────────────────────────

    /**
     * Scans OCR text for explicit currency markers and returns the ISO 4217 code.
     * Returns null if no currency is confidently detected.
     * Priority: explicit "AMD"/"USD"/"EUR" strings > currency symbols > Armenian context clues.
     */
    private fun detectCurrencyFromText(ocrText: String, fallback: String): String? {
        val upper = ocrText.uppercase()

        // Explicit ISO currency codes next to amounts (most reliable)
        val isoCodes = listOf(
            "AMD", "USD", "EUR", "RUB", "GBP", "CNY", "JPY", "TRY",
            "CHF", "SEK", "NOK", "DKK", "PLN", "CZK", "HUF", "RON",
            "BRL", "ARS", "MXN", "COP", "PEN", "CLP",
            "INR", "PKR", "BDT", "LKR", "NPR",
            "KRW", "THB", "VND", "MYR", "SGD", "PHP", "IDR",
            "AED", "SAR", "QAR", "KWD", "BHD", "OMR", "JOD", "ILS",
            "ZAR", "KES", "NGN", "EGP", "MAD",
            "AUD", "NZD", "CAD", "HKD", "TWD"
        )
        for (code in isoCodes) {
            if (Regex("""[\d,.]+\s*${code}\b""").containsMatchIn(upper) ||
                Regex("""\b${code}\s*[\d,.]+""").containsMatchIn(upper)) {
                return code
            }
        }

        // Currency symbols (unambiguous first)
        if (ocrText.contains("֏")) return "AMD"
        if (ocrText.contains("€")) return "EUR"
        if (ocrText.contains("₽")) return "RUB"
        if (ocrText.contains("₹")) return "INR"
        if (ocrText.contains("₺")) return "TRY"
        if (ocrText.contains("₩")) return "KRW"
        if (ocrText.contains("₫")) return "VND"
        if (ocrText.contains("฿")) return "THB"
        if (ocrText.contains("₱")) return "PHP"
        if (ocrText.contains("₪")) return "ILS"
        if (ocrText.contains("RM") && Regex("""RM\s?\d""").containsMatchIn(ocrText)) return "MYR"
        if (ocrText.contains("R$") || Regex("""R\$\s?\d""").containsMatchIn(ocrText)) return "BRL"
        if (ocrText.contains("Fr.") || ocrText.contains("CHF")) return "CHF"
        if (Regex("""(?i)\bRs\.?\s?\d""").containsMatchIn(ocrText)) return "INR"
        // "kr" for Scandinavian (disambiguation by context)
        if (Regex("""(?i)\b\d[\d\s.,]*\s*kr\b""").containsMatchIn(ocrText)) {
            return when {
                upper.contains("SEK") || upper.contains("KVITTO") || upper.contains("MOMS") && upper.contains("SUMMA") -> "SEK"
                upper.contains("NOK") || upper.contains("KVITTERING") -> "NOK"
                upper.contains("DKK") || upper.contains("KVITTERING") && upper.contains("MOMS") -> "DKK"
                else -> "SEK" // default Scandinavian
            }
        }
        // £ can be GBP or EGP — default GBP
        if (ocrText.contains("£")) return "GBP"
        // $ is ambiguous — could be USD, CAD, AUD, MXN, ARS, COP, etc.
        if (Regex("""\$\s?\d""").containsMatchIn(ocrText)) {
            return when {
                upper.contains("CA\$") || upper.contains("CAD") -> "CAD"
                upper.contains("A\$") || upper.contains("AUD") -> "AUD"
                upper.contains("S\$") || upper.contains("SGD") -> "SGD"
                upper.contains("HK\$") || upper.contains("HKD") -> "HKD"
                upper.contains("MX\$") || upper.contains("MXN") -> "MXN"
                upper.contains("AR\$") || upper.contains("ARS") -> "ARS"
                else -> "USD"
            }
        }

        // Armenian context clues (Armenian script + bank names)
        val hasArmenianChars = ocrText.any { it.code in 0x0530..0x058F }
        val armenianBankNames = listOf("evocaBANK", "EVOCABANK", "AMERIABANK", "ARDSHINBANK", "INECOBANK", "ARMSWISSBANK", "ACBA", "ID BANK", "CONVERSE BANK")
        if (hasArmenianChars || armenianBankNames.any { upper.contains(it.uppercase()) }) {
            return "AMD"
        }

        // German/Austrian/Swiss receipt context clues (EUR)
        val eurKeywords = listOf("SUMME", "ZU BEZAHLEN", "KARTENZAHLUNG", "BARGELDAUSZAHLUNG",
            "RÜCKGELD", "MWST", "KUNDENBELEG", "IHR EINKAUF", "VIELEN DANK",
            "GUTEN TAG", "KASSENBON", "EINKAUF", "DANKE FÜR")
        if (eurKeywords.any { upper.contains(it) }) return "EUR"

        // French receipt context clues (EUR)
        val frenchKeywords = listOf("MONTANT", "A PAYER", "TICKET DE CAISSE", "TVA", "MERCI DE VOTRE VISITE",
            "ESPÈCES", "ESPECES", "RENDU", "SOUS-TOTAL")
        if (frenchKeywords.count { upper.contains(it) } >= 2) return "EUR"

        // Spanish receipt context clues (EUR — for Spain; Latin America uses different currencies)
        val spanishKeywords = listOf("IMPORTE", "A PAGAR", "TICKET DE COMPRA", "GRACIAS POR SU COMPRA")
        if (spanishKeywords.count { upper.contains(it) } >= 2) return "EUR"

        // Italian receipt context clues (EUR)
        val italianKeywords = listOf("TOTALE", "SCONTRINO", "IMPORTO", "DA PAGARE", "GRAZIE")
        if (italianKeywords.count { upper.contains(it) } >= 2) return "EUR"

        // Dutch receipt context clues (EUR)
        if (upper.contains("TOTAAL") && (upper.contains("BTW") || upper.contains("TE BETALEN"))) return "EUR"

        // Polish receipt context clues (PLN)
        if (upper.contains("RAZEM") || upper.contains("DO ZAPŁATY") || (upper.contains("SUMA") && upper.contains("PTU"))) return "PLN"
        if (Regex("""[\d,.]+\s*(?:ZŁ|ZL|PLN)""").containsMatchIn(upper)) return "PLN"

        // Czech receipt context clues (CZK)
        if (upper.contains("CELKEM") || upper.contains("K ÚHRADĚ")) return "CZK"
        if (Regex("""[\d,.]+\s*(?:KČ|KC|CZK)""").containsMatchIn(upper)) return "CZK"

        // Hungarian receipt context clues (HUF)
        if (upper.contains("ÖSSZESEN") || upper.contains("FIZETENDŐ")) return "HUF"
        if (Regex("""[\d,.]+\s*(?:FT|HUF)""").containsMatchIn(upper)) return "HUF"

        // Turkish receipt context clues (TRY)
        if (upper.contains("TOPLAM") || upper.contains("KDV")) return "TRY"
        if (Regex("""[\d,.]+\s*TL\b""").containsMatchIn(upper)) return "TRY"

        // Japanese context clues (JPY)
        if (ocrText.contains("合計") && (ocrText.contains("円") || ocrText.contains("¥"))) return "JPY"
        if (ocrText.contains("税込") || ocrText.contains("お釣り")) return "JPY"

        // Korean context clues (KRW)
        if (ocrText.contains("합계") || ocrText.contains("총계")) return "KRW"
        if (ocrText.any { it.code in 0xAC00..0xD7AF }) return "KRW" // Hangul syllables

        // Thai context clues (THB)
        if (ocrText.contains("รวม") || ocrText.contains("ยอดรวม")) return "THB"
        if (ocrText.any { it.code in 0x0E00..0x0E7F }) return "THB" // Thai script

        // Arabic context clues (check for specific currencies)
        if (ocrText.contains("المجموع") || ocrText.contains("الإجمالي")) {
            return when {
                upper.contains("AED") || upper.contains("درهم") -> "AED"
                upper.contains("SAR") || upper.contains("ريال") -> "SAR"
                upper.contains("EGP") || upper.contains("جنيه") -> "EGP"
                else -> "AED" // default Arabic
            }
        }

        // Russian context clues
        if (upper.contains("ИТОГО") || upper.contains("СУММА") || ocrText.contains("руб")) return "RUB"

        // Chinese context clues
        if (ocrText.contains("合计") || ocrText.contains("总计") || ocrText.contains("¥")) return "CNY"

        return null
    }

    // ─── Receipt OCR Text Parsing ──────────────────────────────────

    /**
     * Parses a price string handling worldwide decimal/thousands conventions:
     *  - European comma-decimal:  "1,92", "78,32", "1.234,56"
     *  - English period-decimal:  "1.92", "1,234.56"
     *  - Swiss apostrophe thousands: "1'234.56", "1'234,56"
     *  - Space as thousands (French/Slavic): "1 234,56", "1 234.56"
     *  - Whole numbers (JPY/KRW): "1299"
     */
    private fun parseReceiptPrice(raw: String): Double? {
        var s = raw.trim()
        if (s.isEmpty()) return null
        // Strip leading/trailing currency text that may have leaked in
        s = s.replace(Regex("""^[A-Za-z]{0,3}\s+"""), "").replace(Regex("""\s+[A-Za-z]{0,3}$"""), "")
        // Normalise thousands separators: apostrophe → nothing, thin/non-breaking space → nothing
        s = s.replace("'", "").replace("\u00A0", "").replace("\u202F", "")
        // European format: comma as decimal separator (e.g. "1,92", "78,32", "1.234,56")
        if (Regex("""^-?\d[\d.]*,\d{1,2}$""").matches(s)) {
            return s.replace(".", "").replace(",", ".").toDoubleOrNull()
        }
        // English/default: period as decimal, comma as thousands (e.g. "1.92", "1,234.56")
        return s.replace(",", "").toDoubleOrNull()
    }

    /** All worldwide skip keywords for item names — used to reject totals/tax/payment lines. */
    private val itemSkipKeywords: Set<String> by lazy {
        setOf(
            // English
            "total", "subtotal", "sub-total", "grand total", "tax", "change", "cash",
            "balance", "due", "amount due", "tendered", "payment", "credit card",
            "debit card", "visa", "mastercard", "amex",
            // German
            "summe", "zwischensumme", "zw-summe", "kartenzahlung", "bargeld",
            "rückgeld", "ruckgeld", "betrag", "zahlung", "zu bezahlen",
            "mwst", "steuer", "netto", "brutto", "ersparnis", "gutschein",
            "mengenvorteil", "coupon", "aktionsersparnis", "app-joker",
            "pfand", "leergut", "bargeldauszahlung",
            // French
            "total", "sous-total", "montant", "a payer", "net a payer",
            "tva", "espèces", "especes", "rendu", "carte", "remise",
            // Spanish
            "total", "subtotal", "importe", "a pagar", "iva", "efectivo",
            "cambio", "tarjeta", "descuento",
            // Italian
            "totale", "subtotale", "importo", "da pagare", "contanti",
            "resto", "carta", "sconto",
            // Portuguese
            "total", "subtotal", "valor total", "a pagar", "troco",
            "dinheiro", "cartão", "cartao", "desconto",
            // Dutch
            "totaal", "subtotaal", "te betalen", "btw", "contant",
            "wisselgeld", "pin", "korting",
            // Scandinavian (Swedish/Norwegian/Danish)
            "totalt", "summa", "att betala", "moms", "kontant",
            "kort", "rabatt", "å betale", "i alt",
            // Turkish
            "toplam", "genel toplam", "ara toplam", "kdv", "nakit",
            "para üstü", "kart",
            // Polish
            "suma", "razem", "do zapłaty", "do zaplaty", "gotówka",
            "gotowka", "reszta", "karta", "rabat",
            // Czech
            "celkem", "mezisoučet", "k úhradě", "k uhrade", "hotově",
            "hotove", "sleva",
            // Hungarian
            "összesen", "osszesen", "fizetendő", "fizetendo", "kedvezmény",
            "kedvezmeny",
            // Japanese
            "合計", "小計", "税込", "税", "お釣り", "現金", "カード",
            // Korean
            "합계", "총계", "소계", "부가세", "거스름", "카드",
            // Thai
            "รวม", "ยอดรวม", "ภาษี", "เงินสด", "ทอน",
            // Russian
            "итого", "сумма", "налог", "сдача", "к оплате", "всего",
            "наличные", "карта", "скидка",
            // Chinese
            "合计", "总计", "小计", "税额", "实收", "找零",
            // Arabic
            "المجموع", "الإجمالي", "الضريبة", "نقد",
            // Armenian (Unicode)
            "\u0538\u0576\u0564\u0561\u0574\u0565\u0576\u0568",       // Delays (total)
            "\u0533\u0578\u0582\u0574\u0561\u0580",                   // Delays (sum)
            "\u0540\u0561\u0576\u0580\u0561\u0563\u0578\u0582\u0574\u0561\u0580", // Delays (subtotal)
            "\u0563\u0578\u0582\u0574\u0561\u0580",                   // delays (sum, lowercase)
            "\u0568\u0576\u0564\u0561\u0574\u0565\u0576\u0568",       // delays (total, lowercase)
            "\u057e\u0573\u0561\u057c\u0576\u057e\u0561\u056e",       // delays (paid)
            "\u056f\u0561\u0576\u056d\u0561\u057e\u056e\u0561\u0580", // delays (cash)
            "\u0574\u0561\u0576\u0580",                               // delays (change)
            "\u0566\u0565\u0572\u0573",                               // delays (discount)
            "\u057a\u0561\u0570\u057a\u0561\u0576\u0565\u0584"        // delays (keep/save receipt)
        )
    }

    data class ParsedReceipt(
        val totalAmount: Double?,
        val items: List<Pair<String, Double>>,
        val merchantName: String,
        val date: String?,
        /** Currency code detected from the receipt text (e.g. "AMD", "USD", "EUR"). Null = not detected. */
        val detectedCurrencyCode: String? = null,
        /** True when the scanned document is a bank/POS terminal slip (no goods, just a payment confirmation). */
        val isTerminalReceipt: Boolean = false
    )

    /**
     * Parse OCR text from a scanned receipt.
     *
     * [currencyCode] controls which currency markers are searched first.
     * Currency-specific keyword/symbol search order:
     *  - USD  : "$", "usd", "dollar", "dollars"
     *  - AMD  : "֏", "amd", "դր", "դրամ"
     *  - EUR  : "€", "eur", "euro"
     *  - GBP  : "£", "gbp", "pound"
     *  - INR  : "₹", "rs.", "inr", "rupee"
     *  - JPY  : "¥", "jpy", "yen"
     *  - RUB  : "₽", "rub", "ruble", "руб", "ИТОГО"
     *  All other codes: their ISO code string + generic decimal fallback.
     */
    fun parseReceiptText(ocrText: String, currencyCode: String = "USD"): ParsedReceipt {
        try {
            val lines = ocrText.lines().map { it.trim() }.filter { it.isNotEmpty() }

            // ── Auto-detect currency from receipt text ──
            val detectedCurrency = detectCurrencyFromText(ocrText, currencyCode)
            val effectiveCurrency = detectedCurrency ?: currencyCode

            // Armenian POS boilerplate keywords to skip in merchant detection
            val armenianBoilerplate = listOf(
                "\u0533\u0548\u0552\u0544\u0531\u054C",       // ԳՈՒՄDELAYS (sum/amount)
                "\u054E\u0561\u0573\u0561\u057C\u0584",       // delaysdelays (sale)
                "\u0540\u0531\u054D\u054F\u0531\u054F\u054E\u0531\u053E", // DELAYSDELAYSDELAYSDELAYSDELAYSDELAYSDELAYS (confirmed)
                "\u053F\u054F\u054C\u0548\u0546",             // DELAYSDELAYSDELAYSDELAYSDELAYS (receipt number prefix)
                "\u0540\u0561\u0576\u0561\u056D\u0576\u0578\u0580\u0564\u056B", // delaysdelaysdelaysdelays (customer)
                "\u054A\u0561\u0570\u057A\u0561\u0576\u0565\u0584", // delaysdelaysdelays (keep/save)
                "\u0547\u0576\u0578\u0580\u0570\u0561\u056F\u0561\u056C\u0578\u0582\u0569\u0575\u0578\u0582\u0576", // delaysdelaysdelaysdelaysdelaysdelays (thank you)
                "\u0555\u0580\u056B\u0576\u0561\u056F",       // delaysdelays (copy)
                "\u0540\u0531\u0545\u0553\u0548\u054D\u054F", // HAYPOST (Armenian Post)
                "\u0540\u0531\u054D\u054C\u0531\u054F\u0545\u0531\u0546" // DELAYSDELAYSDELAYSDELAYSDELAYSDELAYSDELAYSDELAYSDELAYS
            )

            // Merchant name — first non-numeric, non-date, substantive line.
            val merchantName = lines.firstOrNull { line ->
                line.length > 2 &&
                !line.matches(Regex("""^[\d\s/\-:.]+$""")) &&
                !line.matches(Regex("""(?i)^(receipt|invoice|bill|date|time|tel|phone|fax|www|tid|mid|tarihi|sale|authcode|auth code|approved|visa|mastercard|total|subtotal|tax|change|cash|payment|\*+.*).*""")) &&
                !line.matches(Regex("""^\*[\d\*\s]+$""")) &&  // masked card numbers
                // Skip Russian receipt boilerplate
                !line.matches(Regex("""(?i)^(чек|кассовый чек|ИНН|КПП|ИТОГО|итого|сумма|дата|время|ККТ|ФН|ФД|ФП|КАССА|КАССИР).*""")) &&
                // Skip Chinese/Japanese/Korean receipt boilerplate
                !line.matches(Regex("""^(收据|发票|日期|时间|合计|总计|小计|税额|收银员|谢谢|合計|小計|税込|お釣り|레시트|합계|총계|영수증).*""")) &&
                // Skip French receipt boilerplate
                !line.matches(Regex("""(?i)^(ticket de caisse|montant|sous-total|tva|total|a payer|net a payer|espèces|especes|rendu|merci).*""")) &&
                // Skip Spanish receipt boilerplate
                !line.matches(Regex("""(?i)^(ticket de compra|importe|subtotal|iva|total|a pagar|efectivo|cambio|gracias).*""")) &&
                // Skip Italian receipt boilerplate
                !line.matches(Regex("""(?i)^(scontrino|totale|subtotale|importo|iva|contanti|resto|grazie).*""")) &&
                // Skip Dutch receipt boilerplate
                !line.matches(Regex("""(?i)^(kassabon|totaal|subtotaal|btw|te betalen|contant|wisselgeld|pin|bedankt).*""")) &&
                // Skip Turkish receipt boilerplate
                !line.matches(Regex("""(?i)^(toplam|genel toplam|ara toplam|kdv|nakit|kart|teşekkür).*""")) &&
                // Skip Scandinavian receipt boilerplate
                !line.matches(Regex("""(?i)^(kvitto|kvittering|summa|totalt|moms|kontant|kort|tack|takk).*""")) &&
                // Skip Polish/Czech/Hungarian receipt boilerplate
                !line.matches(Regex("""(?i)^(paragon|razem|suma|celkem|összesen|fizetendő|do zapłaty).*""")) &&
                // Skip Thai receipt boilerplate
                !line.matches(Regex("""^(ใบเสร็จ|รวม|ยอดรวม|ภาษี|เงินสด|ขอบคุณ).*""")) &&
                // Skip Arabic receipt boilerplate
                !line.matches(Regex("""^(المجموع|الإجمالي|الضريبة|فاتورة|إيصال|شكراً).*""")) &&
                // Skip Armenian POS boilerplate
                armenianBoilerplate.none { kw -> line.uppercase().contains(kw) } &&
                // Skip common POS terminal lines
                !line.matches(Regex("""(?i)^(AID:|Cless|RRN:|Authorization|Response|On device|Card:|EXP:).*""")) &&
                // Skip lines like "KTRON 7043", "TID:22013678", "MID:22003678"
                !line.matches(Regex("""(?i)^(KTRON|TID|MID|KTON)\s*[:\s]?\d+.*""")) &&
                // Skip German receipt boilerplate
                !line.matches(Regex("""(?i)^(Summe|SUMME|Zwischensumme|Zw-Summe|ZU BEZAHLEN|Betrag|Kartenzahlung|Bargeld|R.ckgeld|Steuer|MwSt|Netto|Brutto|Firma:|Filiale:|Ihr Einkauf|Vielen Dank|Bitte Beleg|Unsere .ffnungszeiten|Genehmigung|Ust-Id|Kassen?-?Nr|TSE|Bon-Nr|TA-Nr|Beleg-Nr|K-U-N-D-E-N|Digitale Rechnung|Pferdemarkt|Hegaustra|Werrestr|Radetzkystr|Albstra).*""")) &&
                // Skip lines that are just article numbers
                !line.matches(Regex("""^\d{5,13}\s*$"""))
            }?.take(50) ?: "Unknown Store"

            // ── Build currency-specific amount patterns ───────────────
            // currencySymbolPattern is a regex fragment matching the currency marker(s).
            val currencySymbolPattern: String = when (effectiveCurrency.uppercase()) {
                "USD" -> """(?:\$|usd|dollar s?)\s*"""
                "AMD" -> """(?:֏|amd|դր\.?|դրամ)\s*"""
                "EUR" -> """(?:€|eur|euro)\s*"""
                "GBP" -> """(?:£|gbp|pound s?)\s*"""
                "INR" -> """(?:₹|rs\.?|inr|rupee s?)\s*"""
                "JPY" -> """(?:¥|jpy|yen)\s*"""
                "CNY" -> """(?:¥|cny|rmb|yuan)\s*"""
                "RUB" -> """(?:₽|rub|ruble s?|руб\.?)\s*"""
                "TRY" -> """(?:₺|try|lira)\s*"""
                "KRW" -> """(?:₩|krw|won)\s*"""
                "BRL" -> """(?:R\$|brl|real)\s*"""
                "CAD" -> """(?:CA\$|cad)\s*"""
                "AUD" -> """(?:A\$|aud)\s*"""
                "CHF" -> """(?:Fr\.?|chf)\s*"""
                "SGD" -> """(?:S\$|sgd)\s*"""
                "HKD" -> """(?:HK\$|hkd)\s*"""
                "AED" -> """(?:د\.إ|aed|dirham)\s*"""
                "SAR" -> """(?:ر\.س|sar|riyal)\s*"""
                "THB" -> """(?:฿|thb|baht)\s*"""
                "VND" -> """(?:₫|vnd|đồng)\s*"""
                "PHP" -> """(?:₱|php|peso)\s*"""
                "MYR" -> """(?:RM|myr|ringgit)\s*"""
                "IDR" -> """(?:Rp\.?|idr|rupiah)\s*"""
                "ILS" -> """(?:₪|ils|nis)\s*"""
                "SEK" -> """(?:kr\.?|sek)\s*"""
                "NOK" -> """(?:kr\.?|nok)\s*"""
                "DKK" -> """(?:kr\.?|dkk)\s*"""
                "PLN" -> """(?:zł|pln)\s*"""
                "CZK" -> """(?:Kč|czk)\s*"""
                "HUF" -> """(?:Ft|huf)\s*"""
                "RON" -> """(?:lei|ron)\s*"""
                "ZAR" -> """(?:R\s|zar|rand)\s*"""
                "EGP" -> """(?:E£|egp|جنيه)\s*"""
                "TWD" -> """(?:NT\$|twd)\s*"""
                "NZD" -> """(?:NZ\$|nzd)\s*"""
                "MXN" -> """(?:MX\$|mxn)\s*"""
                else  -> """(?:${Regex.escape(currencyCode)}|[$₹€£֏¥₽₺₩₫฿₱₪])\s*"""
            }

            // Generic "any supported symbol" fallback used in item-line matching
            val anySymbol = """[$₹€£֏¥₽₺₩₫฿₱₪]?"""

            // Extract items
            val items = mutableListOf<Pair<String, Double>>()

            // ── Armenian numbered-item receipt parser ──────────────────
            // Armenian grocery receipts (e.g. Yerevan City / SAS) use a numbered format:
            //   1.Մածուն <<Երdelays>> 3.6% 450delays
            //   0403/1102359 1delaysdelays 594/6 600
            // The product name follows the number, the price is the LAST number on the
            // (possibly multi-line) item block. Metadata like weight, codes, quantities
            // appear in between and should be ignored.
            val armenianNumberedItem = Regex("""^\d{1,3}\.""")
            val hasArmenianNumberedItems = lines.any { armenianNumberedItem.containsMatchIn(it) } &&
                    lines.any { it.any { c -> c.code in 0x0530..0x058F } }  // has Armenian chars

            if (hasArmenianNumberedItems) {
                // Group lines into item blocks: each block starts with "N." (number-dot)
                val itemBlocks = mutableListOf<String>()
                val currentBlock = StringBuilder()
                for (line in lines) {
                    if (armenianNumberedItem.containsMatchIn(line)) {
                        if (currentBlock.isNotEmpty()) {
                            itemBlocks.add(currentBlock.toString())
                        }
                        currentBlock.clear()
                        currentBlock.append(line)
                    } else if (currentBlock.isNotEmpty()) {
                        // Continuation line for current item
                        currentBlock.append(" ").append(line)
                    }
                }
                if (currentBlock.isNotEmpty()) itemBlocks.add(currentBlock.toString())

                for (block in itemBlocks) {
                    // Remove the leading number + dot: "1." / "12."
                    val withoutNumber = block.replace(armenianNumberedItem, "").trim()

                    // Extract product name: Armenian text (Unicode 0530-058F) plus
                    // allowed punctuation (<<>>, quotes, hyphens, spaces, &, digits within name)
                    val nameMatch = Regex("^([\\u0530-\\u058F][\\u0530-\\u058F\\s\\d&.,'\u201C\u201D\u00AB\u00BB<>\\-()]+)")
                        .find(withoutNumber)
                    val rawName = nameMatch?.groupValues?.get(1)?.trim() ?: continue

                    // Clean up name: remove trailing digits, punctuation artifacts
                    val name = rawName
                        .replace(Regex("""\s*\d+[.,]?\d*\s*$"""), "")  // trailing numbers
                        .replace(Regex("[<>\u00AB\u00BB\u201C\u201D]+"), "")  // quote marks
                        .replace(Regex("""\s{2,}"""), " ")             // collapse whitespace
                        .trim()

                    if (name.length < 2) continue

                    // Price is the last standalone number in the block
                    // Match integers or decimals (comma or dot), take the last one
                    val allNumbers = Regex("""\b(\d[\d.,]*\d|\d+)\b""").findAll(block).toList()
                    val priceStr = allNumbers.lastOrNull()?.groupValues?.get(1) ?: continue
                    val price = parseReceiptPrice(priceStr)

                    if (price != null && price > 0 && price < 1_000_000 &&
                        !name.lowercase().let { n -> itemSkipKeywords.any { kw -> n.contains(kw) } }
                    ) {
                        items.add(name to price)
                    }
                }
            }

            // ── Generic item patterns (used when Armenian parser didn't find items) ──
            if (items.isEmpty()) {
            val itemPatterns = listOf(
                // ── Period-decimal patterns (US, UK, Asia, etc.) ──
                // Item   <currency>AMOUNT  e.g. "Coffee  $4.99" / "Coffee  €4.99"
                Regex("""(.{3,40}?)\s+${currencySymbolPattern}([\d,]+\.\d{2})\s*$""", RegexOption.IGNORE_CASE),
                // Item   AMOUNT (no symbol) e.g. "Coffee  4.99"
                Regex("""(.{3,40}?)\s+(\d+\.\d{2})"""),
                // Item    <any-symbol>AMOUNT (wider match, 2+ spaces)
                Regex("""^(.+?)\s{2,}${anySymbol}\s?([\d,]+\.\d{2})"""),

                // ── European comma-decimal patterns ──
                // [article#] ItemName   price[,.]dd [tax-letter]
                // e.g. "462913 Birne Abate lose  1,92 A" or "Volvic Naturelle  2,94 A"
                Regex("""^(?:\d{5,7}\s+)?(.{3,40}?)\s{2,}(-?\d{1,6}[,.]\d{2})\s*[A-Ba-b]?\s*$"""),
                // Müller-style: qty name origPrice discount finalPrice[tax]
                // e.g. "1 HOCHGENUSS STUDENTE 1,49 -0,15 1,34b"
                Regex("""^\d+\s+(.{3,30}?)\s+\d+[,.]\d{2}\s+-?\d+[,.]\d{2}\s+(\d+[,.]\d{2})\s*[A-Ba-b]?\s*$"""),

                // ── Currency-after-price (European style) ──
                // e.g. "Coffee  4,99 €" / "Coffee  4.99 EUR"
                Regex("""^(.{3,40}?)\s{2,}(-?\d[\d.,]*\d)\s*(?:€|£|₽|₺|Fr\.?|kr|zł|Kč|Ft|TL|лв)\s*$""", RegexOption.IGNORE_CASE),

                // ── Price-before-name (some Asian / Middle East) ──
                // e.g. "¥150  おにぎり" / "₹120  Dal Rice"
                // NOTE: groups are (price)(name) — handled by priceBeforeNamePattern below
                Regex("""^[$₹€£֏¥₽₺₩₫฿₱₪]?\s?(\d[\d,.]*)\s{2,}(.{3,40})\s*$"""),

                // ── Qty x Name  Price ──
                // e.g. "2 x Coffee  9.98" / "3x Bread  5.97"
                Regex("""^\d+\s*[xX×]\s+(.{3,35}?)\s{2,}${anySymbol}\s?(\d[\d,.]*\d?)\s*[A-Ba-b]?\s*$"""),

                // ── Japanese / Korean whole-number with ¥/₩ ──
                // e.g. "おにぎり  ¥150" / "김밥  ₩3500"
                Regex("""^(.{2,40}?)\s{2,}[¥₩]\s?(\d{1,7})\s*$"""),

                // ── Whole-number items (general): "Item  500" ──
                Regex("""^(.{3,40}?)\s{2,}${anySymbol}\s?(\d{1,7})(?:\s*${'$'})""")
            )
            // Lines to skip: quantity/weight calculations, sub-totals, payment info
            val skipLinePatterns = listOf(
                Regex("""^\s*\d+[,.]\d+\s*kg\s*x""", RegexOption.IGNORE_CASE),    // "0,742 kg x 2,59 EUR/kg"
                Regex("""^\s*\d+\s*[xX×*]\s+\d"""),                                // "3 x  1,99"
                Regex("""(?:EUR|USD|GBP)/(?:kg|Stk|St|lb|oz|pcs?)""", RegexOption.IGNORE_CASE), // price-per-unit
                Regex("""^\s*\d+\s*[*]\s*\d"""),                                   // "2 * 0,59"
                Regex("""^-?K-U-N-D-E""", RegexOption.IGNORE_CASE),               // customer section separator
                Regex("""^={3,}|^-{3,}|^\*{3,}"""),                               // separator lines
                Regex("""(?i)^(tax|vat|tva|mwst|iva|kdv|btw|moms|ptu|gst|hst|pst)\s"""),  // tax detail lines
                Regex("""(?i)^(steuern?|impuesto|taxe)\s"""),                     // tax labels
                Regex("""^\s*\d+[,.]\d+\s*(?:lb|oz|pcs?|st|stk)\s*[x×@]""", RegexOption.IGNORE_CASE) // weight/qty
            )
            // Track which pattern is the price-before-name one (groups are swapped)
            val priceBeforeNamePattern = itemPatterns.firstOrNull {
                it.pattern.startsWith("^[\$₹€£֏¥₽₺₩₫฿₱₪]")
            }
            for (line in lines) {
                // Skip calculation/separator lines
                if (skipLinePatterns.any { it.containsMatchIn(line) }) continue
                for (pattern in itemPatterns) {
                    val match = pattern.find(line)
                    if (match != null && match.groupValues.size >= 3) {
                        var name: String
                        var priceStr: String
                        if (pattern === priceBeforeNamePattern) {
                            // Price-before-name: group1=price, group2=name
                            priceStr = match.groupValues[1]
                            name = match.groupValues[2].trim()
                        } else {
                            name = match.groupValues[1].trim()
                            priceStr = match.groupValues[2]
                        }
                        // Strip leading article numbers (e.g. "462913 Birne" → "Birne")
                        name = name.replace(Regex("""^\d{5,7}\s+"""), "")
                        // Strip trailing tax code letters that may have leaked into name
                        name = name.replace(Regex("""\s+[A-Ba-b]$"""), "")
                        val price = parseReceiptPrice(priceStr)
                        if (price != null && price > 0 && price < 1_000_000 &&
                            name.length >= 2 &&
                            // Not just digits/quantity markers
                            !name.matches(Regex("""^\d+\s*[xX×*]?\s*$""")) &&
                            // Not a total/tax/payment keyword in any supported language
                            !name.lowercase().let { n ->
                                itemSkipKeywords.any { kw -> n.contains(kw) }
                            }
                        ) {
                            items.add(name to price)
                            break
                        }
                    }
                }
            }
            } // end if (items.isEmpty()) – generic patterns fallback

            // ── Total extraction ──────────────────────────────────────
            // Priority-1: currency-specific total markers
            val currencyTotalPatterns: List<Regex> = when (effectiveCurrency.uppercase()) {
                "AMD" -> listOf(
                    // Armenian POS receipts: Գումdelays = "sum/amount", Delays = "total"
                    // Proper Armenian: Գumdar (Գdelays), yndardelays (Delays), Dndelays
                    // Using Unicode escapes for safety:
                    // Delaysdelays = \u0538\u0576\u0564\u0561\u0574\u0565\u0576\u0568 (total)
                    // Delays = \u0533\u0578\u0582\u0574\u0561\u0580 (sum)
                    // Yndelaysunds = \u0540\u0561\u0576\u0580\u0561\u0563\u0578\u0582\u0574\u0561\u0580 (total/sum)
                    Regex("""(?:\u0538\u0576\u0564\u0561\u0574\u0565\u0576\u0568|\u0533\u0578\u0582\u0574\u0561\u0580|\u0540\u0561\u0576\u0580\u0561\u0563\u0578\u0582\u0574\u0561\u0580|\u0531\u0574\u0562\u0578\u0572\u057b)[:\s]*(?:֏|AMD)?\s*([0-9][0-9\s,]*\.?[0-9]*)"""),
                    // Latin transliteration: "Gumari", "Yndameny", "Total"
                    Regex("""(?:gumari?|yndamen[ky]|total|grand\s*total)[:\s]*(?:֏|AMD)?\s*([0-9][0-9\s,]*\.?[0-9]*)""", RegexOption.IGNORE_CASE),
                    // POS receipt: number immediately before AMD/֏ — "12 000.00 AMD"
                    Regex("""([0-9][0-9\s,]*\.[0-9]{1,2})\s*(?:AMD|֏)""", RegexOption.IGNORE_CASE),
                    // AMD/֏ before number — "AMD 12000.00" / "֏ 12,000"
                    Regex("""(?:֏|AMD)[:\s]+([0-9][0-9\s,]*\.?[0-9]*)""", RegexOption.IGNORE_CASE)
                )
                "RUB" -> listOf(
                    Regex("""(?:ИТОГО|итого|СУММА|сумма|total)[:\s]*(?:₽|руб\.?)?\s*([\d,\s]+\.?\d*)""", RegexOption.IGNORE_CASE),
                    Regex("""(?:₽|руб\.?)\s*([\d,\s]+\.?\d*)"""),
                    // Russian receipt: "К ОПЛАТЕ: 1 234.56"
                    Regex("""(?:К ОПЛАТЕ|к оплате|ВСЕГО|всего)[:\s]*(?:₽|руб\.?)?\s*([\d,\s]+\.?\d*)""", RegexOption.IGNORE_CASE)
                )
                "CNY" -> listOf(
                    // Chinese receipt totals: "合计: ¥123.45", "总计 123.45", "实收 123.45"
                    Regex("""(?:合计|总计|实收|应收|小计)[:\s：]*(?:¥|￥|元|RMB)?\s*([\d,]+\.?\d*)"""),
                    Regex("""(?:¥|￥)\s*([\d,]+\.\d{2})"""),
                    Regex("""(?:total|grand\s*total)[:\s]*(?:¥|￥|cny|rmb)?\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
                )
                "EUR" -> listOf(
                    // German: "Summe  78,32", "SUMME:  35,88", "ZU BEZAHLEN  17,92", "Betrag  278,32"
                    Regex("""(?:SUMME|Summe|ZU BEZAHLEN|Zu bezahlen|GESAMTBETRAG|Gesamtbetrag|Gesamtsumme)[:\s]*(?:€|EUR)?\s*([\d.,]+)""", RegexOption.IGNORE_CASE),
                    // "Betrag  EUR  78,32" or "Betrag  78,32"
                    Regex("""(?:Betrag)[:\s]*(?:€|EUR)?\s*([\d.,]+)""", RegexOption.IGNORE_CASE),
                    // € symbol with amount
                    Regex("""(?:€|EUR)\s*([\d.,]+)"""),
                    // English fallback
                    Regex("""(?:total|grand\s*total)[:\s]*(?:€|EUR)?\s*([\d.,]+)""", RegexOption.IGNORE_CASE)
                )
                "USD" -> listOf(
                    Regex("""(?:grand\s*total|total\s*due|amount\s*due|balance\s*due|total)[:\s]*\$?\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE),
                    Regex("""\$\s*([\d,]+\.\d{2})""")
                )
                "GBP" -> listOf(
                    Regex("""(?:grand\s*total|total\s*due|total|amount)[:\s]*£?\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE),
                    Regex("""£\s*([\d,]+\.\d{2})""")
                )
                "TRY" -> listOf(
                    // Turkish: "TOPLAM: ₺123,45" or "TOPLAM 123,45 TL"
                    Regex("""(?:TOPLAM|GENEL TOPLAM|TOTAL)[:\s]*(?:₺|TL)?\s*([\d.,]+)""", RegexOption.IGNORE_CASE),
                    Regex("""([\d.,]+)\s*(?:TL|₺)"""),
                    Regex("""₺\s*([\d.,]+)""")
                )
                "JPY" -> listOf(
                    // Japanese: "合計  ¥1,299" or "合計 1299円"
                    Regex("""(?:合計|合计|小計|税込合計|total)[:\s]*¥?\s*([\d,]+)""", RegexOption.IGNORE_CASE),
                    Regex("""¥\s*([\d,]+)"""),
                    Regex("""([\d,]+)\s*円""")
                )
                "KRW" -> listOf(
                    // Korean: "합계  ₩25,000" or "합계 25000원"
                    Regex("""(?:합계|총계|total)[:\s]*₩?\s*([\d,]+)""", RegexOption.IGNORE_CASE),
                    Regex("""₩\s*([\d,]+)"""),
                    Regex("""([\d,]+)\s*원""")
                )
                "INR" -> listOf(
                    Regex("""(?:grand\s*total|total|amount)[:\s]*(?:₹|Rs\.?)\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE),
                    Regex("""(?:₹|Rs\.?)\s*([\d,]+\.\d{2})""")
                )
                "CHF" -> listOf(
                    // Swiss: "Total CHF 45.90" or "Total Fr. 45.90"
                    Regex("""(?:Total|Betrag|Summe)[:\s]*(?:Fr\.?|CHF)\s*([\d.,'\s]+)""", RegexOption.IGNORE_CASE),
                    Regex("""(?:Fr\.?|CHF)\s*([\d.,'\s]+\d)""")
                )
                "THB" -> listOf(
                    Regex("""(?:รวม|ยอดรวม|total)[:\s]*(?:฿)?\s*([\d.,]+)""", RegexOption.IGNORE_CASE),
                    Regex("""฿\s*([\d.,]+)""")
                )
                "PLN" -> listOf(
                    Regex("""(?:RAZEM|SUMA|DO ZAPŁATY|DO ZAPLATY|TOTAL)[:\s]*(?:zł|PLN)?\s*([\d.,\s]+)""", RegexOption.IGNORE_CASE),
                    Regex("""([\d.,]+)\s*(?:zł|PLN)""", RegexOption.IGNORE_CASE)
                )
                "CZK" -> listOf(
                    Regex("""(?:CELKEM|K ÚHRADĚ|TOTAL)[:\s]*(?:Kč|CZK)?\s*([\d.,\s]+)""", RegexOption.IGNORE_CASE),
                    Regex("""([\d.,]+)\s*(?:Kč|CZK)""", RegexOption.IGNORE_CASE)
                )
                "HUF" -> listOf(
                    Regex("""(?:ÖSSZESEN|FIZETENDŐ|TOTAL)[:\s]*(?:Ft|HUF)?\s*([\d.,\s]+)""", RegexOption.IGNORE_CASE),
                    Regex("""([\d.,]+)\s*(?:Ft|HUF)""", RegexOption.IGNORE_CASE)
                )
                "SEK", "NOK", "DKK" -> listOf(
                    Regex("""(?:TOTALT|SUMMA|ATT BETALA|Å BETALE|I ALT|TOTAL)[:\s]*(?:kr\.?)?\s*([\d.,\s]+)""", RegexOption.IGNORE_CASE),
                    Regex("""([\d.,]+)\s*kr""", RegexOption.IGNORE_CASE)
                )
                "BRL" -> listOf(
                    Regex("""(?:TOTAL|VALOR TOTAL|A PAGAR)[:\s]*(?:R\$)?\s*([\d.,]+)""", RegexOption.IGNORE_CASE),
                    Regex("""R\$\s*([\d.,]+)""")
                )
                "AED" -> listOf(
                    Regex("""(?:total|المجموع|الإجمالي)[:\s]*(?:AED|د\.إ)?\s*([\d.,]+)""", RegexOption.IGNORE_CASE),
                    Regex("""(?:AED|د\.إ)\s*([\d.,]+)""")
                )
                else -> listOf(
                    Regex("""(?:grand\s*total|total\s*due|total)[:\s]*${currencySymbolPattern}?([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
                )
            }

            // Priority-2: generic total patterns — worldwide catch-all
            val genericTotalPatterns = listOf(
                // English
                Regex("""(?:grand\s*total|total\s*due|amount\s*due|total\s*amount|balance\s*due)[:\s]*[$₹€£֏¥₽₺₩]?\s?([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE),
                Regex("""(?:TOTAL|Total|GRAND TOTAL)\s*:?\s*[$₹€£֏¥₽₺₩]?\s?([\d,]+\.\d{2})"""),
                Regex("""(?:total)[:\s]*(?:rs\.?|₹|\$|€|£|֏|[A-Z]{3})?\s?([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE),
                Regex("""(?:TOTAL)\s+[$₹€£֏¥₽₺₩]?([\d,]+\.?\d{0,2})"""),
                // Russian
                Regex("""(?:ИТОГО|итого|СУММА|сумма|К ОПЛАТЕ|к оплате)[:\s]*([\d,\s]+\.?\d*)"""),
                // Chinese / Japanese
                Regex("""(?:合计|总计|实收|应收|合計|小計)[:\s：]*(?:¥|￥|元)?\s*([\d,]+\.?\d*)"""),
                // German
                Regex("""(?:SUMME|Summe|ZU BEZAHLEN|Gesamtbetrag|Gesamtsumme)[:\s]*(?:€|EUR)?\s*([\d.,]+)""", RegexOption.IGNORE_CASE),
                // French
                Regex("""(?:TOTAL|MONTANT|A PAYER|NET A PAYER)[:\s]*(?:€|EUR)?\s*([\d.,\s]+)""", RegexOption.IGNORE_CASE),
                // Spanish
                Regex("""(?:TOTAL|IMPORTE|A PAGAR)[:\s]*(?:€|EUR|\$)?\s*([\d.,\s]+)""", RegexOption.IGNORE_CASE),
                // Italian
                Regex("""(?:TOTALE|IMPORTO|DA PAGARE)[:\s]*(?:€|EUR)?\s*([\d.,\s]+)""", RegexOption.IGNORE_CASE),
                // Dutch
                Regex("""(?:TOTAAL|TE BETALEN)[:\s]*(?:€|EUR)?\s*([\d.,\s]+)""", RegexOption.IGNORE_CASE),
                // Scandinavian
                Regex("""(?:TOTALT|SUMMA|ATT BETALA|Å BETALE|I ALT)[:\s]*(?:kr\.?)?\s*([\d.,\s]+)""", RegexOption.IGNORE_CASE),
                // Turkish
                Regex("""(?:TOPLAM|GENEL TOPLAM)[:\s]*(?:₺|TL)?\s*([\d.,\s]+)""", RegexOption.IGNORE_CASE),
                // Polish
                Regex("""(?:RAZEM|SUMA|DO ZAPŁATY|DO ZAPLATY)[:\s]*(?:zł|PLN)?\s*([\d.,\s]+)""", RegexOption.IGNORE_CASE),
                // Czech
                Regex("""(?:CELKEM|K ÚHRADĚ|K UHRADE)[:\s]*(?:Kč|CZK)?\s*([\d.,\s]+)""", RegexOption.IGNORE_CASE),
                // Hungarian
                Regex("""(?:ÖSSZESEN|OSSZESEN|FIZETENDŐ|FIZETENDO)[:\s]*(?:Ft|HUF)?\s*([\d.,\s]+)""", RegexOption.IGNORE_CASE),
                // Korean
                Regex("""(?:합계|총계)[:\s]*(?:₩)?\s*([\d,]+)"""),
                // Thai
                Regex("""(?:รวม|ยอดรวม)[:\s]*(?:฿)?\s*([\d.,]+)"""),
                // Arabic
                Regex("""(?:المجموع|الإجمالي)[:\s]*([\d.,\s]+)""")
            )

            var total: Double? = null
            for (pattern in currencyTotalPatterns + genericTotalPatterns) {
                val match = pattern.findAll(ocrText).lastOrNull()
                if (match != null) {
                    val rawTotal = match.groupValues[1].replace(Regex("""\s"""), "")
                    total = parseReceiptPrice(rawTotal)
                    if (total != null && total > 0) break
                    total = null
                }
            }

            // Fallback: use largest item price or sum of items
            if (total == null && items.isNotEmpty()) {
                val sum = items.sumOf { it.second }
                val max = items.maxOf { it.second }
                // Use the sum of items as the total. Only fall back to the
                // single largest price when items has exactly one entry (i.e. sum == max).
                total = if (items.size == 1) max else sum
            }

            // AMD-specific fallback: Armenian POS receipts often lose all script text through OCR
            // (ML Kit has no Armenian model). If no total was found yet, scan every line for a
            // bare decimal number that looks like an AMD amount (> 100 AMD is plausible minimum).
            if (total == null && effectiveCurrency.uppercase() == "AMD") {
                val amountCandidates = lines.mapNotNull { line ->
                    // Match "12 000.00", "12000.00", "12000", "12,000.00" — typical AMD receipt amounts
                    val m = Regex("""([0-9][0-9\s,]*\.?[0-9]{0,2})""").findAll(line)
                        .mapNotNull { parseReceiptPrice(it.groupValues[1].replace(Regex("""\s"""), "")) }
                        .filter { it >= 100 }   // AMD amounts are almost always ≥ 100
                        .maxOrNull()
                    m
                }
                total = amountCandidates.maxOrNull()
            }

            // Date — supports worldwide formats
            val datePatterns = listOf(
                // Labelled: "Date: 09/03/26", "Tarihi: 01.02.2024", "Datum: 23.07.2024", "Fecha: 01/02/2024", "Data: 01/02/2024", "日付: 2024/01/02"
                Regex("""(?:tarihi|date|dated?|datum|fecha|data|日付|날짜)[:\s]*(\d{1,2}[/\-\.]\d{1,2}[/\-\.]\d{2,4})""", RegexOption.IGNORE_CASE),
                // European dot-separated: "23.07.2024"
                Regex("""\d{1,2}\.\d{1,2}\.\d{2,4}"""),
                // Slash/dash separated: "09/03/26", "2024-01-15"
                Regex("""\d{1,2}[/\-]\d{1,2}[/\-]\d{2,4}"""),
                Regex("""\d{4}[/\-]\d{1,2}[/\-]\d{1,2}""")
            )
            var date: String? = null
            for (p in datePatterns) {
                val m = p.find(ocrText)
                if (m != null) {
                    // If there's a capturing group (label pattern), use group 1; else full match
                    date = if (m.groupValues.size > 1 && m.groupValues[1].isNotEmpty()) m.groupValues[1] else m.value
                    break
                }
            }

            // ── Bank terminal / POS slip detection ─────────────────────
            // Terminal receipts contain payment confirmation data (card info,
            // authorization codes, TID/MID) but no actual goods/items.
            // They should NOT be saved to the scanned goods sections.
            val terminalIndicators = listOf(
                // English POS terminal keywords
                "approved", "authorization", "auth code", "authcode",
                "card number", "card type", "card:", "exp:",
                "tid:", "mid:", "rrn:", "aid:",
                "visa", "mastercard", "amex", "unionpay", "maestro",
                "contactless", "cless", "chip", "swipe",
                "terminal", "pos terminal",
                "on device", "response code",
                // German POS terminal keywords
                "genehmigung", "autorisierung", "autorisierungsnr",
                "terminal-id", "kundenbeleg", "emv-aid", "emv-daten",
                "karte 1", "genehmigungsnr", "genehmigung karte",
                "bitte beleg aufbewahren", "geprueft", "gepr\u00fcft",
                "verarbeitung ok",
                // French POS terminal keywords
                "autorisation", "carte bancaire", "sans contact",
                "paiement accepté", "paiement accepte",
                // Spanish POS terminal keywords
                "autorización", "autorizacion", "tarjeta",
                "pago aprobado", "sin contacto",
                // Italian POS terminal keywords
                "autorizzazione", "carta di credito", "pagamento approvato",
                // Turkish POS terminal keywords
                "onay", "onay kodu", "kart no", "temassız",
                // Japanese POS terminal keywords
                "承認", "カード番号", "端末",
                // Korean POS terminal keywords
                "승인", "카드번호", "단말기",
                // Armenian POS terminal keywords
                "\u0540\u0531\u054D\u054F\u0531\u054F\u054E\u0531\u053E",
                "\u054E\u0561\u0573\u0561\u057C\u0584",
                "\u0554\u0561\u0580\u057F",
                // Russian POS terminal keywords
                "\u043e\u0434\u043e\u0431\u0440\u0435\u043d\u043e",       // одобрено
                "\u043a\u0430\u0440\u0442\u0430",                         // карта
                "\u0442\u0435\u0440\u043c\u0438\u043d\u0430\u043b",       // терминал
                "\u0430\u0432\u0442\u043e\u0440\u0438\u0437\u0430\u0446\u0438\u044f", // авторизация
                // Arabic POS terminal keywords
                "الموافقة", "رقم البطاقة", "بدون تلامس"
            )
            val maskedCardPattern = Regex("""\*{2,}\d{2,4}|\d{4}\s*\*{4,}|#{3,}\d{2,4}|X{4}\s+X{4}""")
            val lowerText = ocrText.lowercase()
            val terminalHits = terminalIndicators.count { lowerText.contains(it) }
            val hasMaskedCard = maskedCardPattern.containsMatchIn(ocrText)
            // A terminal receipt typically has ≥3 terminal indicators and no real goods items
            val isTerminal = (terminalHits >= 3 || (terminalHits >= 2 && hasMaskedCard)) && items.isEmpty()

            return ParsedReceipt(totalAmount = total, items = items, merchantName = merchantName, date = date, detectedCurrencyCode = detectedCurrency, isTerminalReceipt = isTerminal)
        } catch (e: Exception) {
            return ParsedReceipt(totalAmount = null, items = emptyList(), merchantName = "Unknown", date = null)
        }
    }

    // ─── QR Code String Parsing ──────────────────────────────────────

    /**
     * Attempt to extract amount and merchant from a QR code string found on a receipt.
     *
     * QR codes on receipts may contain:
     * - URLs with query parameters (e.g. `?s=1234.56&fn=...` or `?amount=50.00&merchant=...`)
     * - JSON objects with amount/total/merchant fields
     * - Fiscal data strings with key=value or key:value pairs
     * - Plain-text lines with amounts
     *
     * Returns a [ParsedReceipt] with whatever could be extracted.
     */
    fun parseQrCodeString(qrString: String): ParsedReceipt {
        try {
            if (qrString.isBlank()) return ParsedReceipt(null, emptyList(), "Unknown", null)

            var amount: Double? = null
            var merchant = ""
            var date: String? = null

            // ── 1. Try URL query-parameter extraction ────────────────
            if (qrString.contains("://") || qrString.contains("?")) {
                val queryPart = qrString.substringAfter("?", "")
                if (queryPart.isNotEmpty()) {
                    val params = queryPart.split("&").associate { pair ->
                        val parts = pair.split("=", limit = 2)
                        (parts.getOrNull(0)?.lowercase() ?: "") to (parts.getOrNull(1) ?: "")
                    }
                    // Amount keys commonly used in fiscal QR codes
                    val amountKeys = listOf("s", "sum", "amount", "total", "price", "value", "amt")
                    for (key in amountKeys) {
                        val raw = params[key] ?: continue
                        val parsed = raw.replace(",", ".").replace("[^\\d.]".toRegex(), "").toDoubleOrNull()
                        if (parsed != null && parsed > 0) { amount = parsed; break }
                    }
                    // Merchant keys
                    val merchantKeys = listOf("merchant", "shop", "store", "name", "fn", "n", "org")
                    for (key in merchantKeys) {
                        val raw = params[key] ?: continue
                        if (raw.isNotBlank()) {
                            merchant = java.net.URLDecoder.decode(raw, "UTF-8")
                                .replace("+", " ").trim()
                            break
                        }
                    }
                    // Date keys
                    val dateKeys = listOf("t", "date", "dt", "time")
                    for (key in dateKeys) {
                        val raw = params[key] ?: continue
                        if (raw.isNotBlank()) { date = raw; break }
                    }
                }
            }

            // ── 2. Try JSON-like extraction ──────────────────────────
            if (amount == null && (qrString.trimStart().startsWith("{") || qrString.contains("\"amount\""))) {
                val amountPatterns = listOf(
                    Regex("""["']?(?:amount|total|sum|price|value)["']?\s*[:=]\s*["']?([\d,]+\.?\d*)["']?""", RegexOption.IGNORE_CASE)
                )
                for (p in amountPatterns) {
                    val m = p.find(qrString)
                    if (m != null) {
                        val parsed = m.groupValues[1].replace(",", "").toDoubleOrNull()
                        if (parsed != null && parsed > 0) { amount = parsed; break }
                    }
                }
                val merchantJson = Regex("""["']?(?:merchant|shop|store|name|org)["']?\s*[:=]\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                    .find(qrString)
                if (merchantJson != null && merchant.isEmpty()) {
                    merchant = merchantJson.groupValues[1].trim()
                }
            }

            // ── 3. Try key:value / key=value pair extraction ─────────
            if (amount == null) {
                val kvPattern = Regex("""(?:amount|total|sum|price|s|amt)\s*[=:]\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
                val m = kvPattern.find(qrString)
                if (m != null) {
                    val parsed = m.groupValues[1].replace(",", "").toDoubleOrNull()
                    if (parsed != null && parsed > 0) amount = parsed
                }
            }

            // ── 4. Fallback: find the largest decimal number in the string ──
            if (amount == null) {
                val allNumbers = Regex("""(\d[\d,]*\.?\d+)""").findAll(qrString)
                    .mapNotNull { it.groupValues[1].replace(",", "").toDoubleOrNull() }
                    .filter { it > 0 }
                    .toList()
                if (allNumbers.isNotEmpty()) {
                    amount = allNumbers.max()
                }
            }

            // ── 5. Merchant fallback: use domain from URL or first text segment ──
            if (merchant.isEmpty() && qrString.contains("://")) {
                val host = Regex("""://([^/?#]+)""").find(qrString)?.groupValues?.get(1) ?: ""
                if (host.isNotBlank()) {
                    merchant = host.removePrefix("www.")
                        .substringBeforeLast(".")
                        .replace("-", " ").replace("_", " ")
                        .replaceFirstChar { it.uppercase() }
                }
            }
            if (merchant.isEmpty()) {
                // Take the first alphanumeric segment that looks like a name
                val firstWord = Regex("""[A-Za-z\u0400-\u04FF\u0530-\u058F]{3,}[\w\s]*""")
                    .find(qrString)?.value?.trim()?.take(50) ?: ""
                if (firstWord.isNotBlank()) merchant = firstWord
            }
            if (merchant.isEmpty()) merchant = "QR Receipt"

            return ParsedReceipt(totalAmount = amount, items = emptyList(), merchantName = merchant, date = date)
        } catch (_: Throwable) {
            return ParsedReceipt(null, emptyList(), "QR Receipt", null)
        }
    }
}
