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

    /** Format an amount using the current app currency code. Falls back to USD. */
    fun format(amount: Double, currencyCode: String = "USD"): String {
        return try {
            formatterFor(currencyCode).format(amount)
        } catch (_: Exception) {
            val info = currencyInfoFor(currencyCode)
            "${info.symbol}${String.format("%.2f", amount)}"
        }
    }

    /** Compact format: $1.2K, $3.4M, etc. */
    fun formatCompact(amount: Double, currencyCode: String = "USD"): String {
        val info = currencyInfoFor(currencyCode)
        val sym = info.symbol
        val abs = abs(amount)
        val sign = if (amount < 0) "-" else ""
        return when {
            abs >= 1_000_000 -> "${sign}${sym}${String.format("%.1fM", abs / 1_000_000)}"
            abs >= 1_000     -> "${sign}${sym}${String.format("%.1fK", abs / 1_000)}"
            else             -> "${sign}${sym}${String.format("%.2f", abs)}"
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
