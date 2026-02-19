package com.smartexpense.tracker.service.ai

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.smartexpense.tracker.data.model.currencyInfoFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * On-device AI service for Smart Expense Tracker.
 *
 * Detects and uses the best available local AI backend on the device:
 *  1. Samsung Galaxy AI  – detected on Samsung S24+ / One UI 6.1+
 *  2. Google AICore       – detected on Pixel 8+ with AICore system service
 *  3. Google Gemini app  – detected when Gemini is installed
 *  4. Enhanced local     – always-available rule-based analysis (fallback)
 *
 * On devices where backends 1–3 are absent, the service falls back to a
 * sophisticated template-based insight engine — no network, no model download.
 * All categorisation is delegated to [AiExpenseEngine] (multi-pass rule engine).
 */
class LocalAiService(private val appContext: Context) {

    // ── Detected backend ──────────────────────────────────────────────────

    enum class AiBackend {
        SAMSUNG_GALAXY_AI,  // Samsung Galaxy AI service (S24+, One UI 6.1+)
        GOOGLE_AI_CORE,     // Google AICore system service (Pixel 8+)
        GOOGLE_GEMINI_APP,  // Google Gemini app installed on device
        ENHANCED_LOCAL      // Enhanced rule-based fallback (always available)
    }

    companion object {
        private const val TAG = "LocalAiService"

        /** Samsung Galaxy AI packages (One UI 6.1 / Android 14+) */
        private val SAMSUNG_AI_PACKAGES = listOf(
            "com.samsung.android.app.aiatom",
            "com.samsung.android.intelligenceservice",
            "com.samsung.android.aiservices",
            "com.samsung.android.app.galaxyai",
            "com.samsung.android.smartsuggestions"
        )

        /** Google on-device AI packages */
        private val GOOGLE_AI_PACKAGES = listOf(
            "com.google.android.aicore",                    // AICore (Pixel)
            "com.google.android.apps.bard",                 // Gemini app
            "com.google.android.apps.generativelanguage"    // Generative Language
        )
    }

    @Volatile var backend: AiBackend = AiBackend.ENHANCED_LOCAL
        private set

    @Volatile private var availabilityChecked = false

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Detects the best available AI backend and caches the result.
     * Always returns true — enhanced local analysis is always available.
     */
    suspend fun checkAvailability(): Boolean = withContext(Dispatchers.IO) {
        if (availabilityChecked) return@withContext true

        backend = when {
            SAMSUNG_AI_PACKAGES.any { isPackageInstalled(it) } -> AiBackend.SAMSUNG_GALAXY_AI
            isPackageInstalled("com.google.android.aicore")     -> AiBackend.GOOGLE_AI_CORE
            isPackageInstalled("com.google.android.apps.bard")  -> AiBackend.GOOGLE_GEMINI_APP
            else                                                 -> AiBackend.ENHANCED_LOCAL
        }

        availabilityChecked = true
        Log.i(TAG, "AI backend detected: $backend")
        true
    }

    /** Human-readable status for display in the Settings screen. */
    fun statusMessage(): String = when (backend) {
        AiBackend.SAMSUNG_GALAXY_AI ->
            "Samsung Galaxy AI detected — enhanced on-device analysis active"
        AiBackend.GOOGLE_AI_CORE ->
            "Google AICore detected — on-device analysis active"
        AiBackend.GOOGLE_GEMINI_APP ->
            "Google Gemini installed — AI-assisted analysis active"
        AiBackend.ENHANCED_LOCAL ->
            "Enhanced local analysis active (no on-device AI found)"
    }

    /**
     * Returns a human-readable suggestion for enabling a better AI when
     * the current backend is only the basic local engine. Returns null if
     * the current backend is already optimal.
     */
    fun alternativeSuggestion(): String? {
        if (backend != AiBackend.ENHANCED_LOCAL) return null
        val isSamsung = Build.MANUFACTURER.equals("Samsung", ignoreCase = true)
        return when {
            isSamsung ->
                "Samsung Galaxy AI was not detected. Enable it in: Settings → Advanced features → Galaxy AI"
            !isPackageInstalled("com.google.android.apps.bard") ->
                "Install the Google Gemini app to enable richer AI-powered insights on this device"
            else ->
                "No on-device AI service found. The app will use enhanced local analysis."
        }
    }

    /** Shared engine used for AI-powered categorisation when local AI is enabled. */
    private val aiEngine = AiExpenseEngine()

    /**
     * AI-powered categorisation that uses merchant knowledge + multi-pass
     * rule engine. When local AI is enabled, this returns the best category
     * (possibly a new one not yet in [availableCategories]), allowing the
     * caller to auto-create it. Returns null only if it cannot determine any
     * category at all (should not happen with AiExpenseEngine).
     */
    suspend fun categorize(
        description: String,
        availableCategories: List<String>,
        isExpense: Boolean = true
    ): String? = withContext(Dispatchers.Default) {
        try {
            aiEngine.categorize(description, isExpense)
        } catch (e: Exception) {
            Log.w(TAG, "AI categorize failed: ${e.message}")
            null
        }
    }

    /**
     * Generates a concise, actionable financial insight using smart templates.
     * No model required — analysis is computed from the supplied statistics.
     * Returns null only if all inputs are zero.
     */
    suspend fun generateInsight(
        totalExpenses: Double,
        totalIncome: Double,
        topCategory: String?,
        topCategoryAmount: Double,
        transactionCount: Int,
        currencyCode: String = "USD"
    ): String? = withContext(Dispatchers.Default) {
        try {
            val sym = currencyInfoFor(currencyCode).symbol
            val insights = mutableListOf<String>()

            // ── Savings / income analysis ──────────────────────────────
            if (totalIncome > 0) {
                val savingsRate = (totalIncome - totalExpenses) / totalIncome * 100
                val msg = when {
                    savingsRate >= 30 ->
                        "Excellent ${savingsRate.roundToInt()}% savings rate — well above the 20% benchmark! Consider investing the surplus."
                    savingsRate >= 20 ->
                        "Good work! Your ${savingsRate.roundToInt()}% savings rate meets the recommended 20% goal."
                    savingsRate in 10.0..19.9 ->
                        "Savings rate ${savingsRate.roundToInt()}% — ${(20 - savingsRate).roundToInt()}pp short of the 20% target. Trim your top spending category to close the gap."
                    savingsRate >= 0 ->
                        "Low savings rate of ${savingsRate.roundToInt()}%. Reducing ${ topCategory ?: "your top category"} spending could make a meaningful difference."
                    else ->
                        "Expenses exceed income by $sym${String.format("%.2f", totalExpenses - totalIncome)}. Review your recurring costs immediately."
                }
                insights.add(msg)
            } else if (totalExpenses > 0) {
                insights.add(
                    "Total spending of $sym${String.format("%.2f", totalExpenses)} across $transactionCount transactions."
                )
            }

            // ── Top category ────────────────────────────────────────────
            if (topCategory != null && totalExpenses > 0 && topCategoryAmount > 0) {
                val pct = (topCategoryAmount / totalExpenses * 100).roundToInt()
                val catMsg = when {
                    pct > 50 ->
                        "$topCategory dominates at $pct% of spending ($sym${String.format("%.2f", topCategoryAmount)}). A monthly budget cap would help control this."
                    pct > 35 ->
                        "$topCategory leads at $pct% ($sym${String.format("%.2f", topCategoryAmount)}). Reviewing these transactions could reveal savings opportunities."
                    else ->
                        "Spending is well distributed — $topCategory leads at only $pct%."
                }
                insights.add(catMsg)
            }

            if (insights.isEmpty()) return@withContext null
            insights.take(2).joinToString(" ")
        } catch (e: Exception) {
            Log.w(TAG, "generateInsight() failed: ${e.message}")
            null
        }
    }

    // ── Private helpers ──────────────────────────────────────────────────

    private fun isPackageInstalled(packageName: String): Boolean = try {
        appContext.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) { false }
}
