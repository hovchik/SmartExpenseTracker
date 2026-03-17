package com.smartexpense.tracker.ai.modelmanager

import android.content.Context
import android.util.Log
import com.smartexpense.tracker.ai.capability.DeviceAiCapabilityDetector

/**
 * Central manager for local AI models.
 * Tracks installed models, metadata, install state, validation, and local paths.
 */
class LocalModelManager(private val context: Context) {

    companion object {
        private const val TAG = "LocalModelManager"
        private const val PREFS_NAME = "local_ai_models"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val downloadManager = ModelDownloadManager(context)
    private val installer = ModelInstaller(context)
    private val capabilityDetector = DeviceAiCapabilityDetector(context)
    private val validator = ModelCompatibilityValidator(capabilityDetector)

    /** Download manager for observing download progress. */
    val downloads: ModelDownloadManager get() = downloadManager

    /** Sets the HuggingFace token for downloading gated models. */
    fun setHuggingFaceToken(token: String) {
        downloadManager.huggingFaceToken = token
    }

    /** Validates a HuggingFace token. Returns the username on success, null on failure. */
    suspend fun validateHuggingFaceToken(token: String): String? =
        downloadManager.validateHuggingFaceToken(token)

    /** Installer for import/register operations. */
    val install: ModelInstaller get() = installer

    /** Compatibility validator. */
    val compatibility: ModelCompatibilityValidator get() = validator

    /** Capability detector for device info. */
    val capabilities: DeviceAiCapabilityDetector get() = capabilityDetector

    /**
     * Returns the list of models from the catalog with their current install state.
     */
    fun getCatalogModels(): List<LocalAiModel> {
        return ModelCatalog.availableModels.map { model ->
            val localPath = downloadManager.getModelPath(model)
            if (localPath != null) {
                model.copy(
                    localPath = localPath,
                    installState = InstallState.INSTALLED
                )
            } else {
                model
            }
        }
    }

    /**
     * Gets the currently active model (if any is installed and selected).
     */
    fun getActiveModel(): LocalAiModel? {
        val activeModelId = prefs.getString("active_model_id", null) ?: return null
        return getCatalogModels().find {
            it.modelId == activeModelId && it.installState == InstallState.INSTALLED
        }
    }

    /**
     * Sets the active model.
     */
    fun setActiveModel(modelId: String) {
        prefs.edit().putString("active_model_id", modelId).apply()
        Log.i(TAG, "Active model set to: $modelId")
    }

    /**
     * Downloads a model from the catalog.
     * @return Local file path on success, null on failure.
     */
    suspend fun downloadModel(
        model: LocalAiModel,
        onProgress: ((Float) -> Unit)? = null
    ): String? {
        // Validate compatibility first
        val validation = validator.validate(model)
        if (!validation.compatible) {
            Log.w(TAG, "Model not compatible: ${validation.issues}")
            return null
        }

        val path = downloadManager.downloadModel(model, onProgress)
        if (path != null) {
            setActiveModel(model.modelId)
        }
        return path
    }

    /**
     * Deletes a model and clears it as active if needed.
     */
    fun deleteModel(model: LocalAiModel): Boolean {
        val activeId = prefs.getString("active_model_id", null)
        if (activeId == model.modelId) {
            prefs.edit().remove("active_model_id").apply()
        }
        return downloadManager.deleteModelFile(model)
    }

    /**
     * Returns storage usage in MB for all downloaded models.
     */
    fun getStorageUsageMb(): Long = downloadManager.getStorageUsageMb()

    /**
     * Returns a list of all installed model files.
     */
    fun getInstalledModels(): List<Pair<String, Long>> = installer.listInstalledModels()
}
