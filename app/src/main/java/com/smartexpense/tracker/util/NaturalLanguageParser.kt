package com.smartexpense.tracker.util

import com.smartexpense.tracker.data.model.TransactionType
import java.util.Calendar
import java.util.regex.Pattern

/**
 * Parses natural language input into transaction data.
 *
 * Examples:
 *  - "Coffee $4.50" → expense, $4.50, "Coffee"
 *  - "Salary $3000 income" → income, $3000, "Salary"
 *  - "Uber $12 yesterday" → expense, $12, "Uber", timestamp = yesterday
 *  - "Groceries 50" → expense, $50, "Groceries"
 *  - "Lunch 15.99 food" → expense, $15.99, "Lunch"
 */
object NaturalLanguageParser {

    data class ParsedInput(
        val description: String,
        val amount: Double,
        val type: TransactionType,
        val timestamp: Long,
        val category: String? = null,
        val tags: List<String> = emptyList()
    )

    // Currency symbol patterns
    private val AMOUNT_PATTERN = Pattern.compile(
        """(?:[\$€£¥₹₽₺֏₩])\s*(\d+(?:[.,]\d{1,2})?)""" +
        """|(\d+(?:[.,]\d{1,2})?)\s*(?:[\$€£¥₹₽₺֏₩])""" +
        """|(?:^|\s)(\d+(?:\.\d{1,2})?)(?:\s|$)"""
    )

    private val INCOME_WORDS = setOf(
        "income", "salary", "freelance", "payment", "received",
        "earned", "bonus", "refund", "reimbursement", "dividend"
    )

    private val TIME_WORDS = mapOf(
        "yesterday" to -1,
        "day before yesterday" to -2,
        "last week" to -7,
        "2 days ago" to -2,
        "3 days ago" to -3,
        "4 days ago" to -4,
        "5 days ago" to -5,
        "6 days ago" to -6,
        "7 days ago" to -7
    )

    /**
     * Parses a natural language string into transaction components.
     * Returns null if no amount could be extracted.
     */
    fun parse(input: String, categoryNames: List<String> = emptyList()): ParsedInput? {
        if (input.isBlank()) return null

        val trimmed = input.trim()
        val lower = trimmed.lowercase()

        // 1. Extract amount
        val amount = extractAmount(trimmed) ?: return null

        // 2. Determine transaction type
        val type = if (INCOME_WORDS.any { lower.contains(it) }) {
            TransactionType.INCOME
        } else {
            TransactionType.EXPENSE
        }

        // 3. Extract timestamp from time words
        var timestamp = System.currentTimeMillis()
        var timeWordFound = ""
        for ((word, daysOffset) in TIME_WORDS) {
            if (lower.contains(word)) {
                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_YEAR, daysOffset)
                timestamp = cal.timeInMillis
                timeWordFound = word
                break
            }
        }

        // 4. Extract description (everything except amount, type keywords, time words)
        var description = trimmed
        // Remove amount with currency symbol
        description = description.replace(Regex("""[\$€£¥₹₽₺֏₩]\s*\d+(?:[.,]\d{1,2})?"""), "").trim()
        description = description.replace(Regex("""\d+(?:[.,]\d{1,2})?\s*[\$€£¥₹₽₺֏₩]"""), "").trim()
        // Remove standalone numbers that are the amount
        val amountStr = if (amount == amount.toLong().toDouble()) {
            amount.toLong().toString()
        } else {
            String.format("%.2f", amount)
        }
        description = description.replace(Regex("""\b${Regex.escape(amountStr)}\b"""), "").trim()
        // Also try without trailing zeros
        description = description.replace(Regex("""\b${Regex.escape(String.format("%.1f", amount))}\b"""), "").trim()
        description = description.replace(Regex("""\b${amount.toInt()}\b"""), "").trim()

        // Remove income/type keywords
        for (word in INCOME_WORDS) {
            description = description.replace(Regex("""\b$word\b""", RegexOption.IGNORE_CASE), "").trim()
        }
        // Remove time words
        if (timeWordFound.isNotEmpty()) {
            description = description.replace(timeWordFound, "", ignoreCase = true).trim()
        }

        // Clean up extra spaces and punctuation
        description = description.replace(Regex("""\s+"""), " ").trim()
        description = description.trimEnd(',', '.', '-', ' ')

        if (description.isEmpty()) {
            description = if (type == TransactionType.INCOME) "Income" else "Expense"
        }

        // Capitalize first letter
        description = description.replaceFirstChar { it.uppercase() }

        // 5. Try to match a category from the description
        val matchedCategory = categoryNames.firstOrNull { cat ->
            lower.contains(cat.lowercase())
        }
        // Remove matched category from description if it's a separate word
        if (matchedCategory != null) {
            description = description.replace(Regex("""\b${Regex.escape(matchedCategory)}\b""", RegexOption.IGNORE_CASE), "").trim()
            if (description.isEmpty()) {
                description = matchedCategory
            }
        }

        // 6. Extract hashtags as tags
        val tagPattern = Regex("""#(\w+)""")
        val tags = tagPattern.findAll(trimmed).map { it.groupValues[1] }.toList()
        if (tags.isNotEmpty()) {
            description = description.replace(tagPattern, "").trim()
        }

        return ParsedInput(
            description = description,
            amount = amount,
            type = type,
            timestamp = timestamp,
            category = matchedCategory,
            tags = tags
        )
    }

    private fun extractAmount(input: String): Double? {
        // Try patterns with currency symbols first
        val symbolPattern = Regex("""[\$€£¥₹₽₺֏₩]\s*(\d+(?:[.,]\d{1,2})?)""")
        symbolPattern.find(input)?.let {
            return it.groupValues[1].replace(",", ".").toDoubleOrNull()
        }

        val symbolAfterPattern = Regex("""(\d+(?:[.,]\d{1,2})?)\s*[\$€£¥₹₽₺֏₩]""")
        symbolAfterPattern.find(input)?.let {
            return it.groupValues[1].replace(",", ".").toDoubleOrNull()
        }

        // Try standalone number
        val numberPattern = Regex("""(?:^|\s)(\d+(?:\.\d{1,2})?)(?:\s|$)""")
        numberPattern.find(input)?.let {
            return it.groupValues[1].toDoubleOrNull()
        }

        // Last resort: any number in the string
        val anyNumber = Regex("""(\d+(?:\.\d{1,2})?)""")
        anyNumber.find(input)?.let {
            return it.groupValues[1].toDoubleOrNull()
        }

        return null
    }
}
