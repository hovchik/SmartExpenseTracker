package com.flowsense.app.service.subscription

/**
 * Available subscription plans with their Google Play product IDs and pricing.
 *
 * Pricing (before tax):
 *  - 1 Month  : $1.99
 *  - 6 Months : $10.00  ($1.67/mo)
 *  - 1 Year   : $18.00  ($1.50/mo)
 *  - Lifetime : $40.00  (one-time purchase)
 */
enum class SubscriptionPlan(
    val productId: String,
    val displayName: String,
    val price: String,
    val periodLabel: String,
    val perMonthPrice: String?,
    val durationMonths: Int,
    val isOneTime: Boolean
) {
    MONTHLY(
        productId = "flowsense_premium_monthly",
        displayName = "Monthly",
        price = "$1.99",
        periodLabel = "/ month",
        perMonthPrice = null,
        durationMonths = 1,
        isOneTime = false
    ),
    SEMI_ANNUAL(
        productId = "flowsense_premium_6months",
        displayName = "6 Months",
        price = "$10.00",
        periodLabel = "/ 6 months",
        perMonthPrice = "$1.67/mo",
        durationMonths = 6,
        isOneTime = false
    ),
    ANNUAL(
        productId = "flowsense_premium_annual",
        displayName = "1 Year",
        price = "$18.00",
        periodLabel = "/ year",
        perMonthPrice = "$1.50/mo",
        durationMonths = 12,
        isOneTime = false
    ),
    LIFETIME(
        productId = "flowsense_premium_lifetime",
        displayName = "Lifetime",
        price = "$40.00",
        periodLabel = "one-time",
        perMonthPrice = "Pay once, forever",
        durationMonths = 0,
        isOneTime = true
    );

    /** Savings percentage compared to monthly plan. */
    val savingsPercent: Int
        get() = when (this) {
            MONTHLY -> 0
            SEMI_ANNUAL -> 16   // (1.99*6 - 10) / (1.99*6) ≈ 16%
            ANNUAL -> 25        // (1.99*12 - 18) / (1.99*12) ≈ 25%
            LIFETIME -> 0       // Not comparable
        }

    companion object {
        /** All subscription product IDs (recurring). */
        val subscriptionProductIds = entries.filter { !it.isOneTime }.map { it.productId }

        /** All one-time (in-app) product IDs. */
        val inAppProductIds = entries.filter { it.isOneTime }.map { it.productId }

        fun fromProductId(productId: String): SubscriptionPlan? =
            entries.firstOrNull { it.productId == productId }
    }
}
