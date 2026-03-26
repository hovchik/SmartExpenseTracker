package com.flowsense.app.ai.runtime

/**
 * Abstraction for on-device LLM inference runtimes.
 * Implementations wrap specific runtime libraries (MediaPipe, LiteRT, etc.)
 * so that real bindings can be plugged in later.
 */
interface LocalModelRuntime {
    /** Run a text prompt and return the generated response. */
    suspend fun runPrompt(prompt: String): String

    /** Whether this runtime supports structured JSON output. */
    fun supportsStructuredJson(): Boolean

    /** Whether a model is currently loaded and ready. */
    fun isReady(): Boolean

    /** Load a model from the given file path.
     *  @param maxTokens Maximum combined input+output tokens. Must not exceed
     *         the KV cache size baked into the model file. */
    suspend fun loadModel(modelPath: String, maxTokens: Int = 1280): Boolean

    /** Release the currently loaded model and free resources. */
    fun releaseModel()

    /** Human-readable name of this runtime. */
    fun runtimeName(): String
}
