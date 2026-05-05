package com.flowsense.app.service.sms

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.util.Log
import com.flowsense.app.data.model.Transaction
import com.flowsense.app.data.model.TransactionSource
import com.flowsense.app.data.model.TransactionType
import com.flowsense.app.service.ai.AiExpenseEngine

class SmsInboxScanner(private val context: Context) {

    companion object {
        private const val TAG = "SmsInboxScanner"

        private val DEFAULT_FALLBACK_CATEGORIES = listOf(
            "Food", "Transport", "Shopping", "Bills",
            "Entertainment", "Health", "Education", "Other"
        )

        // Only the columns we actually consume — narrows the IPC payload from
        // every SMS row to the four fields the parser needs.
        private val SMS_PROJECTION = arrayOf("body", "address", "date", "type")

        // Generic "this looks like a banking SMS" markers — currency/transaction
        // context only. Income- and expense-classifier vocabulary lives in
        // Settings.incomeKeywords / Settings.expenseKeywords (user-customizable)
        // and is unioned in by scanInbox() so the gate respects user changes.
        private val FINANCIAL_KEYWORDS = listOf(
            "transaction", "txn", "balance", "amt",
            "a/c", "acct", "account", "bank", "card",
            "approved", "authcode", "auth code", "completion",
            "upi", "neft", "imps", "rtgs", "emi", "loan", "transfer", "transferred"
        )

        private val BANKING_SENDER_PATTERNS = listOf(
            "chase", "wellsfargo", "bofa", "citi", "amex", "discover",
            "capital", "usaa", "ally", "venmo", "paypal", "zelle",
            "cashapp", "bank", "visa", "mastercard", "alert", "notify",
            "sbi", "hdfc", "icici", "axis", "kotak", "paytm", "phonepe",
            "gpay", "upi", "yesbank", "indus", "idbi", "pnb", "canara",
            "boi", "bob", "federal", "rbl", "idfc",
            // Armenian/CIS banks
            "ameria", "ardshin", "inecobank", "converse", "acba", "armswiss",
            "vtb", "mellat", "araratbank", "armeconombank", "evoca", "fast",
            "idbank", "unibank", "byblos",
            // European / other
            "revolut", "wise", "n26", "monzo", "ing", "bnp", "hsbc",
            "barclays", "deutsche", "santander", "raiffeisen"
        )
    }

    data class ScanResult(
        val totalScanned: Int,
        val financialFound: Int,
        val transactionsParsed: Int,
        val transactions: List<Transaction>,
        val errors: Int,
        val errorMessage: String? = null
    )

    /** Returns total number of SMS messages in the inbox. */
    fun getTotalSmsCount(): Int {
        val uris = listOf("content://sms/inbox", "content://sms")
        for (uriString in uris) {
            try {
                context.contentResolver.query(
                    Uri.parse(uriString), arrayOf("_id"), null, null, null
                )?.use { return it.count }
            } catch (_: Throwable) {}
        }
        return 0
    }

    suspend fun scanInbox(
        maxMessages: Int = 500,
        existingTransactionNotes: Set<String> = emptySet(),
        userCategoryNames: List<String> = emptyList(),
        startDate: Long? = null,
        endDate: Long? = null,
        customIncomeKeywords: List<String> = emptyList(),
        customExpenseKeywords: List<String> = emptyList()
    ): ScanResult {
        val aiEngine: AiExpenseEngine = try {
            AiExpenseEngine()
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to create AiExpenseEngine", e)
            return ScanResult(0, 0, 0, emptyList(), 1, "Internal error: ${e.message}")
        }

        val categoryNames = userCategoryNames.ifEmpty { DEFAULT_FALLBACK_CATEGORIES }

        // Combine built-in financial keywords with the user's configured income /
        // expense keywords so the gate filter accepts everything the parser is
        // willing to classify (salary terms, custom company names, etc.).
        val gateKeywords = (FINANCIAL_KEYWORDS +
            customIncomeKeywords.map { it.lowercase() } +
            customExpenseKeywords.map { it.lowercase() })
            .filter { it.isNotEmpty() }
            .distinct()

        for (uriString in listOf("content://sms/inbox", "content://sms")) {
            try {
                val result = doScan(
                    uri = Uri.parse(uriString),
                    aiEngine = aiEngine,
                    maxMessages = maxMessages,
                    existingNotes = existingTransactionNotes,
                    isInboxUri = uriString.contains("inbox"),
                    userCategoryNames = userCategoryNames,
                    fallbackCategoryNames = categoryNames,
                    gateKeywords = gateKeywords,
                    startDate = startDate,
                    endDate = endDate,
                    customIncomeKeywords = customIncomeKeywords,
                    customExpenseKeywords = customExpenseKeywords
                )
                if (result != null) return result
            } catch (e: Throwable) {
                Log.w(TAG, "Failed with $uriString: ${e.message}")
            }
        }
        return ScanResult(0, 0, 0, emptyList(), 1, "Could not access SMS inbox on this device.")
    }

    /**
     * Detects whether a newly parsed transaction duplicates one already collected
     * in this batch (same amount + same card last-4 within 10 min, or same amount
     * within 2 min regardless of card).
     */
    private fun isDuplicateInBatch(
        candidate: Transaction,
        candidateCard: String,
        batch: List<Transaction>,
        batchCards: List<String>
    ): Boolean {
        for (i in batch.indices) {
            val t = batch[i]
            if (t.amount != candidate.amount) continue
            val timeDiff = kotlin.math.abs(t.timestamp - candidate.timestamp)
            if (candidateCard.isNotEmpty() && batchCards[i] == candidateCard && timeDiff < 600_000) return true
            if (timeDiff < 120_000) return true
        }
        return false
    }

    private fun doScan(
        uri: Uri, aiEngine: AiExpenseEngine, maxMessages: Int,
        existingNotes: Set<String>, isInboxUri: Boolean,
        userCategoryNames: List<String>,
        fallbackCategoryNames: List<String>,
        gateKeywords: List<String>,
        startDate: Long?, endDate: Long?,
        customIncomeKeywords: List<String>,
        customExpenseKeywords: List<String>
    ): ScanResult? {
        val transactions = mutableListOf<Transaction>()
        val transactionCards = mutableListOf<String>()
        var totalScanned = 0; var financialFound = 0; var errors = 0

        // Build date range selection clause
        val selectionParts = mutableListOf<String>()
        val selectionArgs = mutableListOf<String>()
        if (startDate != null) {
            selectionParts.add("date >= ?")
            selectionArgs.add(startDate.toString())
        }
        if (endDate != null) {
            selectionParts.add("date <= ?")
            selectionArgs.add(endDate.toString())
        }
        val selection = selectionParts.takeIf { it.isNotEmpty() }?.joinToString(" AND ")
        val selArgs = selectionArgs.takeIf { it.isNotEmpty() }?.toTypedArray()

        // Push LIMIT into the SQL sort order so the provider stops yielding rows
        // once we've consumed enough — avoids loading the full inbox cursor.
        val sortOrder = "date DESC LIMIT ${maxMessages.coerceAtLeast(1)}"

        val cursor: Cursor? = try {
            context.contentResolver.query(uri, SMS_PROJECTION, selection, selArgs, sortOrder)
        } catch (e: Throwable) {
            Log.w(TAG, "query() threw for $uri: ${e.message}")
            return null
        }
        if (cursor == null) return null

        cursor.use { c ->
            try {
                val columns = try { c.columnNames ?: emptyArray() } catch (_: Throwable) { emptyArray() }
                val bodyIdx = findCol(columns, "body")
                val addressIdx = findCol(columns, "address")
                val dateIdx = findCol(columns, "date")
                val typeIdx = findCol(columns, "type")
                if (bodyIdx < 0) return null

                while (totalScanned < maxMessages) {
                    val hasNext = try { c.moveToNext() } catch (_: Throwable) { false }
                    if (!hasNext) break
                    totalScanned++

                    try {
                        val body = safeStr(c, bodyIdx) ?: continue
                        if (body.isBlank()) continue

                        // Cheap early filter before any allocation-heavy work.
                        val sender = safeStr(c, addressIdx) ?: "unknown"
                        if (!isFinancialMessage(sender, body, gateKeywords)) continue

                        if (!isInboxUri && typeIdx >= 0) {
                            val t = safeInt(c, typeIdx) ?: 1
                            if (t != 1) continue
                        }

                        val date = safeLong(c, dateIdx) ?: System.currentTimeMillis()

                        // Skip messages already imported from a previous scan.
                        val noteKey = "SMS scan: $sender | $date"
                        if (existingNotes.contains(noteKey)) continue

                        financialFound++

                        val parsed = try {
                            aiEngine.parseFinancialMessage(body, customIncomeKeywords, customExpenseKeywords)
                        } catch (_: Throwable) { null } ?: continue

                        val desc = parsed.description.ifEmpty { body.take(80) }

                        // Rule-based categorization only — calling the AI provider per
                        // message would be the dominant cost when scanning hundreds of
                        // SMS. The user can refine categories from the review screen.
                        val cat = try {
                            aiEngine.categorize(desc, parsed.isExpense, userCategoryNames)
                        } catch (_: Throwable) {
                            fallbackCategoryNames.firstOrNull() ?: "Other"
                        }

                        // Store original parsed currency so the review screen can show it
                        // and confirmSmsScanResults() can convert if needed.
                        val cardNote = if (parsed.cardLastFour.isNotEmpty()) "\ncard:${parsed.cardLastFour}" else ""
                        val currencyNote = if (parsed.currency.isNotEmpty()) "\nparsedCurrency:${parsed.currency}" else ""

                        val candidate = Transaction(
                            amount = parsed.amount, description = desc, category = cat,
                            type = if (parsed.isExpense) TransactionType.EXPENSE else TransactionType.INCOME,
                            source = TransactionSource.SMS, timestamp = date,
                            merchantName = parsed.merchantName, notes = noteKey + cardNote + currencyNote
                        )

                        if (isDuplicateInBatch(candidate, parsed.cardLastFour, transactions, transactionCards)) continue

                        transactions.add(candidate)
                        transactionCards.add(parsed.cardLastFour)
                    } catch (_: Throwable) { errors++ }
                }
            } catch (_: Throwable) { errors++ }
        }

        return ScanResult(totalScanned, financialFound, transactions.size, transactions, errors)
    }

    private fun findCol(cols: Array<String>, name: String): Int {
        val idx = cols.indexOf(name)
        if (idx >= 0) return idx
        for (i in cols.indices) { if (cols[i].equals(name, ignoreCase = true)) return i }
        return -1
    }
    private fun safeStr(c: Cursor, i: Int): String? {
        if (i < 0) return null
        return try { if (c.isNull(i)) null else c.getString(i) } catch (_: Throwable) { null }
    }
    private fun safeLong(c: Cursor, i: Int): Long? {
        if (i < 0) return null
        return try { if (c.isNull(i)) null else c.getLong(i) } catch (_: Throwable) { null }
    }
    private fun safeInt(c: Cursor, i: Int): Int? {
        if (i < 0) return null
        return try { if (c.isNull(i)) null else c.getInt(i) } catch (_: Throwable) { null }
    }

    private fun isFinancialMessage(
        sender: String,
        body: String,
        gateKeywords: List<String>
    ): Boolean {
        return try {
            val lower = (sender + " " + body).lowercase()
            if (BANKING_SENDER_PATTERNS.any { lower.contains(it) }) return true
            // Require ≥2 keyword hits to qualify as a non-bank-sender financial SMS.
            // gateKeywords is the union of FINANCIAL_KEYWORDS + the user's configured
            // income/expense keywords, so configured income terms (e.g. "salary",
            // "freelance", company name) help income SMS pass the gate.
            var hits = 0
            for (kw in gateKeywords) {
                if (lower.contains(kw)) {
                    hits++
                    if (hits >= 2) return true
                }
            }
            false
        } catch (_: Throwable) { false }
    }
}
