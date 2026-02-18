package com.smartexpense.tracker.service.ai

import com.smartexpense.tracker.data.model.*
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
            "uber", "lyft", "taxi", "gas", "fuel", "parking", "toll", "metro",
            "bus", "train", "airline", "flight", "car wash", "auto", "mechanic",
            "oil change", "tire", "transit", "shell", "chevron", "bp", "exxon",
            "ola", "rapido", "irctc"
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

    fun categorize(description: String): String {
        val lowerDesc = description.lowercase()
        // Pass 1 – exact substring match, scored by keyword length (longer = more specific)
        var bestMatch: String? = null
        var bestScore = 0
        for ((category, keywords) in categoryKeywords) {
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
            // Skip pure income/investment categories for fallback (unlikely for POS merchants)
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
        budgets: List<Budget>
    ): List<AiSuggestion> {
        if (transactions.isEmpty()) return emptyList()
        val suggestions = mutableListOf<AiSuggestion>()
        val expenses = transactions.filter { it.type == TransactionType.EXPENSE }
        val now = System.currentTimeMillis()
        val thirtyDaysAgo = now - 30L * 24 * 60 * 60 * 1000
        val recentExpenses = expenses.filter { it.timestamp > thirtyDaysAgo }

        suggestions.addAll(detectHighSpendingCategories(recentExpenses))
        suggestions.addAll(detectSubscriptionOptimizations(recentExpenses))
        suggestions.addAll(detectSpendingSpikes(expenses))
        suggestions.addAll(detectBudgetOverruns(recentExpenses, budgets))
        suggestions.addAll(detectDiningPatterns(recentExpenses))
        suggestions.addAll(detectWeekendSpending(recentExpenses))
        suggestions.addAll(detectSavingsPotential(recentExpenses, transactions))

        return suggestions.take(10)
    }

    private fun detectHighSpendingCategories(expenses: List<Transaction>): List<AiSuggestion> {
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
                            "(\$${String.format("%.2f", amount)} this month). Consider setting " +
                            "a budget limit and finding alternatives.",
                    potentialSaving = amount * 0.2, category = category,
                    priority = SuggestionPriority.HIGH
                ))
            }
        }
        return suggestions
    }

    private fun detectSubscriptionOptimizations(expenses: List<Transaction>): List<AiSuggestion> {
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
                        "\$${String.format("%.2f", totalSubCost)}. Consider consolidating or canceling unused ones.",
                potentialSaving = totalSubCost * 0.25, category = "Entertainment",
                priority = SuggestionPriority.MEDIUM
            ))
        }
        return suggestions
    }

    private fun detectSpendingSpikes(expenses: List<Transaction>): List<AiSuggestion> {
        val suggestions = mutableListOf<AiSuggestion>()
        val now = System.currentTimeMillis()
        val oneWeekAgo = now - 7L * 24 * 60 * 60 * 1000
        val twoWeeksAgo = now - 14L * 24 * 60 * 60 * 1000
        val thisWeek = expenses.filter { it.timestamp > oneWeekAgo }.sumOf { it.amount }
        val lastWeek = expenses.filter { it.timestamp in twoWeeksAgo..oneWeekAgo }.sumOf { it.amount }
        if (lastWeek > 0 && thisWeek > lastWeek * 1.5) {
            suggestions.add(AiSuggestion(
                title = "Spending spike detected",
                description = "This week's spending (\$${String.format("%.2f", thisWeek)}) is " +
                        "${((thisWeek / lastWeek - 1) * 100).roundToInt()}% higher than last week.",
                potentialSaving = thisWeek - lastWeek, category = "General",
                priority = SuggestionPriority.HIGH
            ))
        }
        return suggestions
    }

    private fun detectBudgetOverruns(expenses: List<Transaction>, budgets: List<Budget>): List<AiSuggestion> {
        val suggestions = mutableListOf<AiSuggestion>()
        for (budget in budgets) {
            val spent = expenses.filter { it.category == budget.categoryId }.sumOf { it.amount }
            if (spent > budget.monthlyLimit * budget.alertThreshold) {
                val percentage = (spent / budget.monthlyLimit * 100).roundToInt()
                suggestions.add(AiSuggestion(
                    title = "Budget alert: ${budget.categoryId}",
                    description = "You've used $percentage% of your \$${String.format("%.2f", budget.monthlyLimit)} budget.",
                    potentialSaving = spent - budget.monthlyLimit, category = budget.categoryId,
                    priority = if (spent > budget.monthlyLimit) SuggestionPriority.HIGH else SuggestionPriority.MEDIUM
                ))
            }
        }
        return suggestions
    }

    private fun detectDiningPatterns(expenses: List<Transaction>): List<AiSuggestion> {
        val suggestions = mutableListOf<AiSuggestion>()
        val dining = expenses.filter { it.category == "Food & Dining" }
        if (dining.size > 10) {
            val total = dining.sumOf { it.amount }
            val avgMeal = total / dining.size
            suggestions.add(AiSuggestion(
                title = "Dining out frequency",
                description = "You've dined out ${dining.size} times recently, averaging " +
                        "\$${String.format("%.2f", avgMeal)} per meal. Cooking at home 2 more " +
                        "days per week could save significant money.",
                potentialSaving = avgMeal * 8, category = "Food & Dining",
                priority = SuggestionPriority.MEDIUM
            ))
        }
        return suggestions
    }

    private fun detectWeekendSpending(expenses: List<Transaction>): List<AiSuggestion> {
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
                    description = "Your average weekend transaction (\$${String.format("%.2f", avgWeekend)}) " +
                            "is ${(avgWeekend / avgWeekday).roundToInt()}x higher than weekday (\$${String.format("%.2f", avgWeekday)}).",
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

    // ─── SMS / Notification Parsing ────────────────────────────────

    data class ParsedTransaction(
        val amount: Double,
        val description: String,
        val isExpense: Boolean,
        val merchantName: String,
        val currency: String = ""
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

    fun parseFinancialMessage(message: String): ParsedTransaction? {
        try {
            val lowerMsg = message.lowercase()

            // Reject pre-auth / authorisation-hold messages — they are not real transactions
            if (preAuthKeywords.any { lowerMsg.contains(it.lowercase()) }) {
                return null
            }

            val oneLine = message.replace(Regex("""\s*\n\s*"""), " ").trim()

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
                return ParsedTransaction(amt, "${txType.trim()} at $merchant", isExpense, merchant, cur)
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
                return ParsedTransaction(amt, "${txType.trim()} at $merchant", true, merchant, cur)
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
                return ParsedTransaction(amt, "$label from $merchant", isExpense, merchant, cur)
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

            // ── EXPENSE VS INCOME ───────────────────────

            val expenseWords = listOf(
                "purchase", "atm cash", "atm", "mail order", "pos",
                "charged", "debited", "spent", "paid", "withdrawal",
                "sent", "debit", "withdrawn", "payment of", "used at",
                "debit account", "e-commerce", "online purchase"
            )
            val incomeWords = listOf(
                "credit account", "credited", "received", "deposit", "refund",
                "cashback", "transfer to your", "added to", "reversed",
                "salary", "income", "reward"
            )

            val isExpense = when {
                incomeWords.any { lowerMsg.contains(it) } -> false
                expenseWords.any { lowerMsg.contains(it) } -> true
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
                merchantName = merchant, currency = currency
            )
        } catch (e: Exception) {
            return null
        }
    }

    // ─── Receipt OCR Text Parsing ──────────────────────────────────

    data class ParsedReceipt(
        val totalAmount: Double?,
        val items: List<Pair<String, Double>>,
        val merchantName: String,
        val date: String?
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

            // Merchant name — first non-numeric, non-date, substantive line.
            // Excludes POS terminal boilerplate lines (TID, MID, Tarihi, AUTH*, card numbers).
            val merchantName = lines.firstOrNull { line ->
                line.length > 2 &&
                !line.matches(Regex("""^[\d\s/\-:.]+$""")) &&
                !line.matches(Regex("""(?i)^(receipt|invoice|bill|date|time|tel|phone|fax|www|tid|mid|tarihi|sale|вaчaрq|authcode|auth code|approved|visa|mastercard|\*+.*).*""")) &&
                !line.matches(Regex("""^\*[\d\*\s]+$"""))  // masked card numbers
            }?.take(50) ?: "Unknown Store"

            // ── Build currency-specific amount patterns ───────────────
            // currencySymbolPattern is a regex fragment matching the currency marker(s).
            val currencySymbolPattern: String = when (currencyCode.uppercase()) {
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
                else  -> """(?:${Regex.escape(currencyCode)}|[$₹€£֏¥₽₺₩])\s*"""
            }

            // Generic "any supported symbol" fallback used in item-line matching
            val anySymbol = """[$₹€£֏¥₽₺₩]?"""

            // Extract items
            val items = mutableListOf<Pair<String, Double>>()
            val itemPatterns = listOf(
                // Item   <currency>AMOUNT
                Regex("""(.{3,40}?)\s+${currencySymbolPattern}([\d,]+\.\d{2})\s*$""", RegexOption.IGNORE_CASE),
                // Item   AMOUNT (no symbol)
                Regex("""(.{3,40}?)\s+(\d+\.\d{2})"""),
                // Item    <any-symbol>AMOUNT (wider match)
                Regex("""^(.+?)\s{2,}${anySymbol}\s?([\d,]+\.\d{2})"""),
                // Whole-number items: "Item  500"
                Regex("""^(.{3,40}?)\s{2,}${anySymbol}\s?(\d{1,7})(?:\s*${'$'})""")
            )
            for (line in lines) {
                for (pattern in itemPatterns) {
                    val match = pattern.find(line)
                    if (match != null) {
                        val name = match.groupValues[1].trim()
                        val price = match.groupValues[2].replace(",", "").toDoubleOrNull()
                        if (price != null && price > 0 && price < 100000 &&
                            !name.lowercase().let { n ->
                                n.contains("total") || n.contains("subtotal") ||
                                n.contains("tax") || n.contains("change") || n.contains("cash") ||
                                n.contains("balance") || n.contains("due")
                            }
                        ) {
                            items.add(name to price)
                            break
                        }
                    }
                }
            }

            // ── Total extraction ──────────────────────────────────────
            // Priority-1: currency-specific total markers
            val currencyTotalPatterns: List<Regex> = when (currencyCode.uppercase()) {
                "AMD" -> listOf(
                    // Armenian POS terminal receipts: "Գumарi: 12 000.00 AMD"
                    // "Գumар" / "Гumari" = Armenian for "sum/amount"
                    // Handles space-as-thousands-separator: "12 000.00"
                    Regex("""(?:Գumар[ий]?|Гumari?|gumar[i]?)[:\s]+([0-9][0-9\s,]*\.?[0-9]*)\s*(?:AMD|֏|դрам)""", RegexOption.IGNORE_CASE),
                    // "ԸՆДAMENHH" = "total" in Armenian; also "TOTAL", "AMD", "֏"
                    Regex("""(?:ԸՆДАМENNH|ընдамennh|total|grand\s*total)[:\s]*(?:֏|AMD|դрам)?\s*([0-9][0-9\s,]*\.?[0-9]*)""", RegexOption.IGNORE_CASE),
                    // POS receipt: number immediately before AMD/֏ — "12 000.00 AMD"
                    Regex("""([0-9][0-9\s,]*\.[0-9]{1,2})\s*(?:AMD|֏|դрам)""", RegexOption.IGNORE_CASE),
                    // AMD/֏ before number — "AMD 12000.00" / "֏ 12,000"
                    Regex("""(?:֏|AMD|դрам)[:\s]+([0-9][0-9\s,]*\.?[0-9]*)""", RegexOption.IGNORE_CASE)
                )
                "RUB" -> listOf(
                    Regex("""(?:ИТОГО|итого|total)[:\s]*(?:₽|руб\.?)?\s*([\d,\s]+\.?\d*)""", RegexOption.IGNORE_CASE),
                    Regex("""(?:₽|руб\.?)\s*([\d,\s]+\.?\d*)""")
                )
                "USD" -> listOf(
                    Regex("""(?:grand\s*total|total\s*due|amount\s*due|balance\s*due|total)[:\s]*\$?\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE),
                    Regex("""\$\s*([\d,]+\.\d{2})""")
                )
                else -> listOf(
                    Regex("""(?:grand\s*total|total\s*due|total)[:\s]*${currencySymbolPattern}?([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
                )
            }

            // Priority-2: generic total patterns (catch-all)
            val genericTotalPatterns = listOf(
                Regex("""(?:grand\s*total|total\s*due|amount\s*due|total\s*amount|balance\s*due)[:\s]*[$₹€£֏¥₽₺₩]?\s?([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE),
                Regex("""(?:TOTAL|Total|GRAND TOTAL)\s*:?\s*[$₹€£֏¥₽₺₩]?\s?([\d,]+\.\d{2})"""),
                Regex("""(?:total)[:\s]*(?:rs\.?|₹|\$|€|£|֏|[A-Z]{3})?\s?([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE),
                Regex("""(?:TOTAL)\s+[$₹€£֏¥₽₺₩]?([\d,]+\.?\d{0,2})"""),
                Regex("""(?:ИТОГО|итого)[:\s]*([\d,\s]+\.?\d*)""")
            )

            var total: Double? = null
            for (pattern in currencyTotalPatterns + genericTotalPatterns) {
                val match = pattern.findAll(ocrText).lastOrNull()
                if (match != null) {
                    total = match.groupValues[1].replace(Regex("""[\s,]"""), "").toDoubleOrNull()
                    if (total != null && total > 0) break
                    total = null
                }
            }

            // Fallback: use largest item price or sum of items
            if (total == null && items.isNotEmpty()) {
                val sum = items.sumOf { it.second }
                val max = items.maxOf { it.second }
                total = if (sum > max * 1.5) sum else max
            }

            // Date — also recognises "Tarihi: DD/MM/YY" found on Armenian/Turkish POS terminals
            val datePatterns = listOf(
                Regex("""(?:tarihi|date|dated?)[:\s]*(\d{1,2}[/\-]\d{1,2}[/\-]\d{2,4})""", RegexOption.IGNORE_CASE),
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

            return ParsedReceipt(totalAmount = total, items = items, merchantName = merchantName, date = date)
        } catch (e: Exception) {
            return ParsedReceipt(totalAmount = null, items = emptyList(), merchantName = "Unknown", date = null)
        }
    }
}
