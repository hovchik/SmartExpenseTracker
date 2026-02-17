package com.smartexpense.tracker.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartexpense.tracker.SmartExpenseApp
import com.smartexpense.tracker.data.model.*
import com.smartexpense.tracker.data.repository.ExpenseRepository
import com.smartexpense.tracker.service.ai.AiExpenseEngine
import com.smartexpense.tracker.service.currency.CurrencyConverterService
import com.smartexpense.tracker.util.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val repository: ExpenseRepository = (application as SmartExpenseApp).repository
    val aiEngine = AiExpenseEngine()

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _reportPeriod = MutableStateFlow(ReportPeriod.MONTHLY)
    val reportPeriod: StateFlow<ReportPeriod> = _reportPeriod.asStateFlow()

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _importExportMessage = MutableStateFlow<String?>(null)
    val importExportMessage: StateFlow<String?> = _importExportMessage.asStateFlow()

    private val _smsScanState = MutableStateFlow(SmsScanState())
    val smsScanState: StateFlow<SmsScanState> = _smsScanState.asStateFlow()

    /** Live exchange rates fetched from open.er-api.com (base = USD). */
    private val _exchangeRates = MutableStateFlow<Map<String, Double>>(emptyMap())
    val exchangeRates: StateFlow<Map<String, Double>> = _exchangeRates.asStateFlow()

    init {
        viewModelScope.launch {
            repository.initialize()
            _themeMode.value = repository.appData.value.settings.themeMode
            refreshSuggestions()
            repository.appData.collect { data ->
                _themeMode.value = data.settings.themeMode
                updateUiState(data)
            }
        }
    }

    private fun updateUiState(data: AppData) {
        val now = System.currentTimeMillis()
        val startOfMonth = DateUtils.getStartOfMonth(now)
        val endOfMonth = DateUtils.getEndOfMonth(now)
        val startOfWeek = DateUtils.getStartOfWeek(now)
        val endOfWeek = DateUtils.getEndOfWeek(now)
        val startOfDay = DateUtils.getStartOfDay(now)
        val endOfDay = DateUtils.getEndOfDay(now)

        val monthlyExpenses = data.transactions
            .filter { it.type == TransactionType.EXPENSE && it.timestamp in startOfMonth..endOfMonth }
            .sumOf { it.amount }
        val monthlyIncome = data.transactions
            .filter { it.type == TransactionType.INCOME && it.timestamp in startOfMonth..endOfMonth }
            .sumOf { it.amount }
        val todayExpenses = data.transactions
            .filter { it.type == TransactionType.EXPENSE && it.timestamp in startOfDay..endOfDay }
            .sumOf { it.amount }
        val weeklyExpenses = data.transactions
            .filter { it.type == TransactionType.EXPENSE && it.timestamp in startOfWeek..endOfWeek }
            .sumOf { it.amount }

        val allTransactionsSorted = data.transactions.sortedByDescending { it.timestamp }
        val recentTransactions = allTransactionsSorted.take(20)
        val categoryBreakdown = data.transactions
            .filter { it.type == TransactionType.EXPENSE && it.timestamp in startOfMonth..endOfMonth }
            .groupBy { it.category }
            .mapValues { it.value.sumOf { t -> t.amount } }
            .entries.sortedByDescending { it.value }
            .associate { it.key to it.value }

        // Group recent transactions by date for the date-grouped view
        val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val transactionsByDate: Map<String, List<Transaction>> = recentTransactions
            .groupBy { dateFormatter.format(Date(it.timestamp)) }

        _uiState.value = UiState(
            isLoading = false,
            monthlyExpenses = monthlyExpenses, monthlyIncome = monthlyIncome,
            todayExpenses = todayExpenses, weeklyExpenses = weeklyExpenses,
            netBalance = monthlyIncome - monthlyExpenses,
            recentTransactions = recentTransactions, categoryBreakdown = categoryBreakdown,
            allTransactions = allTransactionsSorted,
            categories = data.categories,
            suggestions = data.suggestions.filter { !it.isDismissed },
            transactionCount = data.transactions.size, settings = data.settings,
            transactionsByDate = transactionsByDate
        )
    }

    fun setSelectedTab(index: Int) { _selectedTab.value = index }
    fun setReportPeriod(period: ReportPeriod) { _reportPeriod.value = period }

    fun addTransaction(
        amount: Double, description: String, category: String? = null,
        type: TransactionType = TransactionType.EXPENSE,
        source: TransactionSource = TransactionSource.MANUAL,
        merchantName: String = "", notes: String = ""
    ) {
        viewModelScope.launch {
            val finalCategory = category ?: aiEngine.categorize(description)
            val now = System.currentTimeMillis()
            val dtFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            repository.addTransaction(Transaction(
                amount = amount, description = description, category = finalCategory,
                type = type, source = source, merchantName = merchantName, notes = notes,
                timestamp = now, dateTime = dtFormatter.format(Date(now))
            ))
            refreshSuggestions()
        }
    }

    fun deleteTransaction(id: String) { viewModelScope.launch { repository.deleteTransaction(id) } }

    fun processOcrText(ocrText: String) {
        viewModelScope.launch {
            try {
                val currencyCode = repository.appData.value.settings.currencyCode
                val parsed = aiEngine.parseReceiptText(ocrText, currencyCode)
                val amount = parsed.totalAmount ?: parsed.items.sumOf { it.second }
                val currencySymbol = currencyInfoFor(currencyCode).symbol
                if (amount > 0) {
                    val category = aiEngine.categorize(parsed.merchantName)
                    val now = System.currentTimeMillis()
                    val dtFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                    repository.addTransaction(Transaction(
                        amount = amount,
                        description = "Receipt: ${parsed.merchantName}",
                        category = category, type = TransactionType.EXPENSE,
                        source = TransactionSource.OCR_SCAN,
                        merchantName = parsed.merchantName,
                        timestamp = now, dateTime = dtFormatter.format(Date(now)),
                        notes = if (parsed.items.isNotEmpty())
                            "Items: ${parsed.items.joinToString(", ") { "${it.first}: $currencySymbol${String.format("%.2f", it.second)}" }}"
                        else ""
                    ))
                    _uiState.value = _uiState.value.copy(
                        lastOcrResult = "Found: ${parsed.merchantName} - $currencySymbol${String.format("%.2f", amount)}"
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        lastOcrResult = "Could not extract amount from receipt."
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(lastOcrResult = "OCR error: ${e.message}")
            }
        }
    }

    fun dismissSuggestion(id: String) { viewModelScope.launch { repository.dismissSuggestion(id) } }

    fun refreshSuggestions() {
        viewModelScope.launch {
            val data = repository.appData.value
            val suggestions = aiEngine.generateSuggestions(data.transactions, data.budgets)
            repository.addSuggestions(suggestions)
        }
    }

    fun generateReport(period: ReportPeriod): ExpenseReport {
        val now = System.currentTimeMillis()
        val (start, end) = when (period) {
            ReportPeriod.DAILY -> DateUtils.getStartOfDay(now) to DateUtils.getEndOfDay(now)
            ReportPeriod.WEEKLY -> DateUtils.getStartOfWeek(now) to DateUtils.getEndOfWeek(now)
            ReportPeriod.MONTHLY -> DateUtils.getStartOfMonth(now) to DateUtils.getEndOfMonth(now)
        }
        return aiEngine.generateReport(repository.appData.value.transactions, period, start, end)
    }

    fun getWeeklyChartData(): List<Pair<String, Double>> {
        val data = repository.appData.value
        return DateUtils.getDaysInRange(DateUtils.getStartOfWeek(), DateUtils.getEndOfWeek()).map { dayStart ->
            val dayEnd = DateUtils.getEndOfDay(dayStart)
            val total = data.transactions
                .filter { it.type == TransactionType.EXPENSE && it.timestamp in dayStart..dayEnd }
                .sumOf { it.amount }
            DateUtils.formatDay(dayStart) to total
        }
    }

    fun addCategory(name: String) { viewModelScope.launch { repository.addCategory(Category(name = name)) } }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            repository.updateSettings(repository.appData.value.settings.copy(themeMode = mode))
        }
    }

    fun updateSettings(settings: AppSettings) {
        viewModelScope.launch {
            repository.updateSettings(settings)
            // Invalidate cached rates when base currency changes
            CurrencyConverterService.invalidateCache()
        }
    }

    // ─── Currency Converter ────────────────────────────────────────

    /**
     * Fetches live exchange rates (base = USD) and stores them in [exchangeRates].
     * Safe to call multiple times — results are cached for 1 hour.
     */
    fun fetchExchangeRates() {
        viewModelScope.launch {
            val rates = withContext(Dispatchers.IO) {
                CurrencyConverterService.getRates("USD")
            }
            if (rates != null) {
                // Merge in USD itself so the converter can handle USD→X conversions
                _exchangeRates.value = rates + ("USD" to 1.0)
            }
        }
    }

    // ─── Import / Export ───────────────────────────────────────────

    fun exportDataToUri(uri: Uri) {
        viewModelScope.launch {
            try {
                val json = repository.exportData()
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use {
                    it.write(json.toByteArray(Charsets.UTF_8))
                }
                _importExportMessage.value = "Data exported successfully"
            } catch (e: Exception) {
                _importExportMessage.value = "Export failed: ${e.message}"
            }
        }
    }

    fun importDataFromUri(uri: Uri) {
        viewModelScope.launch {
            try {
                val json = getApplication<Application>().contentResolver.openInputStream(uri)?.use {
                    BufferedReader(InputStreamReader(it, Charsets.UTF_8)).readText()
                } ?: throw Exception("Could not read file")
                repository.importData(json).onSuccess { data ->
                    _importExportMessage.value = "Imported ${data.transactions.size} transactions"
                }.onFailure { e ->
                    _importExportMessage.value = "Import failed: ${e.message}"
                }
            } catch (e: Exception) {
                _importExportMessage.value = "Import failed: ${e.message}"
            }
        }
    }

    fun clearImportExportMessage() { _importExportMessage.value = null }

    fun getStorageInfoText(): String {
        val info = com.smartexpense.tracker.data.json.JsonStorageManager(getApplication()).getStorageInfo()
        val sizeKb = info.fileSizeBytes / 1024.0
        return when {
            sizeKb < 1 -> "${info.fileSizeBytes} bytes"
            sizeKb < 1024 -> "${String.format("%.1f", sizeKb)} KB"
            else -> "${String.format("%.1f", sizeKb / 1024)} MB"
        }
    }

    // ─── SMS Inbox Scanning ────────────────────────────────────────

    fun startSmsScan() {
        viewModelScope.launch {
            _smsScanState.value = SmsScanState(isScanning = true)
            try {
                val existingNotes: Set<String> = try {
                    repository.appData.value.transactions
                        .filter { it.source == TransactionSource.SMS }
                        .mapNotNull { it.notes.takeIf { n -> n.isNotEmpty() } }
                        .toSet()
                } catch (_: Throwable) { emptySet() }

                val result = withContext(Dispatchers.IO) {
                    try {
                        com.smartexpense.tracker.service.sms.SmsInboxScanner(getApplication())
                            .scanInbox(maxMessages = 500, existingTransactionNotes = existingNotes)
                    } catch (e: Throwable) {
                        com.smartexpense.tracker.service.sms.SmsInboxScanner.ScanResult(
                            0, 0, 0, emptyList(), 1, "Error: ${e.message}"
                        )
                    }
                }

                _smsScanState.value = SmsScanState(
                    isScanning = false, isComplete = true,
                    totalScanned = result.totalScanned,
                    financialFound = result.financialFound,
                    transactionsParsed = result.transactionsParsed,
                    pendingTransactions = result.transactions,
                    errors = result.errors, errorMessage = result.errorMessage
                )
            } catch (e: Throwable) {
                _smsScanState.value = SmsScanState(
                    isScanning = false, isComplete = true,
                    errorMessage = "Failed: ${e.message ?: e.javaClass.simpleName}"
                )
            }
        }
    }

    fun confirmSmsScanResults() {
        viewModelScope.launch {
            val pending = _smsScanState.value.pendingTransactions
            for (tx in pending) repository.addTransaction(tx)
            _smsScanState.value = _smsScanState.value.copy(
                pendingTransactions = emptyList(), savedCount = pending.size
            )
            refreshSuggestions()
        }
    }

    fun discardSmsScanResult(id: String) {
        val c = _smsScanState.value
        _smsScanState.value = c.copy(
            pendingTransactions = c.pendingTransactions.filter { it.id != id },
            transactionsParsed = c.transactionsParsed - 1
        )
    }

    fun resetSmsScanState() { _smsScanState.value = SmsScanState() }
}

data class SmsScanState(
    val isScanning: Boolean = false, val isComplete: Boolean = false,
    val totalScanned: Int = 0, val financialFound: Int = 0,
    val transactionsParsed: Int = 0,
    val pendingTransactions: List<Transaction> = emptyList(),
    val savedCount: Int = 0, val errors: Int = 0,
    val errorMessage: String? = null
)

data class UiState(
    val isLoading: Boolean = true,
    val monthlyExpenses: Double = 0.0, val monthlyIncome: Double = 0.0,
    val todayExpenses: Double = 0.0, val weeklyExpenses: Double = 0.0,
    val netBalance: Double = 0.0,
    val recentTransactions: List<Transaction> = emptyList(),
    /** All transactions sorted newest-first (used by TransactionsScreen). */
    val allTransactions: List<Transaction> = emptyList(),
    /** Recent transactions grouped by date string, e.g. "2026-02-17" → [Transaction, …]. */
    val transactionsByDate: Map<String, List<Transaction>> = emptyMap(),
    val categoryBreakdown: Map<String, Double> = emptyMap(),
    val categories: List<Category> = emptyList(),
    val suggestions: List<AiSuggestion> = emptyList(),
    val transactionCount: Int = 0,
    val lastOcrResult: String? = null,
    val settings: AppSettings = AppSettings()
)
