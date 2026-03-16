package com.smartexpense.tracker.ai.provider

/**
 * Adapts prompts for different AI runtimes.
 * Ensures the same prompt used for Claude also works with local models
 * that may not support structured JSON output.
 */
class PromptAdapter {

    /**
     * Adapts a prompt based on runtime capabilities.
     * If the runtime doesn't support structured JSON, simplifies the prompt.
     */
    fun adaptPrompt(prompt: String, supportsStructuredJson: Boolean = false): String {
        if (supportsStructuredJson) return prompt

        // Strip JSON formatting instructions for runtimes that don't support them
        return prompt
            .replace(Regex("""(?i)respond\s+(?:only\s+)?in\s+json\s*\.?"""), "")
            .replace(Regex("""(?i)format\s+(?:your\s+)?(?:response|output)\s+as\s+json\s*\.?"""), "")
            .replace(Regex("""(?i)return\s+(?:only\s+)?(?:a\s+)?json\s+(?:object|array)\s*\.?"""), "")
            .trim()
    }

    /**
     * Creates a categorization prompt compatible with both cloud and local models.
     */
    fun createCategorizationPrompt(
        description: String,
        categories: List<String>,
        isExpense: Boolean
    ): String {
        val categoriesList = categories.joinToString(", ")
        return """Categorize this transaction into exactly one category.
Transaction: "$description"
Type: ${if (isExpense) "expense" else "income"}
Available categories: $categoriesList
Reply with ONLY the category name, nothing else."""
    }

    /**
     * Creates an insight generation prompt compatible with both cloud and local models.
     */
    fun createInsightPrompt(
        totalExpenses: Double,
        totalIncome: Double,
        topCategory: String?,
        topCategoryAmount: Double,
        transactionCount: Int,
        currencyCode: String
    ): String {
        return """You are a personal finance advisor. Generate a brief, actionable insight (1-2 sentences) based on:
- Total expenses: $currencyCode ${String.format("%.2f", totalExpenses)}
- Total income: $currencyCode ${String.format("%.2f", totalIncome)}
- Top spending category: ${topCategory ?: "N/A"} ($currencyCode ${String.format("%.2f", topCategoryAmount)})
- Transaction count: $transactionCount
Keep it concise and helpful."""
    }
}
