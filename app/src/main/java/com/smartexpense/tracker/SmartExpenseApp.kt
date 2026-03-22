package com.smartexpense.tracker

import android.app.Application
import com.smartexpense.tracker.ai.provider.AiProviderSelector
import com.smartexpense.tracker.ai.provider.AnalysisInput
import com.smartexpense.tracker.ai.provider.AnalysisType
import com.smartexpense.tracker.ai.provider.PromptAdapter
import com.smartexpense.tracker.data.json.JsonStorageManager
import com.smartexpense.tracker.data.repository.ExpenseRepository
import com.smartexpense.tracker.service.ai.AiExpenseEngine
import com.smartexpense.tracker.service.notification.ExpenseNotificationHelper
import com.smartexpense.tracker.service.scheduler.SalarySchedulerWorker
import com.smartexpense.tracker.service.scheduler.ScheduledExpenseWorker
import com.smartexpense.tracker.service.subscription.SubscriptionManager
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
class SmartExpenseApp : Application() {

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
                android.util.Log.w("SmartExpenseApp", "AI categorize failed: ${e.message}")
            }
        }
        return ruleEngine.categorize(description, isExpense, userCategoryNames)
    }

    companion object {
        lateinit var instance: SmartExpenseApp
            private set
    }
}
