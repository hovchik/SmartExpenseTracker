package com.smartexpense.tracker.ai.modelmanager

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File

/**
 * Handles model installation from various sources:
 *  - Download from URL (via ModelDownloadManager)
 *  - Import from device storage (SAF file picker)
 *  - Register existing model directory
 */
class ModelInstaller(private val context: Context) {

    companion object {
        private const val TAG = "ModelInstaller"
        private val VALID_EXTENSIONS = listOf(".task", ".bin", ".tflite", ".gguf")
    }

    /** App-private models directory. */
    fun modelsDir(): File = File(context.filesDir, "ai_models").also { it.mkdirs() }

    /**
     * Imports a model file from a content URI (e.g. picked via SAF file picker).
     * Copies the file into app-private storage.
     *
     * @return Local file path on success, null on failure.
     */
    fun importModelFile(uri: Uri, fileName: String): String? {
        return try {
            if (VALID_EXTENSIONS.none { fileName.endsWith(it, ignoreCase = true) }) {
                Log.e(TAG, "Invalid model file extension: $fileName")
                return null
            }

            val destFile = File(modelsDir(), fileName)
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: run {
                    Log.e(TAG, "Cannot open input stream for URI: $uri")
                    return null
                }

            inputStream.use { input ->
                destFile.outputStream().buffered().use { output ->
                    input.copyTo(output, bufferSize = 32_768)
                }
            }

            if (destFile.length() < 1024 * 1024) {
                Log.e(TAG, "Imported file too small (${destFile.length()} bytes)")
                destFile.delete()
                return null
            }

            Log.d(TAG, "Model imported: ${destFile.absolutePath} (${destFile.length() / 1024 / 1024} MB)")
            destFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Model import failed: ${e.message}", e)
            null
        }
    }

    /**
     * Registers an existing model file/directory path.
     * Validates the file exists and is a valid model.
     *
     * @return The validated path on success, null if invalid.
     */
    fun registerExistingModel(path: String): String? {
        val file = File(path)
        if (!file.exists()) {
            Log.e(TAG, "Model path does not exist: $path")
            return null
        }

        if (file.isDirectory) {
            // Look for model files in the directory
            val modelFile = file.listFiles()?.firstOrNull { f ->
                VALID_EXTENSIONS.any { f.name.endsWith(it, ignoreCase = true) }
            }
            if (modelFile == null) {
                Log.e(TAG, "No model files found in directory: $path")
                return null
            }
            return modelFile.absolutePath
        }

        if (VALID_EXTENSIONS.none { path.endsWith(it, ignoreCase = true) }) {
            Log.e(TAG, "Invalid model file: $path")
            return null
        }

        if (file.length() < 1024 * 1024) {
            Log.e(TAG, "File too small to be a valid model: ${file.length()} bytes")
            return null
        }

        return path
    }

    /**
     * Lists all model files in the app-private models directory.
     */
    fun listInstalledModels(): List<Pair<String, Long>> {
        val dir = modelsDir()
        if (!dir.exists()) return emptyList()

        return dir.listFiles()
            ?.filter { f ->
                f.isFile && VALID_EXTENSIONS.any { f.name.endsWith(it, ignoreCase = true) }
                        && f.length() > 1024 * 1024
                        && !f.name.endsWith(".tmp")
            }
            ?.map { it.name to it.length() }
            ?: emptyList()
    }

    /**
     * Deletes a model file from the models directory.
     */
    fun deleteModel(fileName: String): Boolean {
        val file = File(modelsDir(), fileName)
        return file.delete()
    }
}
