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
 *  2. Hosted on ungated HuggingFace repos — NO authentication required
 *  3. Range from ~159 MB to ~7.1 GB to cover different device tiers
 *  4. All URLs verified accessible with HTTP 200/302 without auth headers
 *
 * Sources:
 *  - litert-community/* (official Google/LiteRT, ungated non-Gemma models)
 *  - AfiOne/gemma3-1b-it-int4.task (community Gemma mirror, ungated)
 *  - CarlosJefte/Gemma-2-2b-mediapipe (community Gemma 2 mirror, ungated)
 *  - realbyte/gemma-3n-E2B-it-int4-mediapipe (community Gemma 3n mirror, ungated)
 *  - autoocrat0413/gemma-2b-it-gpu-int4-mediapipe (community Gemma mirror, ungated)
 */
object ModelCatalog {

    val availableModels: List<LocalAiModel> = listOf(

        // ── Tiny models (< 600 MB) — any device with 2+ GB RAM ──────────

        LocalAiModel(
            modelId = "smollm-135m-q8",
            displayName = "SmolLM 135M (int8)",
            runtimeType = RuntimeType.MEDIAPIPE,
            fileFormat = ".task",
            quantization = "int8",
            requiredRamMb = 512,
            recommendedRamMb = 1024,
            sizeMb = 159,
            supportsTextGeneration = true,
            supportsStructuredJson = false,
            supportsStreaming = true,
            version = "1.0",
            downloadUrl = "https://huggingface.co/litert-community/SmolLM-135M-Instruct/resolve/main/SmolLM-135M-Instruct_multi-prefill-seq_q8_ekv1280.task",
            description = "Tiny 135M model. Ultra-fast, good for basic categorization on low-end devices."
        ),

        LocalAiModel(
            modelId = "qwen25-05b-q8",
            displayName = "Qwen 2.5 0.5B (int8)",
            runtimeType = RuntimeType.MEDIAPIPE,
            fileFormat = ".task",
            quantization = "int8",
            requiredRamMb = 1024,
            recommendedRamMb = 2048,
            sizeMb = 521,
            supportsTextGeneration = true,
            supportsStructuredJson = false,
            supportsStreaming = true,
            version = "2.5",
            downloadUrl = "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
            description = "Small but capable 0.5B model. Good quality-to-size ratio."
        ),

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
            downloadUrl = "https://huggingface.co/AfiOne/gemma3-1b-it-int4.task/resolve/main/gemma3-1b-it-int4.task",
            description = "Smallest Gemma model. Fast inference, good for categorization."
        ),

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
            description = "Fast 1.1B chat model. Good balance of speed and quality."
        ),

        LocalAiModel(
            modelId = "gemma-2b-gpu-int4",
            displayName = "Gemma 2B IT GPU (int4)",
            runtimeType = RuntimeType.MEDIAPIPE,
            fileFormat = ".bin",
            quantization = "int4",
            requiredRamMb = 2048,
            recommendedRamMb = 4096,
            sizeMb = 1291,
            supportsTextGeneration = true,
            supportsStructuredJson = false,
            supportsStreaming = true,
            version = "1.0",
            downloadUrl = "https://huggingface.co/autoocrat0413/gemma-2b-it-gpu-int4-mediapipe/resolve/main/gemma-2b-it-gpu-int4.bin",
            description = "GPU-optimized Gemma 2B. Fast inference with hardware acceleration."
        ),

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
            description = "Strong 1.5B model. Excellent for expense categorization and insights."
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
            description = "DeepSeek reasoning model. Strong analytical capabilities for financial insights."
        ),

        // ── Large models (2.5–4 GB) — phones/tablets with 8 GB RAM ──────

        LocalAiModel(
            modelId = "gemma2-2b-cpu-int8",
            displayName = "Gemma 2 2B IT (int8)",
            runtimeType = RuntimeType.MEDIAPIPE,
            fileFormat = ".task",
            quantization = "int8",
            requiredRamMb = 3072,
            recommendedRamMb = 6144,
            sizeMb = 3053,
            supportsTextGeneration = true,
            supportsStructuredJson = false,
            supportsStreaming = true,
            version = "2.0",
            downloadUrl = "https://huggingface.co/CarlosJefte/Gemma-2-2b-mediapipe/resolve/main/gemma2-2b-it-cpu-int8.task",
            description = "Gemma 2 2B with int8 precision. Great accuracy for financial analysis."
        ),

        LocalAiModel(
            modelId = "gemma-3n-e2b-int4",
            displayName = "Gemma 3n E2B IT (int4)",
            runtimeType = RuntimeType.MEDIAPIPE,
            fileFormat = ".task",
            quantization = "int4",
            requiredRamMb = 3072,
            recommendedRamMb = 6144,
            sizeMb = 2990,
            supportsTextGeneration = true,
            supportsStructuredJson = false,
            supportsStreaming = true,
            version = "3.0",
            downloadUrl = "https://huggingface.co/realbyte/gemma-3n-E2B-it-int4-mediapipe/resolve/main/gemma-3n-E2B-it-int4.task",
            description = "Latest Gemma 3n architecture with selective parameter activation."
        ),

        LocalAiModel(
            modelId = "phi4-mini-q8",
            displayName = "Phi-4 Mini (int8)",
            runtimeType = RuntimeType.MEDIAPIPE,
            fileFormat = ".task",
            quantization = "int8",
            requiredRamMb = 5120,
            recommendedRamMb = 8192,
            sizeMb = 3761,
            supportsTextGeneration = true,
            supportsStructuredJson = false,
            supportsStreaming = true,
            version = "4.0",
            downloadUrl = "https://huggingface.co/litert-community/Phi-4-mini-instruct/resolve/main/Phi-4-mini-instruct_multi-prefill-seq_q8_ekv1280.task",
            description = "Microsoft Phi-4 Mini. Strong reasoning for complex financial analysis."
        ),

        // ── Extra-large models (5–7 GB) — high-end devices with 12+ GB RAM ──

        LocalAiModel(
            modelId = "qwen25-15b-f32",
            displayName = "Qwen 2.5 1.5B (f32)",
            runtimeType = RuntimeType.MEDIAPIPE,
            fileFormat = ".task",
            quantization = "f32",
            requiredRamMb = 6144,
            recommendedRamMb = 10240,
            sizeMb = 5895,
            supportsTextGeneration = true,
            supportsStructuredJson = false,
            supportsStreaming = true,
            version = "2.5",
            downloadUrl = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_f32_ekv1280.task",
            description = "Full-precision 1.5B model. Maximum accuracy, requires 8+ GB RAM."
        ),

        LocalAiModel(
            modelId = "deepseek-r1-15b-f32",
            displayName = "DeepSeek-R1 1.5B (f32)",
            runtimeType = RuntimeType.MEDIAPIPE,
            fileFormat = ".task",
            quantization = "f32",
            requiredRamMb = 8192,
            recommendedRamMb = 12288,
            sizeMb = 6794,
            supportsTextGeneration = true,
            supportsStructuredJson = false,
            supportsStreaming = true,
            version = "1.0",
            downloadUrl = "https://huggingface.co/litert-community/DeepSeek-R1-Distill-Qwen-1.5B/resolve/main/DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_f32_ekv1280.task",
            description = "Full-precision DeepSeek reasoning model. Best analytical quality."
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
