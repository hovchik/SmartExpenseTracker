package com.smartexpense.tracker.util

import com.smartexpense.tracker.data.model.TransactionType

/**
 * Parsed result from voice input text.
 * Null fields mean the parser couldn't extract that piece of information.
 */
data class VoiceParsedTransaction(
    val amount: Double? = null,
    val description: String? = null,
    val type: TransactionType? = null,
    val category: String? = null,
    val merchantName: String? = null
)

/**
 * Parses free-form spoken text (in English, Russian, or Armenian) into
 * structured transaction fields.
 *
 * Examples of supported phrases:
 *  - EN: "spent 25 dollars on coffee at Starbucks"
 *  - RU: "potratil 500 rubley na produkty v Ashane"
 *  - HY: "tsakhsetsi 3000 dram surch hamar"
 */
object VoiceTransactionParser {

    // ── Expense keywords ────────────────────────────────────────────
    private val expenseKeywords = listOf(
        // English
        "spent", "paid", "bought", "purchase", "expense", "cost",
        // Russian
        "\u043f\u043e\u0442\u0440\u0430\u0442\u0438\u043b", "\u043f\u043e\u0442\u0440\u0430\u0442\u0438\u043b\u0430",
        "\u0437\u0430\u043f\u043b\u0430\u0442\u0438\u043b", "\u0437\u0430\u043f\u043b\u0430\u0442\u0438\u043b\u0430",
        "\u043a\u0443\u043f\u0438\u043b", "\u043a\u0443\u043f\u0438\u043b\u0430",
        "\u0440\u0430\u0441\u0445\u043e\u0434", "\u043e\u043f\u043b\u0430\u0442\u0438\u043b",
        "\u043e\u043f\u043b\u0430\u0442\u0438\u043b\u0430", "\u043e\u043f\u043b\u0430\u0442\u0430",
        // Armenian
        "\u056e\u0561\u056d\u057d\u0565\u0581\u056b", // tsakhsetsi = spent
        "\u057e\u0573\u0561\u0580\u0565\u0581\u056b", // vcharetsi = paid
        "\u0563\u0576\u0565\u0581\u056b",             // gnetsi = bought
        "\u056e\u0561\u056d\u057d",                   // tsakhsl = expense
    )

    // ── Income keywords ─────────────────────────────────────────────
    private val incomeKeywords = listOf(
        // English
        "received", "earned", "income", "salary", "got paid",
        // Russian
        "получил", "получила", "заработал", "заработала", "доход", "зарплата",
        // Armenian
        "\u057d\u057f\u0561\u0581\u0561", // staca = received
        "\u0565\u056f\u0561\u0574\u0578\u0582\u057f", // ekamut = income
        "\u0561\u0577\u056d\u0561\u057f\u0561\u057e\u0561\u0580\u0571", // ashxatavardz = salary
    )

    // ── Category keyword mappings (multilingual) ────────────────────
    private val categoryKeywords: Map<String, List<String>> = mapOf(
        "Food & Dining" to listOf(
            "food", "lunch", "dinner", "breakfast", "coffee", "restaurant", "cafe", "eat", "meal", "pizza", "burger", "sushi",
            "еда", "обед", "ужин", "завтрак", "кофе", "ресторан", "кафе",
            "\u057d\u0576\u0578\u0582\u0576\u0564", // snund = food
            "\u0573\u0561\u0577", // chash = dinner/meal
            "\u057d\u0578\u0582\u0580\u0573", // surch = coffee
        ),
        "Transportation" to listOf(
            "taxi", "uber", "bus", "metro", "gas", "fuel", "parking", "transport", "ride", "train", "flight",
            "такси", "метро", "автобус", "бензин", "транспорт", "парковка",
            "\u057f\u0561\u0584\u057d\u056b", // taksi
            "\u057f\u0580\u0561\u0576\u057d\u057a\u0578\u0580\u057f", // transport
        ),
        "Shopping" to listOf(
            "clothes", "shoes", "shopping", "store", "mall", "amazon",
            "одежда", "обувь", "магазин", "покупки",
            "\u0563\u0576\u0578\u0582\u0574\u0576\u0565\u0580", // gnumner = shopping
        ),
        "Entertainment" to listOf(
            "movie", "cinema", "game", "concert", "theater", "netflix", "spotify",
            "кино", "фильм", "игра", "концерт", "театр",
            "\u056f\u056b\u0576\u0578", // kino
        ),
        "Groceries" to listOf(
            "grocery", "groceries", "supermarket", "market", "vegetables", "fruits",
            "продукты", "супермаркет", "рынок", "овощи", "фрукты",
            "\u0574\u0578\u057f\u0565\u0580\u0561\u0576\u0584", // moteranq = groceries
            "\u057d\u0578\u0582\u057a\u0565\u0580\u0574\u0561\u0580\u056f\u0565\u057f", // supermarket
        ),
        "Healthcare" to listOf(
            "doctor", "medicine", "pharmacy", "hospital", "health", "dental",
            "врач", "лекарство", "аптека", "больница", "здоровье",
            "\u0562\u056a\u056b\u0577\u056f", // bjishk = doctor
            "\u0564\u0565\u0572\u0561\u057f\u0578\u0582\u0576", // deghatun = pharmacy
        ),
        "Bills & Utilities" to listOf(
            "bill", "electricity", "water", "internet", "phone", "rent", "utility",
            "счёт", "электричество", "вода", "интернет", "телефон", "аренда",
            "\u056f\u0578\u0574\u0578\u0582\u0576\u0561\u056c", // komunal = utility
        ),
        "Education" to listOf(
            "book", "course", "school", "university", "tuition", "class",
            "книга", "курс", "школа", "университет", "учёба",
            "\u0563\u056b\u0580\u0584", // girq = book
            "\u0564\u057a\u0580\u0578\u0581", // dproc = school
        ),
        "Salary" to listOf(
            "salary", "paycheck", "wage",
            "зарплата", "оклад",
            "\u0561\u0577\u056d\u0561\u057f\u0561\u057e\u0561\u0580\u0571", // ashxatavardz
        ),
        "Freelance" to listOf(
            "freelance", "gig", "contract", "project payment",
            "фриланс", "подработка", "халтура",
        ),
    )

    // ── Prepositions and connectors to strip ────────────────────────
    private val prepositions = setOf(
        // English
        "on", "for", "at", "in", "to", "the", "a", "an", "of", "from",
        // Russian
        "на", "за", "в", "во", "из", "для", "к",
        // Armenian
        "\u0570\u0561\u0574\u0561\u0580", // hamar = for
        "\u057e\u0580\u0561", // vra = on
    )

    /**
     * Parse spoken text into a [VoiceParsedTransaction].
     * The parser is lenient — it extracts what it can and leaves the rest null.
     */
    fun parse(spokenText: String): VoiceParsedTransaction {
        if (spokenText.isBlank()) return VoiceParsedTransaction()

        val text = spokenText.trim().lowercase()

        val type = detectType(text)
        val amount = extractAmount(text)
        val category = detectCategory(text)
        val merchant = extractMerchant(text)
        val description = buildDescription(text, amount, type)

        return VoiceParsedTransaction(
            amount = amount,
            description = description,
            type = type,
            category = category,
            merchantName = merchant
        )
    }

    private fun detectType(text: String): TransactionType {
        val hasIncome = incomeKeywords.any { text.contains(it) }
        val hasExpense = expenseKeywords.any { text.contains(it) }
        return when {
            hasIncome && !hasExpense -> TransactionType.INCOME
            else -> TransactionType.EXPENSE // default to expense
        }
    }

    /**
     * Extract the first number from the text. Supports:
     * - "25", "25.50", "1500", "1,500", "1 500"
     * - Written-out amounts are NOT supported (use digits from speech-to-text).
     */
    private fun extractAmount(text: String): Double? {
        // Match numbers like 25, 25.50, 1500, 1,500.00
        val regex = Regex("""(\d[\d\s,]*(?:\.\d{1,2})?)""")
        val match = regex.find(text) ?: return null
        val raw = match.groupValues[1]
            .replace(",", "")
            .replace(" ", "")
            .trim()
        return raw.toDoubleOrNull()
    }

    private fun detectCategory(text: String): String? {
        // Score each category by how many keywords match
        var bestCategory: String? = null
        var bestScore = 0
        for ((category, keywords) in categoryKeywords) {
            val score = keywords.count { text.contains(it) }
            if (score > bestScore) {
                bestScore = score
                bestCategory = category
            }
        }
        return bestCategory
    }

    /**
     * Try to find a merchant after "at" / "в" / "у" patterns.
     */
    private fun extractMerchant(text: String): String? {
        // English: "at <merchant>"
        val atRegex = Regex("""(?:^|\s)at\s+([a-zA-Z][\w\s']{1,30})""", RegexOption.IGNORE_CASE)
        atRegex.find(text)?.let {
            return cleanMerchant(it.groupValues[1])
        }
        // Russian: "в <merchant>" (after amount context)
        val vRegex = Regex("""(?:^|\s)в\s+([а-яА-ЯёЁ][\w\s]{1,30})""")
        vRegex.find(text)?.let {
            return cleanMerchant(it.groupValues[1])
        }
        return null
    }

    private fun cleanMerchant(raw: String): String {
        val words = raw.trim().split("\\s+".toRegex())
        val cleaned = words.takeWhile { it !in prepositions && it.length > 1 }
        return if (cleaned.isNotEmpty()) {
            cleaned.joinToString(" ").replaceFirstChar { it.uppercase() }
        } else ""
    }

    /**
     * Build a human-readable description from the spoken text.
     * Removes the amount and type keywords, keeping the "what" part.
     */
    private fun buildDescription(text: String, amount: Double?, type: TransactionType): String? {
        var desc = text

        // Remove amount
        if (amount != null) {
            desc = desc.replace(Regex("""\d[\d\s,]*(?:\.\d{1,2})?"""), " ")
        }

        // Remove type keywords
        val allTypeKeywords = expenseKeywords + incomeKeywords
        for (kw in allTypeKeywords.sortedByDescending { it.length }) {
            desc = desc.replace(kw, " ", ignoreCase = true)
        }

        // Remove currency words
        val currencyWords = listOf(
            "dollars", "dollar", "рублей", "рубль", "рубля",
            "драм", "драмов", "\u0564\u0580\u0561\u0574", // dram
            "euros", "euro", "евро",
        )
        for (cw in currencyWords) {
            desc = desc.replace(cw, " ", ignoreCase = true)
        }

        // Remove prepositions at start
        desc = desc.trim()
        val words = desc.split("\\s+".toRegex()).filter {
            it.isNotBlank() && it !in prepositions
        }

        return if (words.isNotEmpty()) {
            words.joinToString(" ").replaceFirstChar { it.uppercase() }.trim()
        } else null
    }
}
