package com.smartexpense.tracker.service.notification

import android.app.Notification
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.smartexpense.tracker.SmartExpenseApp
import com.smartexpense.tracker.data.model.AppSettings
import com.smartexpense.tracker.data.model.InAppNotification
import com.smartexpense.tracker.data.model.InAppNotificationType
import com.smartexpense.tracker.data.model.Transaction
import com.smartexpense.tracker.data.model.TransactionSource
import com.smartexpense.tracker.data.model.TransactionType
import com.smartexpense.tracker.data.model.currencyInfoFor
import com.smartexpense.tracker.service.ai.AiExpenseEngine
import com.smartexpense.tracker.service.currency.CurrencyConverterService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Listens to banking app notifications to automatically log transactions.
 * Monitors:
 *  1. Explicit banking app package names (whitelist).
 *  2. ANY app whose label/package contains a banking keyword (e.g. "bank", "arca",
 *     "payment", "finance") – covers locally-installed Armenian/CIS/regional banks
 *     that are not hard-coded in the whitelist.
 *
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

        /** Keywords matched against the app label (human-readable name) and package name. */
        val BANKING_APP_NAME_KEYWORDS = listOf(
            "bank", "arca", "pay", "wallet", "finance", "credit", "loan",
            "money", "cash", "transfer", "saving", "invest", "revolut",
            "wise", "zelle", "venmo", "paypal"
        )
    }

    // Explicit banking/payment app package whitelist
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
        "com.google.android.apps.nbu.paisa.user",
        "com.google.android.apps.walletnfcrel",
        // Indian Banks
        "com.sbi.lotusintouch",
        "com.csam.icici.bank.imobile",
        "com.axis.mobile",
        "com.msf.kbank.mobile",
        "com.hdfc.retail.banking",
        // Indian UPI / Payment
        "net.one97.paytm",
        "com.phonepe.app",
        // Armenian / CIS banks
        "am.ameriabank.mobilebanking",
        "am.ardshinbank.mobile",
        "am.inecobank.mobilebank",
        "am.conversebank.mobile",
        "am.acbabank.mobile",
        "am.evocabank.mobile",
        "am.idbank.mobile",
        "am.unibank.mobile",
        "am.vtb.mobile",
        "am.arcapay",
        "am.arca",
        // Additional Armenian apps
        "com.sflpro.inecomobile",
        "com.banqr.ameriabank",
        "am.imwallet.android"
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

    /** Returns the human-readable app label for a package, or null on failure. */
    private fun appLabel(packageName: String): String? = try {
        packageManager.getApplicationInfo(packageName, 0)
            .let { packageManager.getApplicationLabel(it).toString() }
    } catch (_: PackageManager.NameNotFoundException) { null }

    /** True if the notification source looks like a banking/financial app. */
    private fun isBankingSource(packageName: String): Boolean {
        if (packageName in monitoredPackages) return true
        val label = appLabel(packageName)?.lowercase() ?: ""
        val pkg   = packageName.lowercase()
        return BANKING_APP_NAME_KEYWORDS.any { kw -> label.contains(kw) || pkg.contains(kw) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            val packageName = sbn.packageName ?: return
            if (!isBankingSource(packageName)) return

            val notification = sbn.notification ?: return
            val extras = notification.extras ?: return

            val title   = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val text    = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""

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

            val appName = appLabel(packageName) ?: packageName

            Log.d(TAG, "Financial notification from $appName ($packageName): ${parsed.amount}")

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val app = applicationContext as? SmartExpenseApp ?: return@launch
                    val repo = app.repository
                    val now = System.currentTimeMillis()

                    // Dedup: skip if same amount from notification within 2 minutes
                    val isDuplicate = repo.appData.value.transactions.any { t ->
                        t.source == TransactionSource.NOTIFICATION &&
                        t.amount == parsed.amount &&
                        kotlin.math.abs(t.timestamp - now) < 120_000
                    }
                    if (isDuplicate) {
                        Log.d(TAG, "Skipped duplicate: ${parsed.amount}")
                        return@launch
                    }

                    val settings = repo.appData.value.settings
                    val appCurrency = settings.currencyCode

                    // ── Currency conversion ──────────────────────────────────
                    // If the notification reports a different currency, convert to app's currency.
                    val (finalAmount, conversionNote) = if (
                        parsed.currency.isNotEmpty() && parsed.currency != appCurrency
                    ) {
                        val converted = CurrencyConverterService.convert(
                            parsed.amount, parsed.currency, appCurrency
                        )
                        if (converted != null) {
                            val rate = converted / parsed.amount
                            val fromSym = currencyInfoFor(parsed.currency).symbol
                            converted to "Original: $fromSym${String.format("%.2f", parsed.amount)} ${parsed.currency} · 1 ${parsed.currency} = ${String.format("%.4f", rate)} $appCurrency"
                        } else {
                            parsed.amount to ""
                        }
                    } else {
                        parsed.amount to ""
                    }

                    val notes = listOf("Auto-detected from $appName", conversionNote)
                        .filter { it.isNotBlank() }.joinToString("\n")

                    val category = aiEngine.categorize(parsed.description, parsed.isExpense)

                    // Auto-create category if it doesn't exist yet
                    repo.ensureCategoryExists(category)

                    val transaction = Transaction(
                        amount = finalAmount,
                        description = parsed.description,
                        category = category,
                        type = if (parsed.isExpense) TransactionType.EXPENSE else TransactionType.INCOME,
                        source = TransactionSource.NOTIFICATION,
                        merchantName = parsed.merchantName,
                        notes = notes
                    )
                    repo.addTransaction(transaction)

                    // Post an in-app notification
                    val sym = currencyInfoFor(appCurrency).symbol
                    val typeLabel = if (parsed.isExpense) "Expense" else "Income"
                    repo.addInAppNotification(
                        InAppNotification(
                            title = "$typeLabel detected – $appName",
                            message = "${parsed.description}: $sym${String.format("%.2f", finalAmount)}" +
                                (if (parsed.merchantName.isNotEmpty()) " at ${parsed.merchantName}" else "") +
                                (if (conversionNote.isNotEmpty()) " (${parsed.amount} ${parsed.currency})" else ""),
                            type = InAppNotificationType.TRANSACTION_DETECTED,
                            relatedTransactionId = transaction.id
                        )
                    )

                    Log.d(TAG, "Saved notification transaction: $finalAmount $appCurrency")
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
