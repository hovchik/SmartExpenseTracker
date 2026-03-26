package com.flowsense.app.ai.capability

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.util.Log

/**
 * Detects device capabilities for on-device AI inference.
 * Checks Android version, available AI runtimes, RAM, storage, and performance class.
 */
class DeviceAiCapabilityDetector(private val context: Context) {

    companion object {
        private const val TAG = "DeviceAiCapability"

        /** Official Android AICore package. */
        private const val AICORE_PACKAGE = "com.google.android.aicore"

        /** ML Kit GenAI is available via Google Play Services on Android 14+. */
        private const val GMS_PACKAGE = "com.google.android.gms"

        /** Minimum RAM (MB) for running small local models. */
        const val MIN_RAM_MB_LOCAL = 3072 // 3 GB

        /** Recommended RAM (MB) for good local AI performance. */
        const val RECOMMENDED_RAM_MB = 6144 // 6 GB

        /** Minimum free storage (MB) for downloading a model. */
        const val MIN_STORAGE_MB = 1500 // 1.5 GB

        /** RAM tiers for categorization. */
        enum class RamTier(val label: String) {
            LOW("Low (< 4 GB)"),
            MEDIUM("Medium (4-6 GB)"),
            HIGH("High (6-8 GB)"),
            ULTRA("Ultra (8+ GB)")
        }

        enum class PerformanceClass(val label: String) {
            LOW("Low — Cloud AI recommended"),
            MEDIUM("Medium — Small local models supported"),
            HIGH("High — Full local AI support")
        }
    }

    data class DeviceCapability(
        val androidVersion: Int,
        val androidVersionName: String,
        val aiCoreAvailable: Boolean,
        val mlKitGenAiAvailable: Boolean,
        val totalRamMb: Long,
        val availableRamMb: Long,
        val ramTier: RamTier,
        val freeStorageMb: Long,
        val hasEnoughStorage: Boolean,
        val performanceClass: PerformanceClass,
        val manufacturer: String,
        val model: String,
        val supportedAbis: List<String>,
        val supportsSystemAi: Boolean,
        val supportsCustomLocalModel: Boolean,
        val recommendedMode: RecommendedAiMode
    )

    enum class RecommendedAiMode {
        /** Device has system AI — use it. */
        SYSTEM_AI,
        /** Device can run custom local models. */
        CUSTOM_LOCAL,
        /** Device not suitable for local AI. */
        CLOUD_ONLY
    }

    /**
     * Performs a full device capability scan.
     */
    fun detect(): DeviceCapability {
        val androidVersion = Build.VERSION.SDK_INT
        val androidVersionName = Build.VERSION.RELEASE

        val aiCoreAvailable = isPackageInstalled(AICORE_PACKAGE)
        val mlKitGenAiAvailable = checkMlKitGenAi()

        val totalRamMb = getTotalRamMb()
        val availableRamMb = getAvailableRamMb()
        val ramTier = when {
            totalRamMb < 4096 -> RamTier.LOW
            totalRamMb < 6144 -> RamTier.MEDIUM
            totalRamMb < 8192 -> RamTier.HIGH
            else -> RamTier.ULTRA
        }

        val freeStorageMb = getFreeStorageMb()
        val hasEnoughStorage = freeStorageMb >= MIN_STORAGE_MB

        val performanceClass = when {
            totalRamMb < MIN_RAM_MB_LOCAL -> PerformanceClass.LOW
            totalRamMb < RECOMMENDED_RAM_MB -> PerformanceClass.MEDIUM
            else -> PerformanceClass.HIGH
        }

        val supportsSystemAi = aiCoreAvailable || mlKitGenAiAvailable
        val supportsCustomLocalModel = totalRamMb >= MIN_RAM_MB_LOCAL && hasEnoughStorage

        val recommendedMode = when {
            supportsSystemAi -> RecommendedAiMode.SYSTEM_AI
            supportsCustomLocalModel -> RecommendedAiMode.CUSTOM_LOCAL
            else -> RecommendedAiMode.CLOUD_ONLY
        }

        val capability = DeviceCapability(
            androidVersion = androidVersion,
            androidVersionName = androidVersionName,
            aiCoreAvailable = aiCoreAvailable,
            mlKitGenAiAvailable = mlKitGenAiAvailable,
            totalRamMb = totalRamMb,
            availableRamMb = availableRamMb,
            ramTier = ramTier,
            freeStorageMb = freeStorageMb,
            hasEnoughStorage = hasEnoughStorage,
            performanceClass = performanceClass,
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
            supportsSystemAi = supportsSystemAi,
            supportsCustomLocalModel = supportsCustomLocalModel,
            recommendedMode = recommendedMode
        )

        Log.i(TAG, "Device capability: RAM=${totalRamMb}MB, " +
                "storage=${freeStorageMb}MB, AICore=$aiCoreAvailable, " +
                "MLKit=$mlKitGenAiAvailable, recommended=$recommendedMode")

        return capability
    }

    /**
     * Returns a user-friendly message describing device AI capability.
     */
    fun getCapabilityMessage(capability: DeviceCapability): String = when (capability.recommendedMode) {
        RecommendedAiMode.SYSTEM_AI ->
            "This device supports built-in on-device AI. The app can use the system AI engine."
        RecommendedAiMode.CUSTOM_LOCAL ->
            "This device does not expose built-in AI to apps, but it can run a downloadable local model."
        RecommendedAiMode.CLOUD_ONLY ->
            "This device is not ideal for local AI. Cloud AI is recommended."
    }

    // ── Private helpers ──────────────────────────────────────────────

    private fun isPackageInstalled(packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private fun checkMlKitGenAi(): Boolean {
        // ML Kit GenAI requires Google Play Services and Android 14+
        if (Build.VERSION.SDK_INT < 34) return false
        return isPackageInstalled(GMS_PACKAGE)
    }

    private fun getTotalRamMb(): Long {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return memInfo.totalMem / (1024 * 1024)
    }

    private fun getAvailableRamMb(): Long {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return memInfo.availMem / (1024 * 1024)
    }

    private fun getFreeStorageMb(): Long {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            stat.availableBlocksLong * stat.blockSizeLong / (1024 * 1024)
        } catch (_: Exception) {
            0
        }
    }
}
