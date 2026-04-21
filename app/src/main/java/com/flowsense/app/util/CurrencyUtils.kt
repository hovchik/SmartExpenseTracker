package com.flowsense.app.util

import com.flowsense.app.data.model.currencyInfoFor
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import kotlin.math.abs

object CurrencyUtils {

    /** Returns a NumberFormat configured for the given ISO-4217 currency code. */
    private fun formatterFor(currencyCode: String): NumberFormat {
        return try {
            val currency = Currency.getInstance(currencyCode)
            // Use a locale that natively uses this currency where possible
            val locale = when (currencyCode) {
                "USD" -> Locale.US
                "EUR" -> Locale.GERMANY
                "GBP" -> Locale.UK
                "JPY" -> Locale.JAPAN
                "CNY" -> Locale.CHINA
                "INR" -> Locale("en", "IN")
                "AMD" -> Locale("hy", "AM")
                "RUB" -> Locale("ru", "RU")
                "TRY" -> Locale("tr", "TR")
                "KRW" -> Locale.KOREA
                else  -> Locale.US
            }
            val fmt = NumberFormat.getCurrencyInstance(locale)
            fmt.currency = currency
            fmt
        } catch (_: Exception) {
            NumberFormat.getCurrencyInstance(Locale.US)
        }
    }

    /** Number of decimal places for the currency (0 for JPY/KRW, 2 for most, 3 for BHD, etc.). */
    private fun fractionDigitsFor(currencyCode: String): Int {
        return try {
            Currency.getInstance(currencyCode).defaultFractionDigits.coerceAtLeast(0)
        } catch (_: Exception) {
            2
        }
    }

    private fun safeAmount(amount: Double): Double = if (amount.isFinite()) amount else 0.0

    /** Format an amount using the current app currency code. Falls back to USD. */
    fun format(amount: Double, currencyCode: String = "USD"): String {
        val value = safeAmount(amount)
        return try {
            formatterFor(currencyCode).format(value)
        } catch (_: Exception) {
            val info = currencyInfoFor(currencyCode)
            val digits = fractionDigitsFor(currencyCode)
            "${info.symbol}${String.format("%.${digits}f", value)}"
        }
    }

    /** Compact format: $1.2K, $3.4M, etc. */
    fun formatCompact(amount: Double, currencyCode: String = "USD"): String {
        val info = currencyInfoFor(currencyCode)
        val sym = info.symbol
        val value = safeAmount(amount)
        val abs = abs(value)
        val sign = if (value < 0) "-" else ""
        val digits = fractionDigitsFor(currencyCode)
        return when {
            abs >= 1_000_000 -> "${sign}${sym}${String.format("%.1fM", abs / 1_000_000)}"
            abs >= 1_000     -> "${sign}${sym}${String.format("%.1fK", abs / 1_000)}"
            else             -> "${sign}${sym}${String.format("%.${digits}f", abs)}"
        }
    }

    /** Format with a leading + for positive amounts. */
    fun formatWithSign(amount: Double, currencyCode: String = "USD"): String {
        val prefix = if (amount >= 0) "+" else ""
        return "$prefix${format(amount, currencyCode)}"
    }

    /** Returns just the symbol for a currency code. */
    fun symbolFor(currencyCode: String): String = currencyInfoFor(currencyCode).symbol
}
