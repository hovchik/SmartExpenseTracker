package com.smartexpense.tracker.ai.provider

import android.content.Context
import com.smartexpense.tracker.ai.modelmanager.LocalAiModel
import com.smartexpense.tracker.ai.modelmanager.LocalModelManager
import com.smartexpense.tracker.ai.modelmanager.RuntimeType
import com.smartexpense.tracker.ai.runtime.LiteRtRuntimeAdapter
import com.smartexpense.tracker.ai.runtime.LocalModelRuntime
import com.smartexpense.tracker.ai.runtime.MediaPipeLlmRuntimeAdapter

/**
 * AI provider that runs user-installed or app-downloaded local models.
 * Uses pluggable runtime adapters (MediaPipe, LiteRT) for inference.
 */
class CustomLocalModelProvider(
    private val context: Context,
    private val modelManager: LocalModelManager
) : AiProvider {

    private val mediaPipeRuntime = MediaPipeLlmRuntimeAdapter(context)
    private val liteRtRuntime = LiteRtRuntimeAdapter()
    private val promptAdapter = PromptAdapter()

    @Volatile
    private var activeRuntime: LocalModelRuntime? = null

    @Volatile
    private var activeModel: LocalAiModel? = null

    /**
     * Loads the currently active model (if any).
     */
    suspend fun loadActiveModel(): Boolean {
        val model = modelManager.getActiveModel() ?: return false
        return loadModel(model)
    }

    /**
     * Loads a specific model using the appropriate runtime.
     */
    suspend fun loadModel(model: LocalAiModel): Boolean {
        if (model.localPath.isBlank()) return false

        val runtime = getRuntimeForModel(model)
        val success = runtime.loadModel(model.localPath)

        if (success) {
            activeRuntime = runtime
            activeModel = model
        }

        return success
    }

    /**
     * Releases the currently loaded model.
     */
    fun release() {
        activeRuntime?.releaseModel()
        activeRuntime = null
        activeModel = null
    }

    override suspend fun generateAnalysis(input: AnalysisInput): AnalysisResult {
        val runtime = activeRuntime
        if (runtime == null || !runtime.isReady()) {
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

    override fun isAvailable(): Boolean = activeRuntime?.isReady() == true

    override fun displayName(): String {
        val model = activeModel
        return if (model != null) "Local Model (${model.displayName})" else "Local Model"
    }

    override fun description(): String {
        val model = activeModel
        val runtime = activeRuntime
        return when {
            model != null && runtime?.isReady() == true ->
                "${model.displayName} via ${runtime.runtimeName()}"
            model != null -> "${model.displayName} — not loaded"
            else -> "No model installed"
        }
    }

    override fun isLocal(): Boolean = true

    /** Returns the active model's name for display. */
    fun activeModelName(): String = activeModel?.displayName ?: ""

    /** Returns the active runtime name. */
    fun activeRuntimeName(): String = activeRuntime?.runtimeName() ?: ""

    private fun getRuntimeForModel(model: LocalAiModel): LocalModelRuntime {
        return when (model.runtimeType) {
            RuntimeType.MEDIAPIPE -> mediaPipeRuntime
            RuntimeType.LITE_RT -> liteRtRuntime
            RuntimeType.SYSTEM_AI -> mediaPipeRuntime // fallback
        }
    }
}
