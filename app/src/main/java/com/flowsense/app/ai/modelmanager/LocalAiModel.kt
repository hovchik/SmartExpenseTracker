package com.flowsense.app.ai.modelmanager

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
    val description: String = "",
    /** Whether this model requires a HuggingFace token (gated repo). */
    val isGated: Boolean = false,
    /** URL to the model's license/agreement page on HuggingFace (for gated models). */
    val licenseUrl: String = "",
    /** True for reasoning models (e.g. DeepSeek R1) that use chain-of-thought
     *  and work best without system/role-play instructions. */
    val isReasoningModel: Boolean = false,
    /** Maximum token limit (input + output) for the model's KV cache.
     *  Must not exceed the value baked into the .task/.bin file.
     *  Models from litert-community with "ekv1280" in the filename use 1280.
     *  Community mirrors without explicit ekv info use a conservative 1024. */
    val maxTokens: Int = 1280,
    /** Recommendation priority (higher = recommended first).
     *  Combines inference speed and output quality for expense-tracking tasks.
     *  Used by [ModelCatalog.recommendedModel] instead of raw size. */
    val recommendationPriority: Int = 0
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
 * Models are split into two groups:
 *  1. **Ungated** — publicly accessible, NO HuggingFace token needed
 *  2. **Gated**   — require accepting model terms + HuggingFace token
 *
 * All URLs have been verified for correctness.
 *
 * Sources (ungated):
 *  - litert-community (official Google/LiteRT)
 *
 * Sources (gated — require HuggingFace token):
 *  - litert-community/Gemma3-1B-IT (official Gemma 3 1B)
 *  - litert-community/Gemma2-2B-IT (official Gemma 2 2B)
 */
object ModelCatalog {

    /** Models that can be downloaded without any authentication. */
    val ungatedModels: List<LocalAiModel> = listOf(

        // ── Small models (1–1.5 GB) — phones with 4 GB RAM ─────────────

        LocalAiModel(
            modelId = "tinyllama-11b-q8",
            displayName = "TinyLlama 1.1B Chat (int8)",
            runtimeType = RuntimeType.MEDIAPIPE,
            fileFormat = ".task",
            quantization = "int8",
            requiredRamMb = 2048,
            recommendedRamMb = 3072,
            sizeMb = 1095,
            supportsTextGeneration = true,
            supportsStructuredJson = false,
            supportsStreaming = true,
            version = "1.0",
            downloadUrl = "https://huggingface.co/litert-community/TinyLlama-1.1B-Chat-v1.0/resolve/main/TinyLlama-1.1B-Chat-v1.0_multi-prefill-seq_q8_ekv1280.task",
            description = "Fast 1.1B chat model. Good balance of speed and quality.",
            recommendationPriority = 60
        ),

        // NOTE: autoocrat0413/gemma-2b-it-gpu-int4.bin was removed — old .bin format
        // is incompatible with tasks-genai 0.10.x ("Unsupported model signature").

        // ── Medium models (1.5–2 GB) — phones with 6 GB RAM ────────────

        LocalAiModel(
            modelId = "qwen25-15b-q8",
            displayName = "Qwen 2.5 1.5B (int8)",
            runtimeType = RuntimeType.MEDIAPIPE,
            fileFormat = ".task",
            quantization = "int8",
            requiredRamMb = 2048,
            recommendedRamMb = 4096,
            sizeMb = 1523,
            supportsTextGeneration = true,
            supportsStructuredJson = false,
            supportsStreaming = true,
            version = "2.5",
            downloadUrl = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
            description = "Best pick: fast inference with strong accuracy. Excellent for expense categorization and insights.",
            recommendationPriority = 100
        ),

        LocalAiModel(
            modelId = "deepseek-r1-15b-q8",
            displayName = "DeepSeek-R1 1.5B (int8)",
            runtimeType = RuntimeType.MEDIAPIPE,
            fileFormat = ".task",
            quantization = "int8",
            requiredRamMb = 2560,
            recommendedRamMb = 4096,
            sizeMb = 1774,
            supportsTextGeneration = true,
            supportsStructuredJson = false,
            supportsStreaming = true,
            version = "1.0",
            downloadUrl = "https://huggingface.co/litert-community/DeepSeek-R1-Distill-Qwen-1.5B/resolve/main/DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv1280.task",
            description = "DeepSeek reasoning model. Strong analytical capabilities for financial insights.",
            isReasoningModel = true,
            recommendationPriority = 80
        ),

    )

    /**
     * Models hosted on gated HuggingFace repos (official Google Gemma, etc.).
     * Require a HuggingFace account with accepted model terms + API token.
     *
     * To obtain a token:
     *  1. Create a free account at https://huggingface.co/join
     *  2. Accept Gemma terms at https://huggingface.co/litert-community/Gemma3-1B-IT
     *  3. Create an access token at https://huggingface.co/settings/tokens
     *
     * Verified repos (all return HTTP 401 = gated, exists):
     *  - litert-community/Gemma3-1B-IT
     *  - litert-community/Gemma2-2B-IT
     */
    val gatedModels: List<LocalAiModel> = listOf(

        // ── Official Gemma models (gated, require HuggingFace token) ────

        LocalAiModel(
            modelId = "gemma3-1b-it-q8-official",
            displayName = "Gemma 3 1B IT (int8) [Official]",
            runtimeType = RuntimeType.MEDIAPIPE,
            fileFormat = ".task",
            quantization = "int8",
            requiredRamMb = 1536,
            recommendedRamMb = 3072,
            sizeMb = 1100,
            supportsTextGeneration = true,
            supportsStructuredJson = false,
            supportsStreaming = true,
            version = "3.0",
            downloadUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/Gemma3-1B-IT_multi-prefill-seq_q8_ekv1280.task",
            description = "Official Google Gemma 3 1B. Fast and accurate. Requires HuggingFace token.",
            isGated = true,
            licenseUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT",
            recommendationPriority = 90
        ),

        LocalAiModel(
            modelId = "gemma2-2b-it-q8-official",
            displayName = "Gemma 2 2B IT (int8) [Official]",
            runtimeType = RuntimeType.MEDIAPIPE,
            fileFormat = ".task",
            quantization = "int8",
            requiredRamMb = 3072,
            recommendedRamMb = 6144,
            sizeMb = 2710,
            supportsTextGeneration = true,
            supportsStructuredJson = false,
            supportsStreaming = true,
            version = "2.0",
            downloadUrl = "https://huggingface.co/litert-community/Gemma2-2B-IT/resolve/main/Gemma2-2B-IT_multi-prefill-seq_q8_ekv1280.task",
            description = "Official Google Gemma 2 2B (2.71 GB). Good quality but slower than 1B models. Requires HuggingFace token.",
            isGated = true,
            licenseUrl = "https://huggingface.co/litert-community/Gemma2-2B-IT",
            recommendationPriority = 70
        )
    )

    /** All models: ungated first, then gated. */
    val availableModels: List<LocalAiModel> = ungatedModels + gatedModels

    /** Returns models that fit in the given available RAM. */
    fun modelsForDevice(availableRamMb: Int): List<LocalAiModel> =
        availableModels.filter { it.requiredRamMb <= availableRamMb }

    /** Returns the recommended model for a given RAM tier.
     *  Picks the model with the highest [LocalAiModel.recommendationPriority]
     *  that fits comfortably in the device's available RAM. */
    fun recommendedModel(availableRamMb: Int): LocalAiModel? =
        modelsForDevice(availableRamMb)
            .filter { it.recommendedRamMb <= availableRamMb }
            .maxByOrNull { it.recommendationPriority }
            ?: modelsForDevice(availableRamMb).maxByOrNull { it.recommendationPriority }
}
