package com.smartexpense.tracker.ai.benchmark

import android.util.Log
import com.smartexpense.tracker.ai.provider.AiProvider
import com.smartexpense.tracker.ai.provider.AnalysisInput
import com.smartexpense.tracker.ai.provider.AnalysisType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Simple benchmarking for local AI providers.
 * Measures inference latency, approximate token generation speed, and memory estimate.
 */
class LocalAiBenchmarkRunner {

    companion object {
        private const val TAG = "AiBenchmark"

        private val TEST_PROMPTS = listOf(
            "Categorize this expense: Coffee at Starbucks $5.50. Categories: Food & Dining, Shopping, Entertainment. Reply with only the category name.",
            "Give a brief financial insight: Monthly expenses $2000, income $3500, top category Food at $600.",
            "Summarize in one sentence: This month you spent 45% on housing, 20% on food, 15% on transport."
        )
    }

    data class BenchmarkResult(
        val providerName: String,
        val averageLatencyMs: Long,
        val minLatencyMs: Long,
        val maxLatencyMs: Long,
        val approximateTokensPerSecond: Double,
        val memoryEstimateMb: Long,
        val promptsRun: Int,
        val successfulPrompts: Int,
        val isLocal: Boolean
    )

    /**
     * Runs a benchmark against the given AI provider.
     * Sends real prompts and measures actual inference latency.
     */
    suspend fun runBenchmark(provider: AiProvider): BenchmarkResult = withContext(Dispatchers.IO) {
        Log.i(TAG, "Starting benchmark for: ${provider.displayName()}")

        val runtime = Runtime.getRuntime()
        val memBefore = runtime.totalMemory() - runtime.freeMemory()

        val latencies = mutableListOf<Long>()
        val responseLengths = mutableListOf<Int>()
        var successCount = 0

        for (prompt in TEST_PROMPTS) {
            try {
                val startTime = System.nanoTime()
                val result = provider.generateAnalysis(
                    AnalysisInput(
                        prompt = prompt,
                        type = AnalysisType.INSIGHT
                    )
                )
                val elapsed = (System.nanoTime() - startTime) / 1_000_000 // ms

                latencies.add(elapsed)
                if (result.success && result.text.isNotBlank()) {
                    successCount++
                    responseLengths.add(result.text.length)
                }

                Log.d(TAG, "Prompt ${latencies.size}: ${elapsed}ms, " +
                        "response=${result.text.length} chars, success=${result.success}")
            } catch (e: Exception) {
                Log.w(TAG, "Benchmark prompt failed: ${e.message}")
                latencies.add(-1)
            }
        }

        val memAfter = runtime.totalMemory() - runtime.freeMemory()
        val memDelta = (memAfter - memBefore) / (1024 * 1024)

        val validLatencies = latencies.filter { it > 0 }
        if (validLatencies.isEmpty()) {
            return@withContext BenchmarkResult(
                providerName = provider.displayName(),
                averageLatencyMs = 0,
                minLatencyMs = 0,
                maxLatencyMs = 0,
                approximateTokensPerSecond = 0.0,
                memoryEstimateMb = memDelta.coerceAtLeast(0),
                promptsRun = TEST_PROMPTS.size,
                successfulPrompts = 0,
                isLocal = provider.isLocal()
            )
        }

        // Estimate tokens/sec: ~4 chars per token on average
        val avgResponseChars = if (responseLengths.isNotEmpty()) {
            responseLengths.average()
        } else 0.0
        val avgLatencySec = validLatencies.average() / 1000.0
        val tokensPerSec = if (avgLatencySec > 0 && avgResponseChars > 0) {
            (avgResponseChars / 4.0) / avgLatencySec
        } else 0.0

        val result = BenchmarkResult(
            providerName = provider.displayName(),
            averageLatencyMs = validLatencies.average().toLong(),
            minLatencyMs = validLatencies.min(),
            maxLatencyMs = validLatencies.max(),
            approximateTokensPerSecond = tokensPerSec,
            memoryEstimateMb = memDelta.coerceAtLeast(0),
            promptsRun = TEST_PROMPTS.size,
            successfulPrompts = successCount,
            isLocal = provider.isLocal()
        )

        Log.i(TAG, "Benchmark complete: avg=${result.averageLatencyMs}ms, " +
                "tokens/s=${String.format("%.1f", result.approximateTokensPerSecond)}, " +
                "mem=${result.memoryEstimateMb}MB")

        result
    }
}
