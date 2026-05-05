package com.flowsense.app.service.notification

import android.app.Notification
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.flowsense.app.FlowSenseApp
import com.flowsense.app.data.model.GeoLocation
import com.flowsense.app.data.model.InAppNotification
import com.flowsense.app.data.model.InAppNotificationType
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

    @Volatile
    private var isConnected = false

    override fun onListenerConnected() {
        isConnected = true
        Log.d(TAG, "Notification listener connected")
    }

    override fun onListenerDisconnected() {
        // The framework rebinds automatically as long as the user has granted
        // notification access. Manually calling requestRebind() here races with
        // the system's own rebind cycle (e.g. during package changes) and has
        // been observed to crash the system server with
        // "Service not registered" inside ManagedServices.unbindService.
        isConnected = false
        Log.d(TAG, "Notification listener disconnected")
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

    // Generic "this looks financial" markers — currency symbols / banking
    // context. Income- and expense-classifier vocabulary lives in
    // Settings.incomeKeywords / Settings.expenseKeywords (user-customizable)
    // and is unioned in below before checking the gate.
    private val financialKeywords = listOf(
        "transaction", "transfer", "amt", "balance:",
        "approved", "authcode",
        "$", "₹", "֏", "rs.", "inr", "usd", "amd", "eur", "gbp"
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
        val app = applicationContext as? FlowSenseApp
        val userPackages = app?.repository?.appData?.value?.settings?.bankingAppPackages.orEmpty()
        if (packageName in userPackages) return true
        val label = appLabel(packageName)?.lowercase() ?: ""
        val pkg   = packageName.lowercase()
        return BANKING_APP_NAME_KEYWORDS.any { kw -> label.contains(kw) || pkg.contains(kw) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!isConnected) return
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
            val app0 = applicationContext as? FlowSenseApp
            val settings0 = app0?.repository?.appData?.value?.settings

            // User-configured keywords also count as financial indicators
            val userKeywords = settings0?.expenseKeywords.orEmpty() +
                settings0?.incomeKeywords.orEmpty()
            val allFinancialKw = financialKeywords + userKeywords.map { it.lowercase() }
            if (allFinancialKw.none { it.isNotEmpty() && lowerText.contains(it) }) return

            val aiEngine = AiExpenseEngine()
            val parsed = aiEngine.parseFinancialMessage(
                fullText,
                customIncomeKeywords = settings0?.incomeKeywords.orEmpty(),
                customExpenseKeywords = settings0?.expenseKeywords.orEmpty()
            ) ?: return

            // Only proceed if the text contains at least one configured expense or income keyword
            val allKeywords = settings0?.expenseKeywords.orEmpty() + settings0?.incomeKeywords.orEmpty()
            if (allKeywords.isNotEmpty()) {
                val lowerMsg = fullText.lowercase()
                if (allKeywords.none { it.isNotEmpty() && lowerMsg.contains(it.lowercase()) }) {
                    Log.d(TAG, "Notification skipped: no configured expense/income keyword found")
                    return
                }
            }

            val appName = appLabel(packageName) ?: packageName

            Log.d(TAG, "Financial notification from $appName ($packageName): ${parsed.amount}")

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val app = applicationContext as? FlowSenseApp ?: return@launch
                    val repo = app.repository
                    // Wait for repository to finish loading data from disk.
                    if (!repo.awaitInitialization()) {
                        Log.w(TAG, "Repository init timed out, skipping notification")
                        return@launch
                    }
                    val settings = repo.appData.value.settings
                    val appCurrency = settings.currencyCode

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
                        }
                    }

                    val cardNote = if (parsed.cardLastFour.isNotEmpty()) "card:${parsed.cardLastFour}" else ""
                    val notes = listOf("Auto-detected from $appName", cardNote)
                        .filter { it.isNotBlank() }.joinToString("\n")

                    val userCatNames = repo.appData.value.categories
                        .filter { !it.isDefault }.map { it.name }
                    val categoryNames = repo.appData.value.categories.map { it.name }
                    val category = app.aiCategorize(
                        parsed.description, categoryNames, parsed.isExpense, userCatNames
                    )

                    // Auto-create category if it doesn't exist yet
                    repo.ensureCategoryExists(category)

                    // ── Capture current device location ──────────────────────
                    val location = LocationProvider.getLastKnownLocation(applicationContext)

                    val transaction = Transaction(
                        amount = finalAmount,
                        description = parsed.description,
                        category = category,
                        type = if (parsed.isExpense) TransactionType.EXPENSE else TransactionType.INCOME,
                        source = TransactionSource.NOTIFICATION,
                        merchantName = parsed.merchantName,
                        notes = notes,
                        currencyCode = appCurrency,
                        originalAmount = origAmount,
                        originalCurrencyCode = origCurrencyCode,
                        exchangeRate = usedRate,
                        location = location?.let { GeoLocation(it.latitude, it.longitude) }
                    )
                    // addTransaction returns false if duplicate
                    val added = repo.addTransaction(transaction)
                    if (added) {
                        val appSym = currencyInfoFor(appCurrency).symbol
                        val typeLabel = if (parsed.isExpense) "Expense" else "Income"
                        val notifMsg = if (origAmount > 0.0) {
                            val origSym = currencyInfoFor(origCurrencyCode).symbol
                            "${parsed.description}: $appSym${String.format("%.2f", finalAmount)} " +
                                "(${origSym}${String.format("%.2f", origAmount)} $origCurrencyCode)" +
                                (if (parsed.merchantName.isNotEmpty()) " at ${parsed.merchantName}" else "")
                        } else {
                            "${parsed.description}: $appSym${String.format("%.2f", finalAmount)}" +
                                (if (parsed.merchantName.isNotEmpty()) " at ${parsed.merchantName}" else "")
                        }
                        repo.addInAppNotification(
                            InAppNotification(
                                title = "$typeLabel detected – $appName",
                                message = notifMsg,
                                type = InAppNotificationType.TRANSACTION_DETECTED,
                                relatedTransactionId = transaction.id
                            )
                        )
                        // Auto-pin location on the map
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
                        Log.d(TAG, "Saved notification transaction: $finalAmount $appCurrency")
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
