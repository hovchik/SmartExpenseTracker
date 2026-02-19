package com.smartexpense.tracker

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartexpense.tracker.ui.screens.*
import com.smartexpense.tracker.ui.theme.SmartExpenseTheme
import com.smartexpense.tracker.ui.viewmodel.MainViewModel
import com.smartexpense.tracker.util.LocaleHelper
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrapContext(newBase))
    }

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

    /** Called from Compose when the user changes the language. */
    fun applyLanguage(languageCode: String) {
        LocaleHelper.setLanguage(this, languageCode)
        recreate()
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
    val exchangeRates by viewModel.exchangeRates.collectAsState()
    val scope = rememberCoroutineScope()

    var currentScreen by remember { mutableStateOf("dashboard") }

    // Resolve navigation labels from string resources
    val navHome = stringResource(R.string.nav_home)
    val navReports = stringResource(R.string.nav_reports)
    val navAdd = stringResource(R.string.nav_add)
    val navScan = stringResource(R.string.nav_scan)
    val navSettings = stringResource(R.string.nav_settings)

    // Get the activity reference for language changes
    val activity = androidx.compose.ui.platform.LocalContext.current as? MainActivity

    Scaffold(
        bottomBar = {
            if (currentScreen != "add" && currentScreen != "scan" && currentScreen != "sms_scan") {
                NavigationBar(tonalElevation = 2.dp) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { viewModel.setSelectedTab(0); currentScreen = "dashboard" },
                        icon = { Icon(if (selectedTab == 0) Icons.Filled.Home else Icons.Outlined.Home, navHome) },
                        label = { Text(navHome) }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { viewModel.setSelectedTab(1); currentScreen = "reports" },
                        icon = { Icon(if (selectedTab == 1) Icons.Filled.Receipt else Icons.Outlined.Receipt, navReports) },
                        label = { Text(navReports) }
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = { currentScreen = "add" },
                        icon = { Icon(Icons.Filled.AddCircle, navAdd, modifier = Modifier.size(32.dp)) },
                        label = { Text(navAdd, fontWeight = FontWeight.Bold) }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { viewModel.setSelectedTab(2); currentScreen = "scan" },
                        icon = { Icon(if (selectedTab == 2) Icons.Filled.CameraAlt else Icons.Outlined.CameraAlt, navScan) },
                        label = { Text(navScan) }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 3,
                        onClick = { viewModel.setSelectedTab(3); currentScreen = "settings" },
                        icon = { Icon(if (selectedTab == 3) Icons.Filled.Settings else Icons.Outlined.Settings, navSettings) },
                        label = { Text(navSettings) }
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
                        onDeleteTransaction = { viewModel.deleteTransaction(it) }
                    )
                    "reports" -> ReportsScreen(
                        generateReport = { viewModel.generateReport(it) },
                        currentPeriod = reportPeriod,
                        onPeriodChange = { viewModel.setReportPeriod(it) },
                        currencyCode = uiState.settings.currencyCode
                    )
                    "add" -> AddTransactionScreen(
                        categories = uiState.categories,
                        onAdd = { amount, desc, category, type, source, merchant ->
                            viewModel.addTransaction(amount = amount, description = desc,
                                category = category, type = type, source = source, merchantName = merchant)
                        },
                        onScanReceipt = { currentScreen = "scan" },
                        onNavigateBack = { currentScreen = "dashboard"; viewModel.setSelectedTab(0) }
                    )
                    "scan" -> ScanReceiptScreen(
                        onOcrResult = { text -> viewModel.processOcrText(text) },
                        onNavigateBack = { currentScreen = "dashboard"; viewModel.setSelectedTab(0) },
                        lastResult = uiState.lastOcrResult
                    )
                    "sms_scan" -> SmsScanScreen(
                        scanState = smsScanState,
                        onStartScan = { viewModel.startSmsScan() },
                        onConfirmAll = { viewModel.confirmSmsScanResults() },
                        onDiscard = { id -> viewModel.discardSmsScanResult(id) },
                        onReset = { viewModel.resetSmsScanState() },
                        onNavigateBack = { currentScreen = "settings"; viewModel.setSelectedTab(3) }
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
                        onLanguageChanged = { langCode ->
                            activity?.applyLanguage(langCode)
                        }
                    )
                }
            }
        }
    }
}
