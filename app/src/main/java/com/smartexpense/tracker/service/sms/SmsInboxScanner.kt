package com.smartexpense.tracker.service.sms

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.util.Log
import com.smartexpense.tracker.data.model.Transaction
import com.smartexpense.tracker.data.model.TransactionSource
import com.smartexpense.tracker.data.model.TransactionType
import com.smartexpense.tracker.service.ai.AiExpenseEngine

class SmsInboxScanner(private val context: Context) {

    companion object {
        private const val TAG = "SmsInboxScanner"
    }

    data class ScanResult(
        val totalScanned: Int,
        val financialFound: Int,
        val transactionsParsed: Int,
        val transactions: List<Transaction>,
        val errors: Int,
        val errorMessage: String? = null
    )

    private val financialKeywords = listOf(
        "transaction", "debit", "credit", "payment", "charged", "spent",
        "transferred", "withdrawal", "deposit", "balance", "amt", "txn",
        "debited", "credited", "paid", "received", "purchase", "refund",
        "cashback", "upi", "neft", "imps", "rtgs", "emi", "loan",
        "a/c", "acct", "account", "bank", "card",
        // International banking
        "approved", "authcode", "auth code", "atm cash", "mail order",
        "pos", "e-commerce", "completion", "credit account", "debit account"
    )

    private val bankingSenderPatterns = listOf(
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

    fun scanInbox(
        maxMessages: Int = 500,
        existingTransactionNotes: Set<String> = emptySet(),
        userCategoryNames: List<String> = emptyList()
    ): ScanResult {
        val aiEngine: AiExpenseEngine
        try {
            aiEngine = AiExpenseEngine()
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to create AiExpenseEngine", e)
            return ScanResult(0, 0, 0, emptyList(), 1, "Internal error: ${e.message}")
        }

        // Try multiple URIs
        val uris = listOf("content://sms/inbox", "content://sms")
        for (uriString in uris) {
            try {
                val result = doScan(Uri.parse(uriString), aiEngine, maxMessages, existingTransactionNotes, uriString.contains("inbox"), userCategoryNames)
                if (result != null) return result
            } catch (e: Throwable) {
                Log.w(TAG, "Failed with $uriString: ${e.message}")
            }
        }
        return ScanResult(0, 0, 0, emptyList(), 1, "Could not access SMS inbox on this device.")
    }

    private fun doScan(
        uri: Uri, aiEngine: AiExpenseEngine, maxMessages: Int,
        existingNotes: Set<String>, isInboxUri: Boolean,
        userCategoryNames: List<String> = emptyList()
    ): ScanResult? {
        val transactions = mutableListOf<Transaction>()
        var totalScanned = 0; var financialFound = 0; var errors = 0
        var cursor: Cursor? = null

        try {
            cursor = context.contentResolver.query(uri, null, null, null, "date DESC")
        } catch (e: Throwable) {
            Log.w(TAG, "query() threw for $uri: ${e.message}")
            return null
        }
        if (cursor == null) return null

        try {
            val columns = try { cursor.columnNames ?: emptyArray() } catch (_: Throwable) { emptyArray() }
            val bodyIdx = findCol(columns, "body")
            val addressIdx = findCol(columns, "address")
            val dateIdx = findCol(columns, "date")
            val typeIdx = findCol(columns, "type")
            if (bodyIdx < 0) return null

            while (true) {
                val hasNext = try { cursor.moveToNext() } catch (_: Throwable) { false }
                if (!hasNext || totalScanned >= maxMessages) break
                totalScanned++

                try {
                    val body = safeStr(cursor, bodyIdx) ?: continue
                    if (body.isBlank()) continue
                    val sender = safeStr(cursor, addressIdx) ?: "unknown"
                    val date = safeLong(cursor, dateIdx) ?: System.currentTimeMillis()

                    if (!isInboxUri && typeIdx >= 0) {
                        val t = safeInt(cursor, typeIdx) ?: 1
                        if (t != 1) continue
                    }

                    if (!isFinancialMessage(sender, body)) continue
                    financialFound++

                    val parsed = try { aiEngine.parseFinancialMessage(body) } catch (_: Throwable) { null }
                        ?: continue

                    val noteKey = "SMS scan: $sender | $date"
                    if (existingNotes.contains(noteKey)) continue

                    val desc = try { parsed.description.ifEmpty { body.take(80) } } catch (_: Throwable) { body.take(80) }
                    val cat = try { aiEngine.categorize(desc, userCategoryNames = userCategoryNames) } catch (_: Throwable) { "Other" }

                    // Store original parsed currency so the review screen can show it
                    // and confirmSmsScanResults() can convert if needed
                    val currencyNote = if (parsed.currency.isNotEmpty()) "\nparsedCurrency:${parsed.currency}" else ""

                    transactions.add(Transaction(
                        amount = parsed.amount, description = desc, category = cat,
                        type = if (parsed.isExpense) TransactionType.EXPENSE else TransactionType.INCOME,
                        source = TransactionSource.SMS, timestamp = date,
                        merchantName = parsed.merchantName, notes = noteKey + currencyNote
                    ))
                } catch (e: Throwable) { errors++ }
            }
        } catch (e: Throwable) { errors++ }
        finally { try { cursor.close() } catch (_: Throwable) {} }

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

    private fun isFinancialMessage(sender: String, body: String): Boolean {
        return try {
            val lower = (sender + " " + body).lowercase()
            if (bankingSenderPatterns.any { lower.contains(it) }) return true
            financialKeywords.count { lower.contains(it) } >= 2
        } catch (_: Throwable) { false }
    }
}
