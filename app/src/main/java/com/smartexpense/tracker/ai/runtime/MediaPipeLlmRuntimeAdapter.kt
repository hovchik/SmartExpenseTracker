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
        // Most catalog models use ekv1280 (1280-token KV cache).
        // Setting maxTokens higher than the KV cache causes GATHER_ND
        // out-of-bounds errors in TFLite, leading to a native SIGSEGV.
        private const val MAX_TOKENS = 1280
        // Reserve ~256 tokens for output → ~1024 input tokens.
        // Multilingual text (Armenian, CJK, Cyrillic) tokenizes at ~1.5 chars/token
        // so we need a tighter char limit than pure English would require.
        // 1200 chars ≈ 800 tokens, leaving ~480 for output.
        private const val MAX_INPUT_CHARS = 1200
    }

    @Volatile
    private var llmInference: LlmInference? = null

    @Volatile
    private var ready = false

    @Volatile
    private var currentModelPath: String? = null

    @Volatile
    var modelName: String = ""
        private set

    override suspend fun runPrompt(prompt: String): String = withContext(Dispatchers.IO) {
        var inference = llmInference ?: return@withContext ""
        try {
            // Truncate input to avoid exceeding the combined input+output token limit
            // which causes a native crash (SIGSEGV) in MediaPipe
            val safePrompt = if (prompt.length > MAX_INPUT_CHARS) {
                Log.w(TAG, "Prompt truncated from ${prompt.length} to $MAX_INPUT_CHARS chars")
                prompt.take(MAX_INPUT_CHARS)
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
                .setMaxTokens(MAX_TOKENS)
                .build()
            llmInference = LlmInference.createFromOptions(context, options)
            Log.d(TAG, "Session recreated successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to recreate session: ${e.message}")
            llmInference = null
            ready = false
        }
    }

    override fun supportsStructuredJson(): Boolean = false

    override fun isReady(): Boolean = ready

    override suspend fun loadModel(modelPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            releaseModel()

            val file = File(modelPath)
            if (!file.exists()) {
                Log.e(TAG, "Model file not found: $modelPath")
                return@withContext false
            }

            Log.d(TAG, "Loading MediaPipe model: $modelPath (${file.length() / 1024 / 1024} MB)")

            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(MAX_TOKENS)
                .build()

            llmInference = LlmInference.createFromOptions(context, options)
            currentModelPath = modelPath
            modelName = file.nameWithoutExtension.replaceFirstChar { it.uppercase() }
            ready = true
            Log.d(TAG, "MediaPipe model loaded: $modelName")
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
