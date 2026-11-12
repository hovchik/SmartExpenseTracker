package com.flowsense.app.util

import com.flowsense.app.data.model.currencyInfoFor
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import kotlin.math.abs

object CurrencyUtils {

    /**
     * NumberFormat is expensive to build and not thread-safe, but [format] runs for
     * every row of every list. Cache one formatter per currency code per thread,
     * and drop the cache if the default locale changes (per-app language switch).
     */
    private val formatterCache = ThreadLocal.withInitial { HashMap<String, NumberFormat>() }
    @Volatile private var cachedForLocale: Locale = Locale.getDefault()

    private fun formatterFor(currencyCode: String): NumberFormat {
        val defaultLocale = Locale.getDefault()
        if (defaultLocale != cachedForLocale) {
            cachedForLocale = defaultLocale
            formatterCache.remove()
        }
        return formatterCache.get()!!.getOrPut(currencyCode) { createFormatter(currencyCode) }
    }

    private fun createFormatter(currencyCode: String): NumberFormat {
        return try {
            val currency = Currency.getInstance(currencyCode)
            // Use a locale that natively uses this currency where possible
            val locale = when (currencyCode) {
                "USD" -> Locale.US
                "EUR" -> Locale.GERMANY
                "GBP" -> Locale.UK
                "JPY" -> Locale.JAPAN
                "CNY" -> Locale.CHINA
                "INR" -> Locale.forLanguageTag("en-IN")
                "AMD" -> Locale.forLanguageTag("hy-AM")
                "RUB" -> Locale.forLanguageTag("ru-RU")
                "TRY" -> Locale.forLanguageTag("tr-TR")
                "KRW" -> Locale.KOREA
                "CAD" -> Locale.CANADA
                "AUD" -> Locale.forLanguageTag("en-AU")
                "CHF" -> Locale.forLanguageTag("de-CH")
                "BRL" -> Locale.forLanguageTag("pt-BR")
                "MXN" -> Locale.forLanguageTag("es-MX")
                "AED" -> Locale.forLanguageTag("en-AE")
                "GEL" -> Locale.forLanguageTag("ka-GE")
                "UAH" -> Locale.forLanguageTag("uk-UA")
                "KZT" -> Locale.forLanguageTag("kk-KZ")
                "PLN" -> Locale.forLanguageTag("pl-PL")
                else  -> Locale.US
            }
            val fmt = NumberFormat.getCurrencyInstance(locale)
            fmt.currency = currency
            // Some locales (e.g. hy-AM) default to 0 fraction digits regardless of the
            // currency; pin the digits to what the currency actually uses.
            val digits = currency.defaultFractionDigits.coerceAtLeast(0)
            fmt.minimumFractionDigits = digits
            fmt.maximumFractionDigits = digits
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
            "${info.symbol}${String.format(Locale.US, "%.${digits}f", value)}"
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
        // Thresholds are just below the boundary so values that round up (e.g. 999,960)
        // print as "1.0M" rather than "1000.0K". Locale.US keeps the decimal point
        // and Latin digits stable regardless of device language.
        return when {
            abs >= 999_950_000 -> "${sign}${sym}${String.format(Locale.US, "%.1fB", abs / 1_000_000_000)}"
            abs >= 999_950     -> "${sign}${sym}${String.format(Locale.US, "%.1fM", abs / 1_000_000)}"
            abs >= 999.95      -> "${sign}${sym}${String.format(Locale.US, "%.1fK", abs / 1_000)}"
            else               -> "${sign}${sym}${String.format(Locale.US, "%.${digits}f", abs)}"
        }
    }

    /** Format with a leading + for positive amounts. */
    fun formatWithSign(amount: Double, currencyCode: String = "USD"): String {
        // NaN/Infinity render as 0 in format(); don't prefix "+" for them either.
        val prefix = if (amount.isFinite() && amount > 0) "+" else ""
        return "$prefix${format(amount, currencyCode)}"
    }

    /** Returns just the symbol for a currency code. */
    fun symbolFor(currencyCode: String): String = currencyInfoFor(currencyCode).symbol
}
