package com.flowsense.app.service.ocr

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
 * Multi-language on-device OCR using Tesseract4Android (Tesseract 5.x).
 *
 * Supplements ML Kit by supporting scripts ML Kit cannot handle (Armenian)
 * and providing a Tesseract-based fallback for Russian and other scripts.
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
         *
         * Recognition runs in focused passes for reliability:
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
     * Core languages are downloaded first; supplementary ones follow.
     * Returns `true` if at least Armenian is available.
     */
    suspend fun ensureTrainedData(): Boolean = withContext(Dispatchers.IO) {
        val tessDir = File(dataPath, TESSDATA_DIR)
        if (!tessDir.exists()) tessDir.mkdirs()

        // Download core languages first
        for (lang in CORE_LANGS) {
            val url = LANG_FILES[lang] ?: continue
            if (!downloadIfMissing(tessDir, lang, url)) {
                Log.e(TAG, "Failed to download core language: $lang")
            }
        }

        // Download supplementary languages (failures are OK)
        for ((lang, url) in LANG_FILES) {
            if (lang in CORE_LANGS) continue
            downloadIfMissing(tessDir, lang, url)
        }

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
     * Uses Tesseract4Android (Tesseract 5.x) API.
     */
    private fun runTesseractPass(bitmap: Bitmap, langs: String): String {
        Log.d(TAG, "Running Tesseract pass: $langs")
        val tess = TessBaseAPI()
        return try {
            // Tesseract4Android init() throws on failure instead of returning boolean
            try {
                tess.init(dataPath, langs)
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Tesseract init failed for $langs: ${e.message}")
                return ""
            }

            // Page segmentation: fully automatic, no OSD
            tess.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
            tess.setImage(bitmap)

            // Tesseract4Android uses getUTF8Text()
            val result = tess.utF8Text ?: ""
            val confidence = tess.meanConfidence()
            Log.d(TAG, "Tesseract $langs: ${result.length} chars, confidence=$confidence")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Tesseract $langs pass failed", e)
            ""
        } finally {
            // Tesseract4Android uses recycle() to free native resources
            try { tess.recycle() } catch (_: Exception) {}
        }
    }
}
