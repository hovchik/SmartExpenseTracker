package com.smartexpense.tracker.service.ocr

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * On-device Armenian OCR using Tesseract (tess-two).
 *
 * ML Kit has no Armenian script model, so we use Tesseract with the
 * `hye` (Armenian) trained data. The trained data file is downloaded
 * on first use and cached in the app's internal storage.
 *
 * Also supports `rus` (Russian) as a Tesseract fallback when ML Kit's
 * Latin recognizer produces poor results on Cyrillic-heavy receipts.
 */
class ArmenianOcrService(private val context: Context) {

    companion object {
        private const val TAG = "ArmenianOCR"
        private const val TESSDATA_DIR = "tessdata"

        /** Languages to download: Armenian + Russian. */
        private val LANG_FILES = mapOf(
            "hye" to "https://github.com/tesseract-ocr/tessdata_fast/raw/main/hye.traineddata",
            "rus" to "https://github.com/tesseract-ocr/tessdata_fast/raw/main/rus.traineddata"
        )
    }

    /** Root directory for Tesseract — must contain a `tessdata/` subdirectory. */
    private val dataPath: String get() = context.filesDir.absolutePath

    /** Whether all required trained data files are present on disk. */
    fun isReady(): Boolean {
        val tessDir = File(dataPath, TESSDATA_DIR)
        return LANG_FILES.keys.all { File(tessDir, "$it.traineddata").exists() }
    }

    /**
     * Downloads any missing trained data files.
     * Returns `true` on success, `false` if any download fails.
     */
    suspend fun ensureTrainedData(): Boolean = withContext(Dispatchers.IO) {
        val tessDir = File(dataPath, TESSDATA_DIR)
        if (!tessDir.exists()) tessDir.mkdirs()

        for ((lang, url) in LANG_FILES) {
            val file = File(tessDir, "$lang.traineddata")
            if (file.exists() && file.length() > 100_000) continue // already present

            Log.d(TAG, "Downloading $lang trained data from $url")
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                conn.instanceFollowRedirects = true
                conn.connect()

                if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                    Log.e(TAG, "HTTP ${conn.responseCode} downloading $lang")
                    conn.disconnect()
                    return@withContext false
                }

                conn.inputStream.use { input ->
                    FileOutputStream(file).use { output ->
                        input.copyTo(output, bufferSize = 8192)
                    }
                }
                conn.disconnect()
                Log.d(TAG, "Downloaded $lang: ${file.length()} bytes")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to download $lang trained data", e)
                file.delete() // remove partial file
                return@withContext false
            }
        }
        true
    }

    /**
     * Runs Tesseract OCR on the given bitmap using Armenian + Russian languages.
     * Returns the recognised text, or empty string on failure.
     */
    suspend fun recognizeText(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        if (!isReady()) {
            Log.w(TAG, "Trained data not ready, attempting download")
            if (!ensureTrainedData()) {
                Log.e(TAG, "Cannot download trained data")
                return@withContext ""
            }
        }

        val tess = TessBaseAPI()
        try {
            // "hye+rus" tells Tesseract to try both Armenian and Russian
            val initOk = tess.init(dataPath, "hye+rus")
            if (!initOk) {
                Log.e(TAG, "Tesseract init failed")
                return@withContext ""
            }

            // Page segmentation mode 3 = "Fully automatic page segmentation, but no OSD"
            tess.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
            tess.setImage(bitmap)

            val result = tess.utF8Text ?: ""
            val confidence = tess.meanConfidence()
            Log.d(TAG, "Tesseract result: ${result.length} chars, confidence=$confidence")

            result
        } catch (e: Exception) {
            Log.e(TAG, "Tesseract recognition failed", e)
            ""
        } finally {
            tess.end()
        }
    }
}
