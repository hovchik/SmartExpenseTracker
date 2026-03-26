package com.flowsense.app.data.repository

import com.flowsense.app.data.json.JsonStorageManager
import com.flowsense.app.data.model.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/**
 * Single source of truth for all app data.
 * Wraps JsonStorageManager with reactive flows and business logic.
 */
class ExpenseRepository(private val storage: JsonStorageManager) {

    private val _appData = MutableStateFlow(AppData())
    val appData: StateFlow<AppData> = _appData.asStateFlow()

    /** Protects addTransaction from race conditions during duplicate checking. */
    private val transactionMutex = Mutex()

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
    suspend fun addTransaction(transaction: Transaction): Boolean = transactionMutex.withLock {
        val current = _appData.value
        val isDuplicate = current.transactions.any { t ->
            if (t.amount != transaction.amount) return@any false
            val timeDiff = kotlin.math.abs(t.timestamp - transaction.timestamp)
            when (transaction.source) {
                TransactionSource.MANUAL, TransactionSource.OCR_SCAN, TransactionSource.VOICE -> {
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
        if (isDuplicate) return@withLock false
        val updated = current.copy(
            transactions = current.transactions + transaction
        )
        _appData.value = updated
        storage.saveData(updated)
        return@withLock true
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

    /**
     * Soft-deletes a transaction by setting its [deletedAt] timestamp.
     * The transaction remains in storage for [AppSettings.trashRetentionDays] days.
     * Returns the deleted transaction for undo support.
     */
    suspend fun deleteTransaction(id: String): Transaction? {
        val current = _appData.value
        val tx = current.transactions.find { it.id == id } ?: return null
        val softDeleted = tx.copy(deletedAt = System.currentTimeMillis())
        val updated = current.copy(
            transactions = current.transactions.map {
                if (it.id == id) softDeleted else it
            }
        )
        _appData.value = updated
        storage.saveData(updated)
        return tx
    }

    /**
     * Permanently deletes a transaction (bypasses soft-delete).
     */
    suspend fun permanentlyDeleteTransaction(id: String) {
        val current = _appData.value
        val updated = current.copy(
            transactions = current.transactions.filter { it.id != id }
        )
        _appData.value = updated
        storage.saveData(updated)
    }

    /**
     * Restores a soft-deleted transaction.
     */
    suspend fun restoreTransaction(id: String) {
        val current = _appData.value
        val updated = current.copy(
            transactions = current.transactions.map {
                if (it.id == id) it.copy(deletedAt = 0) else it
            }
        )
        _appData.value = updated
        storage.saveData(updated)
    }

    /**
     * Restores a transaction from a full copy (for undo after soft-delete).
     */
    suspend fun restoreTransactionFromCopy(tx: Transaction) {
        val current = _appData.value
        val exists = current.transactions.any { it.id == tx.id }
        val updated = if (exists) {
            current.copy(transactions = current.transactions.map {
                if (it.id == tx.id) tx.copy(deletedAt = 0) else it
            })
        } else {
            current.copy(transactions = current.transactions + tx.copy(deletedAt = 0))
        }
        _appData.value = updated
        storage.saveData(updated)
    }

    /**
     * Purges soft-deleted transactions older than [retentionDays].
     */
    suspend fun purgeDeletedTransactions(retentionDays: Int = 30) {
        val cutoff = System.currentTimeMillis() - (retentionDays * 24 * 60 * 60 * 1000L)
        val current = _appData.value
        val updated = current.copy(
            transactions = current.transactions.filter {
                (it.deletedAt ?: 0) == 0L || (it.deletedAt ?: 0) > cutoff
            }
        )
        if (updated.transactions.size != current.transactions.size) {
            _appData.value = updated
            storage.saveData(updated)
        }
    }

    /**
     * Returns all soft-deleted transactions (trash).
     */
    fun getDeletedTransactions(): List<Transaction> {
        return _appData.value.transactions.filter { it.isDeleted }
            .sortedByDescending { it.deletedAt ?: 0 }
    }

    // ─── Batch Operations ─────────────────────────────────────────

    /**
     * Batch re-categorize multiple transactions.
     */
    suspend fun batchRecategorize(ids: Set<String>, newCategory: String) {
        val current = _appData.value
        val updated = current.copy(
            transactions = current.transactions.map {
                if (it.id in ids) it.copy(category = newCategory) else it
            }
        )
        _appData.value = updated
        storage.saveData(updated)
    }

    /**
     * Batch delete multiple transactions (soft-delete).
     */
    suspend fun batchDelete(ids: Set<String>) {
        val now = System.currentTimeMillis()
        val current = _appData.value
        val updated = current.copy(
            transactions = current.transactions.map {
                if (it.id in ids) it.copy(deletedAt = now) else it
            }
        )
        _appData.value = updated
        storage.saveData(updated)
    }

    /**
     * Batch add/remove tags on multiple transactions.
     */
    suspend fun batchUpdateTags(ids: Set<String>, addTags: List<String> = emptyList(), removeTags: List<String> = emptyList()) {
        val current = _appData.value
        val updated = current.copy(
            transactions = current.transactions.map { tx ->
                if (tx.id in ids) {
                    val newTags = (tx.tags + addTags).filter { it !in removeTags }.distinct()
                    tx.copy(tags = newTags)
                } else tx
            }
        )
        _appData.value = updated
        storage.saveData(updated)
    }

    fun getTransactionsByDateRange(start: Long, end: Long): List<Transaction> {
        return _appData.value.transactions.filter {
            !it.isDeleted && it.timestamp in start..end
        }.sortedByDescending { it.timestamp }
    }

    fun getTransactionsByCategory(category: String): List<Transaction> {
        return _appData.value.transactions.filter {
            !it.isDeleted && it.category == category
        }.sortedByDescending { it.timestamp }
    }

    fun getTransactionsByType(type: TransactionType): List<Transaction> {
        return _appData.value.transactions.filter {
            !it.isDeleted && it.type == type
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

    suspend fun clearAllStoreLocations() {
        val current = _appData.value
        val updated = current.copy(storeLocations = emptyList())
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
     * Converts every transaction amount and budget limit to [newCurrency].
     *
     * For transactions that carry original foreign-currency metadata
     * (originalAmount / originalCurrencyCode), the amount is re-converted
     * from the *original* currency to [newCurrency] using the provided
     * [rateFromOriginal] lookup, avoiding compounded rounding errors.
     *
     * Transactions without original metadata are multiplied by [fallbackRate]
     * (the old-currency → new-currency rate).
     *
     * @param newCurrency       ISO-4217 code of the new app currency.
     * @param fallbackRate      1 old-currency = fallbackRate new-currency.
     * @param rateFromOriginal  Lookup: (originalCurrencyCode) → rate where
     *                          1 originalCurrency = rate new-currency.
     *                          Return null when a rate is unavailable.
     */
    suspend fun convertAmounts(
        newCurrency: String,
        fallbackRate: Double,
        rateFromOriginal: (String) -> Double?
    ) {
        // Validate exchange rates to prevent data corruption from NaN, Infinity, or zero rates
        if (fallbackRate <= 0 || fallbackRate.isNaN() || fallbackRate.isInfinite()) return

        val current = _appData.value
        val updated = current.copy(
            transactions = current.transactions.map { tx ->
                if (tx.originalAmount > 0.0 && tx.originalCurrencyCode.orEmpty().isNotEmpty()) {
                    val rate = rateFromOriginal(tx.originalCurrencyCode.orEmpty())
                    if (rate != null && rate > 0 && rate.isFinite()) {
                        tx.copy(
                            amount = tx.originalAmount * rate,
                            currencyCode = newCurrency,
                            exchangeRate = rate
                        )
                    } else {
                        tx.copy(
                            amount = tx.amount * fallbackRate,
                            currencyCode = newCurrency
                        )
                    }
                } else {
                    tx.copy(
                        amount = tx.amount * fallbackRate,
                        currencyCode = newCurrency
                    )
                }
            },
            budgets = current.budgets.map { it.copy(monthlyLimit = it.monthlyLimit * fallbackRate) }
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

    suspend fun addInAppNotification(notification: com.flowsense.app.data.model.InAppNotification) {
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
        val newCategory = com.flowsense.app.data.model.Category(
            name = categoryName,
            icon = "category",
            color = 0xFF9E9E9E,
            isDefault = false
        )
        val withCategory = current.copy(categories = current.categories + newCategory)
        _appData.value = withCategory
        storage.saveData(withCategory)
        // Post a notification about the new category
        val notification = com.flowsense.app.data.model.InAppNotification(
            title = "New category added",
            message = "\"$categoryName\" was automatically created based on your transaction. You can rename or remove it in Settings.",
            type = com.flowsense.app.data.model.InAppNotificationType.CATEGORY_CREATED,
            suggestedCategoryName = categoryName
        )
        addInAppNotification(notification)
        return true
    }

    // ─── OCR Sections ─────────────────────────────────────────────

    suspend fun addOcrSection(section: com.flowsense.app.data.model.OcrSection) {
        val current = _appData.value
        val updated = current.copy(
            ocrSections = current.ocrSections + section
        )
        _appData.value = updated
        storage.saveData(updated)
    }

    suspend fun updateOcrSection(section: com.flowsense.app.data.model.OcrSection) {
        val current = _appData.value
        val updated = current.copy(
            ocrSections = current.ocrSections.map {
                if (it.id == section.id) section else it
            }
        )
        _appData.value = updated
        storage.saveData(updated)
    }

    suspend fun deleteOcrSection(id: String) {
        val current = _appData.value
        val updated = current.copy(
            ocrSections = current.ocrSections.filter { it.id != id }
        )
        _appData.value = updated
        storage.saveData(updated)
    }

    suspend fun clearAllOcrSections() {
        val current = _appData.value
        val updated = current.copy(ocrSections = emptyList())
        _appData.value = updated
        storage.saveData(updated)
    }

    // ─── Rate History ─────────────────────────────────────────────

    /**
     * Stores a rate snapshot. Keeps only current (index 0) and previous (index 1).
     */
    suspend fun addRateHistoryEntry(entry: com.flowsense.app.data.model.RateHistoryEntry) {
        val current = _appData.value
        val updated = current.copy(
            rateHistory = (listOf(entry) + current.rateHistory).take(2)
        )
        _appData.value = updated
        storage.saveData(updated)
    }

    // ─── AI Conversations ──────────────────────────────────────────

    suspend fun addAiConversation(conversation: AiConversation) {
        val current = _appData.value
        // Keep at most 200 conversations to avoid unbounded growth
        val updated = current.copy(
            aiConversations = (listOf(conversation) + current.aiConversations).take(200)
        )
        _appData.value = updated
        storage.saveData(updated)
    }

    suspend fun deleteAiConversation(id: String) {
        val current = _appData.value
        val updated = current.copy(
            aiConversations = current.aiConversations.filter { it.id != id }
        )
        _appData.value = updated
        storage.saveData(updated)
    }

    suspend fun clearAllAiConversations() {
        val current = _appData.value
        val updated = current.copy(aiConversations = emptyList())
        _appData.value = updated
        storage.saveData(updated)
    }

    // ─── Recurring Patterns ─────────────────────────────────────────

    /**
     * Detects recurring transaction patterns from history.
     * Looks for transactions with similar amounts and descriptions
     * that occur at regular intervals.
     */
    suspend fun detectRecurringPatterns(): List<RecurringPattern> {
        val current = _appData.value
        val activeTransactions = current.transactions.filter { !it.isDeleted && it.type == TransactionType.EXPENSE }

        // Group by similar description + similar amount (within 5%)
        val groups = activeTransactions.groupBy { tx ->
            val normalizedDesc = tx.description.trim().lowercase()
            val merchant = tx.merchantName.trim().lowercase()
            "${merchant.ifEmpty { normalizedDesc }}|${(tx.amount / 5).toLong() * 5}" // bucket by ~$5 increments
        }.filter { it.value.size >= 2 }

        val patterns = mutableListOf<RecurringPattern>()
        for ((_, txs) in groups) {
            if (txs.size < 2) continue
            val sorted = txs.sortedBy { it.timestamp }
            val intervals = sorted.zipWithNext { a, b ->
                ((b.timestamp - a.timestamp) / (24 * 60 * 60 * 1000.0)).toInt()
            }
            val avgInterval = intervals.average().toInt()
            // Only consider patterns with reasonable intervals (7-45 days)
            if (avgInterval in 7..45 && intervals.all { it in (avgInterval / 2)..(avgInterval * 2) }) {
                val avgAmount = txs.map { it.amount }.average()
                val existing = current.recurringPatterns.orEmpty().find {
                    it.description.equals(sorted.last().description, ignoreCase = true) &&
                        kotlin.math.abs(it.amount - avgAmount) < avgAmount * 0.1
                }
                if (existing == null || (!existing.confirmed && !existing.dismissed)) {
                    patterns.add(RecurringPattern(
                        description = sorted.last().description,
                        merchantName = sorted.last().merchantName,
                        amount = avgAmount,
                        category = sorted.last().category,
                        intervalDays = avgInterval,
                        occurrenceCount = txs.size
                    ))
                }
            }
        }
        // Save detected patterns
        if (patterns.isNotEmpty()) {
            val updated = current.copy(
                recurringPatterns = (patterns + current.recurringPatterns.orEmpty().filter { it.confirmed || it.dismissed }).distinctBy { it.description.lowercase() }
            )
            _appData.value = updated
            storage.saveData(updated)
        }
        return patterns
    }

    suspend fun confirmRecurringPattern(id: String) {
        val current = _appData.value
        val updated = current.copy(
            recurringPatterns = current.recurringPatterns.orEmpty().map {
                if (it.id == id) it.copy(confirmed = true) else it
            }
        )
        _appData.value = updated
        storage.saveData(updated)
    }

    suspend fun dismissRecurringPattern(id: String) {
        val current = _appData.value
        val updated = current.copy(
            recurringPatterns = current.recurringPatterns.orEmpty().map {
                if (it.id == id) it.copy(dismissed = true) else it
            }
        )
        _appData.value = updated
        storage.saveData(updated)
    }

    // ─── Spending Streaks ─────────────────────────────────────────

    /**
     * Updates the spending streak after a new transaction is logged.
     */
    suspend fun updateSpendingStreak() {
        val current = _appData.value
        val streak = current.spendingStreak ?: SpendingStreak()
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US)
            .format(java.util.Date(System.currentTimeMillis()))

        if (today == streak.lastLogDate) {
            // Already logged today, just increment total
            val updated = current.copy(
                spendingStreak = streak.copy(
                    totalTransactionsLogged = current.transactions.count { !it.isDeleted }
                )
            )
            _appData.value = updated
            storage.saveData(updated)
            return
        }

        // Check if yesterday was the last log date
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
        val yesterday = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)

        val newStreak = if (streak.lastLogDate == yesterday) {
            streak.currentStreak + 1
        } else {
            1 // Reset streak
        }

        val totalTx = current.transactions.count { !it.isDeleted }

        // Check for new achievements
        val achievements = streak.unlockedAchievements.toMutableList()
        if (newStreak >= 7 && "streak_7" !in achievements) achievements.add("streak_7")
        if (newStreak >= 14 && "streak_14" !in achievements) achievements.add("streak_14")
        if (newStreak >= 30 && "streak_30" !in achievements) achievements.add("streak_30")
        if (newStreak >= 100 && "streak_100" !in achievements) achievements.add("streak_100")
        if (totalTx >= 100 && "tx_100" !in achievements) achievements.add("tx_100")
        if (totalTx >= 500 && "tx_500" !in achievements) achievements.add("tx_500")

        val updated = current.copy(
            spendingStreak = streak.copy(
                currentStreak = newStreak,
                longestStreak = maxOf(newStreak, streak.longestStreak),
                lastLogDate = today,
                totalTransactionsLogged = totalTx,
                unlockedAchievements = achievements
            )
        )
        _appData.value = updated
        storage.saveData(updated)
    }

    // ─── CSV Export ───────────────────────────────────────────────

    /**
     * Exports transactions as CSV string, optionally filtered by date range.
     */
    fun exportTransactionsCsv(startMillis: Long? = null, endMillis: Long? = null): String {
        val transactions = _appData.value.transactions
            .filter { !it.isDeleted }
            .let { txs ->
                if (startMillis != null && endMillis != null) {
                    txs.filter { it.timestamp in startMillis..endMillis }
                } else txs
            }
            .sortedByDescending { it.timestamp }

        val sb = StringBuilder()
        sb.appendLine("Date,Description,Amount,Type,Category,Merchant,Tags,Notes,Source,Currency")
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        for (tx in transactions) {
            val date = dateFormat.format(java.util.Date(tx.timestamp))
            val desc = tx.description.replace("\"", "\"\"")
            val merchant = tx.merchantName.replace("\"", "\"\"")
            val tags = tx.tags.joinToString(";")
            val notes = tx.notes.replace("\"", "\"\"").replace("\n", " ")
            sb.appendLine("\"$date\",\"$desc\",${tx.amount},${tx.type},\"${tx.category}\",\"$merchant\",\"$tags\",\"$notes\",${tx.source},${tx.currencyCode.ifEmpty { "USD" }}")
        }
        return sb.toString()
    }

    // ─── Tag Suggestions ──────────────────────────────────────────

    /**
     * Returns auto-suggested tags based on category, description, and history.
     */
    fun suggestTags(description: String, category: String): List<String> {
        val allTags = _appData.value.transactions
            .filter { !it.isDeleted }
            .flatMap { it.tags }
            .groupBy { it }
            .entries
            .sortedByDescending { it.value.size }
            .map { it.key }

        // Tags used with this category
        val categoryTags = _appData.value.transactions
            .filter { !it.isDeleted && it.category.equals(category, ignoreCase = true) }
            .flatMap { it.tags }
            .groupBy { it }
            .entries
            .sortedByDescending { it.value.size }
            .map { it.key }

        return (categoryTags + allTags).distinct().take(10)
    }

    // ─── Split Expense ────────────────────────────────────────────

    /**
     * Updates the split information for a transaction.
     */
    suspend fun updateSplitExpense(transactionId: String, splitWith: List<SplitPerson>) {
        val current = _appData.value
        val updated = current.copy(
            transactions = current.transactions.map {
                if (it.id == transactionId) it.copy(splitWith = splitWith) else it
            }
        )
        _appData.value = updated
        storage.saveData(updated)
    }

    /**
     * Returns outstanding debts from split expenses.
     */
    fun getOutstandingDebts(): Map<String, Double> {
        val debts = mutableMapOf<String, Double>()
        for (tx in _appData.value.transactions.filter { !it.isDeleted && it.isSplit }) {
            val share = tx.splitAmount
            for (person in tx.splitWith.orEmpty()) {
                if (!person.paid) {
                    debts[person.name] = (debts[person.name] ?: 0.0) + share
                }
            }
        }
        return debts
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
