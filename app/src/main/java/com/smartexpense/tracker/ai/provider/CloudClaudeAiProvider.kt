package com.smartexpense.tracker.ai.provider

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cloud AI provider using the Claude API.
 * Sends prompts to Anthropic's Claude API for high-quality analysis.
 */
class CloudClaudeAiProvider(
    private var apiKey: String = "",
    private var model: String = DEFAULT_MODEL
) : AiProvider {

    companion object {
        private const val TAG = "CloudClaudeAi"
        private const val API_URL = "https://api.anthropic.com/v1/messages"
        private const val DEFAULT_MODEL = "claude-haiku-4-5-20251001"
        private const val CONNECT_TIMEOUT = 15_000
        private const val READ_TIMEOUT = 60_000
        private const val API_VERSION = "2023-06-01"
    }

    fun updateApiKey(key: String) {
        apiKey = key
    }

    fun updateModel(modelId: String) {
        model = modelId
    }

    override suspend fun generateAnalysis(input: AnalysisInput): AnalysisResult {
        if (apiKey.isBlank()) {
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
                val conn = (URL(API_URL).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = CONNECT_TIMEOUT
                    readTimeout = READ_TIMEOUT
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("x-api-key", apiKey)
                    setRequestProperty("anthropic-version", API_VERSION)
                }

                val requestBody = JSONObject().apply {
                    put("model", model)
                    put("max_tokens", 512)
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", input.prompt)
                        })
                    })
                }

                conn.outputStream.bufferedWriter().use { it.write(requestBody.toString()) }

                val latency = System.currentTimeMillis() - startTime

                if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                    val errBody = try {
                        conn.errorStream?.bufferedReader()?.readText()
                    } catch (_: Exception) { null }
                    Log.w(TAG, "Claude API error: HTTP ${conn.responseCode} — $errBody")
                    conn.disconnect()
                    return@withContext AnalysisResult(
                        text = "",
                        success = false,
                        providerName = displayName(),
                        latencyMs = latency,
                        isLocal = false
                    )
                }

                val body = conn.inputStream.bufferedReader().readText()
                conn.disconnect()

                val json = JSONObject(body)
                val content = json.optJSONArray("content")
                val text = content?.optJSONObject(0)?.optString("text", "") ?: ""

                AnalysisResult(
                    text = text.trim(),
                    success = text.isNotBlank(),
                    providerName = displayName(),
                    latencyMs = latency,
                    isLocal = false
                )
            } catch (e: Exception) {
                Log.e(TAG, "Claude API call failed: ${e.message}")
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

    override fun isAvailable(): Boolean = apiKey.isNotBlank()

    override fun displayName(): String = "Cloud AI (Claude)"

    override fun description(): String = when {
        apiKey.isBlank() -> "API key required"
        else -> "Claude $model — cloud-powered analysis"
    }

    override fun isLocal(): Boolean = false
}
