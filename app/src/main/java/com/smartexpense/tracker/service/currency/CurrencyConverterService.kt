package com.smartexpense.tracker.service.currency

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches live exchange rates from open.er-api.com (free tier, no API key required).
 * Rates are cached in memory for [CACHE_TTL_MS] milliseconds to avoid repeated network calls.
 *
 * Primary source : https://open.er-api.com/v6/latest/{base}
 * Fallback source: https://api.exchangerate-api.com/v4/latest/{base}
 *
 * Both return JSON shaped like:
 *   { "rates": { "EUR": 0.92, "AMD": 389.5, ... } }
 */
object CurrencyConverterService {

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
            fetchRates(baseCurrency)?.also { rates ->
                cache = RateCache(baseCurrency, rates, System.currentTimeMillis())
            }
        }
    }

    /** Invalidate the in-memory cache (e.g. when user changes base currency). */
    fun invalidateCache() { cache = null }

    private fun fetchRates(base: String): Map<String, Double>? {
        val sources = listOf(
            "https://open.er-api.com/v6/latest/$base",
            "https://api.exchangerate-api.com/v4/latest/$base"
        )
        for (urlStr in sources) {
            try {
                val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
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
                    // Both APIs expose rates under the "rates" key
                    val ratesObj = json.optJSONObject("rates") ?: continue
                    val map = mutableMapOf<String, Double>()
                    ratesObj.keys().forEach { key ->
                        map[key] = ratesObj.getDouble(key)
                    }
                    if (map.isNotEmpty()) return map
                } else {
                    conn.disconnect()
                }
            } catch (_: Exception) {
                // try next source
            }
        }
        return null
    }
}
