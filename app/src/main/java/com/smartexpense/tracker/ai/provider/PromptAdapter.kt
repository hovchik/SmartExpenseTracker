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
        currencyCode: String = ""
    ): String {
        val categoriesList = categories.joinToString(", ")
        val amountStr = if (amount > 0) "\nAmount: $currencyCode ${String.format("%.2f", amount)}" else ""
        val merchantStr = if (merchantName.isNotBlank()) "\nMerchant: $merchantName" else ""
        return """You are a financial transaction categorizer. Categorize this transaction into the most appropriate category.

Transaction: "$description"$merchantStr$amountStr
Type: ${if (isExpense) "expense" else "income"}

Existing categories: $categoriesList

Instructions:
- If the transaction clearly fits one of the existing categories, reply with that exact category name.
- If none of the existing categories fit well, you may suggest a new descriptive category name (e.g. "Pet Care", "Subscriptions", "Personal Care").
- Reply with ONLY the category name on the first line, nothing else.
- Do not add explanations, punctuation, or extra text."""
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
        val sb = StringBuilder()
        sb.appendLine("You are a financial analyst. Provide a brief AI insight for this $periodLabel expense report.")
        sb.appendLine()
        sb.appendLine("Total expenses: $currencyCode ${String.format("%.2f", totalExpenses)}")
        sb.appendLine("Total income: $currencyCode ${String.format("%.2f", totalIncome)}")
        sb.appendLine("Transactions: $transactionCount")

        if (comparisonWithPrevious != 0.0) {
            sb.appendLine("Change vs previous period: ${String.format("%+.1f", comparisonWithPrevious)}%")
        }

        if (categoryBreakdown.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("Category breakdown:")
            categoryBreakdown.entries.sortedByDescending { it.value }.take(6).forEach { (cat, amt) ->
                sb.appendLine("  $cat: $currencyCode ${String.format("%.2f", amt)}")
            }
        }

        if (topMerchants.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("Top merchants:")
            topMerchants.entries.sortedByDescending { it.value }.take(5).forEach { (m, amt) ->
                sb.appendLine("  $m: $currencyCode ${String.format("%.2f", amt)}")
            }
        }

        if (dayOfWeekSpending.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("Day-of-week spending:")
            dayOfWeekSpending.forEach { (day, amt) ->
                sb.appendLine("  $day: $currencyCode ${String.format("%.2f", amt)}")
            }
        }

        sb.appendLine()
        sb.appendLine("Write 1-2 sentence insight. Be specific and actionable. Do not restate the numbers.")
        return sb.toString()
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
