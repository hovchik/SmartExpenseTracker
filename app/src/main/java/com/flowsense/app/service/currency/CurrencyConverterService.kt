package com.flowsense.app.service.currency

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches live exchange rates from multiple sources.
 *
 * Supported sources:
 *   • Open API  – open.er-api.com / exchangerate-api.com (global, free, no key)
 *   • rate.am   – Armenian bank average exchange rates (AMD-centric)
 *
 * Rates are cached in memory for [CACHE_TTL_MS] milliseconds.
 */
object CurrencyConverterService {

    private const val TAG = "CurrencyConverter"
    private const val CACHE_TTL_MS = 60 * 60 * 1000L  // 1 hour

    data class RateCache(
        val base: String,
        val rates: Map<String, Double>,
        val fetchedAt: Long
    )

    @Volatile
    private var cache: RateCache? = null

    /**
     * Convert [amount] from [fromCode] to [toCode].
     * Returns null if rates cannot be fetched.
     */
    suspend fun convert(amount: Double, fromCode: String, toCode: String): Double? {
        if (fromCode == toCode) return amount
        val rates = getRates(fromCode) ?: return null
        val rate = rates[toCode] ?: return null
        return amount * rate
    }

    /**
     * Fetches rates relative to [baseCurrency], using in-memory cache.
     * Returns a map of { "EUR" -> 0.92, "AMD" -> 389.5, … }.
     */
    suspend fun getRates(baseCurrency: String = "USD"): Map<String, Double>? {
        val cached = cache
        if (cached != null &&
            cached.base == baseCurrency &&
            System.currentTimeMillis() - cached.fetchedAt < CACHE_TTL_MS
        ) {
            return cached.rates
        }

        return withContext(Dispatchers.IO) {
            fetchRatesFromApis(baseCurrency)?.also { rates ->
                cache = RateCache(baseCurrency, rates, System.currentTimeMillis())
            }
        }
    }

    /**
     * Fetches rates from rate.am (Armenian bank rates).
     * Returns USD-based rates like the API sources, or null on failure.
     */
    suspend fun getRatesFromRateAm(): Map<String, Double>? {
        val cached = cache
        if (cached != null &&
            cached.base == "RATE_AM" &&
            System.currentTimeMillis() - cached.fetchedAt < CACHE_TTL_MS
        ) {
            return cached.rates
        }

        return withContext(Dispatchers.IO) {
            fetchRatesFromRateAm()?.also { rates ->
                cache = RateCache("RATE_AM", rates, System.currentTimeMillis())
            }
        }
    }

    /** Invalidate the in-memory cache (e.g. when user changes rate source). */
    fun invalidateCache() { cache = null }

    // ── Open API sources ──────────────────────────────────────────────

    private fun fetchRatesFromApis(base: String): Map<String, Double>? {
        val sources = listOf(
            "https://open.er-api.com/v6/latest/$base",
            "https://api.exchangerate-api.com/v4/latest/$base"
        )
        for (urlStr in sources) {
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8_000
                    readTimeout = 8_000
                    setRequestProperty("Accept", "application/json")
                }
                val code = conn.responseCode
                if (code == HttpURLConnection.HTTP_OK) {
                    val body = conn.inputStream.bufferedReader().readText()
                    conn.disconnect()
                    val json = JSONObject(body)
                    val ratesObj = json.optJSONObject("rates") ?: continue
                    val map = mutableMapOf<String, Double>()
                    ratesObj.keys().forEach { key ->
                        val rate = ratesObj.optDouble(key, Double.NaN)
                        if (rate.isFinite()) {
                            map[key] = rate
                        }
                    }
                    if (map.isNotEmpty()) return map
                }
            } catch (_: Exception) {
                // try next source
            } finally {
                conn?.disconnect()
            }
        }
        return null
    }

    // ── rate.am source ────────────────────────────────────────────────

    /** Currency codes on rate.am mapped to standard ISO codes. */
    private val RATE_AM_CURRENCIES = mapOf(
        "USD" to "USD", "EUR" to "EUR", "RUR" to "RUB",
        "GBP" to "GBP", "CHF" to "CHF", "CNY" to "CNY",
        "AED" to "AED", "GEL" to "GEL", "JPY" to "JPY",
        "CAD" to "CAD", "AUD" to "AUD", "SEK" to "SEK",
        "KZT" to "KZT"
    )

    /**
     * Fetches exchange rates from rate.am by parsing the SSR HTML.
     * Returns USD-based rates (same format as the API sources).
     */
    private fun fetchRatesFromRateAm(): Map<String, Double>? {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL("https://www.rate.am/").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) FlowSense")
                setRequestProperty("Accept", "text/html,application/xhtml+xml")
                setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                return null
            }
            val html = conn.inputStream.bufferedReader().readText()
            return parseRateAmHtml(html)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch rate.am: ${e.message}")
            return null
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Parses rate.am HTML to extract average bank exchange rates.
     * rate.am embeds rate data in Next.js SSR format.
     *
     * Rate data appears as patterns like:
     *   "USD":{"CASH":{"buy":"374","sell":"379"}, ...}
     *
     * We extract buy/sell values, compute midpoints, average across banks,
     * and convert to USD-based rates.
     */
    private fun parseRateAmHtml(html: String): Map<String, Double>? {
        val rateCollector = mutableMapOf<String, MutableList<Double>>()

        val buySellPattern = Regex(""""buy"\s*:\s*"(\d+\.?\d*)"\s*,\s*"sell"\s*:\s*"(\d+\.?\d*)"""")

        for (rateAmCode in RATE_AM_CURRENCIES.keys) {
            // Find all occurrences of currency code as a JSON key
            val codePattern = Regex(""""$rateAmCode"\s*:\s*\{""")
            for (codeMatch in codePattern.findAll(html)) {
                // Look ahead up to 500 characters for the first buy/sell pair
                val searchStart = codeMatch.range.last
                val searchEnd = minOf(searchStart + 500, html.length)
                val window = html.substring(searchStart, searchEnd)
                val rateMatch = buySellPattern.find(window) ?: continue
                val buy = rateMatch.groupValues[1].toDoubleOrNull() ?: continue
                val sell = rateMatch.groupValues[2].toDoubleOrNull() ?: continue
                if (buy > 0 && sell > 0) {
                    rateCollector.getOrPut(rateAmCode) { mutableListOf() }
                        .add((buy + sell) / 2.0)
                }
            }
        }

        // Fallback: look for broader patterns if inline extraction failed
        if (rateCollector.isEmpty()) {
            // Try to find patterns where currency codes appear near rate data
            // with possible pointer references (Next.js RSC format)
            for (rateAmCode in RATE_AM_CURRENCIES.keys) {
                val broadPattern = Regex(
                    """"$rateAmCode"[^"]*?"buy"\s*:\s*"(\d+\.?\d*)"[^"]*?"sell"\s*:\s*"(\d+\.?\d*)""""
                )
                for (match in broadPattern.findAll(html)) {
                    val buy = match.groupValues[1].toDoubleOrNull() ?: continue
                    val sell = match.groupValues[2].toDoubleOrNull() ?: continue
                    if (buy > 0 && sell > 0) {
                        rateCollector.getOrPut(rateAmCode) { mutableListOf() }
                            .add((buy + sell) / 2.0)
                    }
                }
            }
        }

        // We need at least USD to compute cross-rates
        if (!rateCollector.containsKey("USD")) {
            Log.w(TAG, "rate.am: could not extract USD rate")
            return null
        }

        // rateCollector values are "AMD per 1 unit of currency" midpoints
        val amdPerUsd = rateCollector["USD"]!!.average()

        // Build USD-based rate map (same format as API sources)
        val result = mutableMapOf<String, Double>()
        result["AMD"] = amdPerUsd  // 1 USD = X AMD

        for ((rateAmCode, appCode) in RATE_AM_CURRENCIES) {
            if (rateAmCode == "USD") continue
            val amdPerUnit = rateCollector[rateAmCode]?.average() ?: continue
            if (amdPerUnit > 0) {
                // 1 USD = (amdPerUsd / amdPerUnit) units of this currency
                result[appCode] = amdPerUsd / amdPerUnit
            }
        }

        Log.d(TAG, "rate.am: parsed ${result.size} currencies (AMD/$amdPerUsd)")
        return if (result.size >= 2) result else null
    }
}
