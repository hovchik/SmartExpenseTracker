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
         *
         * IMPORTANT: Loading too many languages at once makes Tesseract slow and
         * error-prone. We download the Armenian + English core set, plus optional
         * supplementary languages. Recognition runs in focused passes:
         *  - Pass 1: hye+eng (Armenian + English — for Armenian POS receipts)
         *  - Pass 2: rus (Russian — for Cyrillic-heavy receipts)
         * ML Kit handles Chinese, Japanese, Korean, Devanagari natively.
         */
        private val LANG_FILES = mapOf(
            "hye" to "https://github.com/tesseract-ocr/tessdata_fast/raw/main/hye.traineddata",
            "eng" to "https://github.com/tesseract-ocr/tessdata_fast/raw/main/eng.traineddata",
            "rus" to "https://github.com/tesseract-ocr/tessdata_fast/raw/main/rus.traineddata",
            "kat" to "https://github.com/tesseract-ocr/tessdata_fast/raw/main/kat.traineddata",
            "ara" to "https://github.com/tesseract-ocr/tessdata_fast/raw/main/ara.traineddata",
            "tur" to "https://github.com/tesseract-ocr/tessdata_fast/raw/main/tur.traineddata",
            "fra" to "https://github.com/tesseract-ocr/tessdata_fast/raw/main/fra.traineddata",
            "deu" to "https://github.com/tesseract-ocr/tessdata_fast/raw/main/deu.traineddata",
            "spa" to "https://github.com/tesseract-ocr/tessdata_fast/raw/main/spa.traineddata"
        )

        /** The core set always downloaded on first use. */
        private val CORE_LANGS = setOf("hye", "eng", "rus")
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
     * Runs Tesseract OCR on the given bitmap.
     *
     * Uses focused language passes for reliability:
     * 1. Armenian + English (hye+eng) — primary pass for Armenian POS receipts
     * 2. Russian (rus) — secondary pass for Cyrillic text
     *
     * Loading fewer languages per pass makes Tesseract faster and more stable.
     * Results from both passes are merged (deduped by line).
     */
    suspend fun recognizeText(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        if (!isReady()) {
            Log.w(TAG, "Trained data not ready, attempting download")
            if (!ensureTrainedData()) {
                Log.e(TAG, "Cannot download trained data")
                return@withContext ""
            }
        }

        val results = mutableListOf<String>()

        // Pass 1: Armenian + English (primary)
        val pass1 = runTesseractPass(bitmap, "hye+eng")
        if (pass1.isNotBlank()) results.add(pass1)

        // Pass 2: Russian (if trained data available)
        val tessDir = File(dataPath, TESSDATA_DIR)
        if (File(tessDir, "rus.traineddata").exists()) {
            val pass2 = runTesseractPass(bitmap, "rus")
            if (pass2.isNotBlank()) results.add(pass2)
        }

        // Merge results, dedup by line
        if (results.isEmpty()) return@withContext ""
        val seenLines = mutableSetOf<String>()
        val merged = mutableListOf<String>()
        for (text in results) {
            for (line in text.lines()) {
                val trimmed = line.trim()
                if (trimmed.isNotEmpty() && seenLines.add(trimmed.lowercase())) {
                    merged.add(trimmed)
                }
            }
        }
        Log.d(TAG, "Tesseract merged: ${merged.size} lines from ${results.size} passes")
        merged.joinToString("\n")
    }

    /**
     * Single Tesseract recognition pass with specified language(s).
     */
    private fun runTesseractPass(bitmap: Bitmap, langs: String): String {
        Log.d(TAG, "Running Tesseract pass: $langs")
        val tess = TessBaseAPI()
        return try {
            val initOk = tess.init(dataPath, langs)
            if (!initOk) {
                Log.e(TAG, "Tesseract init failed for $langs")
                return ""
            }

            tess.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
            tess.setImage(bitmap)

            val result = tess.utF8Text ?: ""
            val confidence = tess.meanConfidence()
            Log.d(TAG, "Tesseract $langs: ${result.length} chars, confidence=$confidence")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Tesseract $langs pass failed", e)
            ""
        } finally {
            tess.end()
        }
    }
}
