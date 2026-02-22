package com.smartexpense.tracker.data.repository

import com.smartexpense.tracker.data.json.JsonStorageManager
import com.smartexpense.tracker.data.model.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/**
 * Single source of truth for all app data.
 * Wraps JsonStorageManager with reactive flows and business logic.
 */
class ExpenseRepository(private val storage: JsonStorageManager) {

    private val _appData = MutableStateFlow(AppData())
    val appData: StateFlow<AppData> = _appData.asStateFlow()

    private val _initialized = CompletableDeferred<Unit>()

    suspend fun initialize() {
        val data = storage.loadData()
        _appData.value = data
        _initialized.complete(Unit)
    }

    /**
     * Suspends until [initialize] has completed, up to [timeoutMs].
     * Background components (BroadcastReceivers, NotificationListenerService)
     * must call this before reading [appData] to avoid racing with init.
     */
    suspend fun awaitInitialization(timeoutMs: Long = 10_000): Boolean {
        return withTimeoutOrNull(timeoutMs) { _initialized.await() } != null
    }

    // ─── Transactions ──────────────────────────────────────────────

    /**
     * Adds a transaction after checking for duplicates.
     * Returns `true` if the transaction was saved, `false` if it was a duplicate.
     *
     * Dedup rules (by source):
     *  - MANUAL / OCR_SCAN  : same amount + same description within 30 s
     *  - SMS / NOTIFICATION  : same amount + same merchant/description within 2 min,
     *                          OR same amount + same card last-4 within 10 min
     *  - IMPORT              : never considered a duplicate (imports are intentional)
     */
    suspend fun addTransaction(transaction: Transaction): Boolean {
        val current = _appData.value
        val isDuplicate = current.transactions.any { t ->
            if (t.amount != transaction.amount) return@any false
            val timeDiff = kotlin.math.abs(t.timestamp - transaction.timestamp)
            when (transaction.source) {
                TransactionSource.MANUAL, TransactionSource.OCR_SCAN -> {
                    t.source == transaction.source &&
                        t.description.equals(transaction.description, ignoreCase = true) &&
                        timeDiff < 30_000
                }
                TransactionSource.SMS, TransactionSource.NOTIFICATION -> {
                    // Strong match: same card last-4 digits within 10 minutes
                    val cardRegex = Regex("""card:(\d{4})""")
                    val newCard = cardRegex.find(transaction.notes)?.groupValues?.get(1)
                    val existingCard = cardRegex.find(t.notes)?.groupValues?.get(1)
                    if (!newCard.isNullOrEmpty() && newCard == existingCard && timeDiff < 600_000) {
                        true
                    } else {
                        // Weak match: same amount + similar description within 2 minutes
                        (t.source == TransactionSource.SMS || t.source == TransactionSource.NOTIFICATION) &&
                            timeDiff < 120_000 &&
                            (t.description.equals(transaction.description, ignoreCase = true) ||
                                (t.merchantName.isNotEmpty() && t.merchantName.equals(transaction.merchantName, ignoreCase = true)))
                    }
                }
                TransactionSource.IMPORT -> false
            }
        }
        if (isDuplicate) return false
        val updated = current.copy(
            transactions = current.transactions + transaction
        )
        _appData.value = updated
        storage.saveData(updated)
        return true
    }

    suspend fun updateTransaction(transaction: Transaction) {
        val current = _appData.value
        val updated = current.copy(
            transactions = current.transactions.map {
                if (it.id == transaction.id) transaction else it
            }
        )
        _appData.value = updated
        storage.saveData(updated)
    }

    suspend fun deleteTransaction(id: String) {
        val current = _appData.value
        val updated = current.copy(
            transactions = current.transactions.filter { it.id != id }
        )
        _appData.value = updated
        storage.saveData(updated)
    }

    fun getTransactionsByDateRange(start: Long, end: Long): List<Transaction> {
        return _appData.value.transactions.filter {
            it.timestamp in start..end
        }.sortedByDescending { it.timestamp }
    }

    fun getTransactionsByCategory(category: String): List<Transaction> {
        return _appData.value.transactions.filter {
            it.category == category
        }.sortedByDescending { it.timestamp }
    }

    fun getTransactionsByType(type: TransactionType): List<Transaction> {
        return _appData.value.transactions.filter {
            it.type == type
        }.sortedByDescending { it.timestamp }
    }

    // ─── Categories ────────────────────────────────────────────────

    suspend fun addCategory(category: Category) {
        val current = _appData.value
        val updated = current.copy(
            categories = current.categories + category
        )
        _appData.value = updated
        storage.saveData(updated)
    }

    suspend fun deleteCategory(id: String) {
        val current = _appData.value
        val updated = current.copy(
            categories = current.categories.filter { it.id != id }
        )
        _appData.value = updated
        storage.saveData(updated)
    }

    // ─── Budgets ───────────────────────────────────────────────────

    suspend fun setBudget(budget: Budget) {
        val current = _appData.value
        val existing = current.budgets.find { it.categoryId == budget.categoryId }
        val updatedBudgets = if (existing != null) {
            current.budgets.map { if (it.categoryId == budget.categoryId) budget else it }
        } else {
            current.budgets + budget
        }
        val updated = current.copy(budgets = updatedBudgets)
        _appData.value = updated
        storage.saveData(updated)
    }

    // ─── Suggestions ───────────────────────────────────────────────

    suspend fun addSuggestions(suggestions: List<AiSuggestion>) {
        val current = _appData.value
        val updated = current.copy(
            suggestions = suggestions + current.suggestions.filter { it.isDismissed }
        )
        _appData.value = updated
        storage.saveData(updated)
    }

    suspend fun dismissSuggestion(id: String) {
        val current = _appData.value
        val updated = current.copy(
            suggestions = current.suggestions.map {
                if (it.id == id) it.copy(isDismissed = true) else it
            }
        )
        _appData.value = updated
        storage.saveData(updated)
    }

    // ─── Store Locations ─────────────────────────────────────────

    suspend fun addStoreLocation(storeLocation: StoreLocation) {
        val current = _appData.value
        val updated = current.copy(
            storeLocations = current.storeLocations + storeLocation
        )
        _appData.value = updated
        storage.saveData(updated)
    }

    suspend fun deleteStoreLocation(id: String) {
        val current = _appData.value
        val updated = current.copy(
            storeLocations = current.storeLocations.filter { it.id != id }
        )
        _appData.value = updated
        storage.saveData(updated)
    }

    suspend fun updateStoreLocation(updated: StoreLocation) {
        val current = _appData.value
        val newData = current.copy(
            storeLocations = current.storeLocations.map {
                if (it.id == updated.id) updated else it
            }
        )
        _appData.value = newData
        storage.saveData(newData)
    }

    /**
     * Multiplies every transaction amount and budget limit by [rate].
     * Used when the user switches the app currency.
     */
    suspend fun convertAmounts(rate: Double) {
        val current = _appData.value
        val updated = current.copy(
            transactions = current.transactions.map { it.copy(amount = it.amount * rate) },
            budgets = current.budgets.map { it.copy(monthlyLimit = it.monthlyLimit * rate) }
        )
        _appData.value = updated
        storage.saveData(updated)
    }

    // ─── Settings ──────────────────────────────────────────────────

    suspend fun updateSettings(settings: AppSettings) {
        val current = _appData.value
        val updated = current.copy(settings = settings)
        _appData.value = updated
        storage.saveData(updated)
    }

    // ─── In-App Notifications ──────────────────────────────────────

    suspend fun addInAppNotification(notification: com.smartexpense.tracker.data.model.InAppNotification) {
        val current = _appData.value
        // Keep at most 100 notifications to avoid unbounded growth
        val trimmed = (listOf(notification) + current.inAppNotifications).take(100)
        val updated = current.copy(inAppNotifications = trimmed)
        _appData.value = updated
        storage.saveData(updated)
    }

    suspend fun deleteNotification(id: String) {
        val current = _appData.value
        val updated = current.copy(
            inAppNotifications = current.inAppNotifications.filter { it.id != id }
        )
        _appData.value = updated
        storage.saveData(updated)
    }

    suspend fun markNotificationRead(id: String) {
        val current = _appData.value
        val updated = current.copy(
            inAppNotifications = current.inAppNotifications.map {
                if (it.id == id) it.copy(isRead = true) else it
            }
        )
        _appData.value = updated
        storage.saveData(updated)
    }

    suspend fun markAllNotificationsRead() {
        val current = _appData.value
        val updated = current.copy(
            inAppNotifications = current.inAppNotifications.map { it.copy(isRead = true) }
        )
        _appData.value = updated
        storage.saveData(updated)
    }

    suspend fun clearNotifications() {
        val current = _appData.value
        val updated = current.copy(inAppNotifications = emptyList())
        _appData.value = updated
        storage.saveData(updated)
    }

    /**
     * If [categoryName] is not already in the category list, creates it automatically
     * and posts an in-app notification informing the user.
     * Returns true if a new category was created.
     */
    suspend fun ensureCategoryExists(categoryName: String): Boolean {
        val current = _appData.value
        if (current.categories.any { it.name.equals(categoryName, ignoreCase = true) }) return false
        // Create the new category
        val newCategory = com.smartexpense.tracker.data.model.Category(
            name = categoryName,
            icon = "category",
            color = 0xFF9E9E9E,
            isDefault = false
        )
        val withCategory = current.copy(categories = current.categories + newCategory)
        _appData.value = withCategory
        storage.saveData(withCategory)
        // Post a notification about the new category
        val notification = com.smartexpense.tracker.data.model.InAppNotification(
            title = "New category added",
            message = "\"$categoryName\" was automatically created based on your transaction. You can rename or remove it in Settings.",
            type = com.smartexpense.tracker.data.model.InAppNotificationType.CATEGORY_CREATED,
            suggestedCategoryName = categoryName
        )
        addInAppNotification(notification)
        return true
    }

    // ─── Rate History ─────────────────────────────────────────────

    /**
     * Stores a rate snapshot. Keeps only current (index 0) and previous (index 1).
     */
    suspend fun addRateHistoryEntry(entry: com.smartexpense.tracker.data.model.RateHistoryEntry) {
        val current = _appData.value
        val updated = current.copy(
            rateHistory = (listOf(entry) + current.rateHistory).take(2)
        )
        _appData.value = updated
        storage.saveData(updated)
    }

    // ─── Export/Import ─────────────────────────────────────────────

    suspend fun exportData(): String = storage.exportData()

    suspend fun importData(json: String): Result<AppData> {
        val result = storage.importData(json)
        result.onSuccess { _appData.value = it }
        return result
    }

    suspend fun clearAllData() {
        val current = _appData.value
        // Reset currency to the device's country default
        val localeCurrency = try {
            java.util.Currency.getInstance(Locale.getDefault()).currencyCode
        } catch (_: Exception) { null }
        val info = localeCurrency?.let { code ->
            SUPPORTED_CURRENCIES.firstOrNull { it.code == code }
        }
        val resetSettings = if (info != null) {
            current.settings.copy(currencyCode = info.code, currency = info.symbol)
        } else {
            current.settings
        }
        // Preserve user-configured settings (banking apps, scan keywords, theme,
        // etc.) and custom categories across data clears.
        val preserved = AppData(
            settings = resetSettings,
            categories = current.categories
        )
        storage.clearData()
        _appData.value = preserved
        storage.saveData(preserved)
    }
}
