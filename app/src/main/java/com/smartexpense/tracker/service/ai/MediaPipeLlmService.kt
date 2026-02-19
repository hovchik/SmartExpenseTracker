package com.smartexpense.tracker.service.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * On-device LLM inference via MediaPipe LLM Inference API.
 * Supports Gemma and other models distributed through Google AI Edge Gallery
 * or downloaded manually.
 *
 * The model file (`.task` / `.bin`) must be present on-device before use.
 * Users can obtain models from:
 *   1. Google AI Edge Gallery app (Play Store)
 *   2. Hugging Face (download + push via adb)
 *   3. Direct download within app to internal storage
 */
class MediaPipeLlmService(private val context: Context) {

    companion object {
        private const val TAG = "MediaPipeLlm"

        /** Well-known model directories to scan for .task files. */
        private val MODEL_SEARCH_DIRS = listOf(
            "/data/local/tmp/llm",
            "/sdcard/Download",
            "/sdcard/Documents"
        )

        /** File extensions recognized as MediaPipe model files. */
        private val MODEL_EXTENSIONS = listOf(".task", ".bin", ".tflite")

        /** Google AI Edge Gallery package name. */
        const val GALLERY_PACKAGE = "com.google.ai.edge.gallery"
    }

    @Volatile
    private var llmInference: Any? = null   // com.google.mediapipe.tasks.genai.llminference.LlmInference

    @Volatile
    var isReady: Boolean = false
        private set

    @Volatile
    var modelName: String = ""
        private set

    @Volatile
    var errorMessage: String? = null
        private set

    private val ruleEngine = AiExpenseEngine()

    /**
     * Checks whether Google AI Edge Gallery app is installed on the device.
     */
    fun isGalleryInstalled(): Boolean = try {
        context.packageManager.getApplicationInfo(GALLERY_PACKAGE, 0)
        true
    } catch (_: Exception) {
        false
    }

    /**
     * Scans well-known directories and app internal storage for model files.
     * Returns a list of (name, absolutePath) pairs.
     */
    fun discoverModels(): List<Pair<String, String>> {
        val models = mutableListOf<Pair<String, String>>()

        // Check app-private models dir
        val appModelsDir = File(context.filesDir, "models")
        if (appModelsDir.exists()) {
            scanDir(appModelsDir, models)
        }

        // Check well-known external locations
        for (dir in MODEL_SEARCH_DIRS) {
            val f = File(dir)
            if (f.exists() && f.isDirectory) {
                scanDir(f, models)
            }
        }

        // Deduplicate by path
        return models.distinctBy { it.second }
    }

    private fun scanDir(dir: File, out: MutableList<Pair<String, String>>) {
        dir.listFiles()?.forEach { file ->
            if (file.isFile && MODEL_EXTENSIONS.any { file.name.endsWith(it, ignoreCase = true) }) {
                val name = file.nameWithoutExtension
                    .replace("_", " ")
                    .replace("-", " ")
                    .replaceFirstChar { it.uppercase() }
                out.add(name to file.absolutePath)
            }
        }
    }

    /**
     * Loads a model from the given path.
     * Must be called on a background thread.
     */
    suspend fun loadModel(modelPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // Release previous model if any
            releaseModel()

            val file = File(modelPath)
            if (!file.exists()) {
                errorMessage = "Model file not found: ${file.name}"
                Log.e(TAG, "Model file not found: $modelPath")
                return@withContext false
            }

            Log.d(TAG, "Loading model from: $modelPath (${file.length() / 1024 / 1024} MB)")

            // Use reflection to avoid hard compile-time dependency failures on
            // devices where the native libs might not load.
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
            isReady = true
            errorMessage = null
            Log.d(TAG, "Model loaded successfully: $modelName")
            true
        } catch (e: Exception) {
            val cause = e.cause ?: e
            errorMessage = "Failed to load model: ${cause.message}"
            Log.e(TAG, "Failed to load model", e)
            isReady = false
            false
        }
    }

    /**
     * Releases the loaded model and frees resources.
     */
    fun releaseModel() {
        try {
            llmInference?.let { inference ->
                try {
                    inference.javaClass.getMethod("close").invoke(inference)
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
        llmInference = null
        isReady = false
        modelName = ""
    }

    /**
     * Sends a prompt to the loaded model and returns the generated text.
     * Returns null if the model is not loaded or inference fails.
     */
    suspend fun generateResponse(prompt: String): String? = withContext(Dispatchers.IO) {
        val inference = llmInference ?: return@withContext null
        try {
            val method = inference.javaClass.getMethod("generateResponse", String::class.java)
            val result = method.invoke(inference, prompt) as? String
            result?.trim()
        } catch (e: Exception) {
            Log.e(TAG, "Inference failed: ${e.message}")
            null
        }
    }

    /**
     * Uses the on-device LLM to categorize a transaction description.
     * Falls back to rule-based if LLM fails or is not loaded.
     */
    suspend fun categorize(
        description: String,
        availableCategories: List<String>,
        isExpense: Boolean = true,
        userCategoryNames: List<String> = emptyList()
    ): String {
        if (!isReady) {
            return ruleEngine.categorize(description, isExpense, userCategoryNames)
        }

        val categoriesList = availableCategories.joinToString(", ")
        val prompt = """Categorize this transaction into exactly one category.
Transaction: "$description"
Type: ${if (isExpense) "expense" else "income"}
Available categories: $categoriesList
Reply with ONLY the category name, nothing else."""

        val response = generateResponse(prompt)
        if (response != null) {
            // Find the best matching category from the response
            val cleaned = response.trim().removeSurrounding("\"")
            val exactMatch = availableCategories.find { it.equals(cleaned, ignoreCase = true) }
            if (exactMatch != null) return exactMatch

            // Fuzzy: check if response contains a category name
            val containsMatch = availableCategories.find { cleaned.contains(it, ignoreCase = true) }
            if (containsMatch != null) return containsMatch
        }

        // Fallback to rule-based
        return ruleEngine.categorize(description, isExpense, userCategoryNames)
    }

    /**
     * Uses the on-device LLM to generate a financial insight.
     * Falls back to null if LLM is not loaded.
     */
    suspend fun generateInsight(
        totalExpenses: Double,
        totalIncome: Double,
        topCategory: String?,
        topCategoryAmount: Double,
        transactionCount: Int,
        currencyCode: String = "USD"
    ): String? {
        if (!isReady) return null

        val prompt = """You are a personal finance advisor. Generate a brief, actionable insight (1-2 sentences) based on:
- Total expenses: $currencyCode ${String.format("%.2f", totalExpenses)}
- Total income: $currencyCode ${String.format("%.2f", totalIncome)}
- Top spending category: ${topCategory ?: "N/A"} ($currencyCode ${String.format("%.2f", topCategoryAmount)})
- Transaction count: $transactionCount
Keep it concise and helpful."""

        return generateResponse(prompt)
    }

    /**
     * Returns a human-readable status string.
     */
    fun statusMessage(): String = when {
        isReady -> "MediaPipe LLM active — $modelName"
        errorMessage != null -> "MediaPipe LLM error: $errorMessage"
        else -> "MediaPipe LLM — no model loaded"
    }
}
