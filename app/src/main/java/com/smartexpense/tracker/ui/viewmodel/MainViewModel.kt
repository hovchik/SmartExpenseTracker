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
import com.smartexpense.tracker.util.LocationProvider
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

    /** Subscription manager for premium feature gating. */
    val subscriptionManager = (application as SmartExpenseApp).subscriptionManager
    val isSubscribed: StateFlow<Boolean> = subscriptionManager.isSubscribed

    /** On-device Gemini Nano service – null responses mean "not available / not enabled". */
    private val localAiService = LocalAiService(application.applicationContext)

    // ── Tri-mode AI (must be initialized before init block) ───────────
    private val localModelManager = com.smartexpense.tracker.ai.modelmanager.LocalModelManager(
        application.applicationContext
    )
    private val aiProviderSelector = com.smartexpense.tracker.ai.provider.AiProviderSelector(
        application.applicationContext,
        localModelManager
    )
    private val benchmarkRunner = com.smartexpense.tracker.ai.benchmark.LocalAiBenchmarkRunner()

    /** Human-readable AI backend status shown in Settings (null = not yet checked). */
    private val _localAiStatus = MutableStateFlow<String?>(null)
    val localAiStatus: StateFlow<String?> = _localAiStatus.asStateFlow()

    /** Suggestion for enabling a better AI engine; null when none is needed. */
    private val _localAiSuggestion = MutableStateFlow<String?>(null)
    val localAiSuggestion: StateFlow<String?> = _localAiSuggestion.asStateFlow()

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** Dashboard section order for drag-and-drop reordering. */
    private val _dashboardSectionOrder = MutableStateFlow(DashboardSection.entries.toList())
    val dashboardSectionOrder: StateFlow<List<DashboardSection>> = _dashboardSectionOrder.asStateFlow()

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

    /**
     * Cached conversion rates keyed by base currency, e.g. "USD" → {"AMD" → 389.5, …}.
     * Populated lazily when [convertAmount] encounters a cross-currency transaction.
     */
    @Volatile
    private var conversionRateCache: Map<String, Map<String, Double>> = emptyMap()

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

    /** Saved OCR scan sections (receipts with items and costs). */
    private val _ocrSections = MutableStateFlow<List<OcrSection>>(emptyList())
    val ocrSections: StateFlow<List<OcrSection>> = _ocrSections.asStateFlow()

    /** AI conversation history (prompt + response, grouped by date in UI). */
    private val _aiConversations = MutableStateFlow<List<AiConversation>>(emptyList())
    val aiConversations: StateFlow<List<AiConversation>> = _aiConversations.asStateFlow()

    // ── Tri-mode AI state (must be declared before init) ──────────────
    private val _aiModeStatus = MutableStateFlow<String?>(null)
    val aiModeStatus: StateFlow<String?> = _aiModeStatus.asStateFlow()
    private val _aiPrivacyMessage = MutableStateFlow<String?>(null)
    val aiPrivacyMessage: StateFlow<String?> = _aiPrivacyMessage.asStateFlow()
    private val _deviceCapability = MutableStateFlow<com.smartexpense.tracker.ai.capability.DeviceAiCapabilityDetector.DeviceCapability?>(null)
    val deviceCapability: StateFlow<com.smartexpense.tracker.ai.capability.DeviceAiCapabilityDetector.DeviceCapability?> = _deviceCapability.asStateFlow()
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()
    private val _catalogModels = MutableStateFlow<List<com.smartexpense.tracker.ai.modelmanager.LocalAiModel>>(emptyList())
    val catalogModels: StateFlow<List<com.smartexpense.tracker.ai.modelmanager.LocalAiModel>> = _catalogModels.asStateFlow()
    val modelDownloadState: StateFlow<com.smartexpense.tracker.ai.modelmanager.ModelDownloadManager.DownloadState> =
        localModelManager.downloads.downloadState
    private val _benchmarkResult = MutableStateFlow<com.smartexpense.tracker.ai.benchmark.LocalAiBenchmarkRunner.BenchmarkResult?>(null)
    val benchmarkResult: StateFlow<com.smartexpense.tracker.ai.benchmark.LocalAiBenchmarkRunner.BenchmarkResult?> = _benchmarkResult.asStateFlow()
    private val _isRunningBenchmark = MutableStateFlow(false)
    val isRunningBenchmark: StateFlow<Boolean> = _isRunningBenchmark.asStateFlow()
    private val _installedModelName = MutableStateFlow("")
    val installedModelName: StateFlow<String> = _installedModelName.asStateFlow()
    private val _modelStorageUsageMb = MutableStateFlow(0L)
    val modelStorageUsageMb: StateFlow<Long> = _modelStorageUsageMb.asStateFlow()
    private val _wizardImportMessage = MutableStateFlow<String?>(null)
    val wizardImportMessage: StateFlow<String?> = _wizardImportMessage.asStateFlow()

    /** Whether a HuggingFace token is configured. */
    private val _hasHuggingFaceToken = MutableStateFlow(false)
    val hasHuggingFaceToken: StateFlow<Boolean> = _hasHuggingFaceToken.asStateFlow()

    /** HuggingFace username if token is valid. */
    private val _huggingFaceUsername = MutableStateFlow<String?>(null)
    val huggingFaceUsername: StateFlow<String?> = _huggingFaceUsername.asStateFlow()

    /** Token validation error message shown to user, null when no error. */
    private val _tokenValidationError = MutableStateFlow<String?>(null)
    val tokenValidationError: StateFlow<String?> = _tokenValidationError.asStateFlow()

    /** The ID of the currently active local model. */
    private val _activeModelId = MutableStateFlow("")
    val activeModelId: StateFlow<String> = _activeModelId.asStateFlow()

    /** Descriptions for each engine option, updated after availability check. */
    private val _engineDescriptions = MutableStateFlow<Map<AiEnginePreference, String>>(emptyMap())
    val engineDescriptions: StateFlow<Map<AiEnginePreference, String>> = _engineDescriptions.asStateFlow()

    init {
        viewModelScope.launch {
            repository.initialize()
            val settings = repository.appData.value.settings
            _themeMode.value = settings.themeMode

            // Restore dashboard section order from settings
            _dashboardSectionOrder.value = restoreSectionOrder(settings.dashboardSectionOrder)

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

            // Initialize tri-mode AI provider selector
            initAiProviderSelector()
            refreshCatalogModels()

            refreshSuggestions()
            scheduleRateRefresh()
            // Warm up the location cache so background receivers (SMS, notifications)
            // can fall back to a recent foreground fix.
            currentLocation()
            repository.appData.collect { data ->
                _themeMode.value = data.settings.themeMode
                _inAppNotifications.value = data.inAppNotifications
                _ocrSections.value = data.ocrSections
                _aiConversations.value = data.aiConversations
                updateUiState(data)
            }
        }
    }

    // ─── Currency conversion helper ─────────────────────────────────────

    /**
     * Returns [Transaction.amount] converted to [targetCurrency].
     *
     * When the transaction carries original-currency metadata (e.g. 800 RUB)
     * we always convert from the *original* amount/currency so that switching
     * display currency never compounds rounding errors (RUB→USD→AMD).
     * Falls back to the unconverted amount when rates are unavailable.
     */
    fun convertAmount(tx: Transaction, targetCurrency: String): Double {
        // Guard against null leaking from JSON deserialisation of older data
        val origCode = tx.originalCurrencyCode.orEmpty()
        val origAmt = tx.originalAmount

        // ── Path 1: transaction has original foreign-currency metadata ──
        if (origAmt > 0.0 && origCode.isNotEmpty()) {
            if (origCode == targetCurrency) return origAmt
            val rateMap = conversionRateCache[origCode]
            val rate = rateMap?.get(targetCurrency)
            if (rate != null) return origAmt * rate
            // Fall through to stored amount if rate unavailable
        }
        // ── Path 2: no original metadata – use stored amount & currency ──
        val txCur = tx.currencyCode.orEmpty().ifEmpty { targetCurrency }
        if (txCur == targetCurrency) return tx.amount
        val rateMap = conversionRateCache[txCur]
        val rate = rateMap?.get(targetCurrency)
        return if (rate != null) tx.amount * rate else tx.amount
    }

    /**
     * Pre-fetches exchange rates for every distinct currency found in
     * the current transaction list so that [convertAmount] can work
     * synchronously.  Includes both stored currencies AND original
     * foreign currencies so cross-rate conversions work correctly.
     */
    private fun preloadConversionRates(transactions: List<Transaction>, appCurrency: String) {
        val currencies = buildSet {
            for (tx in transactions) {
                add(tx.currencyCode.orEmpty().ifEmpty { appCurrency })
                val origCode = tx.originalCurrencyCode.orEmpty()
                if (origCode.isNotEmpty()) add(origCode)
            }
        }.filter { it != appCurrency }
        if (currencies.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val newCache = conversionRateCache.toMutableMap()
            for (cur in currencies) {
                if (newCache.containsKey(cur)) continue
                val rates = CurrencyConverterService.getRates(cur)
                if (rates != null) newCache[cur] = rates
            }
            conversionRateCache = newCache
        }
    }

    private fun updateUiState(data: AppData) {
        val now = System.currentTimeMillis()
        val appCurrency = data.settings.currencyCode
        val startOfMonth = DateUtils.getStartOfMonth(now)
        val endOfMonth = DateUtils.getEndOfMonth(now)
        val startOfWeek = DateUtils.getStartOfWeek(now)
        val endOfWeek = DateUtils.getEndOfWeek(now)
        val startOfDay = DateUtils.getStartOfDay(now)
        val endOfDay = DateUtils.getEndOfDay(now)

        // Pre-fetch rates for cross-currency transactions (async; first render uses raw amounts)
        preloadConversionRates(data.transactions, appCurrency)

        val monthlyExpenses = data.transactions
            .filter { it.type == TransactionType.EXPENSE && it.timestamp in startOfMonth..endOfMonth }
            .sumOf { convertAmount(it, appCurrency) }
        val monthlyIncome = data.transactions
            .filter { it.type == TransactionType.INCOME && it.timestamp in startOfMonth..endOfMonth }
            .sumOf { convertAmount(it, appCurrency) }
        val todayExpenses = data.transactions
            .filter { it.type == TransactionType.EXPENSE && it.timestamp in startOfDay..endOfDay }
            .sumOf { convertAmount(it, appCurrency) }
        val weeklyExpenses = data.transactions
            .filter { it.type == TransactionType.EXPENSE && it.timestamp in startOfWeek..endOfWeek }
            .sumOf { convertAmount(it, appCurrency) }

        val allTransactionsSorted = data.transactions.sortedByDescending { it.timestamp }
        val recentTransactions = allTransactionsSorted.take(20)
        val categoryBreakdown = data.transactions
            .filter { it.type == TransactionType.EXPENSE && it.timestamp in startOfMonth..endOfMonth }
            .groupBy { it.category }
            .mapValues { it.value.sumOf { t -> convertAmount(t, appCurrency) } }
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

    // ─── Dashboard section reordering ────────────────────────────────

    /**
     * Moves a dashboard section from one position to another and persists the new order.
     */
    fun moveDashboardSection(from: DashboardSection, to: DashboardSection) {
        val list = _dashboardSectionOrder.value.toMutableList()
        val fromIdx = list.indexOf(from)
        val toIdx = list.indexOf(to)
        if (fromIdx >= 0 && toIdx >= 0) {
            val item = list.removeAt(fromIdx)
            list.add(toIdx, item)
            _dashboardSectionOrder.value = list
            viewModelScope.launch {
                val settings = repository.appData.value.settings
                repository.updateSettings(settings.copy(dashboardSectionOrder = list.map { it.name }))
            }
        }
    }

    private fun restoreSectionOrder(saved: List<String>): List<DashboardSection> {
        if (saved.isEmpty()) return DashboardSection.entries.toList()
        val restored = saved.mapNotNull { name ->
            try { DashboardSection.valueOf(name) } catch (_: Exception) { null }
        }
        // Append any new sections that weren't in the saved order
        val missing = DashboardSection.entries.filter { it !in restored }
        return (restored + missing).ifEmpty { DashboardSection.entries.toList() }
    }

    // ─── Local AI Engine ────────────────────────────────────────────

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
                aiEnginePreference = preference
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

    // ─── Tri-Mode AI Architecture ─────────────────────────────────────

    /** Sets the AI execution mode and persists it. */
    fun setAiMode(mode: com.smartexpense.tracker.data.model.AiModePreference) {
        viewModelScope.launch {
            val settings = repository.appData.value.settings
            repository.updateSettings(settings.copy(aiModePreference = mode))
            _aiModeStatus.value = "Switching AI mode..."
            withContext(Dispatchers.IO) { aiProviderSelector.selectProvider(mode) }
            _aiModeStatus.value = aiProviderSelector.statusMessage()
            _aiPrivacyMessage.value = aiProviderSelector.privacyMessage()
            refreshModelInfo()
        }
    }

    /** Scans device capabilities for AI support. */
    fun scanDeviceCapabilities() {
        viewModelScope.launch {
            _isScanning.value = true
            val capability = withContext(Dispatchers.IO) {
                localModelManager.capabilities.detect()
            }
            _deviceCapability.value = capability
            _isScanning.value = false
        }
    }

    /** Refreshes catalog models with current install state. */
    fun refreshCatalogModels() {
        _catalogModels.value = localModelManager.getCatalogModels()
        refreshModelInfo()
    }

    /** Downloads a catalog model. */
    fun downloadCatalogModelNew(model: com.smartexpense.tracker.ai.modelmanager.LocalAiModel) {
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) {
                localModelManager.downloadModel(model)
            }
            if (path != null) {
                refreshCatalogModels()
                // Auto-load the downloaded model
                withContext(Dispatchers.IO) {
                    aiProviderSelector.customLocalProvider.loadModel(
                        model.copy(localPath = path, installState = com.smartexpense.tracker.ai.modelmanager.InstallState.INSTALLED)
                    )
                }
                // Auto-switch to LOCAL_MODEL mode so the downloaded model is actually used
                val settings = repository.appData.value.settings
                if (settings.aiModePreference != AiModePreference.LOCAL_MODEL) {
                    repository.updateSettings(settings.copy(
                        aiModePreference = AiModePreference.LOCAL_MODEL,
                        activeLocalModelId = model.modelId
                    ))
                    withContext(Dispatchers.IO) {
                        aiProviderSelector.selectProvider(AiModePreference.LOCAL_MODEL)
                    }
                }
                _aiModeStatus.value = aiProviderSelector.statusMessage()
                _aiPrivacyMessage.value = aiProviderSelector.privacyMessage()
            }
        }
    }

    /** Cancels in-progress download. */
    fun cancelModelDownload() {
        localModelManager.downloads.cancelDownload()
    }

    /** Sets the active local model and loads it into the provider. */
    fun setActiveLocalModel(model: com.smartexpense.tracker.ai.modelmanager.LocalAiModel) {
        viewModelScope.launch {
            localModelManager.setActiveModel(model.modelId)
            withContext(Dispatchers.IO) {
                aiProviderSelector.customLocalProvider.loadModel(model)
            }
            // Auto-switch to LOCAL_MODEL mode so the selected model is actually used
            val settings = repository.appData.value.settings
            if (settings.aiModePreference != AiModePreference.LOCAL_MODEL) {
                repository.updateSettings(settings.copy(
                    aiModePreference = AiModePreference.LOCAL_MODEL,
                    activeLocalModelId = model.modelId
                ))
                withContext(Dispatchers.IO) {
                    aiProviderSelector.selectProvider(AiModePreference.LOCAL_MODEL)
                }
            }
            _aiModeStatus.value = aiProviderSelector.statusMessage()
            _aiPrivacyMessage.value = aiProviderSelector.privacyMessage()
            refreshModelInfo()
            refreshCatalogModels()
        }
    }

    /** Deletes a downloaded local model and refreshes the catalog. */
    fun deleteLocalModel(model: com.smartexpense.tracker.ai.modelmanager.LocalAiModel) {
        viewModelScope.launch {
            // If deleting the active model, release it from the provider first
            if (model.modelId == _activeModelId.value) {
                aiProviderSelector.customLocalProvider.release()
            }
            localModelManager.deleteModel(model)
            refreshCatalogModels()
            refreshModelInfo()
            _aiModeStatus.value = aiProviderSelector.statusMessage()
        }
    }

    /** Runs benchmark against the active provider. */
    fun runBenchmark() {
        viewModelScope.launch {
            _isRunningBenchmark.value = true
            try {
                val result = withContext(Dispatchers.IO) {
                    benchmarkRunner.runBenchmark(aiProviderSelector.getActiveProvider())
                }
                _benchmarkResult.value = result
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Benchmark failed", e)
                _benchmarkResult.value = null
            } finally {
                _isRunningBenchmark.value = false
            }
        }
    }

    /** Initializes the AI provider selector based on saved settings. */
    fun initAiProviderSelector() {
        viewModelScope.launch {
            val settings = repository.appData.value.settings
            if (settings.claudeApiKey.isNotBlank()) {
                aiProviderSelector.cloudProvider.updateApiKey(settings.claudeApiKey)
            }
            // Pass HuggingFace token for gated model downloads
            if (settings.huggingFaceToken.isNotBlank()) {
                localModelManager.setHuggingFaceToken(settings.huggingFaceToken)
                _hasHuggingFaceToken.value = true
                // Validate token in background to get username
                launch(Dispatchers.IO) {
                    when (val result = localModelManager.validateHuggingFaceToken(settings.huggingFaceToken)) {
                        is com.smartexpense.tracker.ai.modelmanager.ModelDownloadManager.TokenValidationResult.Valid -> {
                            _huggingFaceUsername.value = result.username
                        }
                        is com.smartexpense.tracker.ai.modelmanager.ModelDownloadManager.TokenValidationResult.Invalid -> {
                            // Token definitely invalid — clear it
                            _huggingFaceUsername.value = null
                            _hasHuggingFaceToken.value = false
                            localModelManager.setHuggingFaceToken("")
                            repository.updateSettings(settings.copy(huggingFaceToken = ""))
                            _tokenValidationError.value = "Saved HuggingFace token is no longer valid."
                        }
                        is com.smartexpense.tracker.ai.modelmanager.ModelDownloadManager.TokenValidationResult.NetworkError -> {
                            // Network issue — keep token, just don't show username
                            _huggingFaceUsername.value = null
                        }
                    }
                }
            }
            // If the user has an active local model but preference is still AUTO,
            // override to LOCAL_MODEL so the downloaded model is actually used.
            val effectivePreference = if (
                settings.aiModePreference == AiModePreference.AUTO &&
                settings.activeLocalModelId.isNotBlank()
            ) {
                AiModePreference.LOCAL_MODEL
            } else {
                settings.aiModePreference
            }
            withContext(Dispatchers.IO) {
                aiProviderSelector.selectProvider(effectivePreference)
            }
            _aiModeStatus.value = aiProviderSelector.statusMessage()
            _aiPrivacyMessage.value = aiProviderSelector.privacyMessage()
            refreshModelInfo()
        }
    }

    /**
     * Saves and validates a HuggingFace token.
     * Persists to AppSettings, sets on download manager, and validates against HuggingFace API.
     */
    fun saveHuggingFaceToken(token: String) {
        // Set token eagerly so subsequent download calls can use it immediately
        localModelManager.setHuggingFaceToken(token)
        _hasHuggingFaceToken.value = true
        _tokenValidationError.value = null

        viewModelScope.launch {
            // Persist token immediately so it survives restarts
            val settings = repository.appData.value.settings
            repository.updateSettings(settings.copy(huggingFaceToken = token))

            // Validate token against HuggingFace API
            val result = withContext(Dispatchers.IO) {
                localModelManager.validateHuggingFaceToken(token)
            }

            when (result) {
                is com.smartexpense.tracker.ai.modelmanager.ModelDownloadManager.TokenValidationResult.Valid -> {
                    _huggingFaceUsername.value = result.username
                    _tokenValidationError.value = null
                }
                is com.smartexpense.tracker.ai.modelmanager.ModelDownloadManager.TokenValidationResult.Invalid -> {
                    // Definitively invalid (HTTP 401) — revert only if no download is running
                    if (!localModelManager.downloads.downloadState.value.isDownloading) {
                        localModelManager.setHuggingFaceToken("")
                        repository.updateSettings(
                            repository.appData.value.settings.copy(huggingFaceToken = "")
                        )
                        _hasHuggingFaceToken.value = false
                    }
                    _huggingFaceUsername.value = null
                    _tokenValidationError.value = "Invalid HuggingFace token. Please check and try again."
                }
                is com.smartexpense.tracker.ai.modelmanager.ModelDownloadManager.TokenValidationResult.NetworkError -> {
                    // Network issue — keep token (it might be valid), show warning
                    _huggingFaceUsername.value = null
                    _tokenValidationError.value = "Could not verify token (network error). Token saved — try downloading."
                }
            }
        }
    }

    /**
     * Removes the stored HuggingFace token.
     */
    fun removeHuggingFaceToken() {
        // Clear token eagerly for immediate UI update
        localModelManager.setHuggingFaceToken("")
        _hasHuggingFaceToken.value = false
        _huggingFaceUsername.value = null

        viewModelScope.launch {
            val settings = repository.appData.value.settings
            repository.updateSettings(settings.copy(huggingFaceToken = ""))
        }
    }

    private fun refreshModelInfo() {
        val activeModel = localModelManager.getActiveModel()
        _installedModelName.value = activeModel?.displayName ?: ""
        _activeModelId.value = activeModel?.modelId ?: ""
        _modelStorageUsageMb.value = localModelManager.getStorageUsageMb()
    }

    /**
     * Uses the AI provider selector for categorization when the new mode is active,
     * falls back to existing local AI service otherwise.
     */
    private val promptAdapter = com.smartexpense.tracker.ai.provider.PromptAdapter()

    /**
     * Result of AI-driven categorization, carrying both the category name
     * and a flag indicating whether it was resolved by an AI model.
     */
    private data class CategorizationResult(val category: String, val byAi: Boolean)

    /**
     * Uses the AI provider for categorization with rich context.
     * Supports AI-driven new category creation when the AI suggests one.
     */
    private suspend fun smartCategorizeWithProvider(
        description: String,
        isExpense: Boolean = true,
        merchantName: String = "",
        amount: Double = 0.0,
        tags: List<String> = emptyList(),
        notes: String = "",
        isRecurring: Boolean = false,
        dateTime: String = "",
        source: String = "",
        hasLocation: Boolean = false
    ): String? {
        val provider = aiProviderSelector.getActiveProvider()

        val data = repository.appData.value
        val categories = data.categories.map { it.name }
        val currencyCode = data.settings.currencyCode
        val prompt = promptAdapter.createCategorizationPrompt(
            description, categories, isExpense, merchantName, amount, currencyCode,
            tags = tags, notes = notes, isRecurring = isRecurring,
            dateTime = dateTime, source = source, hasLocation = hasLocation
        )

        val result = withContext(Dispatchers.IO) {
            provider.generateAnalysis(
                com.smartexpense.tracker.ai.provider.AnalysisInput(
                    prompt = prompt,
                    availableCategories = categories,
                    isExpense = isExpense,
                    type = com.smartexpense.tracker.ai.provider.AnalysisType.CATEGORIZE
                )
            )
        }

        if (result.success && result.text.isNotBlank()) {
            val parsed = promptAdapter.parseCategorization(result.text, categories)
            if (parsed.category != null) {
                if (parsed.isNewCategory) {
                    // AI suggested a brand-new category — auto-create it
                    repository.ensureCategoryExists(parsed.category)
                }
                return parsed.category
            }
        }
        return null
    }

    /**
     * Uses the AI provider for insight generation with comprehensive financial data.
     */
    suspend fun generateInsightWithProvider(
        totalExpenses: Double,
        totalIncome: Double,
        topCategory: String?,
        topCategoryAmount: Double,
        transactionCount: Int,
        currencyCode: String
    ): String? {
        val provider = aiProviderSelector.getActiveProvider()

        val data = repository.appData.value
        val now = System.currentTimeMillis()
        val monthStart = DateUtils.getStartOfMonth(now)
        val transactions = data.transactions.filter { it.timestamp >= monthStart }

        // Build category breakdown
        val categoryBreakdown = transactions
            .filter { it.type == TransactionType.EXPENSE }
            .groupBy { it.category }
            .mapValues { (_, txs) -> txs.sumOf { it.amount } }

        // Build budget limits map
        val budgetLimits = data.budgets.associate { budget ->
            val catName = data.categories.find { it.id == budget.categoryId }?.name ?: budget.categoryId
            catName to budget.monthlyLimit
        }

        // Get recent large transactions
        val recentLarge = transactions
            .filter { it.type == TransactionType.EXPENSE }
            .sortedByDescending { it.amount }
            .take(5)
            .map { com.smartexpense.tracker.ai.provider.PromptAdapter.TransactionSummary(it.description, it.amount, it.category) }

        // Previous period expenses for comparison
        val prevMonthStart = DateUtils.getStartOfMonth(monthStart - 1)
        val prevMonthEnd = monthStart - 1
        val previousPeriodExpenses = data.transactions
            .filter { it.type == TransactionType.EXPENSE && it.timestamp in prevMonthStart..prevMonthEnd }
            .sumOf { it.amount }

        val avgDaily = if (transactionCount > 0) {
            val days = ((now - monthStart) / (24 * 60 * 60 * 1000.0)).coerceAtLeast(1.0)
            totalExpenses / days
        } else 0.0

        val prompt = promptAdapter.createInsightPrompt(
            totalExpenses, totalIncome, topCategory, topCategoryAmount,
            transactionCount, currencyCode,
            categoryBreakdown = categoryBreakdown,
            recentTransactions = recentLarge,
            previousPeriodExpenses = previousPeriodExpenses,
            averageDailySpend = avgDaily,
            budgetLimits = budgetLimits
        )

        val result = withContext(Dispatchers.IO) {
            provider.generateAnalysis(
                com.smartexpense.tracker.ai.provider.AnalysisInput(
                    prompt = prompt,
                    totalExpenses = totalExpenses,
                    totalIncome = totalIncome,
                    topCategory = topCategory,
                    topCategoryAmount = topCategoryAmount,
                    transactionCount = transactionCount,
                    currencyCode = currencyCode,
                    type = com.smartexpense.tracker.ai.provider.AnalysisType.INSIGHT
                )
            )
        }

        return if (result.success) promptAdapter.parseInsight(result.text) else null
    }

    /**
     * Generates an AI insight for a completed report, enriching it with provider analysis.
     */
    private suspend fun enrichReportWithAiInsight(report: ExpenseReport, currencyCode: String): ExpenseReport {
        val provider = aiProviderSelector.getActiveProvider()

        val periodLabel = when (report.periodType) {
            ReportPeriod.DAILY -> "daily"
            ReportPeriod.WEEKLY -> "weekly"
            ReportPeriod.MONTHLY -> "monthly"
            ReportPeriod.CUSTOM -> "custom period"
        }

        val prompt = promptAdapter.createReportInsightPrompt(
            periodLabel = periodLabel,
            totalExpenses = report.totalExpenses,
            totalIncome = report.totalIncome,
            categoryBreakdown = report.categoryBreakdown,
            topMerchants = report.topMerchants,
            transactionCount = report.transactionCount,
            currencyCode = currencyCode,
            comparisonWithPrevious = report.comparisonWithPrevious,
            dayOfWeekSpending = report.dayOfWeekSpending
        )

        val result = withContext(Dispatchers.IO) {
            provider.generateAnalysis(
                com.smartexpense.tracker.ai.provider.AnalysisInput(
                    prompt = prompt,
                    totalExpenses = report.totalExpenses,
                    totalIncome = report.totalIncome,
                    transactionCount = report.transactionCount,
                    currencyCode = currencyCode,
                    type = com.smartexpense.tracker.ai.provider.AnalysisType.REPORT
                )
            )
        }

        return if (result.success && result.text.isNotBlank()) {
            report.copy(aiInsight = promptAdapter.parseInsight(result.text))
        } else report
    }

    /**
     * Returns a category using the AI provider, with fallback to legacy local AI service
     * and then to the rule-based engine. AI can suggest new categories that are auto-created.
     * The returned [CategorizationResult.byAi] flag is true when an AI model (not rule-based)
     * performed the categorization.
     */
    private suspend fun smartCategorize(
        description: String,
        isExpense: Boolean = true,
        merchantName: String = "",
        amount: Double = 0.0,
        tags: List<String> = emptyList(),
        notes: String = "",
        isRecurring: Boolean = false,
        dateTime: String = "",
        source: String = "",
        hasLocation: Boolean = false
    ): CategorizationResult {
        val settings = repository.appData.value.settings
        val userCatNames = repository.appData.value.categories
            .filter { !it.isDefault }.map { it.name }

        // Try the tri-mode AI provider first (with richer context)
        val providerCategory = smartCategorizeWithProvider(
            description, isExpense, merchantName, amount,
            tags = tags, notes = notes, isRecurring = isRecurring,
            dateTime = dateTime, source = source, hasLocation = hasLocation
        )
        if (providerCategory != null) {
            repository.ensureCategoryExists(providerCategory)
            return CategorizationResult(providerCategory, byAi = true)
        }

        // Fall back to existing local AI service
        if (settings.localAiEnabled) {
            val categoryNames = repository.appData.value.categories.map { it.name }
            val aiCategory = localAiService.categorize(description, categoryNames, isExpense, userCatNames)
            if (aiCategory != null) {
                repository.ensureCategoryExists(aiCategory)
                return CategorizationResult(aiCategory, byAi = true)
            }
        }
        // Rule-based fallback — not AI
        val category = aiEngine.categorize(description, isExpense, userCatNames)
        repository.ensureCategoryExists(category)
        return CategorizationResult(category, byAi = false)
    }

    fun addTransaction(
        amount: Double, description: String, category: String? = null,
        type: TransactionType = TransactionType.EXPENSE,
        source: TransactionSource = TransactionSource.MANUAL,
        merchantName: String = "", notes: String = "",
        timestamp: Long = System.currentTimeMillis()
    ) {
        viewModelScope.launch {
            val dtFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            val dateTime = dtFormatter.format(Date(timestamp))
            val loc = currentLocation()
            val geoLoc = loc?.let { GeoLocation(it.latitude, it.longitude) }

            val catResult = if (category != null) {
                CategorizationResult(category, byAi = false)
            } else {
                smartCategorize(
                    description, type == TransactionType.EXPENSE, merchantName, amount,
                    notes = notes, dateTime = dateTime, source = source.name,
                    hasLocation = geoLoc != null
                )
            }

            val added = repository.addTransaction(Transaction(
                amount = amount, description = description, category = catResult.category,
                type = type, source = source, merchantName = merchantName, notes = notes,
                timestamp = timestamp, dateTime = dateTime,
                location = geoLoc,
                categorizedByAi = catResult.byAi
            ))
            if (added) {
                autoCreateStoreIfNeeded(merchantName, geoLoc)
                refreshSuggestions()
            }
        }
    }

    fun deleteTransaction(id: String) { viewModelScope.launch { repository.deleteTransaction(id) } }

    // ─── Store locations ────────────────────────────────────────

    /** Reactive flow of store locations for the Store Map screen. */
    val storeLocations: StateFlow<List<StoreLocation>> = repository.appData
        .map { it.storeLocations }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun addStoreLocation(merchantName: String, latitude: Double, longitude: Double, address: String) {
        viewModelScope.launch {
            repository.addStoreLocation(
                StoreLocation(
                    merchantName = merchantName,
                    latitude = latitude,
                    longitude = longitude,
                    address = address
                )
            )
        }
    }

    fun deleteStoreLocation(id: String) {
        viewModelScope.launch { repository.deleteStoreLocation(id) }
    }

    fun clearAllStoreLocations() {
        viewModelScope.launch { repository.clearAllStoreLocations() }
    }

    fun updateStoreLocation(store: StoreLocation) {
        viewModelScope.launch { repository.updateStoreLocation(store) }
    }

    /**
     * Automatically creates a [StoreLocation] for [merchantName] at the given
     * [GeoLocation] if one doesn't already exist for that merchant.
     */
    private suspend fun autoCreateStoreIfNeeded(
        merchantName: String,
        location: GeoLocation?
    ) {
        if (merchantName.isBlank() || location == null) return
        val existing = repository.appData.value.storeLocations
        if (existing.any { it.merchantName.equals(merchantName, ignoreCase = true) }) return
        repository.addStoreLocation(
            StoreLocation(
                merchantName = merchantName,
                latitude = location.lat,
                longitude = location.lng
            )
        )
    }

    /** Returns the current device location (and caches it for background use), or null. */
    private fun currentLocation(): LocationProvider.LatLng? {
        val loc = LocationProvider.getLastKnownLocation(getApplication())
        if (loc != null) LocationProvider.cacheLocation(getApplication(), loc)
        return loc
    }

    /**
     * Parses OCR/QR receipt data and stores it in [ocrParsedData] for the user to review
     * and edit before saving. Does NOT save the transaction automatically.
     */
    fun processOcrText(ocrText: String, qrData: String? = null) {
        viewModelScope.launch {
            try {
                val settings = repository.appData.value.settings
                val appCurrencyCode = settings.currencyCode

                val ocrParsed = if (ocrText.isNotBlank()) aiEngine.parseReceiptText(ocrText, appCurrencyCode) else null
                val ocrAmount = (ocrParsed?.totalAmount ?: ocrParsed?.items?.sumOf { it.second }) ?: 0.0

                // Use detected currency from receipt text, fallback to app default
                val detectedCurrency = ocrParsed?.detectedCurrencyCode ?: appCurrencyCode
                val currencySymbol = currencyInfoFor(detectedCurrency).symbol

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
                    val catResult = smartCategorize(
                        "${parsed.merchantName} ${parsed.items.joinToString(" ") { it.first }}",
                        isExpense = true,
                        merchantName = parsed.merchantName,
                        amount = amount
                    )
                    _ocrParsedData.value = OcrParsedData(
                        amount = amount,
                        merchantName = parsed.merchantName,
                        category = catResult.category,
                        items = parsed.items,
                        fromQr = fromQr,
                        rawOcrText = ocrText,
                        currencySymbol = currencySymbol,
                        detectedCurrencyCode = detectedCurrency,
                        isTerminalReceipt = parsed.isTerminalReceipt
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
                val data = _ocrParsedData.value
                // Use detected currency from OCR if available, otherwise app default
                val currencyCode = data?.detectedCurrencyCode?.takeIf { it.isNotBlank() } ?: settings.currencyCode
                val currencySymbol = currencyInfoFor(currencyCode).symbol

                val items = data?.items ?: emptyList()
                val fromQr = data?.fromQr ?: false

                val itemsNote = if (items.isNotEmpty())
                    "Items: ${items.joinToString(", ") { "${it.first}: $currencySymbol${String.format("%.2f", it.second)}" }}"
                else ""
                val sourceNote = if (fromQr) "Parsed from QR code" else ""

                val aiNote: String = run {
                    // Try new AI provider first
                    val providerInsight = withContext(Dispatchers.IO) {
                        generateInsightWithProvider(amount, 0.0, category, amount, 1, currencyCode)
                    }
                    if (providerInsight != null) return@run "\nAI: $providerInsight"

                    // Fall back to existing local AI
                    if (settings.localAiEnabled) {
                        val insight = withContext(Dispatchers.IO) {
                            localAiService.generateInsight(
                                totalExpenses = amount, totalIncome = 0.0,
                                topCategory = category, topCategoryAmount = amount,
                                transactionCount = 1, currencyCode = currencyCode
                            )
                        }
                        if (insight != null) "\nAI: $insight" else ""
                    } else ""
                }

                repository.ensureCategoryExists(category)

                val loc = currentLocation()
                val geoLoc = loc?.let { GeoLocation(it.latitude, it.longitude) }
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
                    notes = listOf(itemsNote, sourceNote, aiNote).filter { it.isNotBlank() }.joinToString("\n"),
                    location = geoLoc,
                    currencyCode = currencyCode
                ))
                autoCreateStoreIfNeeded(merchantName, geoLoc)

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

    // ─── OCR Sections (scanned goods storage) ───────────────────────

    /**
     * Saves the current OCR parsed data as a new [OcrSection] for later reference and reporting.
     * Called when the user confirms the scan and chooses "Save to Sections".
     */
    fun saveOcrSection(
        label: String,
        merchantName: String,
        items: List<Pair<String, Double>>,
        totalAmount: Double,
        rawOcrText: String,
        detectedLanguages: String = "",
        notes: String = ""
    ) {
        viewModelScope.launch {
            val settings = repository.appData.value.settings
            val ocrItems = items.map { (name, price) ->
                val catResult = smartCategorize(name)
                OcrItem(name = name, price = price, category = catResult.category)
            }
            val section = OcrSection(
                label = label.ifBlank { merchantName },
                merchantName = merchantName,
                currencyCode = settings.currencyCode,
                items = ocrItems,
                totalAmount = totalAmount,
                detectedLanguages = detectedLanguages,
                rawOcrText = rawOcrText,
                notes = notes
            )
            repository.addOcrSection(section)
        }
    }

    fun deleteOcrSection(id: String) {
        viewModelScope.launch { repository.deleteOcrSection(id) }
    }

    fun updateOcrSection(section: OcrSection) {
        viewModelScope.launch { repository.updateOcrSection(section) }
    }

    fun clearAllOcrSections() {
        viewModelScope.launch { repository.clearAllOcrSections() }
    }

    /**
     * Generates a text report summarising goods across OCR sections.
     * When [sinceTimestamp] is non-null, only sections scanned after that time are included.
     * Groups items by category, shows totals, averages, and per-section breakdown.
     */
    /**
     * Returns aggregated goods data: items grouped by name with purchase count and total spend.
     * Used by the UI to render diagrams and frequency tables.
     */
    fun getGoodsReportItems(sinceTimestamp: Long? = null): List<GoodsReportItem> {
        val allSections = _ocrSections.value
        val sections = if (sinceTimestamp != null) {
            allSections.filter { it.timestamp >= sinceTimestamp }
        } else allSections
        if (sections.isEmpty()) return emptyList()

        val allItems = sections.flatMap { it.items }
        // Group by normalised item name (lowercase, trimmed)
        return allItems.groupBy { it.name.trim().lowercase() }
            .map { (_, items) ->
                GoodsReportItem(
                    name = items.first().name.trim(), // keep original casing from first occurrence
                    count = items.size,
                    totalSpent = items.sumOf { it.price },
                    category = items.first().category
                )
            }
            .sortedByDescending { it.totalSpent }
    }

    fun generateOcrSectionsReport(sinceTimestamp: Long? = null): String {
        val allSections = _ocrSections.value
        val sections = if (sinceTimestamp != null) {
            allSections.filter { it.timestamp >= sinceTimestamp }
        } else allSections
        if (sections.isEmpty()) return "No scanned sections in this period."

        val settings = repository.appData.value.settings
        val currencySymbol = currencyInfoFor(settings.currencyCode).symbol

        val sb = StringBuilder()
        sb.appendLine("══════════════════════════════════════")
        sb.appendLine("       SCANNED GOODS REPORT")
        sb.appendLine("══════════════════════════════════════")
        sb.appendLine()
        sb.appendLine("Total sections: ${sections.size}")

        val allItems = sections.flatMap { it.items }
        val grandTotal = sections.sumOf { it.totalAmount }
        sb.appendLine("Total items scanned: ${allItems.size}")
        sb.appendLine("Grand total: $currencySymbol${String.format("%.2f", grandTotal)}")
        sb.appendLine()

        // ── Goods frequency breakdown (most requested feature) ──
        val goodsReport = getGoodsReportItems(sinceTimestamp)
        sb.appendLine("── Goods Frequency ──")
        goodsReport.take(20).forEach { item ->
            val avg = item.totalSpent / item.count
            sb.appendLine("  ${item.name}: bought ${item.count}x, total $currencySymbol${String.format("%.0f", item.totalSpent)}, avg $currencySymbol${String.format("%.0f", avg)}")
        }
        sb.appendLine()

        // ── Per-category breakdown ──
        val byCategory = allItems.groupBy { it.category.ifBlank { "Uncategorized" } }
        sb.appendLine("── Category Breakdown ──")
        byCategory.entries
            .sortedByDescending { it.value.sumOf { item -> item.price } }
            .forEach { (cat, items) ->
                val catTotal = items.sumOf { it.price }
                val pct = if (grandTotal > 0) (catTotal / grandTotal * 100) else 0.0
                sb.appendLine("  $cat: $currencySymbol${String.format("%.2f", catTotal)} (${String.format("%.1f", pct)}%) — ${items.size} items")
            }
        sb.appendLine()

        // ── Per-section breakdown ──
        sb.appendLine("── Receipt Details ──")
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
        sections.sortedByDescending { it.timestamp }.forEach { section ->
            sb.appendLine("┌─ ${section.label} (${section.merchantName})")
            sb.appendLine("│  Date: ${dateFormat.format(java.util.Date(section.timestamp))}")
            if (section.items.isNotEmpty()) {
                section.items.forEach { item ->
                    sb.appendLine("│  • ${item.name}: $currencySymbol${String.format("%.2f", item.price)}")
                }
            }
            sb.appendLine("│  Total: $currencySymbol${String.format("%.2f", section.totalAmount)}")
            sb.appendLine("└──────────────")
        }

        // ── Most expensive items ──
        sb.appendLine()
        sb.appendLine("── Top 10 Most Expensive Items ──")
        allItems.sortedByDescending { it.price }.take(10).forEachIndexed { i, item ->
            sb.appendLine("  ${i + 1}. ${item.name}: $currencySymbol${String.format("%.2f", item.price)}")
        }

        // ── Averages ──
        sb.appendLine()
        if (allItems.isNotEmpty()) {
            val avg = grandTotal / allItems.size
            sb.appendLine("Average item price: $currencySymbol${String.format("%.2f", avg)}")
        }
        if (sections.isNotEmpty()) {
            val avgPerReceipt = grandTotal / sections.size
            sb.appendLine("Average per receipt: $currencySymbol${String.format("%.2f", avgPerReceipt)}")
        }

        sb.appendLine()
        sb.appendLine("══════════════════════════════════════")
        return sb.toString()
    }

    fun dismissSuggestion(id: String) { viewModelScope.launch { repository.dismissSuggestion(id) } }

    fun refreshSuggestions() {
        viewModelScope.launch {
            val data = repository.appData.value
            val suggestions = aiEngine.generateSuggestions(
                data.transactions, data.budgets, data.settings.currencyCode
            )
            repository.addSuggestions(suggestions)
        }
    }

    /**
     * Returns a copy of all transactions with amounts converted to [targetCurrency].
     * Original currency metadata (originalAmount, originalCurrencyCode) is always
     * preserved so further conversions can go back to the source amount.
     */
    private fun transactionsInDisplayCurrency(targetCurrency: String): List<Transaction> {
        return repository.appData.value.transactions.map { tx ->
            val converted = convertAmount(tx, targetCurrency)
            if (converted != tx.amount) {
                tx.copy(amount = converted, currencyCode = targetCurrency)
            } else tx
        }
    }

    fun generateReport(period: ReportPeriod): ExpenseReport {
        val now = System.currentTimeMillis()
        val (start, end) = when (period) {
            ReportPeriod.DAILY -> DateUtils.getStartOfDay(now) to DateUtils.getEndOfDay(now)
            ReportPeriod.WEEKLY -> DateUtils.getStartOfWeek(now) to DateUtils.getEndOfWeek(now)
            ReportPeriod.MONTHLY -> DateUtils.getStartOfMonth(now) to DateUtils.getEndOfMonth(now)
            ReportPeriod.CUSTOM -> DateUtils.getStartOfMonth(now) to DateUtils.getEndOfMonth(now)
        }
        val currencyCode = repository.appData.value.settings.currencyCode
        val report = aiEngine.generateReport(transactionsInDisplayCurrency(currencyCode), period, start, end, currencyCode)
        // Enrich with AI insight asynchronously; return base report immediately
        viewModelScope.launch { enrichReportWithAiInsightAndNotify(report, currencyCode) }
        return report
    }

    /** Enriches a report with AI insight and triggers a UI refresh. */
    private suspend fun enrichReportWithAiInsightAndNotify(report: ExpenseReport, currencyCode: String) {
        try {
            val enriched = enrichReportWithAiInsight(report, currencyCode)
            if (enriched.aiInsight != report.aiInsight) {
                _uiState.update { it.copy(latestAiInsight = enriched.aiInsight) }
            }
        } catch (_: Exception) { /* AI enrichment is best-effort */ }
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
        val report = aiEngine.generateReport(
            transactionsInDisplayCurrency(currencyCode),
            ReportPeriod.MONTHLY,
            startCal.timeInMillis,
            endCal.timeInMillis,
            currencyCode
        )
        viewModelScope.launch { enrichReportWithAiInsightAndNotify(report, currencyCode) }
        return report
    }

    /**
     * Generates a report for an arbitrary date range (Custom period).
     */
    fun generateReportForRange(startMillis: Long, endMillis: Long): ExpenseReport {
        val currencyCode = repository.appData.value.settings.currencyCode
        val report = aiEngine.generateReport(
            transactionsInDisplayCurrency(currencyCode),
            ReportPeriod.CUSTOM,
            startMillis,
            endMillis,
            currencyCode
        )
        viewModelScope.launch { enrichReportWithAiInsightAndNotify(report, currencyCode) }
        return report
    }

    fun analyzeTransactions(startMillis: Long, endMillis: Long, category: String?): String {
        val currencyCode = repository.appData.value.settings.currencyCode
        return aiEngine.generateAnalysis(
            transactionsInDisplayCurrency(currencyCode),
            startMillis, endMillis, currencyCode, category
        )
    }

    /**
     * Async version of analyzeTransactions that sends data to the active AI provider
     * for richer, natural-language analysis. Falls back to the rule-based engine if
     * the AI provider is unavailable or fails.
     */
    suspend fun analyzeTransactionsAsync(startMillis: Long, endMillis: Long, category: String?): String {
        val currencyCode = repository.appData.value.settings.currencyCode
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)
        val transactions = transactionsInDisplayCurrency(currencyCode)
        val userPrompt = buildString {
            append("Analyze transactions from ${dateFormat.format(Date(startMillis))} to ${dateFormat.format(Date(endMillis))}")
            if (category != null) append(" in category \"$category\"")
        }

        // Always try the active AI provider — no isAvailable() pre-check.
        // The provider handles lazy loading and retries internally.
        val provider = aiProviderSelector.getActiveProvider()

        val filtered = transactions.filter { t ->
            t.timestamp in startMillis..endMillis &&
                (category == null || t.category == category)
        }

        if (filtered.isNotEmpty()) {
            val expenses = filtered.filter { it.type == TransactionType.EXPENSE }
            val income = filtered.filter { it.type == TransactionType.INCOME }
            val totalExpenses = expenses.sumOf { it.amount }
            val totalIncome = income.sumOf { it.amount }
            val days = ((endMillis - startMillis) / (24 * 60 * 60 * 1000.0)).coerceAtLeast(1.0)

            val categoryBreakdown = expenses.groupBy { it.category }
                .mapValues { (_, txs) -> txs.sumOf { it.amount } }
            val topMerchants = expenses.filter { it.merchantName.isNotEmpty() }
                .groupBy { it.merchantName }
                .mapValues { (_, txs) -> txs.sumOf { it.amount } }
                .entries.sortedByDescending { it.value }
                .take(5)
                .associate { it.key to it.value }
            val recentLarge = expenses.sortedByDescending { it.amount }.take(5)
                .map { com.smartexpense.tracker.ai.provider.PromptAdapter.TransactionSummary(it.description, it.amount, it.category) }
            val budgetLimits = repository.appData.value.budgets.associate { budget ->
                val catName = repository.appData.value.categories.find { it.id == budget.categoryId }?.name ?: budget.categoryId
                catName to budget.monthlyLimit
            }

            // Build day-of-week breakdown
            val cal = java.util.Calendar.getInstance()
            val dayNames = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
            val dayOfWeekSpending = mutableMapOf<String, Double>()
            for (e in expenses) {
                cal.timeInMillis = e.timestamp
                val name = dayNames[cal.get(java.util.Calendar.DAY_OF_WEEK) - 1]
                dayOfWeekSpending[name] = (dayOfWeekSpending[name] ?: 0.0) + e.amount
            }

            val rangeLabel = "${dateFormat.format(Date(startMillis))} – ${dateFormat.format(Date(endMillis))}"
            val prompt = buildString {
                appendLine("You are a personal finance analyst. Provide a comprehensive, detailed analysis of the following spending data.")
                appendLine()
                appendLine("=== Period: $rangeLabel ===")
                if (category != null) appendLine("Filtered to category: $category")
                appendLine("Total transactions: ${filtered.size}")
                appendLine("Total expenses: $currencyCode ${String.format("%.2f", totalExpenses)}")
                appendLine("Total income: $currencyCode ${String.format("%.2f", totalIncome)}")
                appendLine("Net balance: $currencyCode ${String.format("%.2f", totalIncome - totalExpenses)}")
                appendLine("Average daily spend: $currencyCode ${String.format("%.2f", totalExpenses / days)}")
                if (totalIncome > 0) {
                    val savingsRate = ((totalIncome - totalExpenses) / totalIncome * 100)
                    appendLine("Savings rate: ${String.format("%.1f", savingsRate)}%")
                }

                if (categoryBreakdown.isNotEmpty() && category == null) {
                    appendLine()
                    appendLine("=== Spending by Category ===")
                    categoryBreakdown.entries.sortedByDescending { it.value }.forEach { (cat, amt) ->
                        val pct = if (totalExpenses > 0) (amt / totalExpenses * 100) else 0.0
                        appendLine("- $cat: $currencyCode ${String.format("%.2f", amt)} (${String.format("%.1f", pct)}%)")
                    }
                }

                if (recentLarge.isNotEmpty()) {
                    appendLine()
                    appendLine("=== Largest Expenses ===")
                    recentLarge.forEach { tx ->
                        appendLine("- ${tx.description}: $currencyCode ${String.format("%.2f", tx.amount)} (${tx.category})")
                    }
                }

                if (topMerchants.isNotEmpty()) {
                    appendLine()
                    appendLine("=== Top Merchants ===")
                    topMerchants.forEach { (m, amt) ->
                        appendLine("- $m: $currencyCode ${String.format("%.2f", amt)}")
                    }
                }

                if (dayOfWeekSpending.isNotEmpty()) {
                    appendLine()
                    appendLine("=== Day-of-Week Spending ===")
                    dayOfWeekSpending.forEach { (day, amt) ->
                        appendLine("- $day: $currencyCode ${String.format("%.2f", amt)}")
                    }
                }

                if (budgetLimits.isNotEmpty()) {
                    appendLine()
                    appendLine("=== Budget Status ===")
                    budgetLimits.forEach { (cat, limit) ->
                        val spent = categoryBreakdown[cat] ?: 0.0
                        val pct = if (limit > 0) (spent / limit * 100) else 0.0
                        appendLine("- $cat: $currencyCode ${String.format("%.2f", spent)} / $currencyCode ${String.format("%.2f", limit)} (${String.format("%.0f", pct)}% used)")
                    }
                }

                appendLine()
                appendLine("Instructions:")
                appendLine("1. Start with a brief overview of the financial health for this period.")
                appendLine("2. Analyze spending patterns across categories and identify concerning trends.")
                appendLine("3. Highlight unusual or large transactions that deserve attention.")
                appendLine("4. Provide 3-5 specific, actionable recommendations to improve finances.")
                appendLine("5. If applicable, comment on day-of-week patterns or merchant concentration.")
                appendLine("Be specific, reference actual numbers, and keep the tone helpful and concise.")
            }

            val result = withContext(Dispatchers.IO) {
                provider.generateAnalysis(
                    com.smartexpense.tracker.ai.provider.AnalysisInput(
                        prompt = prompt,
                        totalExpenses = totalExpenses,
                        totalIncome = totalIncome,
                        transactionCount = filtered.size,
                        currencyCode = currencyCode,
                        type = com.smartexpense.tracker.ai.provider.AnalysisType.INSIGHT
                    )
                )
            }

            if (result.success && result.text.isNotBlank()) {
                val analysisText = promptAdapter.parseInsight(result.text)
                viewModelScope.launch {
                    repository.addAiConversation(AiConversation(
                        prompt = userPrompt,
                        response = analysisText,
                        aiModelName = provider.displayName()
                    ))
                }
                return analysisText
            }
        }

        // Fallback to rule-based engine only if the AI provider returned no result
        val baseAnalysis = aiEngine.generateAnalysis(
            transactions, startMillis, endMillis, currencyCode, category
        )
        viewModelScope.launch {
            repository.addAiConversation(AiConversation(
                prompt = userPrompt,
                response = baseAnalysis,
                aiModelName = "Rule-Based Engine"
            ))
        }
        return baseAnalysis
    }

    // ── AI Conversation History ─────────────────────────────────────

    fun deleteAiConversation(id: String) {
        viewModelScope.launch { repository.deleteAiConversation(id) }
    }

    fun clearAllAiConversations() {
        viewModelScope.launch { repository.clearAllAiConversations() }
    }

    fun getWeeklyChartData(): List<Pair<String, Double>> {
        val data = repository.appData.value
        val appCurrency = data.settings.currencyCode
        return DateUtils.getDaysInRange(DateUtils.getStartOfWeek(), DateUtils.getEndOfWeek()).map { dayStart ->
            val dayEnd = DateUtils.getEndOfDay(dayStart)
            val total = data.transactions
                .filter { it.type == TransactionType.EXPENSE && it.timestamp in dayStart..dayEnd }
                .sumOf { convertAmount(it, appCurrency) }
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
            val oldCurrency = repository.appData.value.settings.currencyCode
            val newCurrency = settings.currencyCode
            repository.updateSettings(settings)
            CurrencyConverterService.invalidateCache()

            // When currency changes, re-convert all transaction amounts and budget limits.
            // Transactions with original foreign-currency metadata are converted directly
            // from the original currency, avoiding compounded rounding errors.
            if (oldCurrency != newCurrency) {
                val fallbackRate = withContext(Dispatchers.IO) {
                    CurrencyConverterService.convert(1.0, oldCurrency, newCurrency)
                }
                if (fallbackRate != null && fallbackRate > 0) {
                    // Collect all distinct original currencies that need rates
                    val origCurrencies = repository.appData.value.transactions
                        .filter { it.originalAmount > 0.0 && it.originalCurrencyCode.orEmpty().isNotEmpty() }
                        .map { it.originalCurrencyCode.orEmpty() }
                        .distinct()

                    // Pre-fetch rates for each original currency → newCurrency
                    val origRates = mutableMapOf<String, Double>()
                    for (oc in origCurrencies) {
                        if (oc == newCurrency) { origRates[oc] = 1.0; continue }
                        val r = withContext(Dispatchers.IO) {
                            CurrencyConverterService.convert(1.0, oc, newCurrency)
                        }
                        if (r != null) origRates[oc] = r
                    }

                    repository.convertAmounts(
                        newCurrency = newCurrency,
                        fallbackRate = fallbackRate,
                        rateFromOriginal = { origRates[it] }
                    )
                    refreshSuggestions()
                }
            }
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

    // ─── Scheduled Expenses (loans, subscriptions) ──────────────────

    fun addScheduledExpense(expense: com.smartexpense.tracker.data.model.ScheduledExpense) {
        viewModelScope.launch {
            val settings = repository.appData.value.settings
            repository.updateSettings(
                settings.copy(scheduledExpenses = settings.scheduledExpenses + expense)
            )
            rescheduleExpenseWorker()
        }
    }

    fun updateScheduledExpense(expense: com.smartexpense.tracker.data.model.ScheduledExpense) {
        viewModelScope.launch {
            val settings = repository.appData.value.settings
            repository.updateSettings(
                settings.copy(
                    scheduledExpenses = settings.scheduledExpenses.map {
                        if (it.id == expense.id) expense else it
                    }
                )
            )
            rescheduleExpenseWorker()
        }
    }

    fun deleteScheduledExpense(id: String) {
        viewModelScope.launch {
            val settings = repository.appData.value.settings
            val updated = settings.scheduledExpenses.filter { it.id != id }
            repository.updateSettings(settings.copy(scheduledExpenses = updated))
            rescheduleExpenseWorker()
        }
    }

    private fun rescheduleExpenseWorker() {
        val appContext = getApplication<android.app.Application>().applicationContext
        val hasEnabled = repository.appData.value.settings.scheduledExpenses.any { it.enabled }
        if (hasEnabled) {
            com.smartexpense.tracker.service.scheduler.ScheduledExpenseWorker.schedule(appContext)
        } else {
            com.smartexpense.tracker.service.scheduler.ScheduledExpenseWorker.cancel(appContext)
        }
    }

    // ─── Currency Converter ────────────────────────────────────────

    private var rateRefreshJob: kotlinx.coroutines.Job? = null

    /**
     * Fetches live exchange rates (base = USD), archives them in rate history,
     * and stores them in [exchangeRates].
     * Also persists the fetch timestamp to settings for frequency-based refresh.
     */
    fun fetchExchangeRates() {
        viewModelScope.launch {
            val settings = repository.appData.value.settings
            val sourceName = settings.rateSource.name
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
                val fullRates = rates + ("USD" to 1.0)
                _exchangeRates.value = fullRates

                // Archive this rate snapshot
                val now = System.currentTimeMillis()
                repository.addRateHistoryEntry(
                    RateHistoryEntry(timestamp = now, source = sourceName, rates = fullRates)
                )
                // Persist fetch timestamp
                repository.updateSettings(settings.copy(lastRateUpdateTimestamp = now))
            }
        }
    }

    /**
     * Starts a repeating background job that refreshes exchange rates at the
     * interval defined by [AppSettings.rateUpdateFrequency].
     * Cancels any previous job before starting a new one.
     */
    fun scheduleRateRefresh() {
        rateRefreshJob?.cancel()
        val settings = repository.appData.value.settings
        val freq = settings.rateUpdateFrequency
        if (freq == RateUpdateFrequency.MANUAL || freq.minutes <= 0) return

        rateRefreshJob = viewModelScope.launch {
            // Check if a refresh is overdue right now
            val elapsed = System.currentTimeMillis() - settings.lastRateUpdateTimestamp
            if (elapsed >= freq.minutes * 60_000L) {
                fetchExchangeRates()
            }
            // Then repeat on schedule
            while (true) {
                kotlinx.coroutines.delay(freq.minutes * 60_000L)
                fetchExchangeRates()
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
                    val parts = mutableListOf<String>()
                    parts.add("${data.transactions.size} transactions")
                    if (data.storeLocations.isNotEmpty()) {
                        parts.add("${data.storeLocations.size} store locations")
                    }
                    if (data.ocrSections.isNotEmpty()) {
                        parts.add("${data.ocrSections.size} receipts")
                    }
                    _importExportMessage.value = "Imported ${parts.joinToString(", ")}"
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

                var finalAmount = tx.amount
                var origAmount = 0.0
                var origCode = ""
                var rate = 0.0

                if (parsedCurrency.isNotEmpty() && parsedCurrency != appCurrency) {
                    val converted = withContext(Dispatchers.IO) {
                        com.smartexpense.tracker.service.currency.CurrencyConverterService.convert(
                            tx.amount, parsedCurrency, appCurrency
                        )
                    }
                    if (converted != null) {
                        rate = converted / tx.amount
                        origAmount = tx.amount
                        origCode = parsedCurrency
                        finalAmount = converted
                    }
                }

                // Remove parsedCurrency marker from notes
                val cleanNotes = tx.notes.lines()
                    .filter { !it.startsWith("parsedCurrency:") }
                    .joinToString("\n").trim()

                val finalTx = tx.copy(
                    amount = finalAmount,
                    notes = cleanNotes,
                    currencyCode = appCurrency,
                    originalAmount = origAmount,
                    originalCurrencyCode = origCode,
                    exchangeRate = rate
                )

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
    val currencySymbol: String = "$",
    /** ISO-4217 currency code detected from the receipt (e.g. "AMD", "USD", "EUR"). */
    val detectedCurrencyCode: String = "",
    /** True when the scanned document is a bank/POS terminal slip (no goods). */
    val isTerminalReceipt: Boolean = false
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
    val settings: AppSettings = AppSettings(),
    /** Latest AI-generated insight from report enrichment. */
    val latestAiInsight: String = ""
)
