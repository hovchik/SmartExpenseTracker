package com.flowsense.app.ai.provider

import android.content.Context
import com.flowsense.app.ai.modelmanager.LocalAiModel
import com.flowsense.app.ai.modelmanager.LocalModelManager
import com.flowsense.app.ai.modelmanager.RuntimeType
import com.flowsense.app.ai.runtime.LiteRtRuntimeAdapter
import com.flowsense.app.ai.runtime.LocalModelRuntime
import com.flowsense.app.ai.runtime.MediaPipeLlmRuntimeAdapter

/**
 * AI provider that runs user-installed or app-downloaded local models.
 * Uses pluggable runtime adapters (MediaPipe, LiteRT) for inference.
 *
 * The provider always stores the active model metadata, even if the runtime
 * fails to load. This ensures correct display names and allows lazy retry
 * of model loading during inference.
 */
class CustomLocalModelProvider(
    private val context: Context,
    private val modelManager: LocalModelManager
) : AiProvider {

    companion object {
        private const val TAG = "CustomLocalProvider"
    }

    private val mediaPipeRuntime = MediaPipeLlmRuntimeAdapter(context)
    private val liteRtRuntime = LiteRtRuntimeAdapter()
    private val promptAdapter = PromptAdapter()

    @Volatile
    private var activeRuntime: LocalModelRuntime? = null

    @Volatile
    private var activeModel: LocalAiModel? = null

    /** Set to true when load fails with a permanent error (e.g. incompatible format).
     *  Prevents endless retry loops during inference. Cleared on loadModel(). */
    @Volatile
    private var permanentLoadFailure: Boolean = false

    /**
     * Loads the currently active model (if any).
     * Always stores the model metadata so displayName() is correct.
     * Returns true if the runtime loaded successfully.
     */
    suspend fun loadActiveModel(): Boolean {
        val model = modelManager.getActiveModel() ?: return false
        return loadModel(model)
    }

    /**
     * Loads a specific model using the appropriate runtime.
     * The model metadata is always stored (even on runtime failure)
     * so isAvailable() and displayName() work correctly.
     */
    suspend fun loadModel(model: LocalAiModel): Boolean {
        if (model.localPath.isBlank()) return false

        // Always store the model metadata — the user explicitly selected it
        activeModel = model
        permanentLoadFailure = false

        val runtime = getRuntimeForModel(model)
        val success = runtime.loadModel(model.localPath, model.maxTokens)

        if (success) {
            activeRuntime = runtime
            android.util.Log.i(TAG, "Model loaded: ${model.displayName} via ${runtime.runtimeName()}")
        } else {
            // Store the runtime reference for lazy retry during inference
            activeRuntime = runtime
            // Check if this is a permanent failure (incompatible format, etc.)
            val error = (runtime as? com.flowsense.app.ai.runtime.MediaPipeLlmRuntimeAdapter)?.lastLoadError
            if (error != null && (error.contains("Incompatible") || error.contains("Unsupported"))) {
                permanentLoadFailure = true
                android.util.Log.e(TAG, "Permanent load failure for ${model.displayName}: $error")
            } else {
                android.util.Log.w(TAG, "Runtime load failed for ${model.displayName}, will retry on inference")
            }
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
        val model = activeModel ?: return AnalysisResult(
            text = "", success = false, providerName = displayName(), isLocal = true
        )

        // Don't retry if the model has a permanent incompatibility (e.g. unsupported signature)
        if (permanentLoadFailure) {
            return AnalysisResult(
                text = "", success = false, providerName = displayName(), isLocal = true
            )
        }

        var runtime = activeRuntime

        // Lazy retry: if the runtime isn't ready, attempt to reload the model
        if (runtime == null || !runtime.isReady()) {
            android.util.Log.i(TAG, "Runtime not ready, retrying load for ${model.displayName}")
            runtime = getRuntimeForModel(model)
            val reloaded = runtime.loadModel(model.localPath, model.maxTokens)
            if (reloaded) {
                activeRuntime = runtime
            } else {
                // Check if this is a permanent failure
                val error = (runtime as? com.flowsense.app.ai.runtime.MediaPipeLlmRuntimeAdapter)?.lastLoadError
                if (error != null && (error.contains("Incompatible") || error.contains("Unsupported"))) {
                    permanentLoadFailure = true
                }
                return AnalysisResult(
                    text = "", success = false, providerName = displayName(), isLocal = true
                )
            }
        }

        val startTime = System.currentTimeMillis()
        // Local models have tight token budgets — condense the prompt first.
        // Scale maxChars with the model's maxTokens (reserve ~20% for output,
        // use ~1.2 chars/token for multilingual safety).
        // Reasoning models (e.g. DeepSeek R1) get a task-oriented format
        // without role-play/system instructions, which improves output quality.
        val maxInputChars = ((model.maxTokens * 0.80) * 1.2).toInt().coerceAtLeast(400)
        val condensed = promptAdapter.condenseForLocalModel(
            input.prompt,
            maxChars = maxInputChars,
            isReasoningModel = model.isReasoningModel
        )
        val adaptedPrompt = promptAdapter.adaptPrompt(
            condensed,
            supportsStructuredJson = runtime.supportsStructuredJson()
        )

        val rawResponse = runtime.runPrompt(adaptedPrompt)
        val latency = System.currentTimeMillis() - startTime

        // DeepSeek R1 models emit <think>…</think> reasoning blocks — strip them
        val response = rawResponse
            .replace(Regex("<think>[\\s\\S]*?</think>"), "")
            .replace(Regex("</think>"), "") // Handle unclosed/partial tags
            .trim()

        return AnalysisResult(
            text = response,
            success = response.isNotBlank(),
            providerName = displayName(),
            latencyMs = latency,
            isLocal = true
        )
    }

    /**
     * Returns true when a model is selected and has a valid local path.
     * The runtime may not be ready yet — it will be lazy-loaded during inference.
     */
    override fun isAvailable(): Boolean {
        val model = activeModel ?: return false
        return model.localPath.isNotBlank()
    }

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
            model != null && model.localPath.isNotBlank() -> {
                val error = (runtime as? com.flowsense.app.ai.runtime.MediaPipeLlmRuntimeAdapter)?.lastLoadError
                if (error != null) "${model.displayName} — error: $error"
                else "${model.displayName} — ready (will load on first use)"
            }
            model != null -> "${model.displayName} — not loaded"
            else -> "No model installed"
        }
    }

    override fun isLocal(): Boolean = true

    /** Returns the active model's name for display. */
    fun activeModelName(): String = activeModel?.displayName ?: ""

    /** Returns the active runtime name. */
    fun activeRuntimeName(): String = activeRuntime?.runtimeName() ?: ""

    /** Returns the last model load error, or null if no error. */
    fun lastLoadError(): String? =
        (activeRuntime as? com.flowsense.app.ai.runtime.MediaPipeLlmRuntimeAdapter)?.lastLoadError

    private fun getRuntimeForModel(model: LocalAiModel): LocalModelRuntime {
        return when (model.runtimeType) {
            RuntimeType.MEDIAPIPE -> mediaPipeRuntime
            RuntimeType.LITE_RT -> liteRtRuntime
            RuntimeType.SYSTEM_AI -> mediaPipeRuntime // fallback
        }
    }
}
