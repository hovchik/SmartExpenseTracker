package com.flowsense.app

import android.app.Application
import com.flowsense.app.ai.provider.AiProviderSelector
import com.flowsense.app.ai.provider.AnalysisInput
import com.flowsense.app.ai.provider.AnalysisType
import com.flowsense.app.ai.provider.PromptAdapter
import com.flowsense.app.data.json.JsonStorageManager
import com.flowsense.app.data.repository.ExpenseRepository
import com.flowsense.app.service.ai.AiExpenseEngine
import com.flowsense.app.service.notification.ExpenseNotificationHelper
import com.flowsense.app.service.scheduler.SalarySchedulerWorker
import com.flowsense.app.service.scheduler.ScheduledExpenseWorker
import com.flowsense.app.service.subscription.SubscriptionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Application class that provides a shared [ExpenseRepository]
 * so that BroadcastReceivers, NotificationListenerService, and
 * the ViewModel all read/write to the same data store.
 */
class FlowSenseApp : Application() {

    lateinit var repository: ExpenseRepository
        private set

    lateinit var subscriptionManager: SubscriptionManager
        private set

    /** Shared AI provider selector — used by background receivers (SMS, notifications)
     *  to categorize transactions with the Gemma model instead of rule-based only.
     *  Initialized lazily by the ViewModel's initAiProviderSelector(). */
    @Volatile
    var aiProviderSelector: AiProviderSelector? = null

    private val promptAdapter = PromptAdapter()
    private val ruleEngine = AiExpenseEngine()

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        subscriptionManager = SubscriptionManager(this)
        val storage = JsonStorageManager(this)
        repository = ExpenseRepository(storage)
        appScope.launch {
            repository.initialize()
            // Re-enqueue salary scheduler if it was enabled before (survives reinstalls/reboots)
            val settings = repository.appData.value.settings
            if (settings.scheduledSalaryEnabled && settings.scheduledSalaryAmount > 0) {
                SalarySchedulerWorker.schedule(applicationContext)
            }
            if (settings.scheduledExpenses.any { it.enabled }) {
                ScheduledExpenseWorker.schedule(applicationContext)
            }
        }
        // Create notification channels (no-op if already created)
        ExpenseNotificationHelper.createChannels(this)
    }

    /**
     * AI-powered categorization for background receivers (SMS, notifications, inbox scanner).
     * Uses the active AI provider (e.g. Gemma) when available, falls back to rule-based.
     */
    suspend fun aiCategorize(
        description: String,
        categories: List<String>,
        isExpense: Boolean = true,
        userCategoryNames: List<String> = emptyList()
    ): String {
        val provider = aiProviderSelector
        if (provider != null && provider.getActiveProvider().isAvailable()) {
            try {
                val prompt = promptAdapter.createCategorizationPrompt(
                    description, categories, isExpense
                )
                val result = withContext(Dispatchers.IO) {
                    provider.getActiveProvider().generateAnalysis(
                        AnalysisInput(
                            prompt = prompt,
                            availableCategories = categories,
                            isExpense = isExpense,
                            type = AnalysisType.CATEGORIZE
                        )
                    )
                }
                if (result.success && result.text.isNotBlank()) {
                    val parsed = promptAdapter.parseCategorization(result.text, categories)
                    if (parsed.category != null) return parsed.category
                }
            } catch (e: Exception) {
                android.util.Log.w("FlowSenseApp", "AI categorize failed: ${e.message}")
            }
        }
        return ruleEngine.categorize(description, isExpense, userCategoryNames)
    }

    companion object {
        lateinit var instance: FlowSenseApp
            private set
    }
}
