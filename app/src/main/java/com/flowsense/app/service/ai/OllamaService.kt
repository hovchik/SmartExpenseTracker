package com.flowsense.app.service.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * REST client for Ollama – a local LLM server for mobile and desktop.
 *
 * Ollama exposes a simple HTTP API:
 *   GET  /api/tags     → list available models
 *   POST /api/generate → text generation (non-streaming)
 *
 * Default server address is http://localhost:11434.
 * Users can also point at an Ollama instance on the local network.
 *
 * @see <a href="https://github.com/ollama/ollama/blob/main/docs/api.md">Ollama API docs</a>
 */
class OllamaService {

    companion object {
        private const val TAG = "OllamaService"
        private const val CONNECT_TIMEOUT = 5_000
        private const val READ_TIMEOUT = 60_000  // LLM generation can be slow
    }

    /** Host URL (e.g. "http://localhost:11434"). Set from AppSettings. */
    @Volatile
    var host: String = "http://localhost:11434"

    /** Currently selected model name (e.g. "llama3.2:1b"). */
    @Volatile
    var modelName: String = ""

    /** True when the server is reachable and a model is selected. */
    @Volatile
    var isReady: Boolean = false
        private set

    @Volatile
    var errorMessage: String? = null
        private set

    private val ruleEngine = AiExpenseEngine()

    // ── Connection ────────────────────────────────────────────────

    /**
     * Checks if the Ollama server is reachable.
     * Returns true if the server responds to /api/tags.
     */
    suspend fun checkConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            val conn = (URL("${host.trimEnd('/')}/api/tags").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = CONNECT_TIMEOUT
                setRequestProperty("Accept", "application/json")
            }
            val code = conn.responseCode
            conn.disconnect()
            if (code != HttpURLConnection.HTTP_OK) {
                errorMessage = "Server returned $code"
                isReady = false
            } else {
                errorMessage = null
            }
            code == HttpURLConnection.HTTP_OK
        } catch (e: Exception) {
            errorMessage = "Cannot connect: ${e.message}"
            isReady = false
            Log.w(TAG, "Connection check failed: ${e.message}")
            false
        }
    }

    // ── Model listing ─────────────────────────────────────────────

    /**
     * Data class for an Ollama model entry.
     */
    data class OllamaModel(
        val name: String,
        val size: Long,
        val parameterSize: String,
        val quantization: String
    ) {
        val sizeLabel: String get() {
            val gb = size / (1024.0 * 1024.0 * 1024.0)
            return if (gb >= 1) "${String.format("%.1f", gb)} GB"
            else "${String.format("%.0f", size / (1024.0 * 1024.0))} MB"
        }
    }

    /**
     * Lists models available on the Ollama server.
     * Returns empty list on failure.
     */
    suspend fun listModels(): List<OllamaModel> = withContext(Dispatchers.IO) {
        try {
            val conn = (URL("${host.trimEnd('/')}/api/tags").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = CONNECT_TIMEOUT
                setRequestProperty("Accept", "application/json")
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                conn.disconnect()
                return@withContext emptyList()
            }
            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            val json = JSONObject(body)
            val models = json.optJSONArray("models") ?: return@withContext emptyList()
            val result = mutableListOf<OllamaModel>()

            for (i in 0 until models.length()) {
                val m = models.getJSONObject(i)
                val name = m.optString("name", "")
                val size = m.optLong("size", 0L)
                val details = m.optJSONObject("details")
                val paramSize = details?.optString("parameter_size", "") ?: ""
                val quant = details?.optString("quantization_level", "") ?: ""
                if (name.isNotEmpty()) {
                    result.add(OllamaModel(name, size, paramSize, quant))
                }
            }
            result
        } catch (e: Exception) {
            Log.w(TAG, "listModels failed: ${e.message}")
            emptyList()
        }
    }

    /**
     * Selects a model and marks the service as ready.
     */
    fun selectModel(name: String) {
        modelName = name
        isReady = name.isNotEmpty()
        if (isReady) errorMessage = null
    }

    // ── Inference ─────────────────────────────────────────────────

    /**
     * Sends a prompt to Ollama and returns the generated response.
     * Uses non-streaming mode for simplicity.
     */
    suspend fun generateResponse(prompt: String): String? = withContext(Dispatchers.IO) {
        if (modelName.isEmpty()) return@withContext null
        var conn: HttpURLConnection? = null
        try {
            conn = (URL("${host.trimEnd('/')}/api/generate").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }

            val requestBody = JSONObject().apply {
                put("model", modelName)
                put("prompt", prompt)
                put("stream", false)
                put("options", JSONObject().apply {
                    put("temperature", 0.3)
                    put("top_k", 40)
                    put("num_predict", 256)
                })
            }

            conn.outputStream.bufferedWriter().use { it.write(requestBody.toString()) }

            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                val errBody = try { conn.errorStream?.bufferedReader()?.readText() } catch (_: Exception) { null }
                Log.w(TAG, "Generate failed: HTTP ${conn.responseCode} — $errBody")
                return@withContext null
            }

            val body = conn.inputStream.bufferedReader().readText()

            val json = JSONObject(body)
            val raw = json.optString("response", "").trim()
            // DeepSeek R1 models emit <think>…</think> reasoning blocks — strip them
            raw.replace(Regex("<think>[\\s\\S]*?</think>"), "")
                .replace(Regex("</think>"), "") // Handle unclosed/partial tags
                .trim()
                .takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.e(TAG, "generateResponse failed: ${e.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Categorizes a transaction using the Ollama LLM.
     * Falls back to the rule engine if the LLM fails.
     */
    suspend fun categorize(
        description: String,
        availableCategories: List<String>,
        isExpense: Boolean = true,
        userCategoryNames: List<String> = emptyList()
    ): String {
        if (!isReady) return ruleEngine.categorize(description, isExpense, userCategoryNames)

        val categoriesList = availableCategories.joinToString(", ")
        val prompt = """Categorize this transaction into exactly one category.
Transaction: "$description"
Type: ${if (isExpense) "expense" else "income"}
Available categories: $categoriesList
Reply with ONLY the category name, nothing else."""

        val response = generateResponse(prompt)
        if (response != null) {
            val cleaned = response.trim().removeSurrounding("\"")
            val exactMatch = availableCategories.find { it.equals(cleaned, ignoreCase = true) }
            if (exactMatch != null) return exactMatch
            val containsMatch = availableCategories.find { cleaned.contains(it, ignoreCase = true) }
            if (containsMatch != null) return containsMatch
        }

        return ruleEngine.categorize(description, isExpense, userCategoryNames)
    }

    /**
     * Generates a financial insight using the Ollama LLM.
     */
    suspend fun generateInsight(
        totalExpenses: Double,
        totalIncome: Double,
        topCategory: String?,
        topCategoryAmount: Double,
        transactionCount: Int,
        currencyCode: String = "USD"
    ): String? {
        if (!isReady) return null

        val prompt = """You are a personal finance advisor. Generate a brief, actionable insight (1-2 sentences) based on:
- Total expenses: $currencyCode ${String.format("%.2f", totalExpenses)}
- Total income: $currencyCode ${String.format("%.2f", totalIncome)}
- Top spending category: ${topCategory ?: "N/A"} ($currencyCode ${String.format("%.2f", topCategoryAmount)})
- Transaction count: $transactionCount
Keep it concise and helpful."""

        return generateResponse(prompt)
    }

    fun statusMessage(): String = when {
        isReady -> "Ollama active \u2014 $modelName"
        errorMessage != null -> "Ollama: $errorMessage"
        else -> "Ollama \u2014 not connected"
    }
}
