package com.smartexpense.tracker.ai.provider

/**
 * Adapts prompts for different AI runtimes and provides robust parsing
 * of AI responses for categorization, insights, and reports.
 */
class PromptAdapter {

    /**
     * Adapts a prompt based on runtime capabilities.
     * Strips JSON formatting instructions for runtimes that can't handle them.
     */
    fun adaptPrompt(prompt: String, supportsStructuredJson: Boolean = false): String {
        if (supportsStructuredJson) return prompt

        return prompt
            .replace(Regex("""(?i)respond\s+(?:only\s+)?in\s+json\s*\.?"""), "")
            .replace(Regex("""(?i)format\s+(?:your\s+)?(?:response|output)\s+as\s+json\s*\.?"""), "")
            .replace(Regex("""(?i)return\s+(?:only\s+)?(?:a\s+)?json\s+(?:object|array)\s*\.?"""), "")
            .trim()
    }

    /**
     * Creates a detailed categorization prompt that also allows the AI to suggest
     * new categories when none of the existing ones fit well.
     */
    fun createCategorizationPrompt(
        description: String,
        categories: List<String>,
        isExpense: Boolean,
        merchantName: String = "",
        amount: Double = 0.0,
        currencyCode: String = "",
        tags: List<String> = emptyList(),
        notes: String = "",
        isRecurring: Boolean = false,
        dateTime: String = "",
        source: String = "",
        hasLocation: Boolean = false
    ): String {
        val categoriesList = categories.joinToString(", ")
        val sb = StringBuilder()
        sb.appendLine("You are a financial transaction categorizer. Categorize this transaction into the most appropriate category.")
        sb.appendLine()
        sb.appendLine("=== Transaction Details ===")
        sb.appendLine("Description: \"$description\"")
        sb.appendLine("Type: ${if (isExpense) "expense" else "income"}")
        if (amount > 0) sb.appendLine("Amount: $currencyCode ${String.format("%.2f", amount)}")
        if (merchantName.isNotBlank()) sb.appendLine("Merchant: $merchantName")
        if (dateTime.isNotBlank()) sb.appendLine("Date/Time: $dateTime")
        if (source.isNotBlank()) sb.appendLine("Source: $source")
        if (isRecurring) sb.appendLine("Recurring: yes")
        if (tags.isNotEmpty()) sb.appendLine("Tags: ${tags.joinToString(", ")}")
        if (notes.isNotBlank()) sb.appendLine("Notes: $notes")
        if (hasLocation) sb.appendLine("Location: has GPS coordinates (in-store purchase)")

        sb.appendLine()
        sb.appendLine("Existing categories: $categoriesList")
        sb.appendLine()
        sb.appendLine("Instructions:")
        sb.appendLine("- If the transaction clearly fits one of the existing categories, reply with that exact category name.")
        sb.appendLine("- If none of the existing categories fit well, you may suggest a new descriptive category name (e.g. \"Pet Care\", \"Subscriptions\", \"Personal Care\").")
        sb.appendLine("- Reply with ONLY the category name on the first line, nothing else.")
        sb.appendLine("- Do not add explanations, punctuation, or extra text.")
        return sb.toString().trim()
    }

    /**
     * Creates a rich insight prompt with detailed financial context.
     */
    fun createInsightPrompt(
        totalExpenses: Double,
        totalIncome: Double,
        topCategory: String?,
        topCategoryAmount: Double,
        transactionCount: Int,
        currencyCode: String,
        categoryBreakdown: Map<String, Double> = emptyMap(),
        recentTransactions: List<TransactionSummary> = emptyList(),
        previousPeriodExpenses: Double = 0.0,
        averageDailySpend: Double = 0.0,
        budgetLimits: Map<String, Double> = emptyMap()
    ): String {
        val sb = StringBuilder()
        sb.appendLine("You are a personal finance advisor. Analyze the following spending data and provide 2-3 brief, actionable insights.")
        sb.appendLine()
        sb.appendLine("=== Financial Summary ===")
        sb.appendLine("Period total expenses: $currencyCode ${String.format("%.2f", totalExpenses)}")
        sb.appendLine("Period total income: $currencyCode ${String.format("%.2f", totalIncome)}")
        sb.appendLine("Net balance: $currencyCode ${String.format("%.2f", totalIncome - totalExpenses)}")
        sb.appendLine("Transaction count: $transactionCount")

        if (averageDailySpend > 0) {
            sb.appendLine("Average daily spend: $currencyCode ${String.format("%.2f", averageDailySpend)}")
        }
        if (previousPeriodExpenses > 0) {
            val change = ((totalExpenses - previousPeriodExpenses) / previousPeriodExpenses * 100)
            sb.appendLine("Change vs previous period: ${String.format("%+.1f", change)}%")
        }

        if (categoryBreakdown.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("=== Category Breakdown ===")
            categoryBreakdown.entries
                .sortedByDescending { it.value }
                .take(8)
                .forEach { (cat, amount) ->
                    val pct = if (totalExpenses > 0) (amount / totalExpenses * 100) else 0.0
                    sb.appendLine("- $cat: $currencyCode ${String.format("%.2f", amount)} (${String.format("%.1f", pct)}%)")
                }
        }

        if (budgetLimits.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("=== Budget Status ===")
            budgetLimits.forEach { (cat, limit) ->
                val spent = categoryBreakdown[cat] ?: 0.0
                val pct = if (limit > 0) (spent / limit * 100) else 0.0
                sb.appendLine("- $cat: $currencyCode ${String.format("%.2f", spent)} / $currencyCode ${String.format("%.2f", limit)} (${String.format("%.0f", pct)}% used)")
            }
        }

        if (recentTransactions.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("=== Recent Large Transactions ===")
            recentTransactions.take(5).forEach { tx ->
                sb.appendLine("- ${tx.description}: $currencyCode ${String.format("%.2f", tx.amount)} (${tx.category})")
            }
        }

        sb.appendLine()
        sb.appendLine("Provide 2-3 concise, actionable insights. Each insight should be 1-2 sentences.")
        sb.appendLine("Focus on: spending patterns, budget adherence, saving opportunities, and unusual transactions.")
        sb.appendLine("Do not repeat the numbers. Be specific and practical.")

        return sb.toString()
    }

    /**
     * Creates a report-level AI insight prompt with comprehensive data.
     */
    fun createReportInsightPrompt(
        periodLabel: String,
        totalExpenses: Double,
        totalIncome: Double,
        categoryBreakdown: Map<String, Double>,
        topMerchants: Map<String, Double>,
        transactionCount: Int,
        currencyCode: String,
        comparisonWithPrevious: Double,
        dayOfWeekSpending: Map<String, Double> = emptyMap()
    ): String {
        val sym = com.smartexpense.tracker.data.model.currencyInfoFor(currencyCode).symbol
        val sb = StringBuilder()
        sb.appendLine("You are a financial analyst providing a brief insight for a client's $periodLabel expense report.")
        sb.appendLine()
        sb.appendLine("IMPORTANT: All monetary amounts in your response MUST use \"$sym\" ($currencyCode). Never convert to another currency.")
        sb.appendLine()
        sb.appendLine("=== Report Data (all amounts in $sym $currencyCode) ===")
        sb.appendLine("Total expenditure: $sym${String.format("%.2f", totalExpenses)}")
        sb.appendLine("Total income: $sym${String.format("%.2f", totalIncome)}")
        sb.appendLine("Net cash flow: $sym${String.format("%.2f", totalIncome - totalExpenses)}")
        sb.appendLine("Transactions: $transactionCount")

        if (comparisonWithPrevious != 0.0) {
            val direction = if (comparisonWithPrevious > 0) "increase" else "decrease"
            sb.appendLine("Period-over-period: ${String.format("%.1f", kotlin.math.abs(comparisonWithPrevious))}% $direction")
        }

        if (categoryBreakdown.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("Category breakdown:")
            categoryBreakdown.entries.sortedByDescending { it.value }.take(6).forEach { (cat, amt) ->
                sb.appendLine("  $cat: $sym${String.format("%.2f", amt)}")
            }
        }

        if (topMerchants.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("Top merchants:")
            topMerchants.entries.sortedByDescending { it.value }.take(5).forEach { (m, amt) ->
                sb.appendLine("  $m: $sym${String.format("%.2f", amt)}")
            }
        }

        if (dayOfWeekSpending.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("Day-of-week spending:")
            dayOfWeekSpending.forEach { (day, amt) ->
                sb.appendLine("  $day: $sym${String.format("%.2f", amt)}")
            }
        }

        sb.appendLine()
        sb.appendLine("Write 1-2 sentence financial insight. Use $sym ($currencyCode) for any amounts. Be specific, actionable, and use proper financial terminology. Do not restate raw numbers.")
        return sb.toString()
    }

    /**
     * Creates a prompt for generating actionable expense reduction tips based on report data.
     * The prompt enforces financial terminology and consistent currency usage.
     */
    fun createExpenseReductionPrompt(
        totalExpenses: Double,
        totalIncome: Double,
        categoryBreakdown: Map<String, Double>,
        topMerchants: Map<String, Double>,
        transactionCount: Int,
        currencyCode: String,
        comparisonWithPrevious: Double,
        dayOfWeekSpending: Map<String, Double> = emptyMap(),
        averageDailySpend: Double = 0.0
    ): String {
        val sym = com.smartexpense.tracker.data.model.currencyInfoFor(currencyCode).symbol
        val sb = StringBuilder()
        sb.appendLine("You are a certified financial planner reviewing a client's personal expense report.")
        sb.appendLine("Analyze the financial data below and provide 3-4 precise, actionable expense-reduction recommendations.")
        sb.appendLine()
        sb.appendLine("CRITICAL RULES:")
        sb.appendLine("- ALL monetary amounts in your response MUST use the currency symbol \"$sym\" ($currencyCode). Never convert to another currency.")
        sb.appendLine("- Use proper financial terminology (e.g. discretionary spending, fixed vs variable costs, debt-to-income ratio, expense-to-income ratio, cash flow).")
        sb.appendLine("- Each recommendation must cite specific categories or merchants from the data and include a concrete $sym savings estimate where possible.")
        sb.appendLine("- Base recommendations on established personal finance principles (e.g. 50/30/20 rule, zero-based budgeting, envelope method).")
        sb.appendLine()
        sb.appendLine("=== Client Financial Summary (all amounts in $sym $currencyCode) ===")
        sb.appendLine("Gross income: $sym${String.format("%.2f", totalIncome)}")
        sb.appendLine("Total expenditure: $sym${String.format("%.2f", totalExpenses)}")
        val netCashFlow = totalIncome - totalExpenses
        sb.appendLine("Net cash flow: $sym${String.format("%.2f", netCashFlow)}")
        sb.appendLine("Transaction volume: $transactionCount transactions")

        if (averageDailySpend > 0) {
            sb.appendLine("Average daily expenditure: $sym${String.format("%.2f", averageDailySpend)}")
        }
        if (comparisonWithPrevious != 0.0) {
            val direction = if (comparisonWithPrevious > 0) "increase" else "decrease"
            sb.appendLine("Period-over-period change: ${String.format("%.1f", kotlin.math.abs(comparisonWithPrevious))}% $direction")
        }
        if (totalIncome > 0) {
            val savingsRate = ((totalIncome - totalExpenses) / totalIncome * 100)
            val expenseRatio = (totalExpenses / totalIncome * 100)
            sb.appendLine("Expense-to-income ratio: ${String.format("%.1f", expenseRatio)}%")
            sb.appendLine("Savings rate: ${String.format("%.1f", savingsRate)}% (recommended target: >=20%)")
        }

        if (categoryBreakdown.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("=== Expenditure by Category ===")
            categoryBreakdown.entries.sortedByDescending { it.value }.take(8).forEach { (cat, amt) ->
                val pct = if (totalExpenses > 0) (amt / totalExpenses * 100) else 0.0
                sb.appendLine("  $cat: $sym${String.format("%.2f", amt)} (${String.format("%.1f", pct)}% of total)")
            }
        }

        if (topMerchants.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("=== Top Merchants by Spend ===")
            topMerchants.entries.sortedByDescending { it.value }.take(5).forEach { (m, amt) ->
                sb.appendLine("  $m: $sym${String.format("%.2f", amt)}")
            }
        }

        if (dayOfWeekSpending.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("=== Spending Distribution by Day of Week ===")
            val weekendDays = listOf("Sat", "Sun")
            val weekendTotal = dayOfWeekSpending.filter { it.key in weekendDays }.values.sum()
            val weekdayTotal = dayOfWeekSpending.filter { it.key !in weekendDays }.values.sum()
            dayOfWeekSpending.forEach { (day, amt) ->
                sb.appendLine("  $day: $sym${String.format("%.2f", amt)}")
            }
            if (weekendTotal > 0 && weekdayTotal > 0) {
                sb.appendLine("  Weekend total: $sym${String.format("%.2f", weekendTotal)} | Weekday total: $sym${String.format("%.2f", weekdayTotal)}")
            }
        }

        sb.appendLine()
        sb.appendLine("=== Response Format ===")
        sb.appendLine("- Provide exactly 3-4 recommendations, each on its own line.")
        sb.appendLine("- Each recommendation: 1-2 sentences. Be specific — name the category or merchant, state the potential $sym savings amount.")
        sb.appendLine("- Use only $sym ($currencyCode) for all monetary values. Do NOT use any other currency.")
        sb.appendLine("- Do not use numbering, bullet points, or prefixes. Write each recommendation as a standalone sentence.")
        sb.appendLine("- Do not repeat raw numbers from the data. Provide analysis and actionable advice.")
        return sb.toString()
    }

    /**
     * Parses AI-generated expense reduction tips from a response string.
     * Splits the response into individual tip lines.
     */
    fun parseExpenseReductionTips(response: String): List<String> {
        return response.trim()
            .lines()
            .map { line ->
                line.trim()
                    .removePrefix("-").removePrefix("•").removePrefix("*")
                    .replace(Regex("^\\d+[.):]\\s*"), "") // Remove numbering like "1. " or "1) "
                    .trim()
            }
            .filter { it.length > 10 } // Filter out too-short lines
            .take(4)
    }

    // ── Response Parsing ─────────────────────────────────────────────

    /**
     * Parses a categorization response from the AI.
     * Tries exact match first, then fuzzy match, then extracts a new category suggestion.
     *
     * @return A pair of (category, isNew) where isNew indicates the AI suggested a new category.
     */
    fun parseCategorization(response: String, existingCategories: List<String>): CategoryResult {
        val cleaned = response.trim()
            .lines().first().trim()                   // Take only the first line
            .removeSurrounding("\"")
            .removeSurrounding("'")
            .removeSurrounding("*")
            .replace(Regex("^(Category:\\s*)", RegexOption.IGNORE_CASE), "")
            .trim()

        if (cleaned.isBlank()) return CategoryResult(null, false)

        // Exact match (case-insensitive)
        existingCategories.find { it.equals(cleaned, ignoreCase = true) }
            ?.let { return CategoryResult(it, false) }

        // Partial match: AI response contains an existing category
        existingCategories.find { cleaned.contains(it, ignoreCase = true) }
            ?.let { return CategoryResult(it, false) }

        // Reverse: existing category contains the AI response
        existingCategories.find { it.contains(cleaned, ignoreCase = true) }
            ?.let { return CategoryResult(it, false) }

        // AI suggested a new category — validate it's a reasonable name
        if (cleaned.length in 2..40 && !cleaned.contains('\n') &&
            cleaned.matches(Regex("^[\\w\\s&/()-]+$"))) {
            // Capitalize properly
            val formatted = cleaned.split(" ").joinToString(" ") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
            return CategoryResult(formatted, true)
        }

        return CategoryResult(null, false)
    }

    /**
     * Parses an insight response, cleaning up common AI artifacts.
     */
    fun parseInsight(response: String): String {
        return response.trim()
            .removePrefix("Here are")
            .removePrefix("Here's")
            .removePrefix("Based on")
            .trimStart(':', ' ', '\n')
            .lines()
            .filter { it.isNotBlank() }
            .joinToString("\n")
            .take(1000) // Cap length
    }

    data class CategoryResult(
        val category: String?,
        val isNewCategory: Boolean
    )

    data class TransactionSummary(
        val description: String,
        val amount: Double,
        val category: String
    )
}
