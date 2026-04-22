# Google Play Console — Subscription Setup Guide

This guide walks through creating the subscription products
required by FlowSense (SmartExpenseTracker) in the Google Play Console.

---

## Prerequisites

- A Google Play Developer account ($25 one-time registration fee).
- The app must be published (at least to an internal testing track) before you
  can create subscriptions.
- A signed release APK/AAB uploaded to at least one testing track.
- A Google merchant account linked in **Play Console → Settings → Payments profile**.

---

## Overview of Subscriptions to Create

| # | Product ID | Type | Price | Billing Period |
|---|-----------|------|-------|----------------|
| 1 | `flowsense-premium-monthly` | Subscription | $1.99 | 1 month |
| 2 | `flowsense-premium-6months` | Subscription | $10.00 | 6 months |
| 3 | `flowsense-premium-annual` | Subscription | $18.00 | 1 year |

All three are auto-renewing subscriptions.

---

## Part A — Create the Subscription Products (Monthly, 6-Month, Annual)

All three recurring plans live under a single **subscription** in the Play
Console, each as a separate **base plan**.

### Step 1: Create the Subscription

1. Open [Google Play Console](https://play.google.com/console/).
2. Select **your app** → **Monetize** → **Products** → **Subscriptions**.
3. Click **Create subscription**.
4. Fill in:
   - **Product ID**: `flowsense-premium-monthly`
     > Note: The product ID for the subscription container can match your
     > first base plan. Alternatively, you can use a generic ID like
     > `flowsense-premium` and set individual base plan IDs. The app code
     > queries each product ID separately, so each plan needs its own
     > subscription entry OR its own base plan within a shared subscription.
   - **Name**: `FlowSense Premium — Monthly`
   - **Description**: `Unlock all premium features: Store Map, OCR Receipt Scanner, Application Scanner, Salary Scheduler, Budget Threshold, Custom Categories, and Scheduled Expenses.`
5. Click **Save**.

### Step 2: Add a Base Plan

After saving, you will see the **Base plans and offers** section.

1. Click **Add base plan**.
2. Configure:
   - **Base plan ID**: `monthly-base` (or leave the auto-generated one)
   - **Auto-renewing**: Yes
   - **Billing period**: **1 month**
   - **Price**: Set default price to **$1.99 USD**
     - Click **Set price** → enter `1.99` → the console will auto-calculate
       local prices for other countries. Review and adjust if needed.
   - **Renewal type**: Auto-renewing
3. Under **Offers** (optional):
   - You may add a free trial offer (e.g., 3 days) here if you want Google Play
     to manage the trial natively instead of the app-side trial logic.
4. Click **Activate** to make the base plan live.

### Step 3: Repeat for 6-Month Plan

Create a new subscription (or add a base plan to an existing one):

1. Go to **Monetize** → **Products** → **Subscriptions** → **Create subscription**.
2. Fill in:
   - **Product ID**: `flowsense-premium-6months`
   - **Name**: `FlowSense Premium — 6 Months`
   - **Description**: `6-month premium access. Save 16% compared to monthly.`
3. Add a base plan:
   - **Billing period**: **6 months**
   - **Price**: **$10.00 USD**
4. **Activate** the base plan.

### Step 4: Repeat for Annual Plan

1. **Create subscription**:
   - **Product ID**: `flowsense-premium-annual`
   - **Name**: `FlowSense Premium — Annual`
   - **Description**: `Annual premium access. Best value — save 25% compared to monthly.`
2. Add a base plan:
   - **Billing period**: **1 year**
   - **Price**: **$18.00 USD**
3. **Activate** the base plan.

---

## Part B — Configure Grace Period & Resubscribe Settings

For each subscription, configure these recommended settings:

1. Go to **Monetize** → **Subscriptions** → select a subscription → **Settings**.
2. **Grace period**: Enable, set to **7 days** (Google retries the payment
   during this window; the user keeps access).
3. **Account hold**: Enable, set to **30 days** (user loses access but can
   resubscribe without re-purchasing).
4. **Resubscribe**: Allow users to resubscribe from the Play Store.
5. **Allow upgrades/downgrades**: If all three plans are under one subscription,
   enable plan changes with proration mode **Charge prorated price immediately**.

---

## Part C — Testing Setup

### License Testers

1. Go to **Settings** → **License testing**.
2. Add the Google account email addresses of your testers.
3. License testers can make purchases **without being charged**.

### Internal Testing Track

1. Go to **Testing** → **Internal testing**.
2. Create a release and upload your signed AAB.
3. Add testers by email or via a Google Group link.
4. Testers install the app from the Play Store link and can make test purchases.

### Verify Product IDs Match the App Code

The product IDs configured in the Play Console **must exactly match** the
values in the app source code:

```
File: app/src/main/java/com/flowsense/app/service/subscription/SubscriptionPlan.kt

MONTHLY     → "flowsense-premium-monthly"
SEMI_ANNUAL → "flowsense-premium-6months"
ANNUAL      → "flowsense-premium-annual"
```

If there is a mismatch, the billing client will return no product details and
the purchase flow will fail silently.

---

## Part D — Checklist

- [ ] Merchant account linked in Play Console
- [ ] App uploaded to at least internal testing track
- [ ] Subscription created: `flowsense-premium-monthly` ($1.99/month)
- [ ] Subscription created: `flowsense-premium-6months` ($10.00/6 months)
- [ ] Subscription created: `flowsense-premium-annual` ($18.00/year)
- [ ] All base plans activated
- [ ] Grace period enabled (7 days recommended)
- [ ] Account hold enabled (30 days recommended)
- [ ] License testers added for testing
- [ ] Product IDs verified against `SubscriptionPlan.kt`
- [ ] Test purchase completed successfully on a real device

---

## Troubleshooting

| Problem | Cause | Fix |
|---------|-------|-----|
| "Item not available" error | Product not active or app version mismatch | Ensure the product is **Active** and the APK with matching billing code is on the same track the tester is using |
| No product details returned | Product ID mismatch | Double-check IDs in `SubscriptionPlan.kt` match the console exactly |
| Purchase completes but app doesn't unlock | Acknowledgment failure | Check logcat for `SubscriptionManager` — the purchase must be acknowledged within 3 days or Google refunds it |
| "This version of the app is not configured for billing" | Unsigned/debug build | Use a **signed release build** uploaded to a testing track; debug builds only work with license testers |
| Test purchase requires payment | Tester not in license testing list | Add the Google account to **Settings → License testing** |

---

## Reference

- [Google Play Billing Library docs](https://developer.android.com/google/play/billing)
- [Create subscriptions](https://support.google.com/googleplay/android-developer/answer/140504)
- [Test in-app billing](https://developer.android.com/google/play/billing/test)
- App billing version: `com.android.billingclient:billing-ktx:8.0.0`
