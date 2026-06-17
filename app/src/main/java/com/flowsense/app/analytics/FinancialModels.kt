package com.flowsense.app.analytics

/**
 * Output models for the [FinancialAnalyticsEngine].
 *
 * These are deliberately framework-free (no Android / Compose imports) so the
 * analysis layer can be reused from the ViewModel, background workers, the widget,
 * and unit tests alike.
 */

/** Aggregate result bundling every analysis the engine produces in one pass. */
data class FinancialAnalysis(
    val health: FinancialHealth,
    val forecast: CashFlowForecast,
    val anomalies: List<SpendingAnomaly>,
    val recurringCharges: List<RecurringCharge>,
    val categoryTrends: List<CategoryTrend>,
    /** Net savings rate over the trailing 30 days, as a fraction (can be negative). */
    val savingsRate: Double,
    val monthlyAverageSpend: Double,
    val monthlyAverageIncome: Double,
    /** Total recurring obligations expressed as an estimated monthly cost. */
    val recurringMonthlyCost: Double,
    /** Number of full calendar months of usable history. */
    val monthsOfHistory: Int
) {
    companion object {
        /** Neutral result used when there is not enough data to analyse. */
        val EMPTY = FinancialAnalysis(
            health = FinancialHealth.UNKNOWN,
            forecast = CashFlowForecast.EMPTY,
            anomalies = emptyList(),
            recurringCharges = emptyList(),
            categoryTrends = emptyList(),
            savingsRate = 0.0,
            monthlyAverageSpend = 0.0,
            monthlyAverageIncome = 0.0,
            recurringMonthlyCost = 0.0,
            monthsOfHistory = 0
        )
    }
}

/**
 * A composite 0-100 financial health score built from weighted, independently
 * scored components. Mirrors the way personal-finance scores (e.g. savings rate,
 * budget adherence, spending stability) are graded in the literature.
 */
data class FinancialHealth(
    val score: Int,
    val grade: String,
    val summary: String,
    val components: List<HealthComponent>
) {
    companion object {
        val UNKNOWN = FinancialHealth(
            score = 0,
            grade = "—",
            summary = "Not enough data yet to score your finances.",
            components = emptyList()
        )
    }
}

/** One weighted dimension of the [FinancialHealth] score. */
data class HealthComponent(
    val name: String,
    val score: Int,
    /** Relative weight (0..1) this component contributed to the final score. */
    val weight: Double,
    val detail: String
)

/**
 * Forward-looking cash-flow projection. Combines an exponentially-weighted average
 * of historical months (for the steady-state estimate) with a pace-based projection
 * for the current, partially-elapsed month.
 */
data class CashFlowForecast(
    val projectedMonthlySpend: Double,
    val projectedMonthlyIncome: Double,
    val projectedNet: Double,
    /** Projected spend for the *current* calendar month at the present pace. */
    val projectedEndOfMonthSpend: Double,
    /** 0..1 confidence driven by amount of history and spending stability. */
    val confidence: Double,
    val method: String
) {
    companion object {
        val EMPTY = CashFlowForecast(0.0, 0.0, 0.0, 0.0, 0.0, "insufficient-data")
    }
}

/** A single transaction flagged as unusually large for its category. */
data class SpendingAnomaly(
    val transactionId: String,
    val description: String,
    val merchantName: String,
    val category: String,
    val amount: Double,
    /** Typical (median) amount for this category. */
    val expectedAmount: Double,
    /** Robust modified z-score; higher = more unusual. */
    val deviationScore: Double,
    val timestamp: Long
)

/** A detected recurring charge / subscription inferred from interval regularity. */
data class RecurringCharge(
    val merchantName: String,
    val category: String,
    val averageAmount: Double,
    /** Median number of days between occurrences. */
    val intervalDays: Double,
    val cadence: Cadence,
    val occurrences: Int,
    val lastChargeTimestamp: Long,
    val estimatedMonthlyCost: Double,
    /** 0..1 — how regular the intervals are (1 = perfectly periodic). */
    val regularity: Double
)

enum class Cadence(val label: String) {
    WEEKLY("weekly"),
    BIWEEKLY("every 2 weeks"),
    MONTHLY("monthly"),
    QUARTERLY("quarterly"),
    YEARLY("yearly"),
    IRREGULAR("irregular")
}

/** Direction and magnitude of a category's spend trend over recent months. */
data class CategoryTrend(
    val category: String,
    val direction: TrendDirection,
    val recentMonthlyAverage: Double,
    val previousMonthlyAverage: Double,
    /** Percent change of the most recent month vs the prior baseline. */
    val changePercent: Double
)

enum class TrendDirection { RISING, FALLING, STABLE }
