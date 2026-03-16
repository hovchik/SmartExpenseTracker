package com.smartexpense.tracker.ai.runtime

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Runtime adapter for MediaPipe LLM Inference API.
 * This wraps the existing MediaPipe GenAI tasks library to conform to the
 * [LocalModelRuntime] interface.
 *
 * Supports .task, .bin, and .tflite model files (Gemma and compatible models).
 */
class MediaPipeLlmRuntimeAdapter(private val context: Context) : LocalModelRuntime {

    companion object {
        private const val TAG = "MediaPipeLlmRuntime"
        private val MODEL_EXTENSIONS = listOf(".task", ".bin", ".tflite")
    }

    @Volatile
    private var llmInference: Any? = null

    @Volatile
    private var ready = false

    @Volatile
    var modelName: String = ""
        private set

    override suspend fun runPrompt(prompt: String): String = withContext(Dispatchers.IO) {
        val inference = llmInference ?: return@withContext ""
        try {
            val method = inference.javaClass.getMethod("generateResponse", String::class.java)
            val result = method.invoke(inference, prompt) as? String
            result?.trim() ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "MediaPipe inference failed: ${e.message}")
            ""
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

            val optionsBuilderClass = Class.forName(
                "com.google.mediapipe.tasks.genai.llminference.LlmInference\$LlmInferenceOptions"
            ).getDeclaredMethod("builder")
            val builder = optionsBuilderClass.invoke(null)
            val builderClass = builder.javaClass
            builderClass.getMethod("setModelPath", String::class.java).invoke(builder, modelPath)
            builderClass.getMethod("setMaxTokens", Int::class.javaPrimitiveType).invoke(builder, 512)
            builderClass.getMethod("setTopK", Int::class.javaPrimitiveType).invoke(builder, 40)
            builderClass.getMethod("setTemperature", Float::class.javaPrimitiveType).invoke(builder, 0.3f)
            val options = builderClass.getMethod("build").invoke(builder)

            val inferenceClass = Class.forName(
                "com.google.mediapipe.tasks.genai.llminference.LlmInference"
            )
            val createMethod = inferenceClass.getMethod(
                "createFromOptions",
                Context::class.java,
                options.javaClass.interfaces.firstOrNull() ?: options.javaClass
            )

            llmInference = createMethod.invoke(null, context, options)
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
            llmInference?.let { inference ->
                try { inference.javaClass.getMethod("close").invoke(inference) } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
        llmInference = null
        ready = false
        modelName = ""
    }

    override fun runtimeName(): String = "MediaPipe LLM"
}
