package com.smartexpense.tracker.service.subscription

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages subscription state for premium feature gating.
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
 * In the current implementation subscription state is stored locally via SharedPreferences.
 * Replace [activate] / [deactivate] with real billing verification when integrating
 * Google Play Billing or a third-party payment provider.
 */
class SubscriptionManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "flowsense_subscription"
        private const val KEY_IS_SUBSCRIBED = "is_subscribed"
        private const val KEY_SUBSCRIPTION_EXPIRY = "subscription_expiry"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isSubscribed = MutableStateFlow(loadSubscriptionState())
    val isSubscribed: StateFlow<Boolean> = _isSubscribed.asStateFlow()

    private fun loadSubscriptionState(): Boolean {
        val subscribed = prefs.getBoolean(KEY_IS_SUBSCRIBED, false)
        if (!subscribed) return false
        val expiry = prefs.getLong(KEY_SUBSCRIPTION_EXPIRY, 0L)
        // 0 = no expiry (lifetime or managed externally)
        if (expiry > 0 && System.currentTimeMillis() > expiry) {
            // Expired – clear locally
            prefs.edit()
                .putBoolean(KEY_IS_SUBSCRIBED, false)
                .remove(KEY_SUBSCRIPTION_EXPIRY)
                .apply()
            return false
        }
        return true
    }

    /**
     * Activate subscription. Call after successful payment verification.
     * @param expiryMillis epoch-millis when the subscription expires, or 0 for no expiry.
     */
    fun activate(expiryMillis: Long = 0L) {
        prefs.edit()
            .putBoolean(KEY_IS_SUBSCRIBED, true)
            .putLong(KEY_SUBSCRIPTION_EXPIRY, expiryMillis)
            .apply()
        _isSubscribed.value = true
    }

    /**
     * Deactivate subscription (e.g. on cancellation or failed renewal).
     */
    fun deactivate() {
        prefs.edit()
            .putBoolean(KEY_IS_SUBSCRIBED, false)
            .remove(KEY_SUBSCRIPTION_EXPIRY)
            .apply()
        _isSubscribed.value = false
    }

    /** Re-check stored state (e.g. after returning from billing flow). */
    fun refresh() {
        _isSubscribed.value = loadSubscriptionState()
    }
}
