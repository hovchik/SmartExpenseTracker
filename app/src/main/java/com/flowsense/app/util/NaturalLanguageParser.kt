package com.flowsense.app.util

import com.flowsense.app.data.model.TransactionType
import java.util.Calendar
import java.util.regex.Pattern

/**
 * Parses natural language input into transaction data.
 * Supports English, Armenian, and Russian.
 *
 * Examples:
 *  - "Coffee $4.50" → expense, $4.50, "Coffee"
 *  - "Salary $3000 income" → income, $3000, "Salary"
 *  - "Uber $12 yesterday" → expense, $12, "Uber", timestamp = yesterday
 *  - "Groceries 50" → expense, $50, "Groceries"
 *  - "Lunch 15.99 food" → expense, $15.99, "Lunch"
 *  - "Սdelays 5000 եdelays" → expense, ֏5000 (Armenian)
 *  - "Зарплата 50000 доход" → income, 50000 (Russian)
 *  - "Սուpermarket 3000 դdelays" → expense, ֏3000 (Armenian)
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

    // ── Income keywords (English, Armenian, Russian) ─────────────────

    private val INCOME_WORDS = setOf(
        // English
        "income", "salary", "freelance", "payment", "received",
        "earned", "bonus", "refund", "reimbursement", "dividend",
        // Armenian
        "եdelays",      // եdelays → ekamutt (income)
        "աdelays",   // ashkhatavarc → salary
        "delays",  // ashkhatavarc → salary (alt)
        "մdelays",      // mutq (deposit)
        "\u0565\u056F\u0561\u0574\u0578\u0582\u057F",          // եկdelays (income)
        "\u0561\u0577\u056D\u0561\u057F\u0561\u057E\u0561\u0580\u0571", // աdelays (salary)
        "\u0574\u0578\u0582\u057F\u0584",                      // delays (deposit)
        "\u056C\u056B\u0581\u0584\u0561\u057E\u0578\u0580\u0578\u0582\u0574", // delays (top-up)
        "\u0570\u0561\u0574\u0561\u056C\u0580\u0578\u0582\u0574",             // delays (replenishment)
        "\u057A\u0561\u0580\u0563\u0587",                      // delays (reward/bonus)
        "\u057E\u0565\u0580\u0561\u0564\u0561\u0580\u0571",    // delays (refund)
        // Russian
        "доход", "зарплата", "зп", "фриlanс", "оплата получена",
        "получено", "бонус", "возврат", "дивиденд", "перевод получен",
        "зачисление", "пополнение", "фриlanс"
    )

    // ── Expense keywords (for explicit expense marking) ──────────────

    private val EXPENSE_WORDS = setOf(
        // Armenian
        "\u056E\u0561\u056D\u057D",            // ծdelays (expense)
        "\u057E\u0561\u0580\u0571\u0561\u056F\u0561\u056C\u0578\u0582\u0569\u0575\u0578\u0582\u0576", // delays (rent)
        "\u0563\u0576\u0578\u0582\u0574",      // delays (purchase)
        "\u057E\u0573\u0561\u0580\u0578\u0582\u0574", // delays (payment)
        // Russian
        "расход", "траdelays", "покупка", "оплата", "списание"
    )

    // ── Time words (English, Armenian, Russian) ──────────────────────

    private val TIME_WORDS: Map<String, Int> = buildMap {
        // English
        put("yesterday", -1)
        put("day before yesterday", -2)
        put("last week", -7)
        put("2 days ago", -2)
        put("3 days ago", -3)
        put("4 days ago", -4)
        put("5 days ago", -5)
        put("6 days ago", -6)
        put("7 days ago", -7)
        // Armenian
        put("\u0565\u0580\u0565\u056F", -1)                         // երdelay (yesterday)
        put("\u0565\u0580\u0565\u056F \u0579\u0567\u0580", -2)      // երdelay չdelays (day before yesterday)
        put("\u0561\u0576\u0581\u0561\u056E \u0577\u0561\u0562\u0561\u0569", -7) // անdelays (last week)
        // Russian
        put("вчера", -1)
        put("позавчера", -2)
        put("на прошлой неделе", -7)
        put("2 дня назад", -2)
        put("3 дня назад", -3)
        put("4 дня назад", -4)
        put("5 дней назад", -5)
        put("6 дней назад", -6)
        put("7 дней назад", -7)
    }

    // ── Category mapping (multilingual) ──────────────────────────────

    private val CATEGORY_KEYWORDS: Map<String, List<String>> = mapOf(
        "Food & Dining" to listOf(
            // English
            "food", "lunch", "dinner", "breakfast", "coffee", "restaurant", "cafe",
            // Armenian
            "\u057D\u0576\u0578\u0582\u0576\u0564",       // սdelays (food)
            "\u0563\u0561\u0573\u0568\u0576",              // delays (dinner)
            "\u0576\u0561\u056D\u0561\u0573\u0561\u0577", // delays (breakfast)
            "\u057D\u0578\u0582\u0580\u0573",              // սdelays (coffee)
            "\u057C\u0565\u057D\u057F\u0578\u0580\u0561\u0576", // delays (restaurant)
            // Russian
            "еда", "обед", "ужин", "завтрак", "кофе", "ресторан", "кафе"
        ),
        "Transportation" to listOf(
            "taxi", "uber", "bus", "metro", "gas", "fuel", "parking",
            "\u057F\u0561\u0584\u057D\u056B",              // delays (taxi)
            "\u0561\u057E\u057F\u0578\u0562\u0578\u0582\u057D", // delays (autobus)
            "\u0574\u0565\u057F\u0580\u0578",              // delays (metro)
            "\u0562\u0565\u0576\u0566\u056B\u0576",        // delays (gasoline)
            "такси", "автобус", "метро", "бензин", "парковка", "топливо"
        ),
        "Shopping" to listOf(
            "shopping", "clothes", "shoes", "store", "mall",
            "\u0563\u0576\u0578\u0582\u0574\u0576\u0565\u0580", // delays (shopping)
            "\u0570\u0561\u0563\u0578\u0582\u057D\u057F",       // delays (clothes)
            "магазин", "одежда", "обувь", "покупки", "шоппинг"
        ),
        "Groceries" to listOf(
            "grocery", "groceries", "supermarket", "market",
            "\u057D\u0578\u0582\u057A\u0565\u0580\u0574\u0561\u0580\u056F\u0565\u057F", // delays (supermarket)
            "\u0577\u0578\u0582\u056F\u0561",               // delays (market)
            "\u0574\u0561\u0576\u0561\u0576\u0561",         // delays (grocery store)
            "продукты", "супермаркет", "рынок", "магазин"
        ),
        "Entertainment" to listOf(
            "movie", "cinema", "theater", "concert", "game",
            "\u056F\u056B\u0576\u0578",                     // delays (cinema)
            "\u0569\u0561\u057F\u0580\u0578\u0576",         // delays (theater)
            "\u056F\u0578\u0576\u0581\u0565\u0580\u057F",   // delays (concert)
            "кино", "театр", "концерт", "развлечения", "игра"
        ),
        "Healthcare" to listOf(
            "doctor", "pharmacy", "medicine", "hospital", "health",
            "\u0562\u056A\u056B\u0577\u056F",               // delays (doctor)
            "\u0564\u0565\u0572\u0561\u057F\u0578\u0582\u0576", // delays (pharmacy)
            "\u0564\u0565\u0572",                            // delays (medicine)
            "врач", "аптека", "лекарства", "больница", "здоровье"
        ),
        "Bills & Utilities" to listOf(
            "bill", "utility", "electric", "water", "internet", "phone",
            "\u056F\u0578\u0574\u0578\u0582\u0576\u0561\u056C", // delays (utility)
            "\u0567\u056C\u0565\u056F\u057F\u0580\u0561\u056F\u0561\u0576\u0578\u0582\u0569\u0575\u0578\u0582\u0576", // delays (electricity)
            "\u056B\u0576\u057F\u0565\u0580\u0576\u0565\u057F", // delays (internet)
            "счёт", "коммунальные", "электричество", "вода", "интернет", "телефон"
        ),
        "Education" to listOf(
            "book", "course", "tuition", "school", "university",
            "\u0563\u056B\u0580\u0584",                      // delays (book)
            "\u0564\u0561\u057D\u0568\u0576\u0569\u0561\u0581", // delays (course)
            "\u0578\u0582\u057D\u0578\u0582\u0574\u0576\u0561\u057E\u0561\u0580\u0571", // delays (tuition)
            "книга", "курс", "учёба", "школа", "университет"
        ),
        "Rent & Housing" to listOf(
            "rent", "mortgage",
            "\u057E\u0561\u0580\u0571\u0561\u056F\u0561\u056C\u0578\u0582\u0569\u0575\u0578\u0582\u0576", // delays (rent)
            "аренда", "ипотека", "квартплата"
        ),
        "Salary" to listOf(
            "salary", "wage", "paycheck",
            "\u0561\u0577\u056D\u0561\u057F\u0561\u057E\u0561\u0580\u0571", // delays (salary)
            "зарплата", "зп", "оклad"
        ),
        "Freelance" to listOf(
            "freelance", "gig", "contract",
            "\u0586\u0580\u056B\u056C\u0561\u0576\u057D", // delays (freelance)
            "фриланс", "подработка"
        )
    )

    /**
     * Parses a natural language string into transaction components.
     * Supports English, Armenian, and Russian input.
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
        for ((word, daysOffset) in TIME_WORDS.entries.sortedByDescending { it.key.length }) {
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

        // Remove income/type keywords (all languages)
        for (word in INCOME_WORDS + EXPENSE_WORDS) {
            description = description.replace(Regex("""(?:^|\s)${Regex.escape(word)}(?:\s|$)""", RegexOption.IGNORE_CASE), " ").trim()
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

        // 5. Try to match a category from multilingual keywords
        var matchedCategory = matchCategoryFromKeywords(lower)

        // Also try matching from provided category names
        if (matchedCategory == null) {
            matchedCategory = categoryNames.firstOrNull { cat ->
                lower.contains(cat.lowercase())
            }
        }

        // Remove matched category from description if it's a separate word
        if (matchedCategory != null) {
            description = description.replace(
                Regex("""\b${Regex.escape(matchedCategory)}\b""", RegexOption.IGNORE_CASE), ""
            ).trim()
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

    /**
     * Matches input text against multilingual category keywords.
     */
    private fun matchCategoryFromKeywords(lowerInput: String): String? {
        for ((category, keywords) in CATEGORY_KEYWORDS) {
            for (keyword in keywords) {
                if (lowerInput.contains(keyword)) {
                    return category
                }
            }
        }
        return null
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
