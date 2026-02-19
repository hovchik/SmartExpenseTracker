package com.smartexpense.tracker

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
import com.smartexpense.tracker.ui.screens.*
import com.smartexpense.tracker.ui.theme.SmartExpenseTheme
import com.smartexpense.tracker.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: MainViewModel = viewModel()
            val themeMode by viewModel.themeMode.collectAsState()
            SmartExpenseTheme(themeMode = themeMode) {
                MainApp(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(viewModel: MainViewModel) {
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
    val scope = rememberCoroutineScope()

    // Shortcut to always-up-to-date currency code
    val currencyCode = uiState.settings.currencyCode

    var currentScreen by remember { mutableStateOf("dashboard") }

    // Intercept system back button: always go to Dashboard instead of closing the app.
    // Sub-screens that go back to a non-dashboard destination (sms_scan → settings)
    // are handled explicitly in the when-branch below.
    BackHandler(enabled = currentScreen != "dashboard") {
        when (currentScreen) {
            "sms_scan" -> { currentScreen = "settings"; viewModel.setSelectedTab(3) }
            else       -> { currentScreen = "dashboard"; viewModel.setSelectedTab(0) }
        }
    }

    // Hide the top bar on full-screen sub-screens
    val showTopBar = currentScreen !in listOf("add", "scan", "sms_scan")

    Scaffold(
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    title = {
                        Text(
                            when (currentScreen) {
                                "dashboard"    -> "Smart Expense"
                                "reports"      -> "Reports"
                                "transactions" -> "Transactions"
                                "settings"     -> "Settings"
                                else           -> "Smart Expense"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    },
                    actions = {
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
            if (currentScreen != "add" && currentScreen != "scan" && currentScreen != "sms_scan") {
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
                        currencyCode = currencyCode
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
                        onScanReceipt = { currentScreen = "scan" },
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
                        ocrParsedData = ocrParsedData,
                        categories = uiState.categories.map { it.name },
                        onNavigateBack = {
                            viewModel.clearOcrData()
                            currentScreen = "dashboard"
                            viewModel.setSelectedTab(0)
                        },
                        lastResult = uiState.lastOcrResult
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
                    "settings" -> SettingsScreen(
                        settings = uiState.settings,
                        storageInfo = viewModel.getStorageInfoText(),
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
}
