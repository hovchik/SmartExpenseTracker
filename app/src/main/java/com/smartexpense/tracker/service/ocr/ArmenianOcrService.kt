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
 * Multi-language on-device OCR using Tesseract (tess-two).
 *
 * Supplements ML Kit by supporting scripts ML Kit cannot handle (Armenian)
 * and providing a Tesseract-based fallback for others (Russian, Chinese,
 * Japanese, Korean, Arabic, Georgian, Thai, etc.).
 *
 * Trained data files are downloaded from GitHub tessdata_fast on first use
 * and cached in the app's internal storage.
 */
class ArmenianOcrService(private val context: Context) {

    companion object {
        private const val TAG = "TesseractOCR"
        private const val TESSDATA_DIR = "tessdata"

        /**
         * Languages to download.
         * Key = Tesseract language code, Value = GitHub tessdata_fast URL.
         * Armenian (hye) is the primary reason for Tesseract; the rest are
         * supplementary fallbacks that improve recognition on mixed-script receipts.
         */
        private val LANG_FILES = mapOf(
            "hye" to "https://github.com/tesseract-ocr/tessdata_fast/raw/main/hye.traineddata",
            "rus" to "https://github.com/tesseract-ocr/tessdata_fast/raw/main/rus.traineddata",
            "chi_sim" to "https://github.com/tesseract-ocr/tessdata_fast/raw/main/chi_sim.traineddata",
            "chi_tra" to "https://github.com/tesseract-ocr/tessdata_fast/raw/main/chi_tra.traineddata",
            "jpn" to "https://github.com/tesseract-ocr/tessdata_fast/raw/main/jpn.traineddata",
            "kor" to "https://github.com/tesseract-ocr/tessdata_fast/raw/main/kor.traineddata",
            "ara" to "https://github.com/tesseract-ocr/tessdata_fast/raw/main/ara.traineddata",
            "kat" to "https://github.com/tesseract-ocr/tessdata_fast/raw/main/kat.traineddata",
            "tha" to "https://github.com/tesseract-ocr/tessdata_fast/raw/main/tha.traineddata",
            "hin" to "https://github.com/tesseract-ocr/tessdata_fast/raw/main/hin.traineddata",
            "fra" to "https://github.com/tesseract-ocr/tessdata_fast/raw/main/fra.traineddata",
            "deu" to "https://github.com/tesseract-ocr/tessdata_fast/raw/main/deu.traineddata",
            "spa" to "https://github.com/tesseract-ocr/tessdata_fast/raw/main/spa.traineddata",
            "tur" to "https://github.com/tesseract-ocr/tessdata_fast/raw/main/tur.traineddata"
        )

        /** The core set always downloaded on first use. */
        private val CORE_LANGS = setOf("hye", "rus", "chi_sim", "jpn", "kor")

        /** Builds the Tesseract language string from available trained data files. */
        fun availableLangsString(dataPath: String): String {
            val tessDir = File(dataPath, TESSDATA_DIR)
            if (!tessDir.exists()) return "hye+rus"
            return LANG_FILES.keys
                .filter { File(tessDir, "$it.traineddata").exists() }
                .joinToString("+")
                .ifEmpty { "hye+rus" }
        }
    }

    /** Root directory for Tesseract — must contain a `tessdata/` subdirectory. */
    private val dataPath: String get() = context.filesDir.absolutePath

    /** Whether all core trained data files are present on disk. */
    fun isReady(): Boolean {
        val tessDir = File(dataPath, TESSDATA_DIR)
        return CORE_LANGS.all { File(tessDir, "$it.traineddata").exists() }
    }

    /**
     * Downloads any missing trained data files.
     * Core languages are downloaded first; supplementary ones are downloaded
     * in the background afterward.
     * Returns `true` if at least the core languages are available.
     */
    suspend fun ensureTrainedData(): Boolean = withContext(Dispatchers.IO) {
        val tessDir = File(dataPath, TESSDATA_DIR)
        if (!tessDir.exists()) tessDir.mkdirs()

        // Download core languages first (required for basic operation)
        for (lang in CORE_LANGS) {
            val url = LANG_FILES[lang] ?: continue
            if (!downloadIfMissing(tessDir, lang, url)) {
                Log.e(TAG, "Failed to download core language: $lang")
                // Continue with whatever we have
            }
        }

        // Download supplementary languages (non-blocking failures are OK)
        for ((lang, url) in LANG_FILES) {
            if (lang in CORE_LANGS) continue
            downloadIfMissing(tessDir, lang, url)
        }

        // Return true if we have at least Armenian (the primary purpose)
        File(tessDir, "hye.traineddata").exists()
    }

    private fun downloadIfMissing(tessDir: File, lang: String, url: String): Boolean {
        val file = File(tessDir, "$lang.traineddata")
        if (file.exists() && file.length() > 100_000) return true

        Log.d(TAG, "Downloading $lang trained data from $url")
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 60_000
            conn.instanceFollowRedirects = true
            conn.connect()

            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "HTTP ${conn.responseCode} downloading $lang")
                conn.disconnect()
                return false
            }

            conn.inputStream.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output, bufferSize = 8192)
                }
            }
            conn.disconnect()
            Log.d(TAG, "Downloaded $lang: ${file.length()} bytes")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download $lang trained data", e)
            file.delete()
            false
        }
    }

    /**
     * Runs Tesseract OCR on the given bitmap using all available language models.
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

        val langs = availableLangsString(dataPath)
        Log.d(TAG, "Running Tesseract with languages: $langs")

        val tess = TessBaseAPI()
        try {
            val initOk = tess.init(dataPath, langs)
            if (!initOk) {
                Log.e(TAG, "Tesseract init failed for $langs")
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
