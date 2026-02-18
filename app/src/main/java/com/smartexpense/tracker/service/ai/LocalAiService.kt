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
 *  - Samsung Galaxy S24 series
 *  - Other AICore-enabled devices
 *
 * On unsupported devices every method returns null and the caller falls back
 * to the existing rule-based [AiExpenseEngine] logic. No crash, no noise.
 */
class LocalAiService(private val appContext: Context) {

    companion object {
        private const val TAG = "LocalAiService"

        /** Package name of the Android AICore system service (ships with Pixel / Samsung). */
        private const val AI_CORE_PACKAGE = "com.google.android.aicore"
    }

    // ── Cached state ──────────────────────────────────────────────
    @Volatile private var model: GenerativeModel? = null
    @Volatile private var availabilityChecked = false
    @Volatile private var availabilityResult = false

    // ── Public API ────────────────────────────────────────────────

    /**
     * Returns a human-readable status string suitable for display in the Settings UI.
     *  - "Checking availability…"  (during check)
     *  - "Gemini Nano available"    (ready to use)
     *  - "Not supported on this device"  (unavailable)
     */
    suspend fun statusMessage(): String {
        if (!availabilityChecked) return "Checking availability…"
        return if (availabilityResult) "Gemini Nano available on this device"
               else "Not supported on this device (requires Pixel 8+ / Galaxy S24+)"
    }

    /**
     * Checks whether Gemini Nano is available and initialises the model.
     * Result is cached – safe to call repeatedly.
     *
     * @return true if ready to use, false otherwise.
     */
    suspend fun checkAvailability(): Boolean = withContext(Dispatchers.IO) {
        if (availabilityChecked) return@withContext availabilityResult

        // Fast pre-check: is the AICore system package installed?
        if (!isAiCoreInstalled()) {
            Log.i(TAG, "AICore package not found – Gemini Nano unavailable")
            availabilityChecked = true
            availabilityResult = false
            return@withContext false
        }

        // Try to build the model and run a minimal probe
        try {
            val m = buildModel()
            // Lightweight probe to confirm the model actually responds
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
     * Returns null when unavailable.
     */
    suspend fun generateInsight(
        totalExpenses: Double,
        totalIncome: Double,
        topCategory: String?,
        topCategoryAmount: Double,
        transactionCount: Int
    ): String? {
        val m = model ?: return null

        return withContext(Dispatchers.Default) {
            try {
                val prompt = buildString {
                    appendLine("Generate a brief 1–2 sentence financial insight based on:")
                    appendLine("• Expenses: ${"%.2f".format(totalExpenses)}")
                    appendLine("• Income: ${"%.2f".format(totalIncome)}")
                    if (topCategory != null) {
                        appendLine("• Highest spend category: $topCategory (${"%.2f".format(topCategoryAmount)})")
                    }
                    appendLine("• Transactions: $transactionCount")
                    appendLine("Be concise, specific, and actionable. Start directly with the insight.")
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

    private fun isAiCoreInstalled(): Boolean = try {
        appContext.packageManager.getPackageInfo(AI_CORE_PACKAGE, 0)
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
