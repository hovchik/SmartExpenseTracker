package com.smartexpense.tracker.ai.runtime

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Runtime adapter for MediaPipe LLM Inference API.
 * Uses direct imports of the MediaPipe tasks-genai library (no reflection).
 *
 * Supports .task, .bin, and .tflite model files (Qwen, Gemma, and compatible models).
 */
class MediaPipeLlmRuntimeAdapter(private val context: Context) : LocalModelRuntime {

    companion object {
        private const val TAG = "MediaPipeLlmRuntime"
        // Default token limit — overridden per-model by loadModel(maxTokens).
        private const val DEFAULT_MAX_TOKENS = 1280
    }

    @Volatile
    private var llmInference: LlmInference? = null

    @Volatile
    private var ready = false

    @Volatile
    private var currentModelPath: String? = null

    /** Active token limit — stored so recreateSession() uses the same value. */
    @Volatile
    private var activeMaxTokens: Int = DEFAULT_MAX_TOKENS

    /** Scaled input char limit based on activeMaxTokens.
     *  Formula: reserve ~20% of tokens for output, convert remaining to chars
     *  at ~1.2 chars/token (conservative for multilingual text). */
    private val maxInputChars: Int
        get() = ((activeMaxTokens * 0.80) * 1.2).toInt().coerceAtLeast(400)

    @Volatile
    var modelName: String = ""
        private set

    override suspend fun runPrompt(prompt: String): String = withContext(Dispatchers.IO) {
        var inference = llmInference ?: return@withContext ""
        try {
            // Truncate input to avoid exceeding the combined input+output token limit
            // which causes a native crash (SIGSEGV) in MediaPipe.
            // maxInputChars scales with the model's actual maxTokens setting.
            val limit = maxInputChars
            val safePrompt = if (prompt.length > limit) {
                Log.w(TAG, "Prompt truncated from ${prompt.length} to $limit chars (maxTokens=$activeMaxTokens)")
                prompt.take(limit)
            } else {
                prompt
            }
            val result = inference.generateResponse(safePrompt)
            result?.trim() ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "MediaPipe inference failed: ${e.message}")
            // After a TFLite error the session is corrupt ("Please create a
            // new Session and start over"). Recreate to prevent SIGSEGV on
            // subsequent calls.
            recreateSession()
            ""
        }
    }

    /**
     * Recreates the LlmInference session after a failed invoke.
     * This prevents use-after-error SIGSEGV crashes.
     */
    private fun recreateSession() {
        val path = currentModelPath ?: return
        try {
            Log.w(TAG, "Recreating MediaPipe session after error")
            llmInference?.close()
            llmInference = null

            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(path)
                .setMaxTokens(activeMaxTokens)
                .build()
            llmInference = LlmInference.createFromOptions(context, options)
            ready = true
            Log.d(TAG, "Session recreated successfully (maxTokens=$activeMaxTokens)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to recreate session: ${e.message}")
            llmInference = null
            ready = false
        }
    }

    override fun supportsStructuredJson(): Boolean = false

    override fun isReady(): Boolean = ready

    override suspend fun loadModel(modelPath: String, maxTokens: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            releaseModel()

            val file = File(modelPath)
            if (!file.exists()) {
                Log.e(TAG, "Model file not found: $modelPath")
                return@withContext false
            }

            activeMaxTokens = maxTokens
            Log.d(TAG, "Loading MediaPipe model: $modelPath (${file.length() / 1024 / 1024} MB, maxTokens=$maxTokens)")

            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(maxTokens)
                .build()

            llmInference = LlmInference.createFromOptions(context, options)
            currentModelPath = modelPath
            modelName = file.nameWithoutExtension.replaceFirstChar { it.uppercase() }
            ready = true
            Log.d(TAG, "MediaPipe model loaded: $modelName (maxTokens=$maxTokens)")
            true
        } catch (e: Exception) {
            val cause = e.cause ?: e
            Log.e(TAG, "Failed to load MediaPipe model: ${cause.message}", e)
            ready = false
            false
        }
    }

    override fun releaseModel() {
        try {
            llmInference?.close()
        } catch (_: Exception) {}
        llmInference = null
        ready = false
        currentModelPath = null
        modelName = ""
    }

    override fun runtimeName(): String = "MediaPipe LLM"
}
