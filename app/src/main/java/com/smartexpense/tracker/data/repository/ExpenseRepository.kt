package com.smartexpense.tracker.data.repository

import com.smartexpense.tracker.data.json.JsonStorageManager
import com.smartexpense.tracker.data.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for all app data.
 * Wraps JsonStorageManager with reactive flows and business logic.
 */
class ExpenseRepository(private val storage: JsonStorageManager) {

    private val _appData = MutableStateFlow(AppData())
    val appData: StateFlow<AppData> = _appData.asStateFlow()

    suspend fun initialize() {
        val data = storage.loadData()
        _appData.value = data
    }

    // ─── Transactions ──────────────────────────────────────────────

    suspend fun addTransaction(transaction: Transaction) {
        val current = _appData.value
        val updated = current.copy(
            transactions = current.transactions + transaction
        )
        _appData.value = updated
        storage.saveData(updated)
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

    // ─── Export/Import ─────────────────────────────────────────────

    suspend fun exportData(): String = storage.exportData()

    suspend fun importData(json: String): Result<AppData> {
        val result = storage.importData(json)
        result.onSuccess { _appData.value = it }
        return result
    }

    suspend fun clearAllData() {
        storage.clearData()
        _appData.value = AppData()
    }
}
