package com.smartexpense.tracker

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartexpense.tracker.ui.components.SubscriptionPaywallDialog
import com.smartexpense.tracker.ui.screens.*
import com.smartexpense.tracker.ui.theme.SmartExpenseTheme
import com.smartexpense.tracker.ui.viewmodel.MainViewModel
import com.smartexpense.tracker.service.subscription.SubscriptionPlan
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: MainViewModel = viewModel()
            val themeMode by viewModel.themeMode.collectAsState()
            SmartExpenseTheme(themeMode = themeMode) {
                MainApp(viewModel = viewModel, activity = this@MainActivity)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(viewModel: MainViewModel, activity: Activity) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val reportPeriod by viewModel.reportPeriod.collectAsState()
    val importExportMessage by viewModel.importExportMessage.collectAsState()
    val smsScanState by viewModel.smsScanState.collectAsState()
    val totalSmsCount by viewModel.totalSmsCount.collectAsState()
    val exchangeRates by viewModel.exchangeRates.collectAsState()
    val inAppNotifications by viewModel.inAppNotifications.collectAsState()
    val unreadCount by viewModel.unreadNotificationCount.collectAsState()
    val localAiStatus by viewModel.localAiStatus.collectAsState()
    val localAiSuggestion by viewModel.localAiSuggestion.collectAsState()
    val discoveredBankingApps by viewModel.discoveredBankingApps.collectAsState()
    val isScanningBankingApps by viewModel.isScanningBankingApps.collectAsState()
    val allInstalledApps by viewModel.allInstalledApps.collectAsState()
    val ocrParsedData by viewModel.ocrParsedData.collectAsState()
    val ocrSections by viewModel.ocrSections.collectAsState()
    val engineDescriptions by viewModel.engineDescriptions.collectAsState()
    val discoveredModels by viewModel.discoveredModels.collectAsState()
    val isLoadingModel by viewModel.isLoadingModel.collectAsState()
    val isDownloadingModel by viewModel.isDownloadingModel.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val downloadError by viewModel.downloadError.collectAsState()
    val modelImportMessage by viewModel.modelImportMessage.collectAsState()
    val ollamaModels by viewModel.ollamaModels.collectAsState()
    val ollamaConnecting by viewModel.ollamaConnecting.collectAsState()
    val dashboardSectionOrder by viewModel.dashboardSectionOrder.collectAsState()
    val storeLocations by viewModel.storeLocations.collectAsState()
    val isSubscribed by viewModel.isSubscribed.collectAsState()
    val billingError by viewModel.subscriptionManager.billingError.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Show billing errors/confirmations via Snackbar
    LaunchedEffect(billingError) {
        billingError?.let { msg ->
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
            viewModel.subscriptionManager.clearBillingError()
        }
    }

    // Paywall dialog state
    var showPaywall by remember { mutableStateOf(false) }
    var paywallFeatureName by remember { mutableStateOf("") }

    // Shortcut to always-up-to-date currency code
    val currencyCode = uiState.settings.currencyCode

    var currentScreen by remember { mutableStateOf("dashboard") }

    // Intercept system back button: always go to Dashboard instead of closing the app.
    // Sub-screens that go back to a non-dashboard destination (sms_scan → settings)
    // are handled explicitly in the when-branch below.
    BackHandler(enabled = currentScreen != "dashboard") {
        when (currentScreen) {
            "sms_scan"      -> { currentScreen = "settings"; viewModel.setSelectedTab(3) }
            "store_map"     -> { currentScreen = "dashboard"; viewModel.setSelectedTab(0) }
            "ocr_sections"  -> { currentScreen = "dashboard"; viewModel.setSelectedTab(0) }
            else            -> { currentScreen = "dashboard"; viewModel.setSelectedTab(0) }
        }
    }

    // Hide the top bar on full-screen sub-screens (they have their own top bar)
    val showTopBar = currentScreen !in listOf("add", "scan", "sms_scan", "store_map", "ai_analyze", "ocr_sections")

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    title = {
                        Text(
                            when (currentScreen) {
                                "dashboard"    -> "FlowSense"
                                "reports"      -> "Reports"
                                "transactions" -> "Transactions"
                                "settings"     -> "Settings"
                                else           -> "FlowSense"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    },
                    actions = {
                        IconButton(onClick = {
                            if (isSubscribed) {
                                currentScreen = "store_map"
                            } else {
                                paywallFeatureName = "Store Map"
                                showPaywall = true
                            }
                        }) {
                            Icon(Icons.Filled.Map, contentDescription = "Store Map")
                        }
                        IconButton(onClick = {
                            if (isSubscribed) {
                                currentScreen = "ocr_sections"
                            } else {
                                paywallFeatureName = "Scanned Sections"
                                showPaywall = true
                            }
                        }) {
                            Icon(Icons.Filled.Inventory2, contentDescription = "Scanned Sections")
                        }
                        NotificationBell(
                            notifications  = inAppNotifications,
                            unreadCount    = unreadCount,
                            onMarkRead     = { viewModel.markNotificationRead(it) },
                            onMarkAllRead  = { viewModel.markAllNotificationsRead() },
                            onDelete       = { viewModel.deleteNotification(it) },
                            onClearAll     = { viewModel.clearAllInAppNotifications() }
                        )
                    }
                )
            }
        },
        bottomBar = {
            if (currentScreen !in listOf("add", "scan", "sms_scan", "store_map", "ai_analyze", "ocr_sections")) {
                NavigationBar(tonalElevation = 2.dp) {
                    // Home
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { viewModel.setSelectedTab(0); currentScreen = "dashboard" },
                        icon = { Icon(if (selectedTab == 0) Icons.Filled.Home else Icons.Outlined.Home, "Home") },
                        label = { Text("Home") }
                    )
                    // Reports
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { viewModel.setSelectedTab(1); currentScreen = "reports" },
                        icon = { Icon(if (selectedTab == 1) Icons.Filled.BarChart else Icons.Outlined.BarChart, "Reports") },
                        label = { Text("Reports") }
                    )
                    // Add (FAB-style centre item)
                    NavigationBarItem(
                        selected = false,
                        onClick = { currentScreen = "add" },
                        icon = { Icon(Icons.Filled.AddCircle, "Add", modifier = Modifier.size(32.dp)) },
                        label = { Text("Add", fontWeight = FontWeight.Bold) }
                    )
                    // Transactions
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { viewModel.setSelectedTab(2); currentScreen = "transactions" },
                        icon = { Icon(if (selectedTab == 2) Icons.Filled.Receipt else Icons.Outlined.Receipt, "Transactions") },
                        label = { Text("History") }
                    )
                    // Settings
                    NavigationBarItem(
                        selected = selectedTab == 3,
                        onClick = { viewModel.setSelectedTab(3); currentScreen = "settings" },
                        icon = { Icon(if (selectedTab == 3) Icons.Filled.Settings else Icons.Outlined.Settings, "Settings") },
                        label = { Text("Settings") }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            Crossfade(targetState = currentScreen, label = "screen") { screen ->
                when (screen) {
                    "dashboard" -> DashboardScreen(
                        uiState = uiState,
                        weeklyChartData = viewModel.getWeeklyChartData(),
                        onDismissSuggestion = { viewModel.dismissSuggestion(it) },
                        onDeleteTransaction = { viewModel.deleteTransaction(it) },
                        sectionOrder = dashboardSectionOrder,
                        onMoveSections = { from, to -> viewModel.moveDashboardSection(from, to) },
                        currencyCode = currencyCode,
                        onNavigateToAnalyze = { currentScreen = "ai_analyze" }
                    )
                    "reports" -> ReportsScreen(
                        generateReport = { viewModel.generateReport(it) },
                        generateMonthlyReport = { year, month ->
                            viewModel.generateReportForMonth(year, month)
                        },
                        generateCustomReport = { startMillis, endMillis ->
                            viewModel.generateReportForRange(startMillis, endMillis)
                        },
                        currentPeriod = reportPeriod,
                        onPeriodChange = { viewModel.setReportPeriod(it) },
                        allTransactions = uiState.allTransactions,
                        currencyCode = currencyCode
                    )
                    "add" -> AddTransactionScreen(
                        categories = uiState.categories,
                        onAdd = { amount, desc, category, type, source, merchant, notes, timestamp ->
                            viewModel.addTransaction(amount = amount, description = desc,
                                category = category, type = type, source = source,
                                merchantName = merchant, notes = notes, timestamp = timestamp)
                        },
                        onScanReceipt = {
                            if (isSubscribed) {
                                currentScreen = "scan"
                            } else {
                                paywallFeatureName = "OCR Receipt Scanner"
                                showPaywall = true
                            }
                        },
                        onNavigateBack = { currentScreen = "dashboard"; viewModel.setSelectedTab(0) },
                        currencyCode = currencyCode
                    )
                    "transactions" -> TransactionsScreen(
                        allTransactions = uiState.allTransactions,
                        currencyCode = currencyCode,
                        onDeleteTransaction = { viewModel.deleteTransaction(it) }
                    )
                    "scan" -> ScanReceiptScreen(
                        onOcrResult = { text, qrData -> viewModel.processOcrText(text, qrData) },
                        onConfirmOcr = { amount, merchant, category ->
                            viewModel.confirmOcrTransaction(amount, merchant, category)
                        },
                        onClearOcr = { viewModel.clearOcrData() },
                        onSaveToSection = { label, merchant, items, total, raw, langs ->
                            viewModel.saveOcrSection(
                                label = label, merchantName = merchant,
                                items = items, totalAmount = total,
                                rawOcrText = raw, detectedLanguages = langs
                            )
                        },
                        ocrParsedData = ocrParsedData,
                        categories = uiState.categories.map { it.name },
                        onNavigateBack = {
                            viewModel.clearOcrData()
                            currentScreen = "dashboard"
                            viewModel.setSelectedTab(0)
                        },
                        onNavigateToSections = { currentScreen = "ocr_sections" },
                        lastResult = uiState.lastOcrResult
                    )
                    "ocr_sections" -> OcrSectionsScreen(
                        sections = ocrSections,
                        onDeleteSection = { viewModel.deleteOcrSection(it) },
                        onClearAll = { viewModel.clearAllOcrSections() },
                        onGenerateReport = { since -> viewModel.generateOcrSectionsReport(since) },
                        onNavigateBack = { currentScreen = "dashboard"; viewModel.setSelectedTab(0) }
                    )
                    "sms_scan" -> SmsScanScreen(
                        scanState = smsScanState,
                        totalSmsCount = totalSmsCount,
                        onLoadSmsCount = { viewModel.loadTotalSmsCount() },
                        onStartScan = { maxMessages, startDate, endDate ->
                            viewModel.startSmsScan(maxMessages, startDate, endDate)
                        },
                        onConfirmAll = { viewModel.confirmSmsScanResults() },
                        onDiscard = { id -> viewModel.discardSmsScanResult(id) },
                        onReset = { viewModel.resetSmsScanState() },
                        onNavigateBack = { currentScreen = "settings"; viewModel.setSelectedTab(3) },
                        currencyCode = currencyCode
                    )
                    "store_map" -> StoreMapScreen(
                        storeLocations = storeLocations,
                        allTransactions = uiState.allTransactions,
                        currencyCode = currencyCode,
                        onAddStoreLocation = { name, lat, lng, addr ->
                            viewModel.addStoreLocation(name, lat, lng, addr)
                        },
                        onDeleteStoreLocation = { id -> viewModel.deleteStoreLocation(id) },
                        onUpdateStoreLocation = { store -> viewModel.updateStoreLocation(store) },
                        onClearAllStoreLocations = { viewModel.clearAllStoreLocations() },
                        onNavigateBack = { currentScreen = "dashboard"; viewModel.setSelectedTab(0) }
                    )
                    "ai_analyze" -> AiAnalyzeScreen(
                        categories = uiState.categories,
                        currencyCode = currencyCode,
                        onAnalyze = { start, end, cat -> viewModel.analyzeTransactions(start, end, cat) },
                        onNavigateBack = { currentScreen = "dashboard"; viewModel.setSelectedTab(0) }
                    )
                    "settings" -> SettingsScreen(
                        settings = uiState.settings,
                        storageInfo = viewModel.getStorageInfoText(),
                        isSubscribed = isSubscribed,
                        isTrialActive = viewModel.subscriptionManager.isTrialActive.collectAsState().value,
                        activePlanName = viewModel.subscriptionManager.activePlan.collectAsState().value?.displayName,
                        onShowPaywall = { feature ->
                            paywallFeatureName = feature
                            showPaywall = true
                        },
                        onRestorePurchases = { viewModel.subscriptionManager.restorePurchases() },
                        onUpdateSettings = { s -> viewModel.updateSettings(s) },
                        onExportToUri = { uri -> viewModel.exportDataToUri(uri) },
                        onImportFromUri = { uri -> viewModel.importDataFromUri(uri) },
                        onClearData = { scope.launch { viewModel.repository.clearAllData() } },
                        importExportMessage = importExportMessage,
                        onClearMessage = { viewModel.clearImportExportMessage() },
                        onScanSms = { currentScreen = "sms_scan" },
                        exchangeRates = exchangeRates,
                        onFetchRates = { viewModel.fetchExchangeRates() },
                        localAiStatus = localAiStatus,
                        localAiSuggestion = localAiSuggestion,
                        onCheckLocalAi = { viewModel.checkLocalAiAvailability() },
                        categories = uiState.categories,
                        onAddCategory = { name -> viewModel.addCategory(name) },
                        onDeleteCategory = { id -> viewModel.deleteCategory(id) },
                        onSetMonthlyLimit = { limit -> viewModel.setMonthlyExpenseLimit(limit) },
                        onConfigureSalary = { enabled, amount, day, desc ->
                            viewModel.configureSalaryScheduler(enabled, amount, day, desc)
                        },
                        onAddScheduledExpense = { viewModel.addScheduledExpense(it) },
                        onUpdateScheduledExpense = { viewModel.updateScheduledExpense(it) },
                        onDeleteScheduledExpense = { viewModel.deleteScheduledExpense(it) },
                        discoveredBankingApps = discoveredBankingApps,
                        isScanningBankingApps = isScanningBankingApps,
                        onScanBankingApps = { viewModel.scanForBankingApps() },
                        onAddBankingApp = { pkg -> viewModel.addBankingApp(pkg) },
                        onRemoveBankingApp = { pkg -> viewModel.removeBankingApp(pkg) },
                        allInstalledApps = allInstalledApps,
                        onLoadAllInstalledApps = { viewModel.loadAllInstalledApps() },
                        onUpdateScanKeywords = { keywords -> viewModel.updateScanKeywords(keywords) },
                        onUpdateIncomeKeywords = { keywords -> viewModel.updateIncomeKeywords(keywords) },
                        onUpdateExpenseKeywords = { keywords -> viewModel.updateExpenseKeywords(keywords) },
                        engineDescriptions = engineDescriptions,
                        onSetAiEngine = { pref -> viewModel.setAiEnginePreference(pref) },
                        discoveredModels = discoveredModels,
                        onDiscoverModels = { viewModel.discoverModels() },
                        onLoadModel = { path -> viewModel.loadMediaPipeModel(path) },
                        isLoadingModel = isLoadingModel,
                        modelCatalog = viewModel.modelCatalog,
                        onDownloadCatalogModel = { model -> viewModel.downloadCatalogModel(model) },
                        onDeleteCatalogModel = { model -> viewModel.deleteCatalogModel(model) },
                        isModelDownloaded = { model -> viewModel.isModelDownloaded(model) },
                        isDownloadingModel = isDownloadingModel,
                        downloadProgress = downloadProgress,
                        downloadError = downloadError,
                        onImportModelFile = { uri -> viewModel.importModelFile(uri) },
                        modelImportMessage = modelImportMessage,
                        isGalleryInstalled = viewModel.isGalleryInstalled(),
                        ollamaModels = ollamaModels,
                        ollamaConnecting = ollamaConnecting,
                        onConnectOllama = { host -> viewModel.connectOllama(host) },
                        onSelectOllamaModel = { name -> viewModel.selectOllamaModel(name) }
                    )
                }
            }
        }
    }

    // ─── Subscription Paywall Dialog ─────────────────────────────────
    if (showPaywall) {
        SubscriptionPaywallDialog(
            featureName = paywallFeatureName,
            isTrialEligible = viewModel.subscriptionManager.isTrialEligible,
            onStartTrial = {
                showPaywall = false
                viewModel.subscriptionManager.startFreeTrial()
            },
            onRestorePurchases = { viewModel.subscriptionManager.restorePurchases() },
            onDismiss = { showPaywall = false },
            onSubscribe = { plan ->
                showPaywall = false
                viewModel.subscriptionManager.launchPurchaseFlow(activity, plan)
            }
        )
    }
}
