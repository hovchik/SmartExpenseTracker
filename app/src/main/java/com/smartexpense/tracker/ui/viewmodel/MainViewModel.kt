package com.smartexpense.tracker.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartexpense.tracker.SmartExpenseApp
import com.smartexpense.tracker.data.model.*
import com.smartexpense.tracker.data.model.currencyInfoFor
import com.smartexpense.tracker.data.repository.ExpenseRepository
import com.smartexpense.tracker.service.ai.AiExpenseEngine
import com.smartexpense.tracker.service.ai.LocalAiService
import com.smartexpense.tracker.service.ai.MediaPipeLlmService
import com.smartexpense.tracker.service.currency.CurrencyConverterService
import com.smartexpense.tracker.service.notification.ExpenseNotificationHelper
import com.smartexpense.tracker.service.scheduler.SalarySchedulerWorker
import com.smartexpense.tracker.util.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val repository: ExpenseRepository = (application as SmartExpenseApp).repository
    val aiEngine = AiExpenseEngine()

    /** On-device Gemini Nano service – null responses mean "not available / not enabled". */
    private val localAiService = LocalAiService(application.applicationContext)

    /** Human-readable AI backend status shown in Settings (null = not yet checked). */
    private val _localAiStatus = MutableStateFlow<String?>(null)
    val localAiStatus: StateFlow<String?> = _localAiStatus.asStateFlow()

    /** Suggestion for enabling a better AI engine; null when none is needed. */
    private val _localAiSuggestion = MutableStateFlow<String?>(null)
    val localAiSuggestion: StateFlow<String?> = _localAiSuggestion.asStateFlow()

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

    private val _totalSmsCount = MutableStateFlow(0)
    val totalSmsCount: StateFlow<Int> = _totalSmsCount.asStateFlow()

    /** Live exchange rates fetched from open.er-api.com (base = USD). */
    private val _exchangeRates = MutableStateFlow<Map<String, Double>>(emptyMap())
    val exchangeRates: StateFlow<Map<String, Double>> = _exchangeRates.asStateFlow()

    /** Parsed OCR data awaiting user confirmation (editable review form). */
    private val _ocrParsedData = MutableStateFlow<OcrParsedData?>(null)
    val ocrParsedData: StateFlow<OcrParsedData?> = _ocrParsedData.asStateFlow()

    /** In-app notifications (bell panel). */
    private val _inAppNotifications = MutableStateFlow<List<InAppNotification>>(emptyList())
    val inAppNotifications: StateFlow<List<InAppNotification>> = _inAppNotifications.asStateFlow()

    val unreadNotificationCount: StateFlow<Int> = _inAppNotifications
        .map { list -> list.count { !it.isRead } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** Available Ollama models on the connected server. */
    private val _ollamaModels = MutableStateFlow<List<com.smartexpense.tracker.service.ai.OllamaService.OllamaModel>>(emptyList())
    val ollamaModels: StateFlow<List<com.smartexpense.tracker.service.ai.OllamaService.OllamaModel>> = _ollamaModels.asStateFlow()

    private val _ollamaConnecting = MutableStateFlow(false)
    val ollamaConnecting: StateFlow<Boolean> = _ollamaConnecting.asStateFlow()

    init {
        viewModelScope.launch {
            repository.initialize()
            val settings = repository.appData.value.settings
            _themeMode.value = settings.themeMode

            // Auto-load saved MediaPipe model if configured
            if (settings.localAiEnabled && settings.mediapipeModelPath.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    localAiService.mediaPipeLlm.loadModel(settings.mediapipeModelPath)
                }
            }
            // Restore Ollama settings if configured
            if (settings.ollamaHost.isNotEmpty()) {
                localAiService.ollamaService.host = settings.ollamaHost
            }
            if (settings.ollamaModel.isNotEmpty()) {
                localAiService.ollamaService.selectModel(settings.ollamaModel)
                if (settings.localAiEnabled && settings.aiEnginePreference == com.smartexpense.tracker.data.model.AiEnginePreference.OLLAMA) {
                    withContext(Dispatchers.IO) { localAiService.ollamaService.checkConnection() }
                }
            }
            // Run initial AI availability check
            if (settings.localAiEnabled) {
                withContext(Dispatchers.IO) {
                    localAiService.checkAvailability(settings.aiEnginePreference)
                }
                _localAiStatus.value = localAiService.statusMessage()
                _engineDescriptions.value = localAiService.engineDescriptions()
            }

            refreshSuggestions()
            repository.appData.collect { data ->
                _themeMode.value = data.settings.themeMode
                _inAppNotifications.value = data.inAppNotifications
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

        // ── Monthly expense threshold check ───────────────────────
        val limit = data.settings.monthlyExpenseLimit
        if (limit > 0 && monthlyExpenses > limit) {
            val currentMonth = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US)
                .format(java.util.Date(now))
            if (data.settings.lastThresholdAlertMonth != currentMonth) {
                val currencySymbol = data.settings.currency.ifEmpty { "$" }
                ExpenseNotificationHelper.postBudgetExceededNotification(
                    context = getApplication(),
                    spent = monthlyExpenses,
                    limit = limit,
                    currencySymbol = currencySymbol
                )
                viewModelScope.launch {
                    repository.updateSettings(
                        data.settings.copy(lastThresholdAlertMonth = currentMonth)
                    )
                    repository.addInAppNotification(
                        InAppNotification(
                            title = "Monthly limit exceeded",
                            message = "You've spent $currencySymbol${String.format("%.2f", monthlyExpenses)}" +
                                " — over your $currencySymbol${String.format("%.2f", limit)} limit.",
                            type = InAppNotificationType.BUDGET_ALERT
                        )
                    )
                }
            }
        }
    }

    fun setSelectedTab(index: Int) { _selectedTab.value = index }
    fun setReportPeriod(period: ReportPeriod) { _reportPeriod.value = period }

    // ─── Local AI Engine ────────────────────────────────────────────

    /** Descriptions for each engine option, updated after availability check. */
    private val _engineDescriptions = MutableStateFlow<Map<AiEnginePreference, String>>(emptyMap())
    val engineDescriptions: StateFlow<Map<AiEnginePreference, String>> = _engineDescriptions.asStateFlow()

    /** Discovered model files for MediaPipe LLM. */
    private val _discoveredModels = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val discoveredModels: StateFlow<List<Pair<String, String>>> = _discoveredModels.asStateFlow()

    /** Whether a MediaPipe model is currently being loaded. */
    private val _isLoadingModel = MutableStateFlow(false)
    val isLoadingModel: StateFlow<Boolean> = _isLoadingModel.asStateFlow()

    /**
     * Checks AI availability on a background thread and updates status.
     * Respects the user's [AiEnginePreference] from settings.
     */
    fun checkLocalAiAvailability() {
        viewModelScope.launch {
            _localAiStatus.value = "Checking availability…"
            val preference = repository.appData.value.settings.aiEnginePreference
            withContext(Dispatchers.IO) { localAiService.checkAvailability(preference) }
            _localAiStatus.value = localAiService.statusMessage()
            _localAiSuggestion.value = localAiService.alternativeSuggestion()
            _engineDescriptions.value = localAiService.engineDescriptions()
        }
    }

    /**
     * Changes the AI engine preference and re-checks availability.
     */
    fun setAiEnginePreference(preference: AiEnginePreference) {
        viewModelScope.launch {
            val settings = repository.appData.value.settings
            repository.updateSettings(settings.copy(
                aiEnginePreference = preference,
                localAiEnabled = preference != AiEnginePreference.RULE_BASED
            ))
            _localAiStatus.value = "Switching engine…"
            withContext(Dispatchers.IO) { localAiService.recheckAvailability(preference) }
            _localAiStatus.value = localAiService.statusMessage()
            _localAiSuggestion.value = localAiService.alternativeSuggestion()
            _engineDescriptions.value = localAiService.engineDescriptions()
        }
    }

    // ── Ollama ─────────────────────────────────────────────────────

    /**
     * Connects to an Ollama server and fetches available models.
     */
    fun connectOllama(host: String) {
        viewModelScope.launch {
            _ollamaConnecting.value = true
            _localAiStatus.value = "Connecting to Ollama\u2026"
            localAiService.ollamaService.host = host

            val ok = withContext(Dispatchers.IO) { localAiService.ollamaService.checkConnection() }
            if (ok) {
                val models = withContext(Dispatchers.IO) { localAiService.ollamaService.listModels() }
                _ollamaModels.value = models
                // Save host
                val settings = repository.appData.value.settings
                repository.updateSettings(settings.copy(ollamaHost = host))
            } else {
                _ollamaModels.value = emptyList()
            }
            _ollamaConnecting.value = false
            _localAiStatus.value = localAiService.statusMessage()
            _engineDescriptions.value = localAiService.engineDescriptions()
        }
    }

    /**
     * Selects an Ollama model and activates the Ollama engine.
     */
    fun selectOllamaModel(modelName: String) {
        viewModelScope.launch {
            localAiService.ollamaService.selectModel(modelName)
            val settings = repository.appData.value.settings
            repository.updateSettings(settings.copy(
                ollamaModel = modelName,
                aiEnginePreference = AiEnginePreference.OLLAMA,
                localAiEnabled = true
            ))
            withContext(Dispatchers.IO) {
                localAiService.recheckAvailability(AiEnginePreference.OLLAMA)
            }
            _localAiStatus.value = localAiService.statusMessage()
            _localAiSuggestion.value = localAiService.alternativeSuggestion()
            _engineDescriptions.value = localAiService.engineDescriptions()
        }
    }

    /**
     * Refreshes the Ollama model list from the current server.
     */
    fun refreshOllamaModels() {
        viewModelScope.launch {
            _ollamaConnecting.value = true
            val models = withContext(Dispatchers.IO) { localAiService.ollamaService.listModels() }
            _ollamaModels.value = models
            _ollamaConnecting.value = false
        }
    }

    /**
     * Scans for available MediaPipe model files on the device.
     */
    fun discoverModels() {
        viewModelScope.launch {
            val models = withContext(Dispatchers.IO) {
                localAiService.mediaPipeLlm.discoverModels()
            }
            _discoveredModels.value = models
        }
    }

    /**
     * Loads a MediaPipe model from the given path and updates engine status.
     */
    fun loadMediaPipeModel(modelPath: String) {
        viewModelScope.launch {
            _isLoadingModel.value = true
            _localAiStatus.value = "Loading model…"
            val success = localAiService.mediaPipeLlm.loadModel(modelPath)
            _isLoadingModel.value = false

            if (success) {
                // Save the model path and switch engine
                val settings = repository.appData.value.settings
                repository.updateSettings(settings.copy(
                    mediapipeModelPath = modelPath,
                    aiEnginePreference = AiEnginePreference.MEDIAPIPE_LLM,
                    localAiEnabled = true
                ))
                withContext(Dispatchers.IO) {
                    localAiService.recheckAvailability(AiEnginePreference.MEDIAPIPE_LLM)
                }
            }
            _localAiStatus.value = localAiService.statusMessage()
            _localAiSuggestion.value = localAiService.alternativeSuggestion()
            _engineDescriptions.value = localAiService.engineDescriptions()
        }
    }

    // ── Model catalog & download ─────────────────────────────────

    /** The built-in model catalog from MediaPipeLlmService. */
    val modelCatalog: List<MediaPipeLlmService.CatalogModel>
        get() = localAiService.mediaPipeLlm.modelCatalog

    /** Download progress (0.0–1.0) for the active model download. */
    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()

    /** Whether a model download is currently in progress. */
    private val _isDownloadingModel = MutableStateFlow(false)
    val isDownloadingModel: StateFlow<Boolean> = _isDownloadingModel.asStateFlow()

    /** Error message from the last download attempt, if any. */
    private val _downloadError = MutableStateFlow<String?>(null)
    val downloadError: StateFlow<String?> = _downloadError.asStateFlow()

    /**
     * Checks if a catalog model is already downloaded to local storage.
     */
    fun isModelDownloaded(model: MediaPipeLlmService.CatalogModel): Boolean =
        localAiService.mediaPipeLlm.isModelDownloaded(model)

    /**
     * Downloads a model from the catalog, then auto-loads it.
     */
    fun downloadCatalogModel(model: MediaPipeLlmService.CatalogModel) {
        viewModelScope.launch {
            _isDownloadingModel.value = true
            _downloadProgress.value = 0f
            _downloadError.value = null
            _localAiStatus.value = "Downloading ${model.name}…"

            val hfToken = repository.appData.value.settings.huggingFaceToken
            val path = localAiService.mediaPipeLlm.downloadModel(model, hfToken) { progress ->
                _downloadProgress.value = progress
            }

            _isDownloadingModel.value = false

            if (path != null) {
                // Auto-load the downloaded model
                loadMediaPipeModel(path)
                // Re-discover models to include the new file
                discoverModels()
            } else {
                _downloadError.value = localAiService.mediaPipeLlm.downloadError
                _localAiStatus.value = localAiService.mediaPipeLlm.downloadError
                    ?: "Download failed"
            }
        }
    }

    /**
     * Deletes a downloaded catalog model from local storage.
     */
    fun deleteCatalogModel(model: MediaPipeLlmService.CatalogModel) {
        val currentPath = localAiService.mediaPipeLlm.catalogModelPath(model)
        // If this model is currently loaded, release it
        if (repository.appData.value.settings.mediapipeModelPath == currentPath) {
            localAiService.mediaPipeLlm.releaseModel()
            viewModelScope.launch {
                val settings = repository.appData.value.settings
                repository.updateSettings(settings.copy(mediapipeModelPath = ""))
                _localAiStatus.value = localAiService.statusMessage()
                _engineDescriptions.value = localAiService.engineDescriptions()
            }
        }
        localAiService.mediaPipeLlm.deleteModel(model)
        discoverModels()
    }

    // ── Model file import (SAF file picker) ───────────────────────

    /** Status message from the last model import attempt. */
    private val _modelImportMessage = MutableStateFlow<String?>(null)
    val modelImportMessage: StateFlow<String?> = _modelImportMessage.asStateFlow()

    /**
     * Imports a model file from a content URI (picked via SAF).
     * Copies it to the app-private models directory, then auto-loads it.
     */
    fun importModelFile(uri: Uri) {
        viewModelScope.launch {
            _isLoadingModel.value = true
            _localAiStatus.value = "Importing model file…"
            _modelImportMessage.value = null

            // Derive file name from URI path or use a default
            val segments = uri.lastPathSegment?.split("/")
            val rawName = segments?.lastOrNull()?.takeIf { it.isNotBlank() } ?: "imported_model.task"
            // Sanitize name to just keep the file name
            val fileName = rawName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")

            val path = localAiService.mediaPipeLlm.importModelFile(uri, fileName)
            _isLoadingModel.value = false

            if (path != null) {
                _modelImportMessage.value = "Model imported successfully"
                loadMediaPipeModel(path)
                discoverModels()
            } else {
                _modelImportMessage.value = "Failed to import model file. Make sure it's a valid .task, .bin, or .tflite file (>1 MB)."
                _localAiStatus.value = localAiService.statusMessage()
            }
        }
    }

    /** Whether the Google AI Edge Gallery app is installed on device. */
    fun isGalleryInstalled(): Boolean = localAiService.mediaPipeLlm.isGalleryInstalled()

    /**
     * Returns a category using on-device AI when enabled, otherwise falls back to rules.
     * When AI suggests a category that doesn't exist yet, it is auto-created.
     */
    private suspend fun smartCategorize(description: String, isExpense: Boolean = true): String {
        val settings = repository.appData.value.settings
        val userCatNames = repository.appData.value.categories
            .filter { !it.isDefault }.map { it.name }
        if (settings.localAiEnabled) {
            val categoryNames = repository.appData.value.categories.map { it.name }
            val aiCategory = localAiService.categorize(description, categoryNames, isExpense, userCatNames)
            if (aiCategory != null) {
                repository.ensureCategoryExists(aiCategory)
                return aiCategory
            }
        }
        val category = aiEngine.categorize(description, isExpense, userCatNames)
        repository.ensureCategoryExists(category)
        return category
    }

    fun addTransaction(
        amount: Double, description: String, category: String? = null,
        type: TransactionType = TransactionType.EXPENSE,
        source: TransactionSource = TransactionSource.MANUAL,
        merchantName: String = "", notes: String = "",
        timestamp: Long = System.currentTimeMillis()
    ) {
        viewModelScope.launch {
            val finalCategory = category ?: smartCategorize(description)
            val dtFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            val added = repository.addTransaction(Transaction(
                amount = amount, description = description, category = finalCategory,
                type = type, source = source, merchantName = merchantName, notes = notes,
                timestamp = timestamp, dateTime = dtFormatter.format(Date(timestamp))
            ))
            if (added) refreshSuggestions()
        }
    }

    fun deleteTransaction(id: String) { viewModelScope.launch { repository.deleteTransaction(id) } }

    /**
     * Parses OCR/QR receipt data and stores it in [ocrParsedData] for the user to review
     * and edit before saving. Does NOT save the transaction automatically.
     */
    fun processOcrText(ocrText: String, qrData: String? = null) {
        viewModelScope.launch {
            try {
                val settings = repository.appData.value.settings
                val currencyCode = settings.currencyCode
                val currencySymbol = currencyInfoFor(currencyCode).symbol

                val ocrParsed = if (ocrText.isNotBlank()) aiEngine.parseReceiptText(ocrText, currencyCode) else null
                val ocrAmount = (ocrParsed?.totalAmount ?: ocrParsed?.items?.sumOf { it.second }) ?: 0.0

                val qrParsed = if (!qrData.isNullOrBlank()) aiEngine.parseQrCodeString(qrData) else null
                val qrAmount = qrParsed?.totalAmount ?: 0.0

                val (parsed, fromQr) = if (qrAmount > 0 && qrParsed != null) {
                    val merchant = if (qrParsed.merchantName in listOf("QR Receipt", "Unknown") && ocrParsed != null && ocrParsed.merchantName.isNotBlank() && ocrParsed.merchantName != "Unknown") {
                        ocrParsed.merchantName
                    } else {
                        qrParsed.merchantName
                    }
                    qrParsed.copy(merchantName = merchant, items = ocrParsed?.items ?: emptyList()) to true
                } else if (ocrAmount > 0 && ocrParsed != null) {
                    ocrParsed to false
                } else {
                    (ocrParsed ?: qrParsed ?: AiExpenseEngine.ParsedReceipt(null, emptyList(), "Unknown", null)) to (qrParsed != null)
                }

                val amount = parsed.totalAmount ?: parsed.items.sumOf { it.second }

                if (amount > 0) {
                    val category = smartCategorize(
                        "${parsed.merchantName} ${parsed.items.joinToString(" ") { it.first }}"
                    )
                    _ocrParsedData.value = OcrParsedData(
                        amount = amount,
                        merchantName = parsed.merchantName,
                        category = category,
                        items = parsed.items,
                        fromQr = fromQr,
                        rawOcrText = ocrText,
                        currencySymbol = currencySymbol
                    )
                    _uiState.value = _uiState.value.copy(lastOcrResult = null)
                } else {
                    _ocrParsedData.value = null
                    _uiState.value = _uiState.value.copy(
                        lastOcrResult = "Could not extract amount from receipt" +
                            if (qrData.isNullOrBlank()) "." else " or QR code."
                    )
                }
            } catch (e: Exception) {
                _ocrParsedData.value = null
                _uiState.value = _uiState.value.copy(lastOcrResult = "OCR error: ${e.message}")
            }
        }
    }

    /**
     * Saves the user-reviewed OCR transaction after edits.
     */
    fun confirmOcrTransaction(amount: Double, merchantName: String, category: String) {
        viewModelScope.launch {
            try {
                val settings = repository.appData.value.settings
                val currencyCode = settings.currencyCode
                val currencySymbol = currencyInfoFor(currencyCode).symbol
                val data = _ocrParsedData.value

                val items = data?.items ?: emptyList()
                val fromQr = data?.fromQr ?: false

                val itemsNote = if (items.isNotEmpty())
                    "Items: ${items.joinToString(", ") { "${it.first}: $currencySymbol${String.format("%.2f", it.second)}" }}"
                else ""
                val sourceNote = if (fromQr) "Parsed from QR code" else ""

                val aiNote: String = if (settings.localAiEnabled) {
                    val insight = withContext(Dispatchers.IO) {
                        localAiService.generateInsight(
                            totalExpenses = amount, totalIncome = 0.0,
                            topCategory = category, topCategoryAmount = amount,
                            transactionCount = 1, currencyCode = currencyCode
                        )
                    }
                    if (insight != null) "\nAI: $insight" else ""
                } else ""

                repository.ensureCategoryExists(category)

                val now = System.currentTimeMillis()
                val dtFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                repository.addTransaction(Transaction(
                    amount = amount,
                    description = "Receipt: $merchantName",
                    category = category,
                    type = TransactionType.EXPENSE,
                    source = TransactionSource.OCR_SCAN,
                    merchantName = merchantName,
                    timestamp = now,
                    dateTime = dtFormatter.format(Date(now)),
                    notes = listOf(itemsNote, sourceNote, aiNote).filter { it.isNotBlank() }.joinToString("\n")
                ))

                val resultMsg = buildString {
                    append("Found: $merchantName — $currencySymbol${String.format("%.2f", amount)}")
                    if (category.isNotEmpty()) append(" · $category")
                    if (fromQr) append(" (from QR)")
                }
                _uiState.value = _uiState.value.copy(lastOcrResult = resultMsg)
                _ocrParsedData.value = null
                refreshSuggestions()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(lastOcrResult = "Save error: ${e.message}")
            }
        }
    }

    fun clearOcrData() {
        _ocrParsedData.value = null
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
            ReportPeriod.CUSTOM -> DateUtils.getStartOfMonth(now) to DateUtils.getEndOfMonth(now) // fallback; use generateReportForRange for custom
        }
        val currencyCode = repository.appData.value.settings.currencyCode
        return aiEngine.generateReport(repository.appData.value.transactions, period, start, end, currencyCode)
    }

    /**
     * Generates a report for any arbitrary month/year (0-based month, matching [Calendar.MONTH]).
     * Called by the month selector in ReportsScreen.
     */
    fun generateReportForMonth(year: Int, month: Int): ExpenseReport {
        val startCal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val endCal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, startCal.getActualMaximum(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        val currencyCode = repository.appData.value.settings.currencyCode
        return aiEngine.generateReport(
            repository.appData.value.transactions,
            ReportPeriod.MONTHLY,
            startCal.timeInMillis,
            endCal.timeInMillis,
            currencyCode
        )
    }

    /**
     * Generates a report for an arbitrary date range (Custom period).
     */
    fun generateReportForRange(startMillis: Long, endMillis: Long): ExpenseReport {
        val currencyCode = repository.appData.value.settings.currencyCode
        return aiEngine.generateReport(
            repository.appData.value.transactions,
            ReportPeriod.CUSTOM,
            startMillis,
            endMillis,
            currencyCode
        )
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
    fun deleteCategory(id: String) { viewModelScope.launch { repository.deleteCategory(id) } }

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

    fun setMonthlyExpenseLimit(limit: Double) {
        viewModelScope.launch {
            val settings = repository.appData.value.settings
            // Reset last-alert month so the new limit can fire immediately if already exceeded
            repository.updateSettings(settings.copy(monthlyExpenseLimit = limit, lastThresholdAlertMonth = ""))
        }
    }

    // ─── Salary Scheduler ─────────────────────────────────────────

    fun configureSalaryScheduler(
        enabled: Boolean,
        amount: Double,
        dayOfMonth: Int,
        description: String
    ) {
        viewModelScope.launch {
            val settings = repository.appData.value.settings
            repository.updateSettings(
                settings.copy(
                    scheduledSalaryEnabled = enabled,
                    scheduledSalaryAmount = amount,
                    scheduledSalaryDayOfMonth = dayOfMonth.coerceIn(1, 31),
                    scheduledSalaryDescription = description.ifBlank { "Monthly Salary" }
                )
            )
            val appContext = getApplication<android.app.Application>().applicationContext
            if (enabled && amount > 0) {
                SalarySchedulerWorker.schedule(appContext)
            } else {
                SalarySchedulerWorker.cancel(appContext)
            }
        }
    }

    // ─── Currency Converter ────────────────────────────────────────

    /**
     * Fetches live exchange rates (base = USD) and stores them in [exchangeRates].
     * Safe to call multiple times — results are cached for 1 hour.
     */
    fun fetchExchangeRates() {
        viewModelScope.launch {
            val settings = repository.appData.value.settings
            val rates = withContext(Dispatchers.IO) {
                when (settings.rateSource) {
                    com.smartexpense.tracker.data.model.RateSource.RATE_AM ->
                        CurrencyConverterService.getRatesFromRateAm()
                            ?: CurrencyConverterService.getRates("USD")  // fallback to API
                    else ->
                        CurrencyConverterService.getRates("USD")
                }
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

    fun loadTotalSmsCount() {
        viewModelScope.launch {
            val count = withContext(Dispatchers.IO) {
                try {
                    com.smartexpense.tracker.service.sms.SmsInboxScanner(getApplication())
                        .getTotalSmsCount()
                } catch (_: Throwable) { 0 }
            }
            _totalSmsCount.value = count
        }
    }

    fun startSmsScan(maxMessages: Int = 500, startDate: Long? = null, endDate: Long? = null) {
        viewModelScope.launch {
            _smsScanState.value = SmsScanState(isScanning = true)
            try {
                val existingNotes: Set<String> = try {
                    repository.appData.value.transactions
                        .filter { it.source == TransactionSource.SMS }
                        .mapNotNull { it.notes.takeIf { n -> n.isNotEmpty() } }
                        .toSet()
                } catch (_: Throwable) { emptySet() }

                val settings = repository.appData.value.settings
                val userCatNames = repository.appData.value.categories
                    .filter { !it.isDefault }.map { it.name }
                val result = withContext(Dispatchers.IO) {
                    try {
                        com.smartexpense.tracker.service.sms.SmsInboxScanner(getApplication())
                            .scanInbox(
                                maxMessages = maxMessages,
                                existingTransactionNotes = existingNotes,
                                userCategoryNames = userCatNames,
                                startDate = startDate,
                                endDate = endDate,
                                customIncomeKeywords = settings.incomeKeywords,
                                customExpenseKeywords = settings.expenseKeywords
                            )
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
            _smsScanState.value = _smsScanState.value.copy(
                isSaving = true, savingProgress = 0, savingTotal = pending.size
            )
            val settings = repository.appData.value.settings
            val appCurrency = settings.currencyCode

            for ((index, tx) in pending.withIndex()) {
                _smsScanState.value = _smsScanState.value.copy(savingProgress = index + 1)

                // Currency conversion: if parsed currency ≠ app currency, convert amount
                val parsedCurrency = tx.notes.lines()
                    .find { it.startsWith("parsedCurrency:") }?.removePrefix("parsedCurrency:") ?: ""

                val (finalAmount, conversionNote) = if (
                    parsedCurrency.isNotEmpty() && parsedCurrency != appCurrency
                ) {
                    val converted = withContext(Dispatchers.IO) {
                        com.smartexpense.tracker.service.currency.CurrencyConverterService.convert(
                            tx.amount, parsedCurrency, appCurrency
                        )
                    }
                    if (converted != null) {
                        val rate = converted / tx.amount
                        val fromSym = currencyInfoFor(parsedCurrency).symbol
                        converted to "Original: $fromSym${String.format("%.2f", tx.amount)} $parsedCurrency · 1 $parsedCurrency = ${String.format("%.4f", rate)} $appCurrency"
                    } else {
                        tx.amount to ""
                    }
                } else {
                    tx.amount to ""
                }

                // Remove parsedCurrency marker, append conversion note if present
                val cleanNotes = tx.notes.lines()
                    .filter { !it.startsWith("parsedCurrency:") }
                    .joinToString("\n")
                    .let { base -> if (conversionNote.isNotEmpty()) "$base\n$conversionNote".trim() else base.trim() }

                val finalTx = tx.copy(amount = finalAmount, notes = cleanNotes)

                // Auto-create category if not in the existing list
                repository.ensureCategoryExists(finalTx.category)
                repository.addTransaction(finalTx)
            }
            _smsScanState.value = _smsScanState.value.copy(
                isSaving = false, pendingTransactions = emptyList(),
                savedCount = pending.size, savingProgress = 0, savingTotal = 0
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

    // ─── Banking App Scanner ──────────────────────────────────────

    data class DiscoveredApp(
        val packageName: String,
        val appName: String,
        val isAlreadyMonitored: Boolean
    )

    private val _discoveredBankingApps = MutableStateFlow<List<DiscoveredApp>>(emptyList())
    val discoveredBankingApps: StateFlow<List<DiscoveredApp>> = _discoveredBankingApps.asStateFlow()

    private val _isScanningBankingApps = MutableStateFlow(false)
    val isScanningBankingApps: StateFlow<Boolean> = _isScanningBankingApps.asStateFlow()

    /** All user-installed (non-system) applications on the device. */
    data class InstalledApp(
        val packageName: String,
        val appName: String
    )

    private val _allInstalledApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val allInstalledApps: StateFlow<List<InstalledApp>> = _allInstalledApps.asStateFlow()

    /**
     * Scans all installed applications in two passes using the user-configured
     * [AppSettings.scanKeywords]:
     * 1. Apps whose **name** (label) contains any keyword
     * 2. Apps whose **package name** contains any keyword
     * Results are merged, duplicates suppressed, and stored in [discoveredBankingApps].
     */
    fun scanForBankingApps() {
        viewModelScope.launch {
            _isScanningBankingApps.value = true
            val pm = getApplication<android.app.Application>().packageManager
            val settings = repository.appData.value.settings
            val currentPackages = settings.bankingAppPackages.toSet()
            val keywords = settings.scanKeywords.map { it.lowercase() }

            val installed = withContext(Dispatchers.IO) {
                val seenPackages = mutableSetOf<String>()
                val results = mutableListOf<DiscoveredApp>()
                val allApps = pm.getInstalledApplications(0)

                // Pass 1: match by app name (label)
                for (appInfo in allApps) {
                    val label = pm.getApplicationLabel(appInfo).toString()
                    val labelLower = label.lowercase()
                    if (keywords.any { kw -> labelLower.contains(kw) } &&
                        seenPackages.add(appInfo.packageName)) {
                        results += DiscoveredApp(
                            packageName = appInfo.packageName,
                            appName = label,
                            isAlreadyMonitored = appInfo.packageName in currentPackages
                        )
                    }
                }

                // Pass 2: match by package name
                for (appInfo in allApps) {
                    val pkgLower = appInfo.packageName.lowercase()
                    if (keywords.any { kw -> pkgLower.contains(kw) } &&
                        seenPackages.add(appInfo.packageName)) {
                        results += DiscoveredApp(
                            packageName = appInfo.packageName,
                            appName = pm.getApplicationLabel(appInfo).toString(),
                            isAlreadyMonitored = appInfo.packageName in currentPackages
                        )
                    }
                }

                results.sortedWith(compareBy({ it.isAlreadyMonitored }, { it.appName.lowercase() }))
            }
            _discoveredBankingApps.value = installed
            _isScanningBankingApps.value = false
        }
    }

    /**
     * Retrieves all user-visible applications on the device (apps that have a launcher
     * intent, i.e. appear in the app drawer), sorted alphabetically by display name.
     */
    fun loadAllInstalledApps() {
        viewModelScope.launch {
            val pm = getApplication<android.app.Application>().packageManager
            val apps = withContext(Dispatchers.IO) {
                val launchIntent = android.content.Intent(android.content.Intent.ACTION_MAIN, null)
                launchIntent.addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                pm.queryIntentActivities(launchIntent, 0)
                    .map { resolveInfo ->
                        val appInfo = resolveInfo.activityInfo.applicationInfo
                        InstalledApp(
                            packageName = appInfo.packageName,
                            appName = pm.getApplicationLabel(appInfo).toString()
                        )
                    }
                    .distinctBy { it.packageName }
                    .sortedBy { it.appName.lowercase() }
            }
            _allInstalledApps.value = apps
        }
    }

    /**
     * Updates the scan keywords used to discover banking apps.
     */
    fun updateScanKeywords(keywords: List<String>) {
        viewModelScope.launch {
            val settings = repository.appData.value.settings
            val updated = settings.copy(scanKeywords = keywords)
            repository.updateSettings(updated)
        }
    }

    fun updateIncomeKeywords(keywords: List<String>) {
        viewModelScope.launch {
            val settings = repository.appData.value.settings
            repository.updateSettings(settings.copy(incomeKeywords = keywords))
        }
    }

    fun updateExpenseKeywords(keywords: List<String>) {
        viewModelScope.launch {
            val settings = repository.appData.value.settings
            repository.updateSettings(settings.copy(expenseKeywords = keywords))
        }
    }

    /**
     * Adds a banking app package to the monitored list in settings.
     */
    fun addBankingApp(packageName: String) {
        viewModelScope.launch {
            val settings = repository.appData.value.settings
            if (packageName !in settings.bankingAppPackages) {
                val updated = settings.copy(
                    bankingAppPackages = settings.bankingAppPackages + packageName
                )
                repository.updateSettings(updated)
                // Refresh discovered list to update isAlreadyMonitored flags
                scanForBankingApps()
            }
        }
    }

    /**
     * Removes a banking app package from the monitored list.
     */
    fun removeBankingApp(packageName: String) {
        viewModelScope.launch {
            val settings = repository.appData.value.settings
            val updated = settings.copy(
                bankingAppPackages = settings.bankingAppPackages.filter { it != packageName }
            )
            repository.updateSettings(updated)
            scanForBankingApps()
        }
    }

    // ─── In-App Notification Management ───────────────────────────

    fun markNotificationRead(id: String) {
        viewModelScope.launch { repository.markNotificationRead(id) }
    }

    fun markAllNotificationsRead() {
        viewModelScope.launch { repository.markAllNotificationsRead() }
    }

    fun deleteNotification(id: String) {
        viewModelScope.launch { repository.deleteNotification(id) }
    }

    fun clearAllInAppNotifications() {
        viewModelScope.launch { repository.clearNotifications() }
    }
}

data class SmsScanState(
    val isScanning: Boolean = false, val isComplete: Boolean = false,
    val isSaving: Boolean = false,
    val totalScanned: Int = 0, val financialFound: Int = 0,
    val transactionsParsed: Int = 0,
    val pendingTransactions: List<Transaction> = emptyList(),
    val savedCount: Int = 0, val errors: Int = 0,
    val errorMessage: String? = null,
    val savingProgress: Int = 0, val savingTotal: Int = 0
)

/**
 * Holds parsed OCR/QR receipt data for the editable review form.
 */
data class OcrParsedData(
    val amount: Double,
    val merchantName: String,
    val category: String,
    val items: List<Pair<String, Double>> = emptyList(),
    val fromQr: Boolean = false,
    val rawOcrText: String = "",
    val currencySymbol: String = "$"
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
