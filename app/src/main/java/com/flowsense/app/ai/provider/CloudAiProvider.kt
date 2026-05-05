package com.flowsense.app.ai.provider

import android.util.Log
import com.flowsense.app.data.model.CloudAiProviderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Unified cloud AI provider supporting Claude (Anthropic), Gemini (Google),
 * ChatGPT (OpenAI), and DeepSeek.
 *
 * Each provider uses its own API key and endpoint.
 */
class CloudAiProvider : AiProvider {

    companion object {
        private const val TAG = "CloudAi"
        private const val CONNECT_TIMEOUT = 15_000
        private const val READ_TIMEOUT = 90_000

        // ── Claude (Anthropic) ──────────────────────────────────
        private const val CLAUDE_API_URL = "https://api.anthropic.com/v1/messages"
        private const val CLAUDE_DEFAULT_MODEL = "claude-haiku-4-5-20251001"
        private const val CLAUDE_API_VERSION = "2023-06-01"

        // ── Gemini (Google) ─────────────────────────────────────
        private const val GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        private const val GEMINI_DEFAULT_MODEL = "gemini-2.5-flash-lite"
        // Gemini key is built-in; users do not provide their own.
        const val GEMINI_HARDCODED_API_KEY = "AIzaSyDSTbJbori-XcZPtoq3Ui7wXwIU740hing"

        // ── ChatGPT (OpenAI) ────────────────────────────────────
        private const val OPENAI_API_URL = "https://api.openai.com/v1/chat/completions"
        private const val OPENAI_DEFAULT_MODEL = "gpt-4o-mini"

        // ── DeepSeek ────────────────────────────────────────────
        private const val DEEPSEEK_API_URL = "https://api.deepseek.com/v1/chat/completions"
        private const val DEEPSEEK_DEFAULT_MODEL = "deepseek-chat"
        // DeepSeek key is built-in; users do not provide their own.
        const val DEEPSEEK_HARDCODED_API_KEY = "sk-206be502069b4591bb643e7ba9aec23f"
    }

    @Volatile var activeProviderType: CloudAiProviderType = CloudAiProviderType.CLAUDE
        private set

    private var claudeApiKey: String = ""
    private var geminiApiKey: String = GEMINI_HARDCODED_API_KEY
    private var openaiApiKey: String = ""
    private var deepseekApiKey: String = DEEPSEEK_HARDCODED_API_KEY

    private var claudeModel: String = CLAUDE_DEFAULT_MODEL
    private var geminiModel: String = GEMINI_DEFAULT_MODEL
    private var openaiModel: String = OPENAI_DEFAULT_MODEL
    private var deepseekModel: String = DEEPSEEK_DEFAULT_MODEL

    // ── Configuration ───────────────────────────────────────────

    fun setProviderType(type: CloudAiProviderType) { activeProviderType = type }

    fun updateApiKey(type: CloudAiProviderType, key: String) {
        when (type) {
            CloudAiProviderType.CLAUDE -> claudeApiKey = key
            // Gemini uses a built-in hardcoded key; ignore any user-supplied value.
            CloudAiProviderType.GEMINI -> Unit
            CloudAiProviderType.CHATGPT -> openaiApiKey = key
            // DeepSeek uses a built-in hardcoded key; ignore any user-supplied value.
            CloudAiProviderType.DEEPSEEK -> Unit
        }
    }

    /** Convenience: update the active provider's API key. */
    fun updateActiveApiKey(key: String) = updateApiKey(activeProviderType, key)

    /** Backward-compat: updates Claude API key (used by initAiProviderSelector). */
    fun updateApiKey(key: String) { claudeApiKey = key }

    fun updateModel(type: CloudAiProviderType, modelId: String) {
        when (type) {
            CloudAiProviderType.CLAUDE -> claudeModel = modelId
            CloudAiProviderType.GEMINI -> geminiModel = modelId
            CloudAiProviderType.CHATGPT -> openaiModel = modelId
            CloudAiProviderType.DEEPSEEK -> deepseekModel = modelId
        }
    }

    private fun activeApiKey(): String = when (activeProviderType) {
        CloudAiProviderType.CLAUDE -> claudeApiKey
        CloudAiProviderType.GEMINI -> geminiApiKey
        CloudAiProviderType.CHATGPT -> openaiApiKey
        CloudAiProviderType.DEEPSEEK -> deepseekApiKey
    }

    // ── AiProvider interface ────────────────────────────────────

    override suspend fun generateAnalysis(input: AnalysisInput): AnalysisResult {
        val key = activeApiKey()
        if (key.isBlank()) {
            Log.w(TAG, "No API key set for ${activeProviderType.label} — cannot call cloud AI")
            return AnalysisResult(
                text = "",
                success = false,
                providerName = displayName(),
                isLocal = false
            )
        }

        val activeModel = when (activeProviderType) {
            CloudAiProviderType.CLAUDE -> claudeModel
            CloudAiProviderType.GEMINI -> geminiModel
            CloudAiProviderType.CHATGPT -> openaiModel
            CloudAiProviderType.DEEPSEEK -> deepseekModel
        }
        Log.i(TAG, "Calling ${activeProviderType.label} API with model=$activeModel")

        val startTime = System.currentTimeMillis()

        return withContext(Dispatchers.IO) {
            try {
                val result = when (activeProviderType) {
                    CloudAiProviderType.CLAUDE -> callClaude(input.prompt, key)
                    CloudAiProviderType.GEMINI -> callGemini(input.prompt, key)
                    CloudAiProviderType.CHATGPT -> callOpenAi(input.prompt, key, OPENAI_API_URL, openaiModel, isOpenAi = true)
                    CloudAiProviderType.DEEPSEEK -> callOpenAi(input.prompt, key, DEEPSEEK_API_URL, deepseekModel, isOpenAi = false)
                }
                val latency = System.currentTimeMillis() - startTime
                if (result.isBlank()) {
                    Log.w(TAG, "${activeProviderType.label} returned empty response (model=$activeModel, latency=${latency}ms)")
                } else {
                    Log.i(TAG, "${activeProviderType.label} returned ${result.length} chars (model=$activeModel, latency=${latency}ms)")
                }
                AnalysisResult(
                    text = result.trim(),
                    success = result.isNotBlank(),
                    providerName = displayName(),
                    latencyMs = latency,
                    isLocal = false
                )
            } catch (e: ApiException) {
                val latency = System.currentTimeMillis() - startTime
                Log.e(TAG, "${activeProviderType.label} API error (model=$activeModel, HTTP ${e.httpCode}): ${e.message}", e)
                AnalysisResult(
                    text = e.userMessage,
                    success = false,
                    providerName = displayName(),
                    latencyMs = latency,
                    isLocal = false
                )
            } catch (e: Exception) {
                Log.e(TAG, "${activeProviderType.label} API call failed (model=$activeModel): ${e.message}", e)
                AnalysisResult(
                    text = "",
                    success = false,
                    providerName = displayName(),
                    latencyMs = System.currentTimeMillis() - startTime,
                    isLocal = false
                )
            }
        }
    }

    override fun isAvailable(): Boolean = activeApiKey().isNotBlank()

    override fun displayName(): String {
        val model = when (activeProviderType) {
            CloudAiProviderType.CLAUDE -> claudeModel
            CloudAiProviderType.GEMINI -> geminiModel
            CloudAiProviderType.CHATGPT -> openaiModel
            CloudAiProviderType.DEEPSEEK -> deepseekModel
        }
        return "Cloud AI (${activeProviderType.label} · $model)"
    }

    override fun description(): String = when {
        activeApiKey().isBlank() -> "${activeProviderType.label} — API key required"
        else -> "${activeProviderType.label} — cloud-powered analysis"
    }

    override fun isLocal(): Boolean = false

    // ── Exception for API errors with user-visible messages ─────

    private class ApiException(
        val httpCode: Int,
        val userMessage: String,
        cause: Throwable? = null
    ) : Exception(userMessage, cause)

    /** Extracts a short, user-friendly message from an API error body. */
    private fun extractErrorMessage(errorBody: String?, httpCode: Int, provider: String): String {
        if (errorBody.isNullOrBlank()) return "$provider API returned HTTP $httpCode"
        return try {
            val json = JSONObject(errorBody)
            // Anthropic: { "error": { "message": "..." } }
            json.optJSONObject("error")?.optString("message")
            // OpenAI / DeepSeek: { "error": { "message": "..." } }
                ?: json.optString("message", "").ifBlank { null }
                // Gemini: { "error": { "message": "..." } }
                ?: "$provider API returned HTTP $httpCode"
        } catch (_: Exception) {
            "$provider: $errorBody".take(200)
        }
    }

    // ── Provider-specific API calls ─────────────────────────────

    /**
     * Calls the Anthropic Claude Messages API.
     */
    private fun callClaude(prompt: String, apiKey: String): String {
        val conn = (URL(CLAUDE_API_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT
            readTimeout = READ_TIMEOUT
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-api-key", apiKey)
            setRequestProperty("anthropic-version", CLAUDE_API_VERSION)
        }

        val requestBody = JSONObject().apply {
            put("model", claudeModel)
            put("max_tokens", 2048)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
        }

        conn.outputStream.bufferedWriter().use { it.write(requestBody.toString()) }
        return parseClaude(conn)
    }

    private fun parseClaude(conn: HttpURLConnection): String {
        if (conn.responseCode != HttpURLConnection.HTTP_OK) {
            val err = try { conn.errorStream?.bufferedReader()?.readText() } catch (_: Exception) { null }
            val msg = extractErrorMessage(err, conn.responseCode, "Claude")
            Log.w(TAG, "Claude API error: HTTP ${conn.responseCode} — $err")
            conn.disconnect()
            throw ApiException(conn.responseCode, msg)
        }
        val body = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        val json = JSONObject(body)
        return json.optJSONArray("content")?.optJSONObject(0)?.optString("text", "") ?: ""
    }

    /**
     * Calls the Google Gemini (Generative Language) API.
     */
    private fun callGemini(prompt: String, apiKey: String): String {
        val url = "$GEMINI_API_URL/$geminiModel:generateContent?key=$apiKey"
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT
            readTimeout = READ_TIMEOUT
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }

        val requestBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("maxOutputTokens", 8192)
            })
        }

        conn.outputStream.bufferedWriter().use { it.write(requestBody.toString()) }
        return parseGemini(conn)
    }

    private fun parseGemini(conn: HttpURLConnection): String {
        if (conn.responseCode != HttpURLConnection.HTTP_OK) {
            val err = try { conn.errorStream?.bufferedReader()?.readText() } catch (_: Exception) { null }
            val msg = extractErrorMessage(err, conn.responseCode, "Gemini")
            Log.w(TAG, "Gemini API error: HTTP ${conn.responseCode} — $err")
            conn.disconnect()
            throw ApiException(conn.responseCode, msg)
        }
        val body = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        val json = JSONObject(body)
        val candidates = json.optJSONArray("candidates")
        val content = candidates?.optJSONObject(0)?.optJSONObject("content")
        val parts = content?.optJSONArray("parts")
        return parts?.optJSONObject(0)?.optString("text", "") ?: ""
    }

    /**
     * Calls an OpenAI-compatible chat completions API.
     * Works for both OpenAI (ChatGPT) and DeepSeek (same API format).
     *
     * @param isOpenAi true for OpenAI (uses max_completion_tokens), false for
     *                 DeepSeek and other OpenAI-compatible APIs (uses max_tokens).
     */
    private fun callOpenAi(prompt: String, apiKey: String, apiUrl: String, model: String, isOpenAi: Boolean = false): String {
        val conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT
            readTimeout = READ_TIMEOUT
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
        }

        val requestBody = JSONObject().apply {
            put("model", model)
            // OpenAI's newer models (gpt-4.1-*) require max_completion_tokens;
            // DeepSeek and older OpenAI models use max_tokens.
            if (isOpenAi) {
                put("max_completion_tokens", 2048)
            } else {
                put("max_tokens", 2048)
            }
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
        }

        conn.outputStream.bufferedWriter().use { it.write(requestBody.toString()) }
        return parseOpenAi(conn)
    }

    private fun parseOpenAi(conn: HttpURLConnection): String {
        if (conn.responseCode != HttpURLConnection.HTTP_OK) {
            val err = try { conn.errorStream?.bufferedReader()?.readText() } catch (_: Exception) { null }
            val msg = extractErrorMessage(err, conn.responseCode, "OpenAI")
            Log.w(TAG, "OpenAI-compat API error: HTTP ${conn.responseCode} — $err")
            conn.disconnect()
            throw ApiException(conn.responseCode, msg)
        }
        val body = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        val json = JSONObject(body)
        val choices = json.optJSONArray("choices")
        val message = choices?.optJSONObject(0)?.optJSONObject("message")
        val content = message?.optString("content", "") ?: ""
        // DeepSeek R1 / reasoner models emit <think>…</think> reasoning blocks — strip them
        return content
            .replace(Regex("<think>[\\s\\S]*?</think>"), "")
            .replace(Regex("</think>"), "") // Handle unclosed/partial tags
            .trim()
    }
}
