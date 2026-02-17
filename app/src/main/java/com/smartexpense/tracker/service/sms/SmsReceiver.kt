package com.smartexpense.tracker.service.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SmsMessage
import android.util.Log
import com.smartexpense.tracker.SmartExpenseApp
import com.smartexpense.tracker.data.model.InAppNotification
import com.smartexpense.tracker.data.model.InAppNotificationType
import com.smartexpense.tracker.data.model.Transaction
import com.smartexpense.tracker.data.model.TransactionSource
import com.smartexpense.tracker.data.model.TransactionType
import com.smartexpense.tracker.service.ai.AiExpenseEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives incoming SMS messages and auto-logs banking transactions.
 */
class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
        private const val SMS_RECEIVED = "android.provider.Telephony.SMS_RECEIVED"
    }

    private val bankingSenders = listOf(
        "chase", "wellsfargo", "bofa", "citi", "amex", "discover",
        "capital", "usaa", "ally", "venmo", "paypal", "zelle",
        "cashapp", "bank", "visa", "mastercard", "alert", "notify",
        "sbi", "hdfc", "icici", "axis", "kotak", "paytm", "phonepe",
        "gpay", "upi", "yesbank", "indus", "idbi", "pnb",
        // Armenian/CIS banks
        "ameria", "ardshin", "inecobank", "converse", "acba", "armswiss",
        "vtb", "mellat", "araratbank", "evoca", "idbank", "unibank",
        // European
        "revolut", "wise", "n26", "monzo", "ing", "hsbc", "barclays"
    )

    private val financialKeywords = listOf(
        "transaction", "debit", "credit", "payment", "charged", "spent",
        "debited", "credited", "paid", "received", "purchase", "withdrawal",
        "deposit", "transferred", "upi", "neft", "imps", "amt", "txn",
        "a/c", "acct", "account", "balance",
        // International
        "approved", "authcode", "auth code", "atm cash", "mail order",
        "credit account", "debit account", "completion"
    )

    override fun onReceive(context: Context, intent: Intent) {
        try {
            if (intent.action != SMS_RECEIVED) return

            // Extract SMS messages from PDU
            val pdus = intent.extras?.get("pdus") as? Array<*> ?: return
            val format = intent.extras?.getString("format") ?: ""

            val messages = mutableListOf<SmsMessage>()
            for (pdu in pdus) {
                try {
                    val bytes = pdu as? ByteArray ?: continue
                    val msg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        SmsMessage.createFromPdu(bytes, format)
                    } else {
                        @Suppress("DEPRECATION")
                        SmsMessage.createFromPdu(bytes)
                    }
                    if (msg != null) messages.add(msg)
                } catch (e: Throwable) {
                    Log.w(TAG, "Failed to parse PDU: ${e.message}")
                }
            }

            if (messages.isEmpty()) return

            val fullMessage = messages.joinToString("") { it.messageBody ?: "" }
            val sender = messages.firstOrNull()?.originatingAddress ?: "unknown"

            if (fullMessage.isBlank()) return
            if (!isFinancialMessage(sender, fullMessage)) return

            Log.d(TAG, "Financial SMS detected from: $sender")

            val aiEngine = AiExpenseEngine()
            val parsed = aiEngine.parseFinancialMessage(fullMessage) ?: return

            val dedupKey = "Auto SMS: $sender | ${System.currentTimeMillis() / 60000}"

            val transaction = Transaction(
                amount = parsed.amount,
                description = parsed.description.ifEmpty { fullMessage.take(80) },
                category = aiEngine.categorize(parsed.description.ifEmpty { fullMessage }),
                type = if (parsed.isExpense) TransactionType.EXPENSE else TransactionType.INCOME,
                source = TransactionSource.SMS,
                merchantName = parsed.merchantName,
                notes = dedupKey
            )

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val app = context.applicationContext as? SmartExpenseApp
                    if (app != null) {
                        // Dedup: skip if similar transaction (same amount, same source) within 2 min
                        val existing = app.repository.appData.value.transactions
                        val now = System.currentTimeMillis()
                        val isDuplicate = existing.any { t ->
                            t.source == TransactionSource.SMS &&
                            t.amount == parsed.amount &&
                            kotlin.math.abs(t.timestamp - now) < 120_000
                        }
                        if (!isDuplicate) {
                            val repo = app.repository
                            // Auto-create category if not present
                            repo.ensureCategoryExists(transaction.category)
                            repo.addTransaction(transaction)
                            // Post in-app notification
                            val sym = repo.appData.value.settings.currency.ifEmpty { "$" }
                            val typeLabel = if (parsed.isExpense) "Expense" else "Income"
                            repo.addInAppNotification(
                                InAppNotification(
                                    title = "$typeLabel detected via SMS",
                                    message = "${transaction.description}: $sym${String.format("%.2f", parsed.amount)}" +
                                        if (parsed.merchantName.isNotEmpty()) " at ${parsed.merchantName}" else "",
                                    type = InAppNotificationType.TRANSACTION_DETECTED,
                                    relatedTransactionId = transaction.id
                                )
                            )
                            Log.d(TAG, "Saved SMS transaction: \$${parsed.amount} from $sender")
                        } else {
                            Log.d(TAG, "Skipped duplicate: \$${parsed.amount}")
                        }
                    } else {
                        // Fallback: own storage instance
                        val storage = com.smartexpense.tracker.data.json.JsonStorageManager(context)
                        val repo = com.smartexpense.tracker.data.repository.ExpenseRepository(storage)
                        repo.initialize()
                        repo.addTransaction(transaction)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save SMS transaction", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing incoming SMS", e)
        }
    }

    private fun isFinancialMessage(sender: String, body: String): Boolean {
        val lower = (sender + " " + body).lowercase()
        if (bankingSenders.any { lower.contains(it) }) return true
        return financialKeywords.count { lower.contains(it) } >= 2
    }
}
