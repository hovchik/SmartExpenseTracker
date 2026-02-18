package com.smartexpense.tracker.service.ai

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.google.ai.edge.aicore.GenerativeModel
import com.google.ai.edge.aicore.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Wrapper around Google AI Edge (Gemini Nano / Android AICore) for on-device inference.
 *
 * Supported devices (Android 14+):
 *  - Google Pixel 8, 8 Pro, 8a, 9 series
 *  - Samsung Galaxy S24 series (ships AICore-compatible service)
 *  - Other AICore-enabled devices
 *
 * On unsupported devices every method returns null and the caller falls back
 * to the existing rule-based [AiExpenseEngine] logic. No crash, no noise.
 */
class LocalAiService(private val appContext: Context) {

    companion object {
        private const val TAG = "LocalAiService"

        /**
         * Known package names for the Android AICore / Gemini Nano system service.
         * Google ships "com.google.android.aicore" on Pixel devices.
         * Samsung Galaxy S24+ ships a compatible service under a different package.
         * We check all known names; if none is found we still attempt SDK init because
         * some OEMs install the service as a pre-loaded module with yet another name.
         */
        private val AI_CORE_PACKAGES = listOf(
            "com.google.android.aicore",                  // Google Pixel
            "com.samsung.android.ai.gemini.service",      // Samsung S24+ (Gemini Nano)
            "com.samsung.android.intelligenceservice",    // Samsung Intelligence Service
            "com.samsung.android.aiservices",             // Samsung AI Services
            "com.samsung.android.ai.core"                 // Samsung AI Core
        )
    }

    // ── Cached state ──────────────────────────────────────────────
    @Volatile private var model: GenerativeModel? = null
    @Volatile private var availabilityChecked = false
    @Volatile private var availabilityResult = false

    // ── Public API ────────────────────────────────────────────────

    /**
     * Returns a human-readable status string suitable for display in the Settings UI.
     *  - "Checking availability…"    (during check)
     *  - "Gemini Nano available"      (ready to use)
     *  - "On-device AI not available" (unavailable)
     */
    suspend fun statusMessage(): String {
        if (!availabilityChecked) return "Checking availability…"
        return if (availabilityResult) "Gemini Nano available on this device"
               else "On-device AI not available on this device"
    }

    /**
     * Checks whether Gemini Nano is available and initialises the model.
     * Result is cached – safe to call repeatedly.
     *
     * We skip the package-name pre-check as a hard gate because Samsung Galaxy S24+
     * ships a compatible AICore service under a different package name.  Instead we
     * let the SDK itself determine availability by attempting a lightweight probe.
     *
     * @return true if ready to use, false otherwise.
     */
    suspend fun checkAvailability(): Boolean = withContext(Dispatchers.IO) {
        if (availabilityChecked) return@withContext availabilityResult

        // Log which (if any) AICore package is present for diagnostics
        val foundPackage = AI_CORE_PACKAGES.firstOrNull { isPackageInstalled(it) }
        if (foundPackage != null) {
            Log.i(TAG, "AICore-compatible package found: $foundPackage")
        } else {
            Log.i(TAG, "No known AICore package found – attempting SDK init anyway (OEM service may be present)")
        }

        // Try to build the model and run a minimal probe.
        // The SDK will throw an appropriate exception on truly unsupported devices.
        try {
            val m = buildModel()
            m.generateContent("hi")
            model = m
            availabilityResult = true
            Log.i(TAG, "Gemini Nano initialised successfully")
        } catch (e: Exception) {
            Log.w(TAG, "Gemini Nano initialisation failed: ${e.javaClass.simpleName}: ${e.message}")
            availabilityResult = false
        }

        availabilityChecked = true
        availabilityResult
    }

    /**
     * Categorises a transaction description into one of [availableCategories] using
     * Gemini Nano. Returns null when the model is unavailable or inference fails;
     * the caller should then fall back to rule-based categorisation.
     */
    suspend fun categorize(
        description: String,
        availableCategories: List<String>
    ): String? {
        val m = model ?: return null
        if (availableCategories.isEmpty()) return null

        return withContext(Dispatchers.Default) {
            try {
                val catList = availableCategories.joinToString(", ")
                val prompt = """You are a personal finance assistant.
Categorize the following expense into exactly one of these categories: $catList

Expense description: "$description"

Reply with ONLY the category name from the list. No explanation."""

                val response = m.generateContent(prompt)
                val raw = response.text?.trim() ?: return@withContext null

                // Match against known categories (exact, then partial)
                availableCategories.firstOrNull { it.equals(raw, ignoreCase = true) }
                    ?: availableCategories.firstOrNull { raw.contains(it, ignoreCase = true) }
            } catch (e: Exception) {
                Log.w(TAG, "categorize() failed: ${e.message}")
                null
            }
        }
    }

    /**
     * Generates a concise financial insight for display in the Reports screen.
     * [currencyCode] is included in the prompt so the model uses the correct currency
     * symbol rather than defaulting to USD.
     * Returns null when unavailable.
     */
    suspend fun generateInsight(
        totalExpenses: Double,
        totalIncome: Double,
        topCategory: String?,
        topCategoryAmount: Double,
        transactionCount: Int,
        currencyCode: String = "USD"
    ): String? {
        val m = model ?: return null

        return withContext(Dispatchers.Default) {
            try {
                val prompt = buildString {
                    appendLine("Generate a brief 1–2 sentence financial insight based on:")
                    appendLine("• Currency: $currencyCode")
                    appendLine("• Expenses: ${"%.2f".format(totalExpenses)} $currencyCode")
                    appendLine("• Income: ${"%.2f".format(totalIncome)} $currencyCode")
                    if (topCategory != null) {
                        appendLine("• Highest spend category: $topCategory (${"%.2f".format(topCategoryAmount)} $currencyCode)")
                    }
                    appendLine("• Transactions: $transactionCount")
                    appendLine("Use the $currencyCode currency symbol. Be concise, specific, and actionable. Start directly with the insight.")
                }

                val response = m.generateContent(prompt)
                response.text?.trim()?.takeIf { it.isNotBlank() }
            } catch (e: Exception) {
                Log.w(TAG, "generateInsight() failed: ${e.message}")
                null
            }
        }
    }

    // ── Private helpers ───────────────────────────────────────────

    private fun isPackageInstalled(packageName: String): Boolean = try {
        appContext.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) { false }

    private fun buildModel() = GenerativeModel(
        generationConfig = generationConfig {
            context = appContext
            temperature = 0.2f
            topK = 16
            maxOutputTokens = 256
        }
    )
}
