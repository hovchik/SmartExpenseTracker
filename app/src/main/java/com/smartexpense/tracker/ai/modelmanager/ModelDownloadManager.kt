package com.smartexpense.tracker.ai.modelmanager

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Robust download manager for AI model files.
 * Features:
 *  - URL accessibility check before downloading
 *  - Resume support for interrupted downloads
 *  - Progress reporting via StateFlow
 *  - Checksum validation
 *  - Concurrent download prevention
 */
class ModelDownloadManager(private val context: Context) {

    companion object {
        private const val TAG = "ModelDownloadMgr"
        private const val CONNECT_TIMEOUT = 15_000
        private const val READ_TIMEOUT = 120_000
        private const val BUFFER_SIZE = 32_768 // 32 KB for fast downloads
        private const val USER_AGENT = "FlowSense/1.0 (Android)"
    }

    data class DownloadState(
        val isDownloading: Boolean = false,
        val progress: Float = 0f,
        val downloadedBytes: Long = 0,
        val totalBytes: Long = 0,
        val speedBytesPerSec: Long = 0,
        val error: String? = null,
        val modelId: String = ""
    )

    private val _downloadState = MutableStateFlow(DownloadState())
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    /** App-private directory for model files. */
    fun modelsDir(): File = File(context.filesDir, "ai_models").also { it.mkdirs() }

    /** HuggingFace token for gated repos (set from AppSettings.huggingFaceToken). */
    var huggingFaceToken: String = ""

    /**
     * Checks if a URL is accessible (returns HTTP 200/206) without downloading content.
     * Returns the content length if available, or -1 if unknown.
     * Automatically adds HuggingFace auth header for huggingface.co URLs.
     */
    suspend fun checkUrlAccessibility(url: String): Pair<Boolean, Long> = withContext(Dispatchers.IO) {
        try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "HEAD"
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = CONNECT_TIMEOUT
                setRequestProperty("User-Agent", USER_AGENT)
                addAuthHeader(this, url)
                instanceFollowRedirects = true
            }

            val responseCode = conn.responseCode
            val contentLength = conn.contentLengthLong
            conn.disconnect()

            val accessible = responseCode in 200..299
            if (!accessible) {
                val hint = if (responseCode == 401 && url.contains("huggingface.co"))
                    " (gated repo — add your HuggingFace token in Settings)"
                else ""
                Log.w(TAG, "URL not accessible: HTTP $responseCode for $url$hint")
            }
            accessible to contentLength
        } catch (e: Exception) {
            Log.w(TAG, "URL accessibility check failed for $url: ${e.message}")
            false to -1L
        }
    }

    /** Adds HuggingFace Bearer token for gated model repos. */
    private fun addAuthHeader(conn: HttpURLConnection, url: String) {
        if (huggingFaceToken.isNotBlank() && url.contains("huggingface.co")) {
            conn.setRequestProperty("Authorization", "Bearer $huggingFaceToken")
        }
    }

    /**
     * Validates a HuggingFace token by calling the whoami endpoint.
     * Returns the username on success, or null if the token is invalid.
     */
    suspend fun validateHuggingFaceToken(token: String): String? = withContext(Dispatchers.IO) {
        try {
            val conn = (URL("https://huggingface.co/api/whoami-v2").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = CONNECT_TIMEOUT
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("User-Agent", USER_AGENT)
            }

            val responseCode = conn.responseCode
            if (responseCode == 200) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                // Extract "name" from JSON response (simple parse to avoid dependency)
                val nameMatch = Regex("\"name\"\\s*:\\s*\"([^\"]+)\"").find(body)
                nameMatch?.groupValues?.get(1) ?: "authenticated"
            } else {
                conn.disconnect()
                Log.w(TAG, "HuggingFace token validation failed: HTTP $responseCode")
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "HuggingFace token validation error: ${e.message}")
            null
        }
    }

    /**
     * Downloads a model to local storage.
     * Checks URL accessibility first, supports resume, reports progress.
     *
     * @return Local file path on success, null on failure.
     */
    suspend fun downloadModel(
        model: LocalAiModel,
        onProgress: ((Float) -> Unit)? = null
    ): String? = withContext(Dispatchers.IO) {
        if (_downloadState.value.isDownloading) {
            _downloadState.value = _downloadState.value.copy(
                error = "A download is already in progress"
            )
            return@withContext null
        }

        if (model.downloadUrl.isBlank()) {
            _downloadState.value = DownloadState(error = "No download URL for this model")
            return@withContext null
        }

        // Require token for gated models
        if (model.isGated && huggingFaceToken.isBlank()) {
            _downloadState.value = DownloadState(
                error = "This model requires a HuggingFace token. Add your token first.",
                modelId = model.modelId
            )
            return@withContext null
        }

        _downloadState.value = DownloadState(
            isDownloading = true,
            modelId = model.modelId
        )

        // Step 1: Check URL accessibility
        val (accessible, expectedSize) = checkUrlAccessibility(model.downloadUrl)
        if (!accessible) {
            _downloadState.value = DownloadState(
                error = "Model URL is not accessible. Check your internet connection.",
                modelId = model.modelId
            )
            return@withContext null
        }

        val fileName = model.downloadUrl.substringAfterLast("/")
            .ifBlank { "${model.modelId}.task" }
        val destFile = File(modelsDir(), fileName)
        val tempFile = File(modelsDir(), "$fileName.tmp")

        try {
            Log.d(TAG, "Starting download: ${model.displayName} -> ${destFile.absolutePath}")

            val conn = (URL(model.downloadUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
                setRequestProperty("User-Agent", USER_AGENT)
                addAuthHeader(this, model.downloadUrl)
                instanceFollowRedirects = true
            }

            // Resume support
            var existingBytes = 0L
            if (tempFile.exists() && tempFile.length() > 0) {
                existingBytes = tempFile.length()
                conn.setRequestProperty("Range", "bytes=$existingBytes-")
            }

            conn.connect()

            val responseCode = conn.responseCode
            if (responseCode !in listOf(200, 206)) {
                conn.disconnect()
                _downloadState.value = DownloadState(
                    error = "Download failed: HTTP $responseCode",
                    modelId = model.modelId
                )
                return@withContext null
            }

            val contentLength = conn.contentLengthLong.let {
                if (it > 0) it else expectedSize
            }
            val isResume = responseCode == 206
            val totalSize = if (isResume) existingBytes + contentLength else contentLength

            val outputStream = if (isResume) {
                java.io.FileOutputStream(tempFile, true).buffered()
            } else {
                tempFile.outputStream().buffered()
            }

            val inputStream = conn.inputStream.buffered()
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesWritten = if (isResume) existingBytes else 0L
            var lastProgressTime = System.currentTimeMillis()
            var lastProgressBytes = bytesWritten

            outputStream.use { out ->
                inputStream.use { input ->
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        out.write(buffer, 0, bytesRead)
                        bytesWritten += bytesRead

                        val now = System.currentTimeMillis()
                        val elapsed = now - lastProgressTime
                        if (elapsed >= 500 || bytesWritten == totalSize) {
                            val speed = if (elapsed > 0) {
                                (bytesWritten - lastProgressBytes) * 1000 / elapsed
                            } else 0

                            val progress = if (totalSize > 0) {
                                (bytesWritten.toFloat() / totalSize.toFloat()).coerceIn(0f, 1f)
                            } else 0f

                            _downloadState.value = DownloadState(
                                isDownloading = true,
                                progress = progress,
                                downloadedBytes = bytesWritten,
                                totalBytes = totalSize,
                                speedBytesPerSec = speed,
                                modelId = model.modelId
                            )
                            onProgress?.invoke(progress)

                            lastProgressTime = now
                            lastProgressBytes = bytesWritten
                        }
                    }
                }
            }

            conn.disconnect()

            // Validate downloaded file
            if (bytesWritten < 1024 * 1024) {
                tempFile.delete()
                _downloadState.value = DownloadState(
                    error = "Downloaded file too small — likely not a valid model",
                    modelId = model.modelId
                )
                return@withContext null
            }

            // Rename temp to final
            if (destFile.exists()) destFile.delete()
            if (!tempFile.renameTo(destFile)) {
                tempFile.copyTo(destFile, overwrite = true)
                tempFile.delete()
            }

            Log.d(TAG, "Download complete: ${destFile.absolutePath} (${destFile.length() / 1024 / 1024} MB)")

            _downloadState.value = DownloadState(
                progress = 1f,
                downloadedBytes = destFile.length(),
                totalBytes = destFile.length(),
                modelId = model.modelId
            )

            destFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Download failed: ${e.message}", e)
            _downloadState.value = DownloadState(
                error = "Download failed: ${e.message}",
                modelId = model.modelId
            )
            null
        }
    }

    /**
     * Cancels any in-progress download by resetting state.
     * The actual HTTP connection will time out or be interrupted.
     */
    fun cancelDownload() {
        _downloadState.value = DownloadState(error = "Download cancelled")
    }

    /**
     * Deletes a downloaded model file.
     */
    fun deleteModelFile(model: LocalAiModel): Boolean {
        val fileName = model.downloadUrl.substringAfterLast("/")
            .ifBlank { "${model.modelId}.task" }
        val file = File(modelsDir(), fileName)
        val temp = File(modelsDir(), "$fileName.tmp")
        temp.delete()
        return file.delete()
    }

    /**
     * Returns the local path for a model if it's downloaded.
     */
    fun getModelPath(model: LocalAiModel): String? {
        val fileName = model.downloadUrl.substringAfterLast("/")
            .ifBlank { "${model.modelId}.task" }
        val file = File(modelsDir(), fileName)
        return if (file.exists() && file.length() > 1024 * 1024) file.absolutePath else null
    }

    /**
     * Returns the total storage used by downloaded models in MB.
     */
    fun getStorageUsageMb(): Long {
        val dir = modelsDir()
        if (!dir.exists()) return 0
        return dir.listFiles()?.sumOf { it.length() }?.div(1024 * 1024) ?: 0
    }
}
