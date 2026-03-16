package com.smartexpense.tracker.ai.modelmanager

import android.os.Build
import com.smartexpense.tracker.ai.capability.DeviceAiCapabilityDetector

/**
 * Validates whether a model is compatible with the current device.
 * Checks ABI, RAM, storage, and runtime support.
 */
class ModelCompatibilityValidator(
    private val capabilityDetector: DeviceAiCapabilityDetector
) {

    data class ValidationResult(
        val compatible: Boolean,
        val issues: List<String> = emptyList(),
        val warnings: List<String> = emptyList()
    )

    /**
     * Validates a model against current device capabilities.
     */
    fun validate(model: LocalAiModel): ValidationResult {
        val capability = capabilityDetector.detect()
        val issues = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // ABI compatibility
        val supportedAbis = Build.SUPPORTED_ABIS.toSet()
        if ("arm64-v8a" !in supportedAbis && "x86_64" !in supportedAbis) {
            issues.add("Device ABI not supported. Requires arm64-v8a or x86_64.")
        }

        // RAM requirement
        if (capability.totalRamMb < model.requiredRamMb) {
            issues.add(
                "Insufficient RAM: ${capability.totalRamMb} MB available, " +
                        "${model.requiredRamMb} MB required."
            )
        } else if (capability.totalRamMb < model.recommendedRamMb) {
            warnings.add(
                "RAM below recommended: ${capability.totalRamMb} MB available, " +
                        "${model.recommendedRamMb} MB recommended. Performance may be reduced."
            )
        }

        // Storage requirement
        if (capability.freeStorageMb < model.sizeMb + 500) { // 500 MB buffer
            issues.add(
                "Insufficient storage: ${capability.freeStorageMb} MB free, " +
                        "need ~${model.sizeMb + 500} MB (model + buffer)."
            )
        }

        // Runtime support check
        when (model.runtimeType) {
            RuntimeType.MEDIAPIPE -> {
                // MediaPipe is bundled via dependency — always available if app compiled with it
            }
            RuntimeType.LITE_RT -> {
                warnings.add("LiteRT runtime is a placeholder — real bindings need to be added.")
            }
            RuntimeType.SYSTEM_AI -> {
                if (!capability.supportsSystemAi) {
                    issues.add("System AI runtime not available on this device.")
                }
            }
        }

        return ValidationResult(
            compatible = issues.isEmpty(),
            issues = issues,
            warnings = warnings
        )
    }
}
