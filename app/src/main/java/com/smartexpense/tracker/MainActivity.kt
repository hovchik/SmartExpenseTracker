package com.smartexpense.tracker

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
    val scope = rememberCoroutineScope()

    var currentScreen by remember { mutableStateOf("dashboard") }

    Scaffold(
        bottomBar = {
            if (currentScreen != "add" && currentScreen != "scan" && currentScreen != "sms_scan") {
                NavigationBar(tonalElevation = 2.dp) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { viewModel.setSelectedTab(0); currentScreen = "dashboard" },
                        icon = { Icon(if (selectedTab == 0) Icons.Filled.Home else Icons.Outlined.Home, "Home") },
                        label = { Text("Home") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { viewModel.setSelectedTab(1); currentScreen = "reports" },
                        icon = { Icon(if (selectedTab == 1) Icons.Filled.Receipt else Icons.Outlined.Receipt, "Reports") },
                        label = { Text("Reports") }
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = { currentScreen = "add" },
                        icon = { Icon(Icons.Filled.AddCircle, "Add", modifier = Modifier.size(32.dp)) },
                        label = { Text("Add", fontWeight = FontWeight.Bold) }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { viewModel.setSelectedTab(2); currentScreen = "scan" },
                        icon = { Icon(if (selectedTab == 2) Icons.Filled.CameraAlt else Icons.Outlined.CameraAlt, "Scan") },
                        label = { Text("Scan") }
                    )
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
            // Crossfade avoids lifecycle issues that AnimatedContent causes
            // with rememberLauncherForActivityResult inside child screens
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
                        onPeriodChange = { viewModel.setReportPeriod(it) }
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
                        onUpdateSettings = { s -> scope.launch { viewModel.repository.updateSettings(s) } },
                        onExportToUri = { uri -> viewModel.exportDataToUri(uri) },
                        onImportFromUri = { uri -> viewModel.importDataFromUri(uri) },
                        onClearData = { scope.launch { viewModel.repository.clearAllData() } },
                        importExportMessage = importExportMessage,
                        onClearMessage = { viewModel.clearImportExportMessage() },
                        onScanSms = { currentScreen = "sms_scan" }
                    )
                }
            }
        }
    }
}
