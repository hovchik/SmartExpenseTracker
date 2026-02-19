package com.smartexpense.tracker.service.notification

import android.app.Notification
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.smartexpense.tracker.SmartExpenseApp
import com.smartexpense.tracker.data.model.InAppNotification
import com.smartexpense.tracker.data.model.InAppNotificationType
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

/**
 * Listens to banking app notifications to automatically log transactions.
 * Monitors:
 *  1. Explicit banking app package names (whitelist).
 *  2. ANY app whose label/package contains a banking keyword (e.g. "bank", "arca",
 *     "payment", "finance") – covers locally-installed Armenian/CIS/regional banks
 *     that are not hard-coded in the whitelist.
 *
 * User must grant Notification Access permission in Android Settings.
 */
class BankingNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "BankingNotifListener"

        /** Keywords matched against the app label (human-readable name) and package name. */
        val BANKING_APP_NAME_KEYWORDS = listOf(
            "bank", "arca", "pay", "wallet", "finance", "credit", "loan",
            "money", "cash", "transfer", "saving", "invest", "revolut",
            "wise", "zelle", "venmo", "paypal", "idram", "ineco",
            "telcell", "easypay", "ameria", "ardshin", "acba",
            "converse", "evoca", "unibank"
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
        "am.imwallet.android",
        // Idram & Telcell
        "am.idram",
        "am.idram.android",
        "com.telcell.app",
        "am.easypay"
    )

    // Financial keywords to filter non-transaction notifications
    private val financialKeywords = listOf(
        "debited", "credited", "spent", "received", "paid", "charged",
        "transaction", "payment", "transfer", "withdrawn", "deposit",
        "$", "₹", "֏", "rs.", "inr", "usd", "amd", "eur", "gbp", "amt",
        "approved", "authcode", "purchase", "atm cash", "balance:", "credit account"
    )

    /** Returns the human-readable app label for a package, or null on failure. */
    private fun appLabel(packageName: String): String? = try {
        packageManager.getApplicationInfo(packageName, 0)
            .let { packageManager.getApplicationLabel(it).toString() }
    } catch (_: PackageManager.NameNotFoundException) { null }

    /** True if the notification source looks like a banking/financial app. */
    private fun isBankingSource(packageName: String): Boolean {
        if (packageName in monitoredPackages) return true
        // Also check dynamically configured packages from user settings
        val app = applicationContext as? SmartExpenseApp
        val userPackages = app?.repository?.appData?.value?.settings?.bankingAppPackages.orEmpty()
        if (packageName in userPackages) return true
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

            val lowerText = fullText.lowercase()
            if (financialKeywords.none { lowerText.contains(it) }) return

            val aiEngine = AiExpenseEngine()
            val app0 = applicationContext as? SmartExpenseApp
            val settings0 = app0?.repository?.appData?.value?.settings
            val parsed = aiEngine.parseFinancialMessage(
                fullText,
                customIncomeKeywords = settings0?.incomeKeywords.orEmpty(),
                customExpenseKeywords = settings0?.expenseKeywords.orEmpty()
            ) ?: return

            val appName = appLabel(packageName) ?: packageName

            Log.d(TAG, "Financial notification from $appName ($packageName): ${parsed.amount}")

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val app = applicationContext as? SmartExpenseApp ?: return@launch
                    val repo = app.repository
                    // Wait for repository to finish loading data from disk.
                    if (!repo.awaitInitialization()) {
                        Log.w(TAG, "Repository init timed out, skipping notification")
                        return@launch
                    }
                    val settings = repo.appData.value.settings
                    val appCurrency = settings.currencyCode

                    // ── Store original amount & currency ─────────────────────
                    val txCurrency = parsed.currency.ifEmpty { appCurrency }

                    val cardNote = if (parsed.cardLastFour.isNotEmpty()) "card:${parsed.cardLastFour}" else ""
                    val notes = listOf("Auto-detected from $appName", cardNote)
                        .filter { it.isNotBlank() }.joinToString("\n")

                    val userCatNames = repo.appData.value.categories
                        .filter { !it.isDefault }.map { it.name }
                    val category = aiEngine.categorize(parsed.description, parsed.isExpense, userCatNames)

                    // Auto-create category if it doesn't exist yet
                    repo.ensureCategoryExists(category)

                    // ── Capture current device location ──────────────────────
                    val location = LocationProvider.getLastKnownLocation(applicationContext)

                    val transaction = Transaction(
                        amount = parsed.amount,
                        description = parsed.description,
                        category = category,
                        type = if (parsed.isExpense) TransactionType.EXPENSE else TransactionType.INCOME,
                        source = TransactionSource.NOTIFICATION,
                        merchantName = parsed.merchantName,
                        notes = notes,
                        currencyCode = txCurrency,
                        latitude = location?.latitude,
                        longitude = location?.longitude
                    )
                    // addTransaction returns false if duplicate
                    val added = repo.addTransaction(transaction)
                    if (added) {
                        val sym = currencyInfoFor(txCurrency).symbol
                        val typeLabel = if (parsed.isExpense) "Expense" else "Income"
                        repo.addInAppNotification(
                            InAppNotification(
                                title = "$typeLabel detected – $appName",
                                message = "${parsed.description}: $sym${String.format("%.2f", parsed.amount)}" +
                                    (if (parsed.merchantName.isNotEmpty()) " at ${parsed.merchantName}" else ""),
                                type = InAppNotificationType.TRANSACTION_DETECTED,
                                relatedTransactionId = transaction.id
                            )
                        )
                        Log.d(TAG, "Saved notification transaction: ${parsed.amount} $txCurrency")
                    } else {
                        Log.d(TAG, "Skipped duplicate: ${parsed.amount}")
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
