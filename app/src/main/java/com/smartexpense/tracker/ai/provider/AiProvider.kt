package com.smartexpense.tracker.ai.provider

/**
 * Input data for AI analysis (categorization, insights, reports).
 */
data class AnalysisInput(
    val prompt: String,
    val totalExpenses: Double = 0.0,
    val totalIncome: Double = 0.0,
    val topCategory: String? = null,
    val topCategoryAmount: Double = 0.0,
    val transactionCount: Int = 0,
    val currencyCode: String = "USD",
    val availableCategories: List<String> = emptyList(),
    val isExpense: Boolean = true,
    val type: AnalysisType = AnalysisType.INSIGHT
)

enum class AnalysisType {
    CATEGORIZE,
    INSIGHT,
    REPORT
}

/**
 * Result from an AI analysis request.
 */
data class AnalysisResult(
    val text: String,
    val success: Boolean = true,
    val providerName: String = "",
    val latencyMs: Long = 0,
    val isLocal: Boolean = false
)

/**
 * Base interface for all AI providers (Cloud, System, Custom Local).
 */
interface AiProvider {
    /** Perform AI analysis based on the given input. */
    suspend fun generateAnalysis(input: AnalysisInput): AnalysisResult

    /** Whether this provider is currently available and ready to use. */
    fun isAvailable(): Boolean

    /** Human-readable name for display in UI. */
    fun displayName(): String

    /** Description of the provider for settings display. */
    fun description(): String

    /** Whether this provider runs entirely on-device (no network). */
    fun isLocal(): Boolean
}
