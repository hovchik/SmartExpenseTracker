package com.smartexpense.tracker.util

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

object CurrencyUtils {
    private val formatter = NumberFormat.getCurrencyInstance(Locale.US)

    fun format(amount: Double): String = formatter.format(amount)

    fun formatCompact(amount: Double): String {
        val abs = abs(amount)
        val sign = if (amount < 0) "-" else ""
        return when {
            abs >= 1_000_000 -> "${sign}$${String.format("%.1fM", abs / 1_000_000)}"
            abs >= 1_000 -> "${sign}$${String.format("%.1fK", abs / 1_000)}"
            else -> "${sign}$${String.format("%.2f", abs)}"
        }
    }

    fun formatWithSign(amount: Double): String {
        val prefix = if (amount >= 0) "+" else ""
        return "$prefix${format(amount)}"
    }
}
