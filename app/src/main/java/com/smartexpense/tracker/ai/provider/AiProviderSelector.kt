package com.smartexpense.tracker.ai.provider

import android.content.Context
import android.util.Log
import com.smartexpense.tracker.ai.modelmanager.LocalModelManager
import com.smartexpense.tracker.data.model.AiModePreference

/**
 * Selects the best AI provider based on:
 *  1. User-selected provider preference
 *  2. System AI availability
 *  3. Custom local model availability
 *  4. Cloud fallback
 */
class AiProviderSelector(
    private val context: Context,
    private val modelManager: LocalModelManager
) {

    companion object {
        private const val TAG = "AiProviderSelector"
    }

    val cloudProvider = CloudClaudeAiProvider()
    val systemProvider = SystemAiProvider(context)
    val customLocalProvider = CustomLocalModelProvider(context, modelManager)

    @Volatile
    private var activeProvider: AiProvider = cloudProvider

    /** The currently active provider. */
    fun getActiveProvider(): AiProvider = activeProvider

    /**
     * Selects the best provider based on user preference and availability.
     */
    suspend fun selectProvider(preference: AiModePreference): AiProvider {
        val provider = when (preference) {
            AiModePreference.CLOUD_AI -> {
                if (cloudProvider.isAvailable()) cloudProvider
                else fallback()
            }
            AiModePreference.SYSTEM_AI -> {
                systemProvider.initialize()
                if (systemProvider.isAvailable()) systemProvider
                else fallback()
            }
            AiModePreference.LOCAL_MODEL -> {
                customLocalProvider.loadActiveModel()
                if (customLocalProvider.isAvailable()) customLocalProvider
                else fallback()
            }
            AiModePreference.AUTO -> autoSelect()
        }

        activeProvider = provider
        Log.i(TAG, "Selected provider: ${provider.displayName()} (preference: $preference)")
        return provider
    }

    /**
     * Auto-selects the best available provider.
     * Priority: active local model (user explicitly activated) -> system AI -> cloud fallback.
     *
     * When a user downloads and activates a local model, it takes priority because it
     * represents an explicit user choice. System AI is tried next, then cloud as fallback.
     */
    private suspend fun autoSelect(): AiProvider {
        // Try user-activated local model first — the user explicitly downloaded and selected it
        customLocalProvider.loadActiveModel()
        if (customLocalProvider.isAvailable()) return customLocalProvider

        // Try system AI next
        systemProvider.initialize()
        if (systemProvider.isAvailable()) return systemProvider

        // Fall back to cloud
        if (cloudProvider.isAvailable()) return cloudProvider

        // Ultimate fallback — cloud provider (may fail without API key, but UI handles this)
        return cloudProvider
    }

    /**
     * Fallback chain when the preferred provider is unavailable.
     */
    private suspend fun fallback(): AiProvider {
        // Try each provider in priority order
        customLocalProvider.loadActiveModel()
        if (customLocalProvider.isAvailable()) return customLocalProvider

        systemProvider.initialize()
        if (systemProvider.isAvailable()) return systemProvider

        return cloudProvider
    }

    /**
     * Returns descriptions for all providers (for settings display).
     */
    fun getProviderDescriptions(): Map<AiModePreference, String> = mapOf(
        AiModePreference.AUTO to "Automatically select the best available engine",
        AiModePreference.SYSTEM_AI to systemProvider.description(),
        AiModePreference.LOCAL_MODEL to customLocalProvider.description(),
        AiModePreference.CLOUD_AI to cloudProvider.description()
    )

    /**
     * Returns the currently active provider's status message.
     */
    fun statusMessage(): String = activeProvider.let { provider ->
        "${provider.displayName()}: ${provider.description()}"
    }

    /**
     * Returns privacy message based on active provider.
     */
    fun privacyMessage(): String? {
        return if (activeProvider.isLocal()) {
            "All analysis is performed locally on your device. No data is sent to external servers."
        } else null
    }
}
