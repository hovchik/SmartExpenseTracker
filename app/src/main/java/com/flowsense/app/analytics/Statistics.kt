package com.flowsense.app.analytics

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Small, dependency-free statistics helpers used by [FinancialAnalyticsEngine].
 *
 * Everything here favours *robust* statistics (median / MAD, linear regression)
 * over naive mean/stddev so that a handful of unusual transactions does not skew
 * the financial analysis. All functions are pure and side-effect free, which keeps
 * the analytics engine fully unit-testable on the JVM.
 */
internal object Statistics {

    fun mean(values: List<Double>): Double =
        if (values.isEmpty()) 0.0 else values.sum() / values.size

    fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2.0 else sorted[mid]
    }

    fun stdDev(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val m = mean(values)
        val variance = values.sumOf { (it - m) * (it - m) } / (values.size - 1)
        return sqrt(variance)
    }

    /**
     * Median Absolute Deviation — a robust estimator of spread that resists outliers.
     * Scaled by 1.4826 so it is comparable to the standard deviation for normal data.
     */
    fun medianAbsoluteDeviation(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val med = median(values)
        val deviations = values.map { abs(it - med) }
        return median(deviations) * 1.4826
    }

    /**
     * Robust modified z-score of [value] within [values]. Uses median + MAD so a
     * single huge transaction does not inflate the baseline. Falls back to a
     * standard z-score when the MAD collapses to zero (e.g. many identical values).
     */
    fun robustZScore(value: Double, values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val med = median(values)
        val mad = medianAbsoluteDeviation(values)
        if (mad > 1e-9) return (value - med) / mad
        val sd = stdDev(values)
        return if (sd > 1e-9) (value - mean(values)) / sd else 0.0
    }

    /**
     * Coefficient of variation (stdDev / mean) — a unitless measure of volatility.
     * Returns 0 when there is no spend to measure against.
     */
    fun coefficientOfVariation(values: List<Double>): Double {
        val m = mean(values)
        if (abs(m) < 1e-9) return 0.0
        return stdDev(values) / abs(m)
    }

    /**
     * Slope of the ordinary-least-squares line fit to (index, value) pairs.
     * Used to detect whether a category's spend is trending up or down over time.
     * Returns 0 when fewer than two points are supplied.
     */
    fun linearSlope(values: List<Double>): Double {
        val n = values.size
        if (n < 2) return 0.0
        val meanX = (n - 1) / 2.0
        val meanY = mean(values)
        var num = 0.0
        var den = 0.0
        for (i in 0 until n) {
            val dx = i - meanX
            num += dx * (values[i] - meanY)
            den += dx * dx
        }
        return if (den < 1e-9) 0.0 else num / den
    }

    /**
     * Exponentially weighted moving average. More recent observations carry more
     * weight ([alpha] closer to 1 = faster reaction). Used for cash-flow forecasting
     * where recent months are more predictive than old ones.
     */
    fun ewma(values: List<Double>, alpha: Double = 0.5): Double {
        if (values.isEmpty()) return 0.0
        var acc = values.first()
        for (i in 1 until values.size) {
            acc = alpha * values[i] + (1 - alpha) * acc
        }
        return acc
    }

    /** Clamps [value] into the inclusive [[min], [max]] range. */
    fun clamp(value: Double, min: Double, max: Double): Double =
        when {
            value < min -> min
            value > max -> max
            else -> value
        }
}
