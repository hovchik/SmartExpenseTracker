package com.smartexpense.tracker

import android.app.Application
import com.smartexpense.tracker.data.json.JsonStorageManager
import com.smartexpense.tracker.data.repository.ExpenseRepository
import com.smartexpense.tracker.service.notification.ExpenseNotificationHelper
import com.smartexpense.tracker.service.scheduler.SalarySchedulerWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application class that provides a shared [ExpenseRepository]
 * so that BroadcastReceivers, NotificationListenerService, and
 * the ViewModel all read/write to the same data store.
 */
class SmartExpenseApp : Application() {

    lateinit var repository: ExpenseRepository
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        val storage = JsonStorageManager(this)
        repository = ExpenseRepository(storage)
        appScope.launch {
            repository.initialize()
            // Re-enqueue salary scheduler if it was enabled before (survives reinstalls/reboots)
            val settings = repository.appData.value.settings
            if (settings.scheduledSalaryEnabled && settings.scheduledSalaryAmount > 0) {
                SalarySchedulerWorker.schedule(applicationContext)
            }
        }
        // Create notification channels (no-op if already created)
        ExpenseNotificationHelper.createChannels(this)
    }

    companion object {
        lateinit var instance: SmartExpenseApp
            private set
    }
}
