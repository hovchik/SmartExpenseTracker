package com.flowsense.app.analytics

import com.flowsense.app.data.model.Budget
import com.flowsense.app.data.model.Category
import com.flowsense.app.data.model.Transaction
import com.flowsense.app.data.model.TransactionType
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Deterministic, on-device financial-analysis engine.
 *
 * This is the single source of truth for "what does the data say about the user's
 * money". It produces a [FinancialAnalysis] covering:
 *
 *  - a weighted **financial-health score** (savings rate, budget adherence,
 *    spending stability, recurring burden);
 *  - a **cash-flow forecast** (EWMA over historical months + current-month pace);
 *  - **anomaly detection** using robust median/MAD z-scores per category;
 *  - **recurring-charge / subscription detection** from interval regularity;
 *  - per-category **trend** direction over recent months.
 *
 * It is intentionally rule-based and framework-free so it runs instantly with no
 * network/model dependency, is fully reproducible, and is unit-testable on the JVM.
 * The optional LLM layer ([com.flowsense.app.ai]) phrases these findings for the
 * user; this engine computes them.
 *
 * @param now injectable clock for deterministic testing.
 */
class FinancialAnalyticsEngine(
    private val now: () -> Long = System::currentTimeMillis
) {

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
        const val DAYS_PER_MONTH = 30.4375 // average Gregorian month length

        // Anomaly detection
        const val ANOMALY_Z_THRESHOLD = 3.5
        const val ANOMALY_MIN_SAMPLES = 5
        const val ANOMALY_LOOKBACK_DAYS = 90L

        // Recurring detection
        const val RECURRING_MIN_OCCURRENCES = 3
        const val RECURRING_MIN_REGULARITY = 0.55
        const val RECURRING_MAX_INTERVAL_DAYS = 400.0

        // Health-score component weights (renormalised when a component is absent).
        const val W_SAVINGS = 0.35
        const val W_BUDGET = 0.25
        const val W_STABILITY = 0.20
        const val W_RECURRING = 0.20
    }

    /** Runs every analysis in a single pass over the (non-deleted) transactions. */
    fun analyze(
        transactions: List<Transaction>,
        budgets: List<Budget> = emptyList(),
        categories: List<Category> = emptyList()
    ): FinancialAnalysis {
        val active = transactions.filter { !it.isDeleted }
        if (active.isEmpty()) return FinancialAnalysis.EMPTY

        val expenses = active.filter { it.type == TransactionType.EXPENSE }
        val income = active.filter { it.type == TransactionType.INCOME }

        val monthlySpend = monthlyTotals(expenses)
        val monthlyIncome = monthlyTotals(income)
        val monthsOfHistory = monthlySpend.size

        val avgMonthlySpend = completedMonthAverage(monthlySpend)
        val avgMonthlyIncome = completedMonthAverage(monthlyIncome)

        val savingsRate = trailingSavingsRate(expenses, income)
        val recurring = detectRecurringCharges(expenses)
        val recurringMonthlyCost = recurring.sumOf { it.estimatedMonthlyCost }

        val forecast = forecastCashFlow(monthlySpend, monthlyIncome, expenses)
        val health = scoreHealth(
            savingsRate = savingsRate,
            monthlySpendSeries = monthlySpend.completedValues(),
            avgMonthlyIncome = avgMonthlyIncome,
            recurringMonthlyCost = recurringMonthlyCost,
            budgets = budgets,
            categories = categories,
            expenses = expenses
        )

        return FinancialAnalysis(
            health = health,
            forecast = forecast,
            anomalies = detectAnomalies(expenses),
            recurringCharges = recurring,
            categoryTrends = categoryTrends(expenses),
            savingsRate = savingsRate,
            monthlyAverageSpend = avgMonthlySpend,
            monthlyAverageIncome = avgMonthlyIncome,
            recurringMonthlyCost = recurringMonthlyCost,
            monthsOfHistory = monthsOfHistory
        )
    }

    // ─── Monthly bucketing ──────────────────────────────────────────

    /** Absolute month index (year*12+month) so chronological ordering is trivial. */
    private fun monthIndex(timestamp: Long): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        return cal.get(Calendar.YEAR) * 12 + cal.get(Calendar.MONTH)
    }

    private val currentMonthIndex: Int get() = monthIndex(now())

    /**
     * Totals per calendar month, with all intermediate months filled with 0 so the
     * resulting series is contiguous (gaps are real "spent nothing" months, which
     * matters for EWMA/volatility). Sorted oldest → newest.
     */
    private fun monthlyTotals(txns: List<Transaction>): MonthSeries {
        if (txns.isEmpty()) return MonthSeries(emptyList(), currentMonthIndex)
        val byMonth = txns.groupBy { monthIndex(it.timestamp) }
            .mapValues { e -> e.value.sumOf { it.amount } }
        val first = byMonth.keys.min()
        val last = max(byMonth.keys.max(), currentMonthIndex)
        val series = (first..last).map { idx -> MonthPoint(idx, byMonth[idx] ?: 0.0) }
        return MonthSeries(series, currentMonthIndex)
    }

    private fun completedMonthAverage(series: MonthSeries): Double {
        val completed = series.completedValues()
        return if (completed.isEmpty()) {
            // Only the current (partial) month exists — fall back to it directly.
            Statistics.mean(series.points.map { it.total })
        } else {
            Statistics.mean(completed)
        }
    }

    // ─── Savings rate ───────────────────────────────────────────────

    /** (income − spend) / income over the trailing 30 days, as a fraction. */
    private fun trailingSavingsRate(
        expenses: List<Transaction>,
        income: List<Transaction>
    ): Double {
        val cutoff = now() - 30 * DAY_MS
        val spend = expenses.filter { it.timestamp >= cutoff }.sumOf { it.amount }
        val earned = income.filter { it.timestamp >= cutoff }.sumOf { it.amount }
        if (earned <= 0.0) return 0.0
        return (earned - spend) / earned
    }

    // ─── Cash-flow forecast ─────────────────────────────────────────

    private fun forecastCashFlow(
        monthlySpend: MonthSeries,
        monthlyIncome: MonthSeries,
        expenses: List<Transaction>
    ): CashFlowForecast {
        val spendHistory = monthlySpend.completedValues()
        val incomeHistory = monthlyIncome.completedValues()

        // Steady-state estimate from completed months; fall back to whatever we have.
        val projSpend = if (spendHistory.isNotEmpty()) Statistics.ewma(spendHistory)
        else Statistics.mean(monthlySpend.points.map { it.total })
        val projIncome = if (incomeHistory.isNotEmpty()) Statistics.ewma(incomeHistory)
        else Statistics.mean(monthlyIncome.points.map { it.total })

        // Current-month pace projection.
        val cal = Calendar.getInstance().apply { timeInMillis = now() }
        val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val spentThisMonth = expenses
            .filter { monthIndex(it.timestamp) == currentMonthIndex }
            .sumOf { it.amount }
        val pace = if (dayOfMonth > 0) spentThisMonth / dayOfMonth * daysInMonth else spentThisMonth

        // Blend EWMA with pace early in the month (pace is noisy until enough days pass).
        val paceWeight = Statistics.clamp(dayOfMonth / daysInMonth.toDouble(), 0.0, 1.0)
        val endOfMonth = if (spendHistory.isEmpty()) pace
        else paceWeight * pace + (1 - paceWeight) * projSpend

        // Confidence: more months + more stable spend => higher confidence.
        val historyConfidence = Statistics.clamp(spendHistory.size / 6.0, 0.0, 1.0)
        val stability = 1.0 - Statistics.clamp(Statistics.coefficientOfVariation(spendHistory), 0.0, 1.0)
        val confidence = if (spendHistory.size < 2) 0.2 else (0.5 * historyConfidence + 0.5 * stability)

        return CashFlowForecast(
            projectedMonthlySpend = projSpend,
            projectedMonthlyIncome = projIncome,
            projectedNet = projIncome - projSpend,
            projectedEndOfMonthSpend = endOfMonth,
            confidence = confidence,
            method = if (spendHistory.isEmpty()) "current-month-pace" else "ewma+pace"
        )
    }

    // ─── Anomaly detection ──────────────────────────────────────────

    /**
     * Flags transactions whose amount is an extreme outlier *for their own category*
     * using a robust median/MAD z-score. Per-category baselines mean a $200 grocery
     * run isn't flagged while a $200 coffee is.
     */
    private fun detectAnomalies(expenses: List<Transaction>): List<SpendingAnomaly> {
        val cutoff = now() - ANOMALY_LOOKBACK_DAYS * DAY_MS
        val recent = expenses.filter { it.timestamp >= cutoff }
        val anomalies = mutableListOf<SpendingAnomaly>()

        recent.groupBy { it.category }.forEach { (category, txns) ->
            if (txns.size < ANOMALY_MIN_SAMPLES) return@forEach
            val amounts = txns.map { it.amount }
            val median = Statistics.median(amounts)
            txns.forEach { t ->
                val z = Statistics.robustZScore(t.amount, amounts)
                // Only flag *over*-spends (positive deviation); under-spends aren't risks.
                if (z >= ANOMALY_Z_THRESHOLD && t.amount > median) {
                    anomalies.add(
                        SpendingAnomaly(
                            transactionId = t.id,
                            description = t.description,
                            merchantName = t.merchantName,
                            category = category,
                            amount = t.amount,
                            expectedAmount = median,
                            deviationScore = z,
                            timestamp = t.timestamp
                        )
                    )
                }
            }
        }
        return anomalies.sortedByDescending { it.deviationScore }
    }

    // ─── Recurring-charge / subscription detection ──────────────────

    /**
     * Infers subscriptions from *behaviour* rather than category guesses: groups by
     * merchant (or description), and treats a group as recurring when its inter-charge
     * intervals are regular enough. Detects weekly→yearly cadences.
     */
    fun detectRecurringCharges(expenses: List<Transaction>): List<RecurringCharge> {
        val groups = expenses
            .map { it to recurringKey(it) }
            .filter { it.second.isNotBlank() }
            .groupBy({ it.second }, { it.first })

        val charges = mutableListOf<RecurringCharge>()
        for ((_, txns) in groups) {
            if (txns.size < RECURRING_MIN_OCCURRENCES) continue
            val sorted = txns.sortedBy { it.timestamp }
            val intervals = sorted.zipWithNext { a, b ->
                (b.timestamp - a.timestamp).toDouble() / DAY_MS
            }.filter { it > 0.5 } // drop same-day duplicates
            if (intervals.size < RECURRING_MIN_OCCURRENCES - 1) continue

            val medianInterval = Statistics.median(intervals)
            if (medianInterval <= 0 || medianInterval > RECURRING_MAX_INTERVAL_DAYS) continue

            // Regularity: 1 − (spread of intervals / median). Perfectly periodic ≈ 1.
            val mad = Statistics.medianAbsoluteDeviation(intervals)
            val regularity = Statistics.clamp(1.0 - mad / medianInterval, 0.0, 1.0)
            if (regularity < RECURRING_MIN_REGULARITY) continue

            val avgAmount = Statistics.median(sorted.map { it.amount })
            charges.add(
                RecurringCharge(
                    merchantName = sorted.first().merchantName.ifBlank { sorted.first().description },
                    category = sorted.last().category,
                    averageAmount = avgAmount,
                    intervalDays = medianInterval,
                    cadence = classifyCadence(medianInterval),
                    occurrences = sorted.size,
                    lastChargeTimestamp = sorted.last().timestamp,
                    estimatedMonthlyCost = avgAmount * (DAYS_PER_MONTH / medianInterval),
                    regularity = regularity
                )
            )
        }
        return charges.sortedByDescending { it.estimatedMonthlyCost }
    }

    private fun recurringKey(t: Transaction): String {
        val merchant = t.merchantName.trim().lowercase()
        if (merchant.isNotEmpty()) return merchant
        // Fall back to a normalised description (strip digits so "Netflix 04/26" groups).
        return t.description.trim().lowercase().replace(Regex("[0-9]"), "").trim()
    }

    private fun classifyCadence(days: Double): Cadence = when {
        days <= 10 -> Cadence.WEEKLY
        days <= 20 -> Cadence.BIWEEKLY
        days <= 45 -> Cadence.MONTHLY
        days <= 120 -> Cadence.QUARTERLY
        days <= RECURRING_MAX_INTERVAL_DAYS -> Cadence.YEARLY
        else -> Cadence.IRREGULAR
    }

    // ─── Category trends ────────────────────────────────────────────

    /**
     * Per-category month-over-month trend. Compares the latest completed month
     * against the average of the preceding months, and confirms direction with the
     * sign of an OLS slope so a single spike doesn't read as a trend.
     */
    private fun categoryTrends(expenses: List<Transaction>): List<CategoryTrend> {
        val trends = mutableListOf<CategoryTrend>()
        expenses.groupBy { it.category }.forEach { (category, txns) ->
            val series = monthlyTotals(txns)
            val completed = series.completedValues()
            if (completed.size < 2) return@forEach

            val recent = completed.last()
            val previousBaseline = Statistics.mean(completed.dropLast(1))
            if (previousBaseline < 1e-9 && recent < 1e-9) return@forEach

            val changePct = if (previousBaseline > 1e-9)
                (recent - previousBaseline) / previousBaseline * 100 else 100.0
            val slope = Statistics.linearSlope(completed)

            val direction = when {
                changePct > 15 && slope > 0 -> TrendDirection.RISING
                changePct < -15 && slope < 0 -> TrendDirection.FALLING
                else -> TrendDirection.STABLE
            }
            trends.add(
                CategoryTrend(
                    category = category,
                    direction = direction,
                    recentMonthlyAverage = recent,
                    previousMonthlyAverage = previousBaseline,
                    changePercent = changePct
                )
            )
        }
        return trends.sortedByDescending { abs(it.changePercent) }
    }

    // ─── Financial-health score ─────────────────────────────────────

    private fun scoreHealth(
        savingsRate: Double,
        monthlySpendSeries: List<Double>,
        avgMonthlyIncome: Double,
        recurringMonthlyCost: Double,
        budgets: List<Budget>,
        categories: List<Category>,
        expenses: List<Transaction>
    ): FinancialHealth {
        val components = mutableListOf<HealthComponent>()

        // 1. Savings rate — 20%+ is the widely cited healthy target.
        val savingsScore = Statistics.clamp(savingsRate / 0.20, 0.0, 1.0) * 100
        components += HealthComponent(
            name = "Savings rate",
            score = savingsScore.roundToInt(),
            weight = W_SAVINGS,
            detail = "${(savingsRate * 100).roundToInt()}% saved (target ≥ 20%)."
        )

        // 2. Budget adherence — share of budgets on pace to stay within limit.
        val budgetScore = budgetAdherenceScore(budgets, categories, expenses)
        if (budgetScore != null) {
            components += HealthComponent(
                name = "Budget adherence",
                score = budgetScore.roundToInt(),
                weight = W_BUDGET,
                detail = "On-pace performance against your set budgets."
            )
        }

        // 3. Spending stability — low month-to-month volatility is healthier.
        if (monthlySpendSeries.size >= 2) {
            val cv = Statistics.coefficientOfVariation(monthlySpendSeries)
            val stabilityScore = Statistics.clamp(1.0 - cv, 0.0, 1.0) * 100
            components += HealthComponent(
                name = "Spending stability",
                score = stabilityScore.roundToInt(),
                weight = W_STABILITY,
                detail = "Month-to-month spending volatility (lower is better)."
            )
        }

        // 4. Recurring burden — recurring cost should be a modest share of income.
        if (avgMonthlyIncome > 1e-9) {
            val burden = recurringMonthlyCost / avgMonthlyIncome
            // 0% burden → 100, 40%+ → 0.
            val burdenScore = Statistics.clamp(1.0 - burden / 0.40, 0.0, 1.0) * 100
            components += HealthComponent(
                name = "Recurring burden",
                score = burdenScore.roundToInt(),
                weight = W_RECURRING,
                detail = "${(burden * 100).roundToInt()}% of income committed to recurring charges."
            )
        }

        if (components.isEmpty()) return FinancialHealth.UNKNOWN

        // Renormalise weights across whatever components were available.
        val totalWeight = components.sumOf { it.weight }
        val score = components.sumOf { it.score * (it.weight / totalWeight) }.roundToInt()
        val grade = gradeFor(score)

        return FinancialHealth(
            score = score,
            grade = grade,
            summary = healthSummary(score, savingsRate),
            components = components
        )
    }

    /** Returns null when the user has no budgets so the component is simply omitted. */
    private fun budgetAdherenceScore(
        budgets: List<Budget>,
        categories: List<Category>,
        expenses: List<Transaction>
    ): Double? {
        if (budgets.isEmpty()) return null
        val cal = Calendar.getInstance().apply { timeInMillis = now() }
        val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val monthExpenses = expenses.filter { monthIndex(it.timestamp) == currentMonthIndex }

        var totalRatio = 0.0
        for (budget in budgets) {
            if (budget.monthlyLimit <= 0) continue
            val categoryName = categories.find { it.id == budget.categoryId }?.name ?: budget.categoryId
            val spent = monthExpenses.filter { it.category == categoryName }.sumOf { it.amount }
            val projected = if (dayOfMonth > 0) spent / dayOfMonth * daysInMonth else spent
            // ratio of projected to limit; 1.0 means exactly on budget.
            totalRatio += projected / budget.monthlyLimit
        }
        val avgRatio = totalRatio / budgets.size
        // On/under budget (ratio ≤ 1) → 100; 2× over → 0.
        return Statistics.clamp(2.0 - avgRatio, 0.0, 1.0) * 100
    }

    private fun gradeFor(score: Int): String = when {
        score >= 85 -> "A"
        score >= 70 -> "B"
        score >= 55 -> "C"
        score >= 40 -> "D"
        else -> "F"
    }

    private fun healthSummary(score: Int, savingsRate: Double): String = when {
        score >= 85 -> "Excellent — your finances are in great shape."
        score >= 70 -> "Good — solid habits with a little room to optimise."
        score >= 55 -> "Fair — a few areas are dragging your score down."
        score >= 40 -> "Needs attention — review spending and savings."
        else -> {
            if (savingsRate < 0) "At risk — you're spending more than you earn."
            else "At risk — savings are very low; tighten discretionary spend."
        }
    }
}

// ─── Internal month-series helpers ──────────────────────────────────

private data class MonthPoint(val monthIndex: Int, val total: Double)

private data class MonthSeries(val points: List<MonthPoint>, val currentMonthIndex: Int) {
    /** Totals for fully-elapsed months only (excludes the in-progress current month). */
    fun completedValues(): List<Double> =
        points.filter { it.monthIndex < currentMonthIndex }.map { it.total }
}
