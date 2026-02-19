package com.smartexpense.tracker.service.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.smartexpense.tracker.SmartExpenseApp
import com.smartexpense.tracker.data.model.AppSettings
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
 *
 * Supports Armenian banking notifications: keywords like
 * "\u0574\u0578\u0582\u057F\u0584" (income), "\u056C\u056B\u0581\u0584\u0561\u057E\u0578\u0580\u0578\u0582\u0574" (top-up),
 * "\u0570\u0561\u0574\u0561\u056C\u0580\u0578\u0582\u0574" (replenishment) indicate income,
 * while "\u0565\u056C\u0584" (exit) indicates an expense.
 * Users can customise these keywords in Settings.
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
        "$", "\u20B9", "rs.", "inr", "usd", "amt",
        // International
        "approved", "authcode", "amd", "eur", "gbp",
        "purchase", "atm cash", "mail order", "credit account", "balance:",
        // Armenian
        "\u0574\u0578\u0582\u057F\u0584",           // income/deposit
        "\u0565\u056C\u0584",                         // expense/withdrawal
        "\u056C\u056B\u0581\u0584\u0561\u057E\u0578\u0580\u0578\u0582\u0574", // top-up
        "\u0570\u0561\u0574\u0561\u056C\u0580\u0578\u0582\u0574", // replenishment
        "\u058F", "\u0564\u0580\u0561\u0574",         // ֏ symbol and "dram"
        // Russian
        "\u0437\u0430\u0447\u0438\u0441\u043B\u0435\u043D\u0438\u0435", // crediting
        "\u0441\u043F\u0438\u0441\u0430\u043D\u0438\u0435", // debiting
        "\u043F\u0435\u0440\u0435\u0432\u043E\u0434", // transfer
        "\u20BD" // ₽ symbol
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

            // Load user-customisable keywords from settings
            val settings = loadSettings()
            val allFinancialKeywords = financialKeywords +
                settings.notificationIncomeKeywords +
                settings.notificationExpenseKeywords

            // Quick check: does it look financial?
            val lowerText = fullText.lowercase()
            if (allFinancialKeywords.none { lowerText.contains(it.lowercase()) }) return

            val aiEngine = AiExpenseEngine()
            val parsed = aiEngine.parseFinancialMessage(
                fullText,
                customIncomeKeywords = settings.notificationIncomeKeywords,
                customExpenseKeywords = settings.notificationExpenseKeywords
            ) ?: return

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

    /** Read settings from the shared app repository, or fall back to defaults. */
    private fun loadSettings(): AppSettings {
        return try {
            val app = applicationContext as? SmartExpenseApp
            app?.repository?.appData?.value?.settings ?: AppSettings()
        } catch (_: Throwable) {
            AppSettings()
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // No action needed
    }
}
