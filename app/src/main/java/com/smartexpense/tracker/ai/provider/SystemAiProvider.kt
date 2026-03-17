package com.smartexpense.tracker.ai.provider

import android.content.Context
import com.smartexpense.tracker.ai.runtime.SystemAiRuntimeAdapter

/**
 * AI provider that uses official Android system AI runtimes (AICore, ML Kit GenAI).
 * Only uses officially supported, documented APIs — no proprietary OEM access.
 */
class SystemAiProvider(context: Context) : AiProvider {

    private val runtime = SystemAiRuntimeAdapter(context)
    private val promptAdapter = PromptAdapter()

    @Volatile
    private var initialized = false

    suspend fun initialize(): Boolean {
        initialized = runtime.loadModel("system")
        return initialized
    }

    override suspend fun generateAnalysis(input: AnalysisInput): AnalysisResult {
        if (!isAvailable()) {
            return AnalysisResult(
                text = "",
                success = false,
                providerName = displayName(),
                isLocal = true
            )
        }

        val startTime = System.currentTimeMillis()
        val adaptedPrompt = promptAdapter.adaptPrompt(
            input.prompt,
            supportsStructuredJson = runtime.supportsStructuredJson()
        )

        val response = runtime.runPrompt(adaptedPrompt)
        val latency = System.currentTimeMillis() - startTime

        return AnalysisResult(
            text = response,
            success = response.isNotBlank(),
            providerName = displayName(),
            latencyMs = latency,
            isLocal = true
        )
    }

    override fun isAvailable(): Boolean = runtime.isReady()

    override fun displayName(): String = "System AI"

    override fun description(): String = when {
        runtime.isReady() -> "Android system AI active — ${runtime.runtimeName()}"
        else -> "System AI not available on this device"
    }

    override fun isLocal(): Boolean = true
}
