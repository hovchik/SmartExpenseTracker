package com.flowsense.app.service.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SmsMessage
import android.util.Log
import com.flowsense.app.FlowSenseApp
import com.flowsense.app.data.model.GeoLocation
import com.flowsense.app.data.model.InAppNotification
import com.flowsense.app.data.model.InAppNotificationType
import com.flowsense.app.data.model.PatternKind
import com.flowsense.app.data.model.StoreLocation
import com.flowsense.app.data.model.Transaction
import com.flowsense.app.data.model.TransactionSource
import com.flowsense.app.data.model.TransactionType
import com.flowsense.app.data.model.currencyInfoFor
import com.flowsense.app.service.ai.AiExpenseEngine
import com.flowsense.app.service.currency.CurrencyConverterService
import com.flowsense.app.util.LocationProvider
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
            // Drop pre-auth / authorisation-hold messages before any further processing.
            // Uses the shared list from AiExpenseEngine so the receiver and parser stay
            // in sync if the keyword set is updated.
            if (AiExpenseEngine.isPreAuthMessage(fullMessage)) {
                Log.d(TAG, "Pre-auth SMS ignored from $sender")
                return
            }

            // Quick pre-filter with built-in banking senders & financial keywords.
            // Don't rely on user settings here — repo may not be initialized on cold start.
            if (!isFinancialMessage(sender, fullMessage)) {
                Log.d(TAG, "SMS skipped: not a financial message (pre-filter)")
                return
            }

            Log.d(TAG, "Financial SMS detected from: $sender")

            // goAsync() extends the BroadcastReceiver's process lifetime beyond onReceive()
            // so the coroutine is not killed before the transaction is persisted.
            // Called early — before any heavy parsing — to prevent the system from
            // killing the receiver while regex-heavy parseFinancialMessage runs.
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val app = context.applicationContext as? FlowSenseApp
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

                        // Respect user toggle — if SMS parsing is disabled, bail out.
                        if (!settings.smsParsingEnabled) {
                            Log.d(TAG, "SMS parsing disabled in settings, skipping")
                            pendingResult.finish()
                            return@launch
                        }

                        // Re-check with user-configured keywords (now properly loaded)
                        val userKeywords = settings.expenseKeywords + settings.incomeKeywords
                        if (!isFinancialMessage(sender, fullMessage, userKeywords)) {
                            Log.d(TAG, "SMS skipped: not financial with user keywords")
                            pendingResult.finish()
                            return@launch
                        }

                        // ── Source pattern: learn the sender, honor exclusions ──
                        // New senders start as PENDING (tracked by default); the user can
                        // exclude them from Settings → Message Sources at any time.
                        val (pattern, isNewPattern) = repo.recordMessagePattern(
                            kind = PatternKind.SMS,
                            rawSource = sender,
                            displayName = sender,
                            sampleText = fullMessage
                        )
                        if (!pattern.isTracked) {
                            Log.d(TAG, "SMS skipped: sender $sender is excluded by pattern")
                            pendingResult.finish()
                            return@launch
                        }
                        if (isNewPattern) {
                            repo.addInAppNotification(
                                InAppNotification(
                                    title = "New SMS source detected",
                                    message = "Transactions from \"$sender\" are now being tracked. " +
                                        "Review it under Settings → Message Sources to keep or ignore this sender.",
                                    type = InAppNotificationType.NEW_SOURCE_DETECTED
                                )
                            )
                        }

                        val aiEngine = AiExpenseEngine()
                        val parsed = aiEngine.parseFinancialMessage(
                            fullMessage,
                            customIncomeKeywords = settings.incomeKeywords,
                            customExpenseKeywords = settings.expenseKeywords
                        )
                        if (parsed == null) {
                            Log.d(TAG, "Could not parse financial data from SMS")
                            pendingResult.finish()
                            return@launch
                        }

                        val dedupKey = "Auto SMS: $sender | ${System.currentTimeMillis() / 60000}"
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
                            if (converted != null && parsed.amount > 0) {
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

                        val categoryNames = repo.appData.value.categories.map { it.name }
                        val category = app.aiCategorize(
                            parsed.description.ifEmpty { fullMessage },
                            categoryNames, parsed.isExpense, userCatNames
                        )
                        val transaction = Transaction(
                            amount = finalAmount,
                            description = parsed.description.ifEmpty { fullMessage.take(80) },
                            category = category,
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
                        // Fallback: own storage instance. Use the user's saved currency
                        // (loaded from disk) rather than hardcoding a region-specific one.
                        val storage = com.flowsense.app.data.json.JsonStorageManager(context)
                        val fallbackRepo = com.flowsense.app.data.repository.ExpenseRepository(storage)
                        fallbackRepo.initialize()
                        val fallbackSettings = fallbackRepo.appData.value.settings

                        if (!fallbackSettings.smsParsingEnabled) {
                            Log.d(TAG, "SMS parsing disabled in settings (fallback), skipping")
                            pendingResult.finish()
                            return@launch
                        }

                        val fbAiEngine = AiExpenseEngine()
                        val fbParsed = fbAiEngine.parseFinancialMessage(
                            fullMessage,
                            customIncomeKeywords = fallbackSettings.incomeKeywords,
                            customExpenseKeywords = fallbackSettings.expenseKeywords
                        )
                        if (fbParsed == null) {
                            Log.d(TAG, "Could not parse financial data from SMS (fallback)")
                            pendingResult.finish()
                            return@launch
                        }

                        val fbDedupKey = "Auto SMS: $sender | ${System.currentTimeMillis() / 60000}"
                        val fbAppCurrency = fallbackSettings.currencyCode
                        val fallbackCatNames = fallbackRepo.appData.value.categories
                            .filter { !it.isDefault }.map { it.name }
                        val fbLocation = LocationProvider.getLastKnownLocation(context)
                        val fbCurrency = fbParsed.currency.ifEmpty { fbAppCurrency }
                        val fbIsForeign = fbCurrency.isNotEmpty() && fbCurrency != fbAppCurrency

                        var fbFinal = fbParsed.amount
                        var fbOrigAmt = 0.0
                        var fbOrigCode = ""
                        var fbRate = 0.0
                        if (fbIsForeign) {
                            val c = CurrencyConverterService.convert(fbParsed.amount, fbCurrency, fbAppCurrency)
                            if (c != null && fbParsed.amount > 0) {
                                fbRate = c / fbParsed.amount
                                fbOrigAmt = fbParsed.amount
                                fbOrigCode = fbCurrency
                                fbFinal = c
                            }
                        }

                        val ruleEngine = com.flowsense.app.service.ai.AiExpenseEngine()
                        val fbCategory = ruleEngine.categorize(
                            fbParsed.description.ifEmpty { fullMessage },
                            fbParsed.isExpense, fallbackCatNames
                        )
                        fallbackRepo.addTransaction(Transaction(
                            amount = fbFinal,
                            description = fbParsed.description.ifEmpty { fullMessage.take(80) },
                            category = fbCategory,
                            type = if (fbParsed.isExpense) TransactionType.EXPENSE else TransactionType.INCOME,
                            source = TransactionSource.SMS,
                            merchantName = fbParsed.merchantName,
                            notes = fbDedupKey,
                            currencyCode = fbAppCurrency,
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
