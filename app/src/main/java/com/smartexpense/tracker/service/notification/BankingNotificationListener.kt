package com.smartexpense.tracker.service.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.smartexpense.tracker.SmartExpenseApp
import com.smartexpense.tracker.data.model.Transaction
import com.smartexpense.tracker.data.model.TransactionSource
import com.smartexpense.tracker.data.model.TransactionType
import com.smartexpense.tracker.service.ai.AiExpenseEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Listens to banking app notifications to automatically log transactions.
 * User must grant Notification Access permission in Android Settings.
 */
class BankingNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "BankingNotifListener"
    }

    // Banking/payment app package names to monitor
    private val monitoredPackages = setOf(
        // US Banks
        "com.chase.sig.android",
        "com.wf.wellsfargomobile",
        "com.infonow.bofa",
        "com.citi.citimobile",
        "com.americanexpress.android.acctsvcs.us",
        "com.discover.mobile",
        "com.konylabs.capitalone",
        "com.usaa.mobile.android.usaa",
        "com.ally.MobileBanking",
        // Payment apps
        "com.paypal.android.p2pmobile",
        "com.venmo",
        "com.squareup.cash",
        "com.zellepay.zelle",
        "com.google.android.apps.nbu.paisa.user", // Google Pay
        "com.google.android.apps.walletnfcrel",     // Google Wallet
        // Indian Banks
        "com.sbi.lotusintouch",       // SBI
        "com.csam.icici.bank.imobile", // ICICI
        "com.axis.mobile",            // Axis
        "com.msf.kbank.mobile",       // Kotak
        "com.hdfc.retail.banking",     // HDFC
        // Indian UPI / Payment
        "net.one97.paytm",
        "com.phonepe.app",
        "in.amazon.mShop.android.shopping", // Amazon
        "com.google.android.apps.nbu.paisa.user" // GPay
    )

    // Financial keywords to filter non-transaction notifications
    private val financialKeywords = listOf(
        "debited", "credited", "spent", "received", "paid", "charged",
        "transaction", "payment", "transfer", "withdrawn", "deposit",
        "$", "₹", "rs.", "inr", "usd", "amt",
        // International
        "approved", "authcode", "amd", "eur", "gbp",
        "purchase", "atm cash", "mail order", "credit account", "balance:"
    )

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            val packageName = sbn.packageName ?: return
            if (packageName !in monitoredPackages) return

            val notification = sbn.notification ?: return
            val extras = notification.extras ?: return

            val title = try { extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() } catch (_: Throwable) { null } ?: ""
            val text = try { extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() } catch (_: Throwable) { null } ?: ""
            val bigText = try { extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() } catch (_: Throwable) { null } ?: ""

            val fullText = "$title $text $bigText".trim()
            if (fullText.length < 5) return

            // Quick check: does it look financial?
            val lowerText = fullText.lowercase()
            if (financialKeywords.none { lowerText.contains(it) }) return

            val aiEngine = AiExpenseEngine()
            val parsed = aiEngine.parseFinancialMessage(fullText) ?: return

            Log.d(TAG, "Financial notification from $packageName: \$${parsed.amount}")

            val transaction = Transaction(
                amount = parsed.amount,
                description = parsed.description,
                category = aiEngine.categorize(parsed.description),
                type = if (parsed.isExpense) TransactionType.EXPENSE else TransactionType.INCOME,
                source = TransactionSource.NOTIFICATION,
                merchantName = parsed.merchantName,
                notes = "Auto-detected from $packageName"
            )

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // Use shared app repository
                    val app = applicationContext as? SmartExpenseApp
                    if (app != null) {
                        // Dedup: skip if same amount from notification within 2 minutes
                        val now = System.currentTimeMillis()
                        val existing = app.repository.appData.value.transactions
                        val isDuplicate = existing.any { t ->
                            t.source == TransactionSource.NOTIFICATION &&
                            t.amount == parsed.amount &&
                            kotlin.math.abs(t.timestamp - now) < 120_000
                        }
                        if (!isDuplicate) {
                            app.repository.addTransaction(transaction)
                            Log.d(TAG, "Saved notification transaction: \$${parsed.amount}")
                        } else {
                            Log.d(TAG, "Skipped duplicate notification: \$${parsed.amount}")
                        }
                    } else {
                        // Fallback
                        val storage = com.smartexpense.tracker.data.json.JsonStorageManager(applicationContext)
                        val repo = com.smartexpense.tracker.data.repository.ExpenseRepository(storage)
                        repo.initialize()
                        repo.addTransaction(transaction)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save notification transaction", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing notification", e)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // No action needed
    }
}
