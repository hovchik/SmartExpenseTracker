package com.smartexpense.tracker.service.subscription

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.android.billingclient.api.*
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
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isSubscribed = MutableStateFlow(loadSubscriptionState())
    val isSubscribed: StateFlow<Boolean> = _isSubscribed.asStateFlow()

    private val _activePlan = MutableStateFlow(loadActivePlan())
    val activePlan: StateFlow<SubscriptionPlan?> = _activePlan.asStateFlow()

    private val _billingError = MutableStateFlow<String?>(null)
    val billingError: StateFlow<String?> = _billingError.asStateFlow()

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
     */
    fun launchPurchaseFlow(activity: Activity, plan: SubscriptionPlan) {
        _billingError.value = null
        ensureConnected {
            if (plan.isOneTime) {
                queryAndLaunchInApp(activity, plan)
            } else {
                queryAndLaunchSubscription(activity, plan)
            }
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
                _billingError.value = "Could not load plan details. Please try again."
                return@queryProductDetailsAsync
            }

            val productDetails = productDetailsList.firstOrNull()
            if (productDetails == null) {
                Log.e(TAG, "No product details for ${plan.productId}")
                _billingError.value = "Plan not available. Please try again later."
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
                _billingError.value = "Could not load plan details. Please try again."
                return@queryProductDetailsAsync
            }

            val productDetails = productDetailsList.firstOrNull()
            if (productDetails == null) {
                _billingError.value = "Plan not available. Please try again later."
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
     */
    fun restorePurchases() {
        _billingError.value = null
        ensureConnected { queryExistingPurchases() }
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
            .apply {
                if (plan != null) putString(KEY_ACTIVE_PLAN_ID, plan.productId)
            }
            .apply()
        _isSubscribed.value = true
        _activePlan.value = plan
    }

    /**
     * Deactivate subscription (e.g. on cancellation or failed renewal).
     */
    fun deactivate() {
        prefs.edit()
            .putBoolean(KEY_IS_SUBSCRIBED, false)
            .remove(KEY_SUBSCRIPTION_EXPIRY)
            .remove(KEY_ACTIVE_PLAN_ID)
            .apply()
        _isSubscribed.value = false
        _activePlan.value = null
    }

    /** Re-check stored state (e.g. after returning from billing flow). */
    fun refresh() {
        _isSubscribed.value = loadSubscriptionState()
        _activePlan.value = loadActivePlan()
    }

    fun clearBillingError() {
        _billingError.value = null
    }
}
