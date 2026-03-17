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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Robust download manager for AI model files.
 * Features:
 *  - URL accessibility check before downloading
 *  - Resume support for interrupted downloads
 *  - Progress reporting via StateFlow
 *  - Immediate cancellation via connection abort
 *  - Manual redirect handling for gated HuggingFace repos
 */
class ModelDownloadManager(private val context: Context) {

    companion object {
        private const val TAG = "ModelDownloadMgr"
        private const val CONNECT_TIMEOUT = 15_000
        private const val READ_TIMEOUT = 120_000
        private const val BUFFER_SIZE = 32_768 // 32 KB for fast downloads
        private const val USER_AGENT = "FlowSense/1.0 (Android)"
        private const val MAX_REDIRECTS = 5
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

    /** Tracks whether the current download should be cancelled. */
    private val cancelled = AtomicBoolean(false)

    /** Holds the active HTTP connection so cancel can disconnect it immediately. */
    private val activeConnection = AtomicReference<HttpURLConnection?>(null)

    /** Guards against concurrent download starts (atomic check-and-set). */
    private val downloading = AtomicBoolean(false)

    /** App-private directory for model files. */
    fun modelsDir(): File = File(context.filesDir, "ai_models").also { it.mkdirs() }

    /** HuggingFace token for gated repos (set from AppSettings.huggingFaceToken). */
    var huggingFaceToken: String = ""

    /**
     * Checks if a URL is accessible without downloading the full content.
     * Tries HEAD first; if that fails (some servers reject HEAD), falls back to
     * a GET with "Range: bytes=0-0" which downloads only 1 byte.
     * Handles HuggingFace redirects manually so the auth header isn't stripped.
     * Returns the content length if available, or -1 if unknown.
     */
    /** Stores the last error detail for UI display. */
    var lastAccessError: String = ""
        private set

    suspend fun checkUrlAccessibility(url: String): Pair<Boolean, Long> = withContext(Dispatchers.IO) {
        lastAccessError = ""
        try {
            // Try HEAD first (fast, no body)
            Log.d(TAG, "Checking accessibility: HEAD $url")
            val headConn = openConnectionWithRedirects(url, "HEAD")
            val headCode = headConn.responseCode
            val headLength = headConn.contentLengthLong
            val hfErrorCode = headConn.getHeaderField("x-error-code")
            headConn.disconnect()
            Log.d(TAG, "HEAD result: HTTP $headCode, length=$headLength, hf-error=$hfErrorCode")

            if (headCode in 200..299) {
                return@withContext true to headLength
            }

            // HEAD failed — some HuggingFace endpoints reject HEAD or return 403/405.
            // Fall back to GET with Range header to fetch only 1 byte.
            Log.d(TAG, "HEAD returned $headCode for $url, falling back to GET with Range")
            val getConn = openConnectionWithRedirects(url, "GET", "bytes=0-0")
            val getCode = getConn.responseCode
            val getHfError = getConn.getHeaderField("x-error-code") ?: hfErrorCode
            val getHfMessage = getConn.getHeaderField("x-error-message")
            // Content-Range: bytes 0-0/TOTAL — extract total size
            val contentRange = getConn.getHeaderField("Content-Range") // e.g. "bytes 0-0/1234567"
            val totalSize = contentRange?.substringAfter("/", "")?.toLongOrNull() ?: -1L
            getConn.disconnect()

            val accessible = getCode in 200..299
            if (!accessible && url.contains("huggingface.co")) {
                lastAccessError = when (getHfError) {
                    "GatedRepo" -> "This model requires you to accept its license terms on HuggingFace before downloading. " +
                        "Visit the model page and click \"Agree and access repository\", then try again."
                    "RepoNotFound" -> "Model repository not found. The URL may be incorrect."
                    else -> when (getCode) {
                        401 -> if (huggingFaceToken.isBlank())
                            "This model requires a HuggingFace token. Add your token in Settings > AI ENGINE."
                        else
                            "Authentication failed (HTTP 401). Your token may be expired or revoked."
                        403 -> "Access denied (HTTP 403). Accept the model license on HuggingFace and verify your token."
                        else -> "HTTP $getCode: ${getHfMessage ?: "Unknown error"}"
                    }
                }
                Log.w(TAG, "URL not accessible: HTTP $getCode, hf-error=$getHfError, msg=$getHfMessage for $url")
            } else if (!accessible) {
                lastAccessError = "HTTP $getCode"
                Log.w(TAG, "URL not accessible: HTTP $getCode for $url")
            }
            accessible to totalSize
        } catch (e: Exception) {
            lastAccessError = "Network error: ${e.message}"
            Log.w(TAG, "URL accessibility check failed for $url: ${e.message}")
            false to -1L
        }
    }

    /** Adds HuggingFace Bearer token for gated model repos. */
    private fun addAuthHeader(conn: HttpURLConnection, url: String) {
        if (url.contains("huggingface.co")) {
            if (huggingFaceToken.isNotBlank()) {
                conn.setRequestProperty("Authorization", "Bearer $huggingFaceToken")
                Log.d(TAG, "Auth header set for: ${url.take(80)}… (token: ${huggingFaceToken.take(6)}…)")
            } else {
                Log.w(TAG, "No HuggingFace token set for: ${url.take(80)}…")
            }
        }
    }

    /**
     * Opens an HTTP connection that follows redirects manually,
     * re-attaching the HuggingFace auth header on each hop.
     *
     * Java's built-in `instanceFollowRedirects` strips the Authorization header
     * when the redirect goes to a different host (e.g. HuggingFace CDN).
     */
    private fun openConnectionWithRedirects(
        initialUrl: String,
        method: String = "GET",
        rangeHeader: String? = null
    ): HttpURLConnection {
        var currentUrl = initialUrl
        var redirects = 0

        while (redirects < MAX_REDIRECTS) {
            val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = if (method == "HEAD") CONNECT_TIMEOUT else READ_TIMEOUT
                setRequestProperty("User-Agent", USER_AGENT)
                // Attach auth header for HuggingFace URLs (including CDN signed URLs)
                addAuthHeader(this, initialUrl)
                instanceFollowRedirects = false // handle redirects manually
                rangeHeader?.let { setRequestProperty("Range", it) }
            }

            val code = conn.responseCode
            if (code in listOf(301, 302, 303, 307, 308)) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrBlank()) {
                    throw java.io.IOException("Redirect without Location header")
                }
                currentUrl = if (location.startsWith("http")) location
                else URL(URL(currentUrl), location).toString()
                redirects++
            } else {
                return conn
            }
        }
        throw java.io.IOException("Too many redirects ($MAX_REDIRECTS)")
    }

    /**
     * Result of HuggingFace token validation.
     */
    sealed class TokenValidationResult {
        data class Valid(val username: String) : TokenValidationResult()
        object Invalid : TokenValidationResult()
        data class NetworkError(val message: String) : TokenValidationResult()
    }

    /**
     * Validates a HuggingFace token by calling the whoami endpoint.
     * Distinguishes between invalid tokens (HTTP 401) and network errors.
     */
    suspend fun validateHuggingFaceToken(token: String): TokenValidationResult = withContext(Dispatchers.IO) {
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
                TokenValidationResult.Valid(nameMatch?.groupValues?.get(1) ?: "authenticated")
            } else {
                conn.disconnect()
                Log.w(TAG, "HuggingFace token validation failed: HTTP $responseCode")
                if (responseCode == 401) TokenValidationResult.Invalid
                else TokenValidationResult.NetworkError("HTTP $responseCode")
            }
        } catch (e: Exception) {
            Log.w(TAG, "HuggingFace token validation error: ${e.message}")
            TokenValidationResult.NetworkError(e.message ?: "Unknown error")
        }
    }

    /**
     * Downloads a model to local storage.
     * Checks URL accessibility first, supports resume, reports progress.
     * Supports immediate cancellation via [cancelDownload].
     *
     * @return Local file path on success, null on failure.
     */
    suspend fun downloadModel(
        model: LocalAiModel,
        onProgress: ((Float) -> Unit)? = null
    ): String? = withContext(Dispatchers.IO) {
        if (!downloading.compareAndSet(false, true)) {
            _downloadState.value = _downloadState.value.copy(
                error = "A download is already in progress"
            )
            return@withContext null
        }
        try {
        // --- guarded section start ---

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

        cancelled.set(false)

        _downloadState.value = DownloadState(
            isDownloading = true,
            modelId = model.modelId
        )

        // Step 1: Check URL accessibility
        val (accessible, expectedSize) = checkUrlAccessibility(model.downloadUrl)
        if (!accessible) {
            _downloadState.value = DownloadState(
                error = lastAccessError.ifBlank { "Model URL is not accessible. Check your internet connection." },
                modelId = model.modelId
            )
            return@withContext null
        }

        if (cancelled.get()) {
            _downloadState.value = DownloadState(error = "Download cancelled", modelId = model.modelId)
            return@withContext null
        }

        val fileName = model.downloadUrl.substringAfterLast("/")
            .ifBlank { "${model.modelId}.task" }
        val destFile = File(modelsDir(), fileName)
        val tempFile = File(modelsDir(), "$fileName.tmp")

        try {
            Log.d(TAG, "Starting download: ${model.displayName} -> ${destFile.absolutePath}")

            // Resume support
            var existingBytes = 0L
            val rangeHeader = if (tempFile.exists() && tempFile.length() > 0) {
                existingBytes = tempFile.length()
                "bytes=$existingBytes-"
            } else null

            val conn = openConnectionWithRedirects(model.downloadUrl, "GET", rangeHeader)
            activeConnection.set(conn)

            if (cancelled.get()) {
                conn.disconnect()
                activeConnection.set(null)
                _downloadState.value = DownloadState(error = "Download cancelled", modelId = model.modelId)
                return@withContext null
            }

            val responseCode = conn.responseCode
            if (responseCode !in listOf(200, 206)) {
                conn.disconnect()
                activeConnection.set(null)
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
                        if (cancelled.get()) {
                            conn.disconnect()
                            activeConnection.set(null)
                            _downloadState.value = DownloadState(
                                error = "Download cancelled",
                                modelId = model.modelId
                            )
                            return@withContext null
                        }

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
            activeConnection.set(null)

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
            activeConnection.set(null)
            if (cancelled.get()) {
                _downloadState.value = DownloadState(
                    error = "Download cancelled",
                    modelId = model.modelId
                )
            } else {
                Log.e(TAG, "Download failed: ${e.message}", e)
                _downloadState.value = DownloadState(
                    error = "Download failed: ${e.message}",
                    modelId = model.modelId
                )
            }
            null
        }
        // --- guarded section end ---
        } finally {
            downloading.set(false)
        }
    }

    /**
     * Cancels any in-progress download immediately.
     * Disconnects the active HTTP connection and sets the cancelled flag.
     */
    fun cancelDownload() {
        cancelled.set(true)
        activeConnection.getAndSet(null)?.let { conn ->
            try {
                conn.disconnect()
            } catch (_: Exception) { }
        }
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
