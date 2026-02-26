package com.smartexpense.tracker.service.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SmsMessage
import android.util.Log
import com.smartexpense.tracker.SmartExpenseApp
import com.smartexpense.tracker.data.model.GeoLocation
import com.smartexpense.tracker.data.model.InAppNotification
import com.smartexpense.tracker.data.model.InAppNotificationType
import com.smartexpense.tracker.data.model.StoreLocation
import com.smartexpense.tracker.data.model.Transaction
import com.smartexpense.tracker.data.model.TransactionSource
import com.smartexpense.tracker.data.model.TransactionType
import com.smartexpense.tracker.data.model.currencyInfoFor
import com.smartexpense.tracker.service.ai.AiExpenseEngine
import com.smartexpense.tracker.service.currency.CurrencyConverterService
import com.smartexpense.tracker.util.LocationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        "armeconombank", "byblos", "fast", "armenbrok",
        // European
        "revolut", "wise", "n26", "monzo", "ing", "hsbc", "barclays",
        "bnp", "deutsche", "santander", "raiffeisen"
    )

    private val financialKeywords = listOf(
        "transaction", "debit", "credit", "payment", "charged", "spent",
        "debited", "credited", "paid", "received", "purchase", "withdrawal",
        "deposit", "transferred", "upi", "neft", "imps", "amt", "txn",
        "a/c", "acct", "account", "balance", "amount", "card",
        // Currency codes & symbols
        "usd", "eur", "gbp", "inr", "rub", "amd",
        "\$", "€", "£", "₹", "֏", "₽",
        // International
        "approved", "authcode", "auth code", "atm cash", "mail order",
        "credit account", "debit account", "completion",
        // Armenian/CIS banking terms (transliterated or common in region)
        "դրամ", "списание", "зачисление", "баланс", "оплата",
        "перевод", "покупка", "снятие", "пополнение"
    )

    /**
     * Pre-authorisation keywords – these messages must be silently ignored.
     * The real charge arrives in a separate "approved"/"completion" message.
     */
    private val preAuthKeywords = listOf(
        "pre-auth", "pre auth", "preauth", "pre-authorization", "pre authorization",
        "preauthorization", "authorisation hold", "authorization hold", "auth hold",
        "card authorised", "card authorized", "temporary hold", "temp hold",
        "pending authorization", "pending authorisation"
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
            // Drop pre-auth / authorisation-hold messages before any further processing
            val lower = fullMessage.lowercase()
            if (preAuthKeywords.any { lower.contains(it) }) {
                Log.d(TAG, "Pre-auth SMS ignored from $sender")
                return
            }
            val app0 = context.applicationContext as? SmartExpenseApp
            val settings0 = app0?.repository?.appData?.value?.settings

            // User-configured keywords also count as financial indicators
            val userKeywords = settings0?.expenseKeywords.orEmpty() +
                settings0?.incomeKeywords.orEmpty()
            if (!isFinancialMessage(sender, fullMessage, userKeywords)) return

            Log.d(TAG, "Financial SMS detected from: $sender")

            val aiEngine = AiExpenseEngine()
            val parsed = aiEngine.parseFinancialMessage(
                fullMessage,
                customIncomeKeywords = settings0?.incomeKeywords.orEmpty(),
                customExpenseKeywords = settings0?.expenseKeywords.orEmpty()
            ) ?: return

            // Only proceed if the message contains at least one configured expense or income keyword
            val allKeywords = settings0?.expenseKeywords.orEmpty() + settings0?.incomeKeywords.orEmpty()
            if (allKeywords.isNotEmpty()) {
                val lowerMsg = fullMessage.lowercase()
                if (allKeywords.none { it.isNotEmpty() && lowerMsg.contains(it.lowercase()) }) {
                    Log.d(TAG, "SMS skipped: no configured expense/income keyword found")
                    return
                }
            }

            val dedupKey = "Auto SMS: $sender | ${System.currentTimeMillis() / 60000}"

            // goAsync() extends the BroadcastReceiver's process lifetime beyond onReceive()
            // so the coroutine is not killed before the transaction is persisted.
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val app = context.applicationContext as? SmartExpenseApp
                    if (app != null) {
                        val repo = app.repository
                        // Wait for repository to finish loading data from disk.
                        // Without this, a cold-start broadcast could read empty defaults
                        // and corrupt existing data on save.
                        if (!repo.awaitInitialization()) {
                            Log.w(TAG, "Repository init timed out, skipping SMS")
                            pendingResult.finish()
                            return@launch
                        }
                        val settings = repo.appData.value.settings
                        val appCurrency = settings.currencyCode
                        val userCatNames = repo.appData.value.categories
                            .filter { !it.isDefault }.map { it.name }

                        // ── Detect foreign currency and convert to app currency ──
                        val parsedCurrency = parsed.currency.ifEmpty { appCurrency }
                        val isForeignCurrency = parsedCurrency.isNotEmpty() &&
                            parsedCurrency != appCurrency

                        var finalAmount = parsed.amount
                        var origAmount = 0.0
                        var origCurrencyCode = ""
                        var usedRate = 0.0

                        if (isForeignCurrency) {
                            val converted = CurrencyConverterService.convert(
                                parsed.amount, parsedCurrency, appCurrency
                            )
                            if (converted != null) {
                                usedRate = converted / parsed.amount
                                origAmount = parsed.amount
                                origCurrencyCode = parsedCurrency
                                finalAmount = converted
                                Log.d(TAG, "Converted ${parsed.amount} $parsedCurrency → $converted $appCurrency (rate: $usedRate)")
                            } else {
                                Log.w(TAG, "Rate unavailable for $parsedCurrency→$appCurrency, storing original amount")
                            }
                        }

                        val cardNote = if (parsed.cardLastFour.isNotEmpty()) "card:${parsed.cardLastFour}" else ""
                        val notes = listOf(dedupKey, cardNote)
                            .filter { it.isNotBlank() }.joinToString("\n")

                        // ── Capture current device location ──────────────────────
                        val location = LocationProvider.getLastKnownLocation(context)

                        val transaction = Transaction(
                            amount = finalAmount,
                            description = parsed.description.ifEmpty { fullMessage.take(80) },
                            category = aiEngine.categorize(parsed.description.ifEmpty { fullMessage }, parsed.isExpense, userCatNames),
                            type = if (parsed.isExpense) TransactionType.EXPENSE else TransactionType.INCOME,
                            source = TransactionSource.SMS,
                            merchantName = parsed.merchantName,
                            notes = notes,
                            currencyCode = appCurrency,
                            originalAmount = origAmount,
                            originalCurrencyCode = origCurrencyCode,
                            exchangeRate = usedRate,
                            location = location?.let { GeoLocation(it.latitude, it.longitude) }
                        )

                        // Auto-create category if not present
                        repo.ensureCategoryExists(transaction.category)
                        // addTransaction returns false if duplicate
                        val added = repo.addTransaction(transaction)
                        if (added) {
                            val appSym = currencyInfoFor(appCurrency).symbol
                            val typeLabel = if (parsed.isExpense) "Expense" else "Income"
                            val notifMsg = if (origAmount > 0.0) {
                                val origSym = currencyInfoFor(origCurrencyCode).symbol
                                "${transaction.description}: $appSym${String.format("%.2f", finalAmount)} " +
                                    "(${origSym}${String.format("%.2f", origAmount)} $origCurrencyCode)" +
                                    (if (parsed.merchantName.isNotEmpty()) " at ${parsed.merchantName}" else "")
                            } else {
                                "${transaction.description}: $appSym${String.format("%.2f", finalAmount)}" +
                                    (if (parsed.merchantName.isNotEmpty()) " at ${parsed.merchantName}" else "")
                            }
                            repo.addInAppNotification(
                                InAppNotification(
                                    title = "$typeLabel detected via SMS",
                                    message = notifMsg,
                                    type = InAppNotificationType.TRANSACTION_DETECTED,
                                    relatedTransactionId = transaction.id
                                )
                            )
                            // Auto-pin location on the map if merchant + GPS are available
                            if (parsed.merchantName.isNotBlank() && location != null) {
                                val existing = repo.appData.value.storeLocations
                                if (existing.none { it.merchantName.equals(parsed.merchantName, ignoreCase = true) }) {
                                    repo.addStoreLocation(StoreLocation(
                                        merchantName = parsed.merchantName,
                                        latitude = location.latitude,
                                        longitude = location.longitude
                                    ))
                                }
                            }
                            Log.d(TAG, "Saved SMS transaction: $finalAmount $appCurrency from $sender")
                        } else {
                            Log.d(TAG, "Skipped duplicate: ${parsed.amount}")
                        }
                    } else {
                        // Fallback: own storage instance
                        val storage = com.smartexpense.tracker.data.json.JsonStorageManager(context)
                        val fallbackRepo = com.smartexpense.tracker.data.repository.ExpenseRepository(storage)
                        fallbackRepo.initialize()
                        val fallbackCatNames = fallbackRepo.appData.value.categories
                            .filter { !it.isDefault }.map { it.name }
                        val fbLocation = LocationProvider.getLastKnownLocation(context)
                        val fbCurrency = parsed.currency.ifEmpty { "AMD" }
                        val fbIsForeign = fbCurrency != "AMD"

                        var fbFinal = parsed.amount
                        var fbOrigAmt = 0.0
                        var fbOrigCode = ""
                        var fbRate = 0.0
                        if (fbIsForeign) {
                            val c = CurrencyConverterService.convert(parsed.amount, fbCurrency, "AMD")
                            if (c != null) {
                                fbRate = c / parsed.amount
                                fbOrigAmt = parsed.amount
                                fbOrigCode = fbCurrency
                                fbFinal = c
                            }
                        }

                        fallbackRepo.addTransaction(Transaction(
                            amount = fbFinal,
                            description = parsed.description.ifEmpty { fullMessage.take(80) },
                            category = aiEngine.categorize(parsed.description.ifEmpty { fullMessage }, parsed.isExpense, fallbackCatNames),
                            type = if (parsed.isExpense) TransactionType.EXPENSE else TransactionType.INCOME,
                            source = TransactionSource.SMS,
                            merchantName = parsed.merchantName,
                            notes = dedupKey,
                            currencyCode = "AMD",
                            originalAmount = fbOrigAmt,
                            originalCurrencyCode = fbOrigCode,
                            exchangeRate = fbRate,
                            location = fbLocation?.let { GeoLocation(it.latitude, it.longitude) }
                        ))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save SMS transaction", e)
                } finally {
                    // Must always call finish() to release the wakelock held by goAsync()
                    pendingResult.finish()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing incoming SMS", e)
        }
    }

    private fun isFinancialMessage(
        sender: String,
        body: String,
        extraKeywords: List<String> = emptyList()
    ): Boolean {
        val lower = (sender + " " + body).lowercase()
        if (bankingSenders.any { lower.contains(it) }) return true
        // Count built-in financial keywords + user-configured keywords
        var hits = financialKeywords.count { lower.contains(it) }
        if (hits < 2) {
            hits += extraKeywords.count { it.isNotEmpty() && lower.contains(it.lowercase()) }
        }
        return hits >= 2
    }
}
