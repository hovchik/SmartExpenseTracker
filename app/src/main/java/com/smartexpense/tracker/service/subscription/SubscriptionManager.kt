package com.smartexpense.tracker.service.subscription

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.android.billingclient.api.*
import com.smartexpense.tracker.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages subscription state for premium feature gating, backed by
 * Google Play Billing Library.
 *
 * Premium features:
 * - Store Map
 * - OCR Receipt Scanner
 * - Application Scanner
 * - Salary Scheduler
 * - Budget Threshold
 * - Categories (add new)
 * - Scheduled Expenses
 *
 * Supports four plans:
 * - Monthly ($1.99), 6-Month ($10), Annual ($18), Lifetime ($40)
 *
 * Lifetime is an in-app (one-time) purchase; the rest are auto-renewing subscriptions.
 */
class SubscriptionManager(private val context: Context) : PurchasesUpdatedListener {

    companion object {
        private const val TAG = "SubscriptionManager"
        private const val PREFS_NAME = "flowsense_subscription"
        private const val KEY_IS_SUBSCRIBED = "is_subscribed"
        private const val KEY_SUBSCRIPTION_EXPIRY = "subscription_expiry"
        private const val KEY_ACTIVE_PLAN_ID = "active_plan_id"
        private const val KEY_FREE_TRIAL_USED = "free_trial_used"
        private const val KEY_IS_TRIAL = "is_trial"
        private const val FREE_TRIAL_DAYS = 3
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isSubscribed = MutableStateFlow(loadSubscriptionState())
    val isSubscribed: StateFlow<Boolean> = _isSubscribed.asStateFlow()

    private val _activePlan = MutableStateFlow(loadActivePlan())
    val activePlan: StateFlow<SubscriptionPlan?> = _activePlan.asStateFlow()

    private val _billingError = MutableStateFlow<String?>(null)
    val billingError: StateFlow<String?> = _billingError.asStateFlow()

    private val _isTrialActive = MutableStateFlow(loadTrialState())
    val isTrialActive: StateFlow<Boolean> = _isTrialActive.asStateFlow()

    val isTrialEligible: Boolean
        get() = !prefs.getBoolean(KEY_FREE_TRIAL_USED, false) && !_isSubscribed.value

    private var billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases()
        .build()

    private var isClientReady = false

    init {
        connectBillingClient()
    }

    // ─── Billing Client Connection ─────────────────────────────────

    private fun connectBillingClient() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    isClientReady = true
                    Log.d(TAG, "Billing client connected")
                    // Verify existing purchases on connect
                    queryExistingPurchases()
                } else {
                    Log.w(TAG, "Billing setup failed: ${billingResult.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                isClientReady = false
                Log.w(TAG, "Billing service disconnected")
            }
        })
    }

    private fun ensureConnected(block: () -> Unit) {
        if (isClientReady) {
            block()
        } else {
            billingClient.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        isClientReady = true
                        block()
                    } else {
                        _billingError.value = "Unable to connect to Google Play. Please try again."
                    }
                }

                override fun onBillingServiceDisconnected() {
                    isClientReady = false
                }
            })
        }
    }

    // ─── Launch Purchase Flow ──────────────────────────────────────

    /**
     * Launch the Google Play purchase flow for the given [plan].
     * Must be called from an Activity context.
     *
     * In debug builds, if the billing client cannot connect (e.g. emulator without
     * Google Play), falls back to activating the subscription directly for testing.
     */
    fun launchPurchaseFlow(activity: Activity, plan: SubscriptionPlan) {
        _billingError.value = null
        if (isClientReady) {
            if (plan.isOneTime) {
                queryAndLaunchInApp(activity, plan)
            } else {
                queryAndLaunchSubscription(activity, plan)
            }
        } else {
            billingClient.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        isClientReady = true
                        if (plan.isOneTime) {
                            queryAndLaunchInApp(activity, plan)
                        } else {
                            queryAndLaunchSubscription(activity, plan)
                        }
                    } else {
                        handleBillingUnavailable(plan)
                    }
                }

                override fun onBillingServiceDisconnected() {
                    isClientReady = false
                    handleBillingUnavailable(plan)
                }
            })
        }
    }

    private fun handleBillingUnavailable(plan: SubscriptionPlan) {
        if (BuildConfig.DEBUG) {
            Log.w(TAG, "Billing unavailable in debug build – activating ${plan.displayName} for testing")
            val expiryMillis = if (plan.isOneTime) 0L
            else System.currentTimeMillis() + plan.durationMonths * 30L * 24 * 60 * 60 * 1000
            activate(expiryMillis, plan)
            _billingError.value = "DEBUG: ${plan.displayName} plan activated for testing (no real charge)."
        } else {
            _billingError.value = "Unable to connect to Google Play. Please check your internet connection and try again."
        }
    }

    private fun queryAndLaunchSubscription(activity: Activity, plan: SubscriptionPlan) {
        val queryParams = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(plan.productId)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )
            )
            .build()

        billingClient.queryProductDetailsAsync(queryParams) { billingResult, productDetailsList ->
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.e(TAG, "Query subs failed: ${billingResult.debugMessage}")
                handleBillingUnavailable(plan)
                return@queryProductDetailsAsync
            }

            val productDetails = productDetailsList.firstOrNull()
            if (productDetails == null) {
                Log.e(TAG, "No product details for ${plan.productId}")
                handleBillingUnavailable(plan)
                return@queryProductDetailsAsync
            }

            val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken
            if (offerToken == null) {
                _billingError.value = "No subscription offer available."
                return@queryProductDetailsAsync
            }

            val flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(
                    listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(productDetails)
                            .setOfferToken(offerToken)
                            .build()
                    )
                )
                .build()

            billingClient.launchBillingFlow(activity, flowParams)
        }
    }

    private fun queryAndLaunchInApp(activity: Activity, plan: SubscriptionPlan) {
        val queryParams = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(plan.productId)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()

        billingClient.queryProductDetailsAsync(queryParams) { billingResult, productDetailsList ->
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.e(TAG, "Query in-app failed: ${billingResult.debugMessage}")
                handleBillingUnavailable(plan)
                return@queryProductDetailsAsync
            }

            val productDetails = productDetailsList.firstOrNull()
            if (productDetails == null) {
                handleBillingUnavailable(plan)
                return@queryProductDetailsAsync
            }

            val flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(
                    listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(productDetails)
                            .build()
                    )
                )
                .build()

            billingClient.launchBillingFlow(activity, flowParams)
        }
    }

    // ─── Purchase Callback ─────────────────────────────────────────

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { purchase ->
                    handlePurchase(purchase)
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                Log.d(TAG, "User cancelled purchase")
            }
            else -> {
                Log.e(TAG, "Purchase error: ${billingResult.responseCode} – ${billingResult.debugMessage}")
                _billingError.value = "Purchase failed. Please try again."
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return

        // Acknowledge the purchase if not already acknowledged
        if (!purchase.isAcknowledged) {
            val ackParams = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            billingClient.acknowledgePurchase(ackParams) { ackResult ->
                if (ackResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Purchase acknowledged: ${purchase.products}")
                    activateFromPurchase(purchase)
                } else {
                    Log.e(TAG, "Acknowledge failed: ${ackResult.debugMessage}")
                }
            }
        } else {
            activateFromPurchase(purchase)
        }
    }

    private fun activateFromPurchase(purchase: Purchase) {
        val productId = purchase.products.firstOrNull() ?: return
        val plan = SubscriptionPlan.fromProductId(productId) ?: return

        val expiryMillis = if (plan.isOneTime) {
            0L // Lifetime: no expiry
        } else {
            // Estimate expiry from plan duration. The actual renewal is managed by Play.
            System.currentTimeMillis() + plan.durationMonths * 30L * 24 * 60 * 60 * 1000
        }

        activate(expiryMillis, plan)
    }

    // ─── Query Existing Purchases (restore) ────────────────────────

    private fun queryExistingPurchases() {
        // Check subscriptions
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        ) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val activeSub = purchases.firstOrNull { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                if (activeSub != null) {
                    activateFromPurchase(activeSub)
                    return@queryPurchasesAsync
                }
            }
            // Check in-app purchases (lifetime)
            billingClient.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            ) { inAppResult, inAppPurchases ->
                if (inAppResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    val lifetime = inAppPurchases.firstOrNull {
                        it.purchaseState == Purchase.PurchaseState.PURCHASED &&
                            it.products.any { pid -> pid == SubscriptionPlan.LIFETIME.productId }
                    }
                    if (lifetime != null) {
                        activateFromPurchase(lifetime)
                    }
                }
            }
        }
    }

    /**
     * Manually restore purchases – call from a "Restore Purchases" button.
     * Provides user feedback via [billingError] on success or failure.
     */
    fun restorePurchases() {
        _billingError.value = null
        ensureConnected {
            queryExistingPurchasesWithFeedback()
        }
    }

    private fun queryExistingPurchasesWithFeedback() {
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        ) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val activeSub = purchases.firstOrNull { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                if (activeSub != null) {
                    activateFromPurchase(activeSub)
                    _billingError.value = "Subscription restored successfully!"
                    return@queryPurchasesAsync
                }
            }
            // Check in-app purchases (lifetime)
            billingClient.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            ) { inAppResult, inAppPurchases ->
                if (inAppResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    val lifetime = inAppPurchases.firstOrNull {
                        it.purchaseState == Purchase.PurchaseState.PURCHASED &&
                            it.products.any { pid -> pid == SubscriptionPlan.LIFETIME.productId }
                    }
                    if (lifetime != null) {
                        activateFromPurchase(lifetime)
                        _billingError.value = "Lifetime purchase restored successfully!"
                        return@queryPurchasesAsync
                    }
                }
                _billingError.value = "No previous purchase found for this account."
            }
        }
    }

    // ─── Free Trial ────────────────────────────────────────────────

    /**
     * Starts a 3-day free trial. Can only be used once per device.
     * Returns `true` if the trial was started, `false` if already used.
     */
    fun startFreeTrial(): Boolean {
        if (prefs.getBoolean(KEY_FREE_TRIAL_USED, false)) return false
        if (_isSubscribed.value) return false

        val expiryMillis = System.currentTimeMillis() + FREE_TRIAL_DAYS * 24L * 60 * 60 * 1000
        prefs.edit()
            .putBoolean(KEY_IS_SUBSCRIBED, true)
            .putBoolean(KEY_FREE_TRIAL_USED, true)
            .putBoolean(KEY_IS_TRIAL, true)
            .putLong(KEY_SUBSCRIPTION_EXPIRY, expiryMillis)
            .remove(KEY_ACTIVE_PLAN_ID)
            .apply()
        _isSubscribed.value = true
        _isTrialActive.value = true
        _activePlan.value = null
        Log.d(TAG, "Free trial started, expires in $FREE_TRIAL_DAYS days")
        return true
    }

    private fun loadTrialState(): Boolean {
        return prefs.getBoolean(KEY_IS_TRIAL, false) && loadSubscriptionState()
    }

    // ─── Local State Management ────────────────────────────────────

    private fun loadSubscriptionState(): Boolean {
        val subscribed = prefs.getBoolean(KEY_IS_SUBSCRIBED, false)
        if (!subscribed) return false
        val expiry = prefs.getLong(KEY_SUBSCRIPTION_EXPIRY, 0L)
        // 0 = no expiry (lifetime or managed externally)
        if (expiry > 0 && System.currentTimeMillis() > expiry) {
            prefs.edit()
                .putBoolean(KEY_IS_SUBSCRIBED, false)
                .remove(KEY_SUBSCRIPTION_EXPIRY)
                .remove(KEY_ACTIVE_PLAN_ID)
                .apply()
            return false
        }
        return true
    }

    private fun loadActivePlan(): SubscriptionPlan? {
        val planId = prefs.getString(KEY_ACTIVE_PLAN_ID, null) ?: return null
        return SubscriptionPlan.fromProductId(planId)
    }

    /**
     * Activate subscription. Call after successful payment verification.
     * @param expiryMillis epoch-millis when the subscription expires, or 0 for no expiry.
     * @param plan the plan that was purchased, or null if activating manually.
     */
    fun activate(expiryMillis: Long = 0L, plan: SubscriptionPlan? = null) {
        prefs.edit()
            .putBoolean(KEY_IS_SUBSCRIBED, true)
            .putLong(KEY_SUBSCRIPTION_EXPIRY, expiryMillis)
            .putBoolean(KEY_IS_TRIAL, false) // real purchase clears trial state
            .apply {
                if (plan != null) putString(KEY_ACTIVE_PLAN_ID, plan.productId)
            }
            .apply()
        _isSubscribed.value = true
        _isTrialActive.value = false
        _activePlan.value = plan
    }

    /**
     * Deactivate subscription (e.g. on cancellation or failed renewal).
     */
    fun deactivate() {
        prefs.edit()
            .putBoolean(KEY_IS_SUBSCRIBED, false)
            .putBoolean(KEY_IS_TRIAL, false)
            .remove(KEY_SUBSCRIPTION_EXPIRY)
            .remove(KEY_ACTIVE_PLAN_ID)
            .apply()
        _isSubscribed.value = false
        _isTrialActive.value = false
        _activePlan.value = null
    }

    /** Re-check stored state (e.g. after returning from billing flow). */
    fun refresh() {
        _isSubscribed.value = loadSubscriptionState()
        _isTrialActive.value = loadTrialState()
        _activePlan.value = loadActivePlan()
    }

    fun clearBillingError() {
        _billingError.value = null
    }
}
