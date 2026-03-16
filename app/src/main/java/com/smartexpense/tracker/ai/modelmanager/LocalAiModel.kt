package com.smartexpense.tracker.ai.modelmanager

/**
 * Metadata entity for a locally installed or available AI model.
 * Persisted via JSON alongside other app data.
 */
data class LocalAiModel(
    val modelId: String,
    val displayName: String,
    val runtimeType: RuntimeType,
    val fileFormat: String,
    val quantization: String,
    val requiredRamMb: Int,
    val recommendedRamMb: Int,
    val sizeMb: Int,
    val localPath: String = "",
    val installState: InstallState = InstallState.NOT_INSTALLED,
    val checksum: String = "",
    val version: String = "1.0",
    val supportsStructuredJson: Boolean = false,
    val supportsStreaming: Boolean = false,
    val supportsTextGeneration: Boolean = true,
    /** Download URL for this model (empty for imported models). */
    val downloadUrl: String = "",
    /** Brief description of model capabilities shown in the catalog UI. */
    val description: String = ""
)

enum class RuntimeType(val label: String) {
    MEDIAPIPE("MediaPipe LLM"),
    LITE_RT("LiteRT (TFLite)"),
    SYSTEM_AI("System AI")
}

enum class InstallState(val label: String) {
    NOT_INSTALLED("Not installed"),
    DOWNLOADING("Downloading"),
    INSTALLING("Installing"),
    INSTALLED("Installed"),
    FAILED("Failed"),
    VALIDATING("Validating")
}

/**
 * Built-in catalog of models compatible with MediaPipe LLM Inference API.
 *
 * All models listed here are:
 *  1. Compatible with MediaPipe LLM Inference (.task format)
 *  2. Hosted on HuggingFace (litert-community org) — gated repos require HF token
 *  3. Range from ~529 MB to ~11 GB to cover different device tiers
 *  4. URLs verified against actual HuggingFace file listings
 *
 * The catalog covers the Gemma family which is officially supported
 * by the MediaPipe LLM Inference API.
 *
 * Note: These repos are gated — downloads require a HuggingFace token
 * with accepted Gemma license. The app's huggingFaceToken setting is
 * passed as an Authorization header during download.
 */
object ModelCatalog {

    val availableModels: List<LocalAiModel> = listOf(
        // ── Small models (< 1 GB) — phones with 4 GB RAM ──────────────

        LocalAiModel(
            modelId = "gemma3-1b-it-int4",
            displayName = "Gemma 3 1B IT (int4)",
            runtimeType = RuntimeType.MEDIAPIPE,
            fileFormat = ".task",
            quantization = "int4",
            requiredRamMb = 1536,
            recommendedRamMb = 3072,
            sizeMb = 529,
            supportsTextGeneration = true,
            supportsStructuredJson = false,
            supportsStreaming = true,
            version = "3.0",
            downloadUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.task",
            description = "Smallest Gemma model. Fast inference, good for basic categorization."
        ),

        // ── Medium models (1–2.6 GB) — phones with 4–6 GB RAM ────────

        LocalAiModel(
            modelId = "gemma3-1b-it-q8",
            displayName = "Gemma 3 1B IT (int8)",
            runtimeType = RuntimeType.MEDIAPIPE,
            fileFormat = ".task",
            quantization = "int8",
            requiredRamMb = 2048,
            recommendedRamMb = 4096,
            sizeMb = 1005,
            supportsTextGeneration = true,
            supportsStructuredJson = false,
            supportsStreaming = true,
            version = "3.0",
            downloadUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/Gemma3-1B-IT_multi-prefill-seq_q8_ekv2048.task",
            description = "Higher precision 1B model. Better accuracy for categorization."
        ),

        LocalAiModel(
            modelId = "gemma3-4b-it-int4",
            displayName = "Gemma 3 4B IT (int4)",
            runtimeType = RuntimeType.MEDIAPIPE,
            fileFormat = ".task",
            quantization = "int4",
            requiredRamMb = 3072,
            recommendedRamMb = 6144,
            sizeMb = 2383,
            supportsTextGeneration = true,
            supportsStructuredJson = false,
            supportsStreaming = true,
            version = "3.0",
            downloadUrl = "https://huggingface.co/litert-community/Gemma3-4B-IT/resolve/main/gemma3-4b-it-int4-web.task",
            description = "Strong 4B model. Excellent categorization and financial insights."
        ),

        // ── Large models (2.5–4 GB) — phones/tablets with 6–8 GB RAM ─

        LocalAiModel(
            modelId = "gemma2-2b-it-q8",
            displayName = "Gemma 2 2B IT (int8)",
            runtimeType = RuntimeType.MEDIAPIPE,
            fileFormat = ".task",
            quantization = "int8",
            requiredRamMb = 3072,
            recommendedRamMb = 6144,
            sizeMb = 2527,
            supportsTextGeneration = true,
            supportsStructuredJson = false,
            supportsStreaming = true,
            version = "2.0",
            downloadUrl = "https://huggingface.co/litert-community/Gemma2-2B-IT/resolve/main/Gemma2-2B-IT_multi-prefill-seq_q8_ekv1280.task",
            description = "Balanced 2B model. Good accuracy for expense analysis and insights."
        ),

        LocalAiModel(
            modelId = "gemma3-4b-it-int8",
            displayName = "Gemma 3 4B IT (int8)",
            runtimeType = RuntimeType.MEDIAPIPE,
            fileFormat = ".task",
            quantization = "int8",
            requiredRamMb = 5120,
            recommendedRamMb = 8192,
            sizeMb = 3628,
            supportsTextGeneration = true,
            supportsStructuredJson = false,
            supportsStreaming = true,
            version = "3.0",
            downloadUrl = "https://huggingface.co/litert-community/Gemma3-4B-IT/resolve/main/gemma3-4b-it-int8-web.task",
            description = "High-precision 4B model. Best quality for complex financial analysis."
        ),

        // ── Extra-large models (7–11 GB) — high-end devices with 12+ GB RAM ──

        LocalAiModel(
            modelId = "gemma3-12b-it-int4",
            displayName = "Gemma 3 12B IT (int4)",
            runtimeType = RuntimeType.MEDIAPIPE,
            fileFormat = ".task",
            quantization = "int4",
            requiredRamMb = 8192,
            recommendedRamMb = 12288,
            sizeMb = 7027,
            supportsTextGeneration = true,
            supportsStructuredJson = false,
            supportsStreaming = true,
            version = "3.0",
            downloadUrl = "https://huggingface.co/litert-community/Gemma3-12B-IT/resolve/main/gemma3-12b-it-int4-web.task",
            description = "Large 12B model. Near cloud-quality analysis for high-end devices."
        ),

        LocalAiModel(
            modelId = "gemma3-12b-it-int8",
            displayName = "Gemma 3 12B IT (int8)",
            runtimeType = RuntimeType.MEDIAPIPE,
            fileFormat = ".task",
            quantization = "int8",
            requiredRamMb = 12288,
            recommendedRamMb = 16384,
            sizeMb = 10981,
            supportsTextGeneration = true,
            supportsStructuredJson = false,
            supportsStreaming = true,
            version = "3.0",
            downloadUrl = "https://huggingface.co/litert-community/Gemma3-12B-IT/resolve/main/gemma3-12b-it-int8-web.task",
            description = "Largest available model. Maximum accuracy, requires 12+ GB RAM."
        )
    )

    /** Returns models that fit in the given available RAM. */
    fun modelsForDevice(availableRamMb: Int): List<LocalAiModel> =
        availableModels.filter { it.requiredRamMb <= availableRamMb }

    /** Returns the recommended model for a given RAM tier. */
    fun recommendedModel(availableRamMb: Int): LocalAiModel? =
        modelsForDevice(availableRamMb)
            .sortedByDescending { it.sizeMb }
            .firstOrNull { it.recommendedRamMb <= availableRamMb }
            ?: modelsForDevice(availableRamMb).firstOrNull()
}
