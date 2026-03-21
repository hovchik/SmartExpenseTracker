package com.smartexpense.tracker.ai.provider

import android.util.Log
import com.smartexpense.tracker.data.model.CloudAiProviderType
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
        private const val GEMINI_DEFAULT_MODEL = "gemini-2.0-flash"

        // ── ChatGPT (OpenAI) ────────────────────────────────────
        private const val OPENAI_API_URL = "https://api.openai.com/v1/chat/completions"
        private const val OPENAI_DEFAULT_MODEL = "gpt-4o-mini"

        // ── DeepSeek ────────────────────────────────────────────
        private const val DEEPSEEK_API_URL = "https://api.deepseek.com/v1/chat/completions"
        private const val DEEPSEEK_DEFAULT_MODEL = "deepseek-chat"
    }

    @Volatile var activeProviderType: CloudAiProviderType = CloudAiProviderType.CLAUDE
        private set

    private var claudeApiKey: String = ""
    private var geminiApiKey: String = ""
    private var openaiApiKey: String = ""
    private var deepseekApiKey: String = ""

    private var claudeModel: String = CLAUDE_DEFAULT_MODEL
    private var geminiModel: String = GEMINI_DEFAULT_MODEL
    private var openaiModel: String = OPENAI_DEFAULT_MODEL
    private var deepseekModel: String = DEEPSEEK_DEFAULT_MODEL

    // ── Configuration ───────────────────────────────────────────

    fun setProviderType(type: CloudAiProviderType) { activeProviderType = type }

    fun updateApiKey(type: CloudAiProviderType, key: String) {
        when (type) {
            CloudAiProviderType.CLAUDE -> claudeApiKey = key
            CloudAiProviderType.GEMINI -> geminiApiKey = key
            CloudAiProviderType.CHATGPT -> openaiApiKey = key
            CloudAiProviderType.DEEPSEEK -> deepseekApiKey = key
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
            return AnalysisResult(
                text = "",
                success = false,
                providerName = displayName(),
                isLocal = false
            )
        }

        val startTime = System.currentTimeMillis()

        return withContext(Dispatchers.IO) {
            try {
                val result = when (activeProviderType) {
                    CloudAiProviderType.CLAUDE -> callClaude(input.prompt, key)
                    CloudAiProviderType.GEMINI -> callGemini(input.prompt, key)
                    CloudAiProviderType.CHATGPT -> callOpenAi(input.prompt, key, OPENAI_API_URL, openaiModel)
                    CloudAiProviderType.DEEPSEEK -> callOpenAi(input.prompt, key, DEEPSEEK_API_URL, deepseekModel)
                }
                val latency = System.currentTimeMillis() - startTime
                AnalysisResult(
                    text = result.trim(),
                    success = result.isNotBlank(),
                    providerName = displayName(),
                    latencyMs = latency,
                    isLocal = false
                )
            } catch (e: Exception) {
                Log.e(TAG, "${activeProviderType.label} API call failed: ${e.message}")
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
            Log.w(TAG, "Claude API error: HTTP ${conn.responseCode} — $err")
            conn.disconnect()
            return ""
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
                put("maxOutputTokens", 2048)
            })
        }

        conn.outputStream.bufferedWriter().use { it.write(requestBody.toString()) }
        return parseGemini(conn)
    }

    private fun parseGemini(conn: HttpURLConnection): String {
        if (conn.responseCode != HttpURLConnection.HTTP_OK) {
            val err = try { conn.errorStream?.bufferedReader()?.readText() } catch (_: Exception) { null }
            Log.w(TAG, "Gemini API error: HTTP ${conn.responseCode} — $err")
            conn.disconnect()
            return ""
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
     */
    private fun callOpenAi(prompt: String, apiKey: String, apiUrl: String, model: String): String {
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
            put("max_tokens", 2048)
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
            Log.w(TAG, "OpenAI-compat API error: HTTP ${conn.responseCode} — $err")
            conn.disconnect()
            return ""
        }
        val body = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        val json = JSONObject(body)
        val choices = json.optJSONArray("choices")
        val message = choices?.optJSONObject(0)?.optJSONObject("message")
        return message?.optString("content", "") ?: ""
    }
}
