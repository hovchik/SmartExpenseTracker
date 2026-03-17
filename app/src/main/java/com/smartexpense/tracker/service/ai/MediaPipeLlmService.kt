package com.smartexpense.tracker.service.ai

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * On-device LLM inference via MediaPipe LLM Inference API.
 * Supports Gemma and other models distributed through Google AI Edge Gallery
 * or downloaded manually.
 *
 * The model file (`.task` / `.bin`) must be present on-device before use.
 * Users can obtain models from:
 *   1. Google AI Edge Gallery app (Play Store)
 *   2. Hugging Face (download + push via adb)
 *   3. In-app download from the model catalog
 */
class MediaPipeLlmService(private val context: Context) {

    companion object {
        private const val TAG = "MediaPipeLlm"
        // Most catalog models use ekv1280 (1280-token KV cache).
        // Multilingual text (Armenian, CJK, Cyrillic) tokenizes at ~1.5 chars/token,
        // so 1200 chars ≈ 800 tokens, leaving ~480 for output.
        private const val MAX_INPUT_CHARS = 1200

        /** File extensions recognized as MediaPipe model files. */
        private val MODEL_EXTENSIONS = listOf(".task", ".bin", ".tflite")

        /** Google AI Edge Gallery package name. */
        const val GALLERY_PACKAGE = "com.google.ai.edge.gallery"

        /** Play Store URL for Google AI Edge Gallery. */
        const val GALLERY_PLAY_STORE_URL =
            "https://play.google.com/store/apps/details?id=$GALLERY_PACKAGE"
    }

    // ── Model catalog ────────────────────────────────────────────────

    /**
     * A downloadable model entry in the built-in catalog.
     */
    data class CatalogModel(
        val id: String,
        val name: String,
        val fileName: String,
        val downloadUrl: String,
        val sizeBytes: Long,
        val description: String,
        val quantization: String
    ) {
        /** Human-readable file size. */
        val sizeLabel: String get() {
            val mb = sizeBytes / (1024.0 * 1024.0)
            return if (mb >= 1024) "${String.format("%.1f", mb / 1024)} GB"
                   else "${String.format("%.0f", mb)} MB"
        }
    }

    /** Built-in catalog of recommended models (use Ollama for model management). */
    val modelCatalog: List<CatalogModel> = emptyList()

    // ── Download state ───────────────────────────────────────────────

    @Volatile
    var downloadProgress: Float = 0f
        private set

    @Volatile
    var isDownloading: Boolean = false
        private set

    @Volatile
    var downloadError: String? = null
        private set

    // ── Inference state ──────────────────────────────────────────────

    @Volatile
    private var llmInference: Any? = null

    @Volatile
    var isReady: Boolean = false
        private set

    @Volatile
    var modelName: String = ""
        private set

    @Volatile
    private var currentModelPath: String? = null

    @Volatile
    var errorMessage: String? = null
        private set

    private val ruleEngine = AiExpenseEngine()

    // ── Gallery check ────────────────────────────────────────────────

    fun isGalleryInstalled(): Boolean = try {
        context.packageManager.getApplicationInfo(GALLERY_PACKAGE, 0)
        true
    } catch (_: Exception) {
        false
    }

    // ── Model directory ──────────────────────────────────────────────

    /** App-private directory for downloaded models. */
    fun modelsDir(): File = File(context.filesDir, "models").also { it.mkdirs() }

    /**
     * Returns the local path for a catalog model (whether it exists or not).
     */
    fun catalogModelPath(model: CatalogModel): String =
        File(modelsDir(), model.fileName).absolutePath

    /**
     * Checks if a catalog model is already downloaded.
     */
    fun isModelDownloaded(model: CatalogModel): Boolean {
        val file = File(modelsDir(), model.fileName)
        return file.exists() && file.length() > 1024 * 1024 // > 1MB as sanity check
    }

    // ── Download ─────────────────────────────────────────────────────

    /**
     * Downloads a model from the catalog to internal storage.
     * Reports progress via [downloadProgress] (0.0–1.0).
     * Returns the file path on success, null on failure.
     */
    suspend fun downloadModel(
        model: CatalogModel,
        hfToken: String = "",
        onProgress: ((Float) -> Unit)? = null
    ): String? = withContext(Dispatchers.IO) {
        if (isDownloading) {
            downloadError = "A download is already in progress"
            return@withContext null
        }

        isDownloading = true
        downloadProgress = 0f
        downloadError = null
        val destFile = File(modelsDir(), model.fileName)
        val tempFile = File(modelsDir(), "${model.fileName}.tmp")

        try {
            Log.d(TAG, "Starting download: ${model.name} (${model.sizeLabel}) → ${destFile.absolutePath}")

            val connection = URL(model.downloadUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 30_000
            connection.readTimeout = 60_000
            connection.setRequestProperty("User-Agent", "FlowSense/1.0")
            // HuggingFace gated models require Bearer token
            if (hfToken.isNotBlank()) {
                connection.setRequestProperty("Authorization", "Bearer $hfToken")
            }
            // Support resuming partial downloads
            if (tempFile.exists() && tempFile.length() > 0) {
                connection.setRequestProperty("Range", "bytes=${tempFile.length()}-")
            }
            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode == 401 || responseCode == 403) {
                downloadError = "Authentication required. Enter your HuggingFace token in settings to download gated models."
                Log.e(TAG, "Auth failed: HTTP $responseCode for ${model.downloadUrl}")
                connection.disconnect()
                isDownloading = false
                return@withContext null
            }
            if (responseCode !in listOf(200, 206)) {
                downloadError = "Download failed: HTTP $responseCode"
                Log.e(TAG, "Download failed: HTTP $responseCode for ${model.downloadUrl}")
                connection.disconnect()
                isDownloading = false
                return@withContext null
            }

            val contentLength = connection.contentLengthLong.let {
                if (it > 0) it else model.sizeBytes
            }
            val isResume = responseCode == 206
            val totalSize = if (isResume) tempFile.length() + contentLength else contentLength

            val outputStream = if (isResume) tempFile.outputStream().buffered().also {
                // Seek not needed — we append
            } else tempFile.outputStream().buffered()
            val inputStream = connection.inputStream.buffered()

            val buffer = ByteArray(8192)
            var bytesWritten = if (isResume) tempFile.length() else 0L

            outputStream.use { out ->
                inputStream.use { input ->
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        out.write(buffer, 0, bytesRead)
                        bytesWritten += bytesRead
                        val progress = (bytesWritten.toFloat() / totalSize.toFloat()).coerceIn(0f, 1f)
                        downloadProgress = progress
                        onProgress?.invoke(progress)
                    }
                }
            }

            connection.disconnect()

            // Rename temp to final
            if (destFile.exists()) destFile.delete()
            if (!tempFile.renameTo(destFile)) {
                // Fallback: copy
                tempFile.copyTo(destFile, overwrite = true)
                tempFile.delete()
            }

            Log.d(TAG, "Download complete: ${destFile.absolutePath} (${destFile.length() / 1024 / 1024} MB)")
            downloadProgress = 1f
            isDownloading = false
            destFile.absolutePath
        } catch (e: Exception) {
            downloadError = "Download failed: ${e.message}"
            Log.e(TAG, "Download failed", e)
            isDownloading = false
            null
        }
    }

    /**
     * Deletes a downloaded model file.
     */
    fun deleteModel(model: CatalogModel): Boolean {
        val file = File(modelsDir(), model.fileName)
        val temp = File(modelsDir(), "${model.fileName}.tmp")
        temp.delete()
        return file.delete()
    }

    // ── File import ───────────────────────────────────────────────────

    /**
     * Imports a model file from a content URI (e.g. picked via SAF file picker).
     * Copies the file into the app-private models directory.
     * Returns the local file path on success, null on failure.
     */
    suspend fun importModelFile(uri: Uri, fileName: String): String? = withContext(Dispatchers.IO) {
        try {
            val destFile = File(modelsDir(), fileName)
            val inputStream: InputStream = context.contentResolver.openInputStream(uri)
                ?: run {
                    Log.e(TAG, "Cannot open input stream for URI: $uri")
                    return@withContext null
                }

            inputStream.use { input ->
                destFile.outputStream().buffered().use { output ->
                    input.copyTo(output, bufferSize = 8192)
                }
            }

            if (destFile.length() < 1024 * 1024) {
                Log.e(TAG, "Imported file too small (${destFile.length()} bytes), likely not a model")
                destFile.delete()
                return@withContext null
            }

            Log.d(TAG, "Model imported: ${destFile.absolutePath} (${destFile.length() / 1024 / 1024} MB)")
            destFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Model import failed: ${e.message}", e)
            null
        }
    }

    // ── Model discovery ──────────────────────────────────────────────

    fun discoverModels(): List<Pair<String, String>> {
        val models = mutableListOf<Pair<String, String>>()

        // App-private models dir
        val appModelsDir = modelsDir()
        if (appModelsDir.exists()) scanDir(appModelsDir, models)

        // Build search dirs dynamically using the Environment API
        // so paths resolve correctly regardless of device / emulator layout.
        val searchDirs = mutableListOf<File>()
        try {
            searchDirs += Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            searchDirs += Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        } catch (_: Exception) { /* not available */ }

        // AI Edge Gallery media dir
        val extStorage = Environment.getExternalStorageDirectory()
        searchDirs += File(extStorage, "Android/media/$GALLERY_PACKAGE")
        searchDirs += File(extStorage, "Android/media/$GALLERY_PACKAGE/files")

        // adb push location
        searchDirs += File("/data/local/tmp/llm")

        // App-specific external files dirs (e.g. models placed by the user)
        context.getExternalFilesDir(null)?.let { searchDirs += it }

        for (dir in searchDirs) {
            try {
                if (dir.exists() && dir.isDirectory) scanDir(dir, models)
            } catch (e: SecurityException) {
                Log.d(TAG, "Cannot access ${dir.absolutePath}: ${e.message}")
            }
        }

        return models.distinctBy { it.second }
    }

    private fun scanDir(dir: File, out: MutableList<Pair<String, String>>) {
        dir.listFiles()?.forEach { file ->
            if (file.isFile && MODEL_EXTENSIONS.any { file.name.endsWith(it, ignoreCase = true) }
                && file.length() > 1024 * 1024) { // > 1MB
                val name = file.nameWithoutExtension
                    .replace("_", " ")
                    .replace("-", " ")
                    .replaceFirstChar { it.uppercase() }
                out.add(name to file.absolutePath)
            }
        }
    }

    // ── Model loading ────────────────────────────────────────────────

    suspend fun loadModel(modelPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            releaseModel()

            val file = File(modelPath)
            if (!file.exists()) {
                errorMessage = "Model file not found: ${file.name}"
                Log.e(TAG, "Model file not found: $modelPath")
                return@withContext false
            }

            Log.d(TAG, "Loading model from: $modelPath (${file.length() / 1024 / 1024} MB)")

            val optionsBuilderClass = Class.forName(
                "com.google.mediapipe.tasks.genai.llminference.LlmInference\$LlmInferenceOptions"
            ).getDeclaredMethod("builder")
            val builder = optionsBuilderClass.invoke(null)
            val builderClass = builder.javaClass
            builderClass.getMethod("setModelPath", String::class.java).invoke(builder, modelPath)
            builderClass.getMethod("setMaxTokens", Int::class.javaPrimitiveType).invoke(builder, 1280)
            try {
                builderClass.getMethod("setTopK", Int::class.javaPrimitiveType).invoke(builder, 40)
            } catch (_: NoSuchMethodException) {
                Log.d(TAG, "setTopK not available in this MediaPipe version")
            }
            try {
                builderClass.getMethod("setTemperature", Float::class.javaPrimitiveType).invoke(builder, 0.3f)
            } catch (_: NoSuchMethodException) {
                Log.d(TAG, "setTemperature not available in this MediaPipe version")
            }
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
            currentModelPath = modelPath
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

    fun releaseModel() {
        try {
            llmInference?.let { inference ->
                try { inference.javaClass.getMethod("close").invoke(inference) } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
        llmInference = null
        isReady = false
        currentModelPath = null
        modelName = ""
    }

    /**
     * Recreates the LlmInference session after a failed invoke.
     * After a TFLite/XNNPack error the session is corrupt — reusing it
     * causes a native SIGSEGV. Recreating prevents the process crash.
     */
    private suspend fun recreateSession() {
        val path = currentModelPath ?: return
        try {
            Log.w(TAG, "Recreating MediaPipe session after error")
            releaseModel()
            loadModel(path)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to recreate session: ${e.message}")
            llmInference = null
            isReady = false
        }
    }

    // ── Inference ────────────────────────────────────────────────────

    suspend fun generateResponse(prompt: String): String? = withContext(Dispatchers.IO) {
        val inference = llmInference ?: return@withContext null
        try {
            // Truncate to avoid exceeding combined input+output token limit (native crash)
            val safePrompt = if (prompt.length > MAX_INPUT_CHARS) {
                Log.w(TAG, "Prompt truncated from ${prompt.length} to $MAX_INPUT_CHARS chars")
                prompt.take(MAX_INPUT_CHARS)
            } else {
                prompt
            }
            val method = inference.javaClass.getMethod("generateResponse", String::class.java)
            val result = method.invoke(inference, safePrompt) as? String
            result?.trim()
        } catch (e: Exception) {
            Log.e(TAG, "Inference failed: ${e.message}")
            // After a TFLite error the session is corrupt — recreate to
            // prevent SIGSEGV on the next call
            recreateSession()
            null
        }
    }

    suspend fun categorize(
        description: String,
        availableCategories: List<String>,
        isExpense: Boolean = true,
        userCategoryNames: List<String> = emptyList()
    ): String {
        if (!isReady) return ruleEngine.categorize(description, isExpense, userCategoryNames)

        val categoriesList = availableCategories.joinToString(", ")
        val prompt = """Categorize this transaction into exactly one category.
Transaction: "$description"
Type: ${if (isExpense) "expense" else "income"}
Available categories: $categoriesList
Reply with ONLY the category name, nothing else."""

        val response = generateResponse(prompt)
        if (response != null) {
            val cleaned = response.trim().removeSurrounding("\"")
            val exactMatch = availableCategories.find { it.equals(cleaned, ignoreCase = true) }
            if (exactMatch != null) return exactMatch
            val containsMatch = availableCategories.find { cleaned.contains(it, ignoreCase = true) }
            if (containsMatch != null) return containsMatch
        }

        return ruleEngine.categorize(description, isExpense, userCategoryNames)
    }

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

    fun statusMessage(): String = when {
        isReady -> "MediaPipe LLM active — $modelName"
        errorMessage != null -> "MediaPipe LLM error: $errorMessage"
        else -> "MediaPipe LLM — no model loaded"
    }
}
