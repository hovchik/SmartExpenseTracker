package com.flowsense.app.service.ai

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.flowsense.app.data.model.AiEnginePreference
import com.flowsense.app.data.model.currencyInfoFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * On-device AI service for FlowSense.
 *
 * Detects and uses the best available local AI backend on the device:
 *  1. MediaPipe LLM     – user-selected Gemma model via MediaPipe Inference API
 *  2. Samsung Galaxy AI  – detected on Samsung S24+ / One UI 6.1+
 *  3. Google AICore       – detected on Pixel 8+ with AICore system service
 *  4. Google Gemini app  – detected when Gemini is installed
 *  5. Enhanced local     – always-available rule-based analysis (fallback)
 *
 * The user can override auto-detection via [AiEnginePreference] in settings.
 */
class LocalAiService(private val appContext: Context) {

    // ── Detected backend ──────────────────────────────────────────────────

    enum class AiBackend {
        MEDIAPIPE_LLM,      // MediaPipe on-device LLM (Gemma model)
        OLLAMA,             // Ollama local LLM server
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

    /** MediaPipe on-device LLM service — lazily initialized. */
    val mediaPipeLlm = MediaPipeLlmService(appContext)

    /** Ollama local LLM server client. */
    val ollamaService = OllamaService()

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Detects the best available AI backend and caches the result.
     * If [preference] is not AUTO, forces that backend.
     * Always returns true — enhanced local analysis is always available.
     */
    suspend fun checkAvailability(
        preference: AiEnginePreference = AiEnginePreference.AUTO
    ): Boolean = withContext(Dispatchers.IO) {
        if (preference != AiEnginePreference.AUTO) {
            backend = when (preference) {
                AiEnginePreference.MEDIAPIPE_LLM -> AiBackend.MEDIAPIPE_LLM
                AiEnginePreference.OLLAMA -> AiBackend.OLLAMA
                AiEnginePreference.GEMINI_NANO -> {
                    when {
                        SAMSUNG_AI_PACKAGES.any { isPackageInstalled(it) } -> AiBackend.SAMSUNG_GALAXY_AI
                        isPackageInstalled("com.google.android.aicore") -> AiBackend.GOOGLE_AI_CORE
                        isPackageInstalled("com.google.android.apps.bard") -> AiBackend.GOOGLE_GEMINI_APP
                        else -> AiBackend.ENHANCED_LOCAL
                    }
                }
                AiEnginePreference.RULE_BASED -> AiBackend.ENHANCED_LOCAL
                AiEnginePreference.AUTO -> AiBackend.ENHANCED_LOCAL // unreachable
            }
            availabilityChecked = true
            Log.i(TAG, "AI backend forced to: $backend (preference: $preference)")
            return@withContext true
        }

        if (availabilityChecked) return@withContext true

        // Auto-detection: prefer Ollama/MediaPipe if ready, then platform-specific
        backend = when {
            ollamaService.isReady -> AiBackend.OLLAMA
            mediaPipeLlm.isReady -> AiBackend.MEDIAPIPE_LLM
            SAMSUNG_AI_PACKAGES.any { isPackageInstalled(it) } -> AiBackend.SAMSUNG_GALAXY_AI
            isPackageInstalled("com.google.android.aicore") -> AiBackend.GOOGLE_AI_CORE
            isPackageInstalled("com.google.android.apps.bard") -> AiBackend.GOOGLE_GEMINI_APP
            else -> AiBackend.ENHANCED_LOCAL
        }

        availabilityChecked = true
        Log.i(TAG, "AI backend detected: $backend")
        true
    }

    /**
     * Re-runs detection without using cached result.
     */
    suspend fun recheckAvailability(
        preference: AiEnginePreference = AiEnginePreference.AUTO
    ): Boolean {
        availabilityChecked = false
        return checkAvailability(preference)
    }

    /** Human-readable status for display in the Settings screen. */
    fun statusMessage(): String = when (backend) {
        AiBackend.OLLAMA -> ollamaService.statusMessage()
        AiBackend.MEDIAPIPE_LLM ->
            if (mediaPipeLlm.isReady) "MediaPipe LLM active \u2014 ${mediaPipeLlm.modelName}"
            else "MediaPipe LLM selected \u2014 no model loaded"
        AiBackend.SAMSUNG_GALAXY_AI ->
            "Samsung Galaxy AI detected \u2014 enhanced on-device analysis active"
        AiBackend.GOOGLE_AI_CORE ->
            "Google AICore detected \u2014 on-device analysis active"
        AiBackend.GOOGLE_GEMINI_APP ->
            "Google Gemini installed \u2014 AI-assisted analysis active"
        AiBackend.ENHANCED_LOCAL ->
            "Enhanced local analysis active (no on-device AI found)"
    }

    /**
     * Returns a human-readable suggestion for enabling a better AI when
     * the current backend is only the basic local engine.
     */
    fun alternativeSuggestion(): String? {
        if (backend != AiBackend.ENHANCED_LOCAL) return null
        val isSamsung = Build.MANUFACTURER.equals("Samsung", ignoreCase = true)
        return when {
            isSamsung ->
                "Try Ollama for on-device AI, or enable Samsung Galaxy AI in: Settings \u2192 Advanced features \u2192 Galaxy AI"
            else ->
                "Install Ollama on your device or local network for AI-powered insights. Select the Ollama engine above to configure."
        }
    }

    /**
     * Returns descriptions for each engine option for the settings selector.
     */
    fun engineDescriptions(): Map<AiEnginePreference, String> {
        val descriptions = mutableMapOf<AiEnginePreference, String>()
        descriptions[AiEnginePreference.AUTO] = "Automatically detect best available engine"
        descriptions[AiEnginePreference.RULE_BASED] = "Fast, no model needed \u2014 works everywhere"

        val ollamaStatus = when {
            ollamaService.isReady -> "Connected \u2014 ${ollamaService.modelName}"
            ollamaService.errorMessage != null -> ollamaService.errorMessage!!
            else -> "Configure server address and select a model"
        }
        descriptions[AiEnginePreference.OLLAMA] = ollamaStatus

        val geminiStatus = when {
            SAMSUNG_AI_PACKAGES.any { isPackageInstalled(it) } -> "Samsung Galaxy AI available"
            isPackageInstalled("com.google.android.aicore") -> "Google AICore available"
            isPackageInstalled("com.google.android.apps.bard") -> "Google Gemini available"
            else -> "Not detected on this device"
        }
        descriptions[AiEnginePreference.GEMINI_NANO] = geminiStatus

        val mediapipeStatus = when {
            mediaPipeLlm.isReady -> "Active \u2014 ${mediaPipeLlm.modelName}"
            mediaPipeLlm.isGalleryInstalled() -> "Gallery installed \u2014 select a model"
            else -> "Import a .task model file"
        }
        descriptions[AiEnginePreference.MEDIAPIPE_LLM] = mediapipeStatus

        return descriptions
    }

    /** Shared engine used for AI-powered categorisation when local AI is enabled. */
    private val aiEngine = AiExpenseEngine()

    /**
     * AI-powered categorisation. When MediaPipe LLM is the active backend
     * and a model is loaded, uses on-device LLM. Otherwise falls back to
     * the multi-pass rule engine.
     */
    suspend fun categorize(
        description: String,
        availableCategories: List<String>,
        isExpense: Boolean = true,
        userCategoryNames: List<String> = emptyList()
    ): String? = withContext(Dispatchers.Default) {
        try {
            if (backend == AiBackend.OLLAMA && ollamaService.isReady) {
                return@withContext ollamaService.categorize(
                    description, availableCategories, isExpense, userCategoryNames
                )
            }
            if (backend == AiBackend.MEDIAPIPE_LLM && mediaPipeLlm.isReady) {
                return@withContext mediaPipeLlm.categorize(
                    description, availableCategories, isExpense, userCategoryNames
                )
            }
            aiEngine.categorize(description, isExpense, userCategoryNames)
        } catch (e: Exception) {
            Log.w(TAG, "AI categorize failed: ${e.message}")
            null
        }
    }

    /**
     * Generates a concise, actionable financial insight.
     * When MediaPipe LLM is active, uses the LLM for richer insights.
     * Otherwise uses smart templates.
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
            // Try Ollama first, then MediaPipe LLM
            if (backend == AiBackend.OLLAMA && ollamaService.isReady) {
                val insight = ollamaService.generateInsight(
                    totalExpenses, totalIncome, topCategory,
                    topCategoryAmount, transactionCount, currencyCode
                )
                if (insight != null) return@withContext insight
            }
            if (backend == AiBackend.MEDIAPIPE_LLM && mediaPipeLlm.isReady) {
                val llmInsight = mediaPipeLlm.generateInsight(
                    totalExpenses, totalIncome, topCategory,
                    topCategoryAmount, transactionCount, currencyCode
                )
                if (llmInsight != null) return@withContext llmInsight
            }

            // Fall back to template-based insights
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
