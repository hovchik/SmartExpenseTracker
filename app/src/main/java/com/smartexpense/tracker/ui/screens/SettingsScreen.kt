package com.smartexpense.tracker.ui.screens

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.smartexpense.tracker.data.model.AppSettings
import com.smartexpense.tracker.data.model.AiEnginePreference
import com.smartexpense.tracker.data.model.ScheduledExpense
import java.io.File
import com.smartexpense.tracker.data.model.Category
import com.smartexpense.tracker.data.model.SUPPORTED_CURRENCIES
import com.smartexpense.tracker.data.model.ThemeMode
import com.smartexpense.tracker.data.model.currencyInfoFor
import com.smartexpense.tracker.BuildConfig
import com.smartexpense.tracker.service.currency.CurrencyConverterService
import com.smartexpense.tracker.ui.components.PremiumBadge
import com.smartexpense.tracker.ui.theme.*

@Composable
fun SettingsScreen(
    settings: AppSettings,
    storageInfo: String,
    isSubscribed: Boolean = false,
    isTrialActive: Boolean = false,
    activePlanName: String? = null,
    onShowPaywall: (featureName: String) -> Unit = {},
    onRestorePurchases: () -> Unit = {},
    onUpdateSettings: (AppSettings) -> Unit,
    onExportToUri: (Uri) -> Unit,
    onImportFromUri: (Uri) -> Unit,
    onClearData: () -> Unit,
    importExportMessage: String?,
    onClearMessage: () -> Unit,
    onScanSms: () -> Unit,
    /** Null = not yet fetched; empty map = fetch failed. */
    exchangeRates: Map<String, Double> = emptyMap(),
    onFetchRates: () -> Unit = {},
    /** Null = availability not checked yet. Non-null = status message from LocalAiService. */
    localAiStatus: String? = null,
    /** Non-null when there's a suggestion for enabling a better AI backend. */
    localAiSuggestion: String? = null,
    onCheckLocalAi: () -> Unit = {},
    categories: List<Category> = emptyList(),
    onAddCategory: (String) -> Unit = {},
    onDeleteCategory: (String) -> Unit = {},
    onSetMonthlyLimit: (Double) -> Unit = {},
    onConfigureSalary: (enabled: Boolean, amount: Double, dayOfMonth: Int, description: String) -> Unit = { _, _, _, _ -> },
    onAddScheduledExpense: (ScheduledExpense) -> Unit = {},
    onUpdateScheduledExpense: (ScheduledExpense) -> Unit = {},
    onDeleteScheduledExpense: (String) -> Unit = {},
    /** Discovered banking apps from device scan. */
    discoveredBankingApps: List<com.smartexpense.tracker.ui.viewmodel.MainViewModel.DiscoveredApp> = emptyList(),
    isScanningBankingApps: Boolean = false,
    onScanBankingApps: () -> Unit = {},
    onAddBankingApp: (String) -> Unit = {},
    onRemoveBankingApp: (String) -> Unit = {},
    /** All user-installed apps on the device. */
    allInstalledApps: List<com.smartexpense.tracker.ui.viewmodel.MainViewModel.InstalledApp> = emptyList(),
    onLoadAllInstalledApps: () -> Unit = {},
    onUpdateScanKeywords: (List<String>) -> Unit = {},
    // ── Transaction type detection keywords ──
    onUpdateIncomeKeywords: (List<String>) -> Unit = {},
    onUpdateExpenseKeywords: (List<String>) -> Unit = {},
    // ── AI engine selection ──
    engineDescriptions: Map<AiEnginePreference, String> = emptyMap(),
    onSetAiEngine: (AiEnginePreference) -> Unit = {},
    discoveredModels: List<Pair<String, String>> = emptyList(),
    onDiscoverModels: () -> Unit = {},
    onLoadModel: (String) -> Unit = {},
    isLoadingModel: Boolean = false,
    // ── Model catalog & download ──
    modelCatalog: List<com.smartexpense.tracker.service.ai.MediaPipeLlmService.CatalogModel> = emptyList(),
    onDownloadCatalogModel: (com.smartexpense.tracker.service.ai.MediaPipeLlmService.CatalogModel) -> Unit = {},
    onDeleteCatalogModel: (com.smartexpense.tracker.service.ai.MediaPipeLlmService.CatalogModel) -> Unit = {},
    isModelDownloaded: (com.smartexpense.tracker.service.ai.MediaPipeLlmService.CatalogModel) -> Boolean = { false },
    isDownloadingModel: Boolean = false,
    downloadProgress: Float = 0f,
    downloadError: String? = null,
    // ── Model file import ──
    onImportModelFile: (Uri) -> Unit = {},
    modelImportMessage: String? = null,
    isGalleryInstalled: Boolean = false,
    // ── Ollama ──
    ollamaModels: List<com.smartexpense.tracker.service.ai.OllamaService.OllamaModel> = emptyList(),
    ollamaConnecting: Boolean = false,
    onConnectOllama: (String) -> Unit = {},
    onSelectOllamaModel: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var showClearDialog by remember { mutableStateOf(false) }
    var showImportConfirmDialog by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

    // Monthly expense limit state
    var monthlyLimitText by remember(settings.monthlyExpenseLimit) {
        mutableStateOf(if (settings.monthlyExpenseLimit > 0) settings.monthlyExpenseLimit.toLong().toString() else "")
    }

    // Salary scheduler state
    var salaryEnabled by remember(settings.scheduledSalaryEnabled) { mutableStateOf(settings.scheduledSalaryEnabled) }
    var salaryAmountText by remember(settings.scheduledSalaryAmount) {
        mutableStateOf(if (settings.scheduledSalaryAmount > 0) settings.scheduledSalaryAmount.toLong().toString() else "")
    }
    var salaryDayText by remember(settings.scheduledSalaryDayOfMonth) {
        mutableStateOf(settings.scheduledSalaryDayOfMonth.toString())
    }
    var salaryDescription by remember(settings.scheduledSalaryDescription) {
        mutableStateOf(settings.scheduledSalaryDescription)
    }

    // Category management state
    var newCategoryText by remember { mutableStateOf("") }

    // Section expanded state (all closed by default)
    var appearanceExpanded by remember { mutableStateOf(false) }
    var currencyExpanded by remember { mutableStateOf(false) }
    var dataSourcesExpanded by remember { mutableStateOf(false) }
    var appScannerExpanded by remember { mutableStateOf(false) }
    var connectedAppsExpanded by remember { mutableStateOf(false) }
    var budgetExpanded by remember { mutableStateOf(false) }
    var salaryExpanded by remember { mutableStateOf(false) }
    var scheduledExpensesExpanded by remember { mutableStateOf(false) }
    var categoriesExpanded by remember { mutableStateOf(false) }
    var localAiExpanded by remember { mutableStateOf(false) }
    var importExportExpanded by remember { mutableStateOf(false) }
    var storageExpanded by remember { mutableStateOf(false) }
    var permissionsExpanded by remember { mutableStateOf(false) }

    // Currency selector state
    var showCurrencyDropdown by remember { mutableStateOf(false) }

    // Currency converter state
    var convertAmount by remember { mutableStateOf("") }
    var convertFromCode by remember { mutableStateOf(settings.currencyCode) }
    var convertToCode by remember { mutableStateOf(if (settings.currencyCode == "USD") "AMD" else "USD") }
    var showFromDropdown by remember { mutableStateOf(false) }
    var showToDropdown by remember { mutableStateOf(false) }

    val convertedResult: String? = remember(convertAmount, convertFromCode, convertToCode, exchangeRates) {
        val amt = convertAmount.toDoubleOrNull() ?: return@remember null
        if (exchangeRates.isEmpty()) return@remember null
        if (convertFromCode == convertToCode) return@remember "${currencyInfoFor(convertToCode).symbol}${String.format("%.2f", amt)}"
        // Rates are relative to the fetched base.
        // We need: toRate / fromRate (all relative to the same base)
        val fromRate = exchangeRates[convertFromCode] ?: return@remember null
        val toRate   = exchangeRates[convertToCode]   ?: return@remember null
        val converted = amt * (toRate / fromRate)
        "${currencyInfoFor(convertToCode).symbol}${String.format("%.2f", converted)}"
    }

    // SAF launchers
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { onExportToUri(it) } }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            pendingImportUri = it
            showImportConfirmDialog = true
        }
    }

    // Show toast for import/export feedback
    LaunchedEffect(importExportMessage) {
        importExportMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            onClearMessage()
        }
    }

    // Fetch rates lazily when converter section comes into view
    LaunchedEffect(Unit) { onFetchRates() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ─── Appearance Section ────────────────────────────────────
        CollapsibleSectionHeader("APPEARANCE", appearanceExpanded) { appearanceExpanded = !appearanceExpanded }

        AnimatedVisibility(
            visible = appearanceExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Palette,
                            contentDescription = null,
                            tint = PurpleAccent,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("Theme", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            Text(
                                "Choose between Light, Dark, or System default",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    ThemeModePicker(
                        currentMode = settings.themeMode,
                        onModeSelected = { mode ->
                            onUpdateSettings(settings.copy(themeMode = mode))
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ─── Currency Section ──────────────────────────────────────
        CollapsibleSectionHeader("CURRENCY", currencyExpanded) { currencyExpanded = !currencyExpanded }

        AnimatedVisibility(
            visible = currencyExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                // Currency picker row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.AttachMoney,
                        contentDescription = null,
                        tint = GreenPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Display Currency", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Text(
                            "Used for formatting and OCR receipt scanning",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Currency selector button
                    Box {
                        val selInfo = currencyInfoFor(settings.currencyCode)
                        OutlinedButton(
                            onClick = { showCurrencyDropdown = true },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("${selInfo.symbol} ${selInfo.code}", fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = "Select currency", modifier = Modifier.size(18.dp))
                        }

                        DropdownMenu(
                            expanded = showCurrencyDropdown,
                            onDismissRequest = { showCurrencyDropdown = false }
                        ) {
                            SUPPORTED_CURRENCIES.forEach { info ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                info.symbol,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.width(30.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(info.code, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                                Text(info.name, style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            if (info.code == settings.currencyCode) {
                                                Spacer(modifier = Modifier.weight(1f))
                                                Icon(Icons.Filled.Check, contentDescription = null,
                                                    tint = GreenPrimary, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    },
                                    onClick = {
                                        onUpdateSettings(
                                            settings.copy(
                                                currencyCode = info.code,
                                                currency = info.symbol
                                            )
                                        )
                                        showCurrencyDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                // ── Exchange Rate Source ──────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.TravelExplore, contentDescription = null,
                        tint = GreenPrimary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Exchange Rate Source", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Text(
                            when (settings.rateSource) {
                                com.smartexpense.tracker.data.model.RateSource.RATE_AM ->
                                    "rate.am \u2013 Armenian bank rates"
                                else ->
                                    "Open API \u2013 global exchange rates"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    com.smartexpense.tracker.data.model.RateSource.entries.forEach { source ->
                        val selected = settings.rateSource == source
                        FilterChip(
                            selected = selected,
                            onClick = {
                                if (!selected) {
                                    CurrencyConverterService.invalidateCache()
                                    onUpdateSettings(settings.copy(rateSource = source))
                                    onFetchRates()
                                }
                            },
                            label = {
                                Text(
                                    when (source) {
                                        com.smartexpense.tracker.data.model.RateSource.OPEN_API -> "Open API"
                                        com.smartexpense.tracker.data.model.RateSource.RATE_AM -> "rate.am"
                                    },
                                    fontSize = 13.sp
                                )
                            },
                            leadingIcon = if (selected) {
                                { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ── Rate Update Frequency ────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Schedule, contentDescription = null,
                        tint = GreenPrimary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Rate Update Frequency", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Text(settings.rateUpdateFrequency.label,
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val visibleFreqs = listOf(
                        com.smartexpense.tracker.data.model.RateUpdateFrequency.EVERY_HOUR,
                        com.smartexpense.tracker.data.model.RateUpdateFrequency.EVERY_3_HOURS,
                        com.smartexpense.tracker.data.model.RateUpdateFrequency.DAILY,
                        com.smartexpense.tracker.data.model.RateUpdateFrequency.MANUAL
                    )
                    visibleFreqs.forEach { freq ->
                        val selected = settings.rateUpdateFrequency == freq
                        FilterChip(
                            selected = selected,
                            onClick = {
                                if (!selected) {
                                    onUpdateSettings(settings.copy(rateUpdateFrequency = freq))
                                }
                            },
                            label = { Text(freq.label, fontSize = 11.sp) },
                            leadingIcon = if (selected) {
                                { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                            } else null
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                // ── Currency Converter ─────────────────────────────
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.SwapHoriz, contentDescription = null,
                        tint = BluePrimary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Currency Converter", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Row 1: Amount input (full width)
                OutlinedTextField(
                    value = convertAmount,
                    onValueChange = { convertAmount = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Amount to convert") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    leadingIcon = {
                        Text(currencyInfoFor(convertFromCode).symbol,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 4.dp))
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Row 2: From / Swap / To selectors
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // From currency
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { showFromDropdown = true },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("From", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(currencyInfoFor(convertFromCode).symbol,
                                        fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(convertFromCode, fontSize = 12.sp)
                                    Icon(Icons.Filled.ArrowDropDown, null, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        DropdownMenu(expanded = showFromDropdown, onDismissRequest = { showFromDropdown = false }) {
                            SUPPORTED_CURRENCIES.forEach { info ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(info.symbol, fontWeight = FontWeight.Bold,
                                                modifier = Modifier.width(28.dp))
                                            Text("${info.code}  ", fontSize = 13.sp)
                                            Text(info.name, fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    },
                                    onClick = { convertFromCode = info.code; showFromDropdown = false }
                                )
                            }
                        }
                    }

                    // Swap button
                    FilledTonalIconButton(
                        onClick = {
                            val tmp = convertFromCode
                            convertFromCode = convertToCode
                            convertToCode = tmp
                        }
                    ) {
                        Icon(Icons.Filled.SwapHoriz, contentDescription = "Swap currencies",
                            modifier = Modifier.size(20.dp))
                    }

                    // To currency
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { showToDropdown = true },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("To", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(currencyInfoFor(convertToCode).symbol,
                                        fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(convertToCode, fontSize = 12.sp)
                                    Icon(Icons.Filled.ArrowDropDown, null, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        DropdownMenu(expanded = showToDropdown, onDismissRequest = { showToDropdown = false }) {
                            SUPPORTED_CURRENCIES.forEach { info ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(info.symbol, fontWeight = FontWeight.Bold,
                                                modifier = Modifier.width(28.dp))
                                            Text("${info.code}  ", fontSize = 13.sp)
                                            Text(info.name, fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    },
                                    onClick = { convertToCode = info.code; showToDropdown = false }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Result area
                when {
                    convertAmount.isNotEmpty() && exchangeRates.isEmpty() -> {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Fetching live rates…", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    convertedResult != null -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.CheckCircle, contentDescription = null,
                                        tint = GreenPrimary, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Conversion Result",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "$convertAmount ${currencyInfoFor(convertFromCode).symbol}" +
                                        " (${convertFromCode}) = ",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    "$convertedResult (${convertToCode})",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        val freqLabel = settings.rateUpdateFrequency.label.lowercase()
                        Text(
                            when (settings.rateSource) {
                                com.smartexpense.tracker.data.model.RateSource.RATE_AM ->
                                    "Rates from rate.am \u00B7 Armenian bank averages \u00B7 $freqLabel"
                                else ->
                                    "Rates from open.er-api.com \u00B7 $freqLabel"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
        } // end AnimatedVisibility for Currency

        Spacer(modifier = Modifier.height(24.dp))

        // ─── Data Sources Section ──────────────────────────────────
        CollapsibleSectionHeader("DATA SOURCES", dataSourcesExpanded) { dataSourcesExpanded = !dataSourcesExpanded }

        AnimatedVisibility(
            visible = dataSourcesExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
        Column {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(4.dp)) {
                SettingsToggleItem(
                    icon = Icons.Filled.Sms,
                    title = "SMS Transaction Detection",
                    subtitle = "Automatically parse banking SMS messages",
                    checked = settings.smsParsingEnabled,
                    onCheckedChange = {
                        onUpdateSettings(settings.copy(smsParsingEnabled = it))
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                SettingsToggleItem(
                    icon = Icons.Filled.Notifications,
                    title = "Banking App Notifications",
                    subtitle = "Read notifications from banking apps",
                    checked = settings.notificationListenerEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            try {
                                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                val intent = Intent(Settings.ACTION_SETTINGS)
                                context.startActivity(intent)
                            }
                        }
                        onUpdateSettings(settings.copy(notificationListenerEnabled = enabled))
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                SettingsToggleItem(
                    icon = Icons.Filled.AutoAwesome,
                    title = "AI Auto-Categorization",
                    subtitle = "Automatically categorize transactions using AI",
                    checked = settings.autoCategorizationEnabled,
                    onCheckedChange = {
                        onUpdateSettings(settings.copy(autoCategorizationEnabled = it))
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ─── Transaction Type Detection Keywords ─────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Label,
                        contentDescription = null,
                        tint = BluePrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Transaction Type Keywords", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Text(
                            "Keywords used to classify incoming SMS/notifications as income or expense.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // ── Income keywords ──
                Text(
                    "Income keywords",
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = GreenPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))

                val incomeKws = settings.incomeKeywords
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    incomeKws.forEach { kw ->
                        InputChip(
                            selected = false,
                            onClick = { onUpdateIncomeKeywords(incomeKws.filter { it != kw }) },
                            label = { Text(kw, fontSize = 12.sp) },
                            trailingIcon = {
                                Icon(Icons.Filled.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp))
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                var newIncomeKw by remember { mutableStateOf("") }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newIncomeKw,
                        onValueChange = { newIncomeKw = it.trim() },
                        label = { Text("Add income keyword") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    Button(
                        onClick = {
                            val kw = newIncomeKw.trim()
                            if (kw.isNotEmpty() && kw !in incomeKws) {
                                onUpdateIncomeKeywords(incomeKws + kw)
                                newIncomeKw = ""
                            }
                        },
                        enabled = newIncomeKw.trim().let { it.isNotEmpty() && it !in incomeKws },
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("Add") }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                // ── Expense keywords ──
                Text(
                    "Expense keywords",
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = RedExpense
                )
                Spacer(modifier = Modifier.height(6.dp))

                val expenseKws = settings.expenseKeywords
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    expenseKws.forEach { kw ->
                        InputChip(
                            selected = false,
                            onClick = { onUpdateExpenseKeywords(expenseKws.filter { it != kw }) },
                            label = { Text(kw, fontSize = 12.sp) },
                            trailingIcon = {
                                Icon(Icons.Filled.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp))
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                var newExpenseKw by remember { mutableStateOf("") }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newExpenseKw,
                        onValueChange = { newExpenseKw = it.trim() },
                        label = { Text("Add expense keyword") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    Button(
                        onClick = {
                            val kw = newExpenseKw.trim()
                            if (kw.isNotEmpty() && kw !in expenseKws) {
                                onUpdateExpenseKeywords(expenseKws + kw)
                                newExpenseKw = ""
                            }
                        },
                        enabled = newExpenseKw.trim().let { it.isNotEmpty() && it !in expenseKws },
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("Add") }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onScanSms,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BluePrimary)
        ) {
            Icon(Icons.Filled.Sms, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text("Scan SMS Inbox for Transactions", fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ─── Connected Banking Apps (collapsible) ────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                val chevronRotation by animateFloatAsState(
                    targetValue = if (connectedAppsExpanded) 180f else 0f,
                    label = "connectedAppsChevron"
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { connectedAppsExpanded = !connectedAppsExpanded },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.AccountBalance,
                        contentDescription = null,
                        tint = BluePrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Connected Banking Apps", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Text(
                            "${settings.bankingAppPackages.size} app(s) being monitored for transactions",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.Filled.ExpandMore,
                        contentDescription = if (connectedAppsExpanded) "Collapse" else "Expand",
                        modifier = Modifier.size(24.dp).rotate(chevronRotation),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                AnimatedVisibility(
                    visible = connectedAppsExpanded,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column {
                        if (settings.bankingAppPackages.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(6.dp))
                            settings.bankingAppPackages.forEach { pkg ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = GreenPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        pkg,
                                        modifier = Modifier.weight(1f),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    IconButton(
                                        onClick = { onRemoveBankingApp(pkg) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = "Remove $pkg",
                                            tint = RedExpense,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "No banking apps connected yet. Use the scanner below to find and add apps.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        } // end Column in AnimatedVisibility for Data Sources
        } // end AnimatedVisibility for Data Sources

        Spacer(modifier = Modifier.height(24.dp))

        // ─── Application Scanner Section ─────────────────────────
        CollapsibleSectionHeader(
            title = "APPLICATION SCANNER",
            expanded = appScannerExpanded,
            isPremium = !isSubscribed,
            onToggle = {
                if (isSubscribed) appScannerExpanded = !appScannerExpanded
                else onShowPaywall("Application Scanner")
            }
        )

        AnimatedVisibility(
            visible = appScannerExpanded && isSubscribed,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
        Column {

        // ─── Scan Keywords ──────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Tune,
                        contentDescription = null,
                        tint = BluePrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Scan Keywords", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Text(
                            "Keywords matched against app names and package names when scanning.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                val currentKeywords = settings.scanKeywords
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    currentKeywords.forEach { keyword ->
                        InputChip(
                            selected = false,
                            onClick = {
                                onUpdateScanKeywords(currentKeywords.filter { it != keyword })
                            },
                            label = { Text(keyword, fontSize = 13.sp) },
                            trailingIcon = {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Remove $keyword",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                var newKeyword by remember { mutableStateOf("") }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newKeyword,
                        onValueChange = { newKeyword = it.lowercase().trim() },
                        label = { Text("New keyword") },
                        placeholder = { Text("e.g. finance", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    Button(
                        onClick = {
                            val kw = newKeyword.trim()
                            if (kw.isNotEmpty() && kw !in currentKeywords) {
                                onUpdateScanKeywords(currentKeywords + kw)
                                newKeyword = ""
                            }
                        },
                        enabled = newKeyword.trim().let { it.isNotEmpty() && it !in currentKeywords },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Add")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ─── Find Banking Apps by Keyword ────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        tint = GreenPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Find Banking Apps on Device", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Text(
                            "Scans installed apps whose name contains ${settings.scanKeywords.joinToString { "\"$it\"" }}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onScanBankingApps,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                    enabled = !isScanningBankingApps
                ) {
                    if (isScanningBankingApps) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (isScanningBankingApps) "Scanning..." else "Scan for Banking Apps",
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (isScanningBankingApps) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                        color = GreenPrimary,
                        trackColor = GreenPrimary.copy(alpha = 0.15f)
                    )
                }

                if (discoveredBankingApps.isNotEmpty()) {
                    val newApps = discoveredBankingApps.filter { !it.isAlreadyMonitored }
                    val alreadyAdded = discoveredBankingApps.filter { it.isAlreadyMonitored }

                    if (newApps.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "${newApps.size} new app(s) found",
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            color = BluePrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        newApps.forEach { app ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.PhoneAndroid,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(app.appName, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                    Text(
                                        app.packageName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                }
                                IconButton(
                                    onClick = { onAddBankingApp(app.packageName) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.AddCircle,
                                        contentDescription = "Add ${app.appName}",
                                        tint = GreenPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                    if (alreadyAdded.isNotEmpty() && newApps.isEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "All ${alreadyAdded.size} detected app(s) are already connected.",
                            style = MaterialTheme.typography.bodySmall,
                            color = GreenPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ─── Browse All Installed Apps ───────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                var showAllApps by remember { mutableStateOf(false) }
                var appSearchQuery by remember { mutableStateOf("") }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Apps,
                        contentDescription = null,
                        tint = BluePrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("All Installed Apps", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Text(
                            "Browse and add any app from your device to the monitored list.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {
                        onLoadAllInstalledApps()
                        showAllApps = !showAllApps
                        appSearchQuery = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BluePrimary)
                ) {
                    Icon(
                        if (showAllApps) Icons.Filled.ExpandLess else Icons.Filled.Apps,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (showAllApps) "Hide App List" else "Show All Installed Apps",
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (showAllApps) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = appSearchQuery,
                        onValueChange = { appSearchQuery = it },
                        label = { Text("Search apps") },
                        placeholder = { Text("Filter by name or package", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Filled.Search, null, modifier = Modifier.size(18.dp)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val monitoredSet = settings.bankingAppPackages.toSet()
                    val query = appSearchQuery.lowercase()
                    val filteredApps = allInstalledApps.filter { app ->
                        (query.isEmpty() ||
                            app.appName.lowercase().contains(query) ||
                            app.packageName.lowercase().contains(query)) &&
                            app.packageName !in monitoredSet
                    }

                    if (allInstalledApps.isEmpty()) {
                        Text(
                            "Loading apps\u2026",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            "${filteredApps.size} app(s) available",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            filteredApps.forEach { app ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Filled.PhoneAndroid,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(app.appName, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                        Text(
                                            app.packageName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp
                                        )
                                    }
                                    IconButton(
                                        onClick = { onAddBankingApp(app.packageName) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.AddCircle,
                                            contentDescription = "Add ${app.appName}",
                                            tint = GreenPrimary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ─── Manual Package Input ────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Add App Manually", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Text(
                            "Enter a package name directly (e.g. com.sflpro.inecomobile).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                var manualPackageName by remember { mutableStateOf("") }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = manualPackageName,
                        onValueChange = { manualPackageName = it.trim() },
                        label = { Text("Package name") },
                        placeholder = { Text("com.example.app", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    Button(
                        onClick = {
                            val pkg = manualPackageName.trim()
                            if (pkg.isNotEmpty() && pkg.contains(".")) {
                                onAddBankingApp(pkg)
                                manualPackageName = ""
                            }
                        },
                        enabled = manualPackageName.trim().let { it.isNotEmpty() && it.contains(".") },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Add")
                    }
                }
            }
        }

        } // end Column in AnimatedVisibility for Application Scanner
        } // end AnimatedVisibility for Application Scanner

        Spacer(modifier = Modifier.height(24.dp))

        // ─── Monthly Expense Limit Section ────────────────────────
        CollapsibleSectionHeader(
            title = "BUDGET THRESHOLD",
            expanded = budgetExpanded,
            isPremium = !isSubscribed,
            onToggle = {
                if (isSubscribed) budgetExpanded = !budgetExpanded
                else onShowPaywall("Budget Threshold")
            }
        )

        AnimatedVisibility(
            visible = budgetExpanded && isSubscribed,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.NotificationsActive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Monthly Expense Limit", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Text(
                            "Receive a phone notification when monthly expenses exceed this amount. Set 0 to disable.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = monthlyLimitText,
                        onValueChange = { monthlyLimitText = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Limit amount (${settings.currencyCode})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        leadingIcon = {
                            Text(
                                com.smartexpense.tracker.data.model.currencyInfoFor(settings.currencyCode).symbol,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    )
                    Button(
                        onClick = {
                            val limit = monthlyLimitText.toDoubleOrNull() ?: 0.0
                            onSetMonthlyLimit(limit)
                            Toast.makeText(
                                context,
                                if (limit > 0) "Limit set to ${settings.currencyCode} ${String.format("%.0f", limit)}"
                                else "Monthly limit disabled",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Save")
                    }
                }
                if (settings.monthlyExpenseLimit > 0) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Active: ${com.smartexpense.tracker.data.model.currencyInfoFor(settings.currencyCode).symbol}" +
                            "${String.format("%.2f", settings.monthlyExpenseLimit)} / month",
                        style = MaterialTheme.typography.bodySmall,
                        color = GreenPrimary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        } // end AnimatedVisibility for Budget

        Spacer(modifier = Modifier.height(24.dp))

        // ─── Salary Scheduler Section ──────────────────────────────
        CollapsibleSectionHeader(
            title = "SALARY SCHEDULER",
            expanded = salaryExpanded,
            isPremium = !isSubscribed,
            onToggle = {
                if (isSubscribed) salaryExpanded = !salaryExpanded
                else onShowPaywall("Salary Scheduler")
            }
        )

        AnimatedVisibility(
            visible = salaryExpanded && isSubscribed,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Schedule,
                        contentDescription = null,
                        tint = GreenPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-add Monthly Salary", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Text(
                            "Automatically record an income transaction on a fixed day each month.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = salaryEnabled,
                        onCheckedChange = { salaryEnabled = it }
                    )
                }

                if (salaryEnabled) {
                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = salaryDescription,
                        onValueChange = { salaryDescription = it },
                        label = { Text("Description") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = salaryAmountText,
                            onValueChange = { salaryAmountText = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text("Amount (${settings.currencyCode})") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(2f),
                            shape = RoundedCornerShape(10.dp),
                            leadingIcon = {
                                Text(
                                    com.smartexpense.tracker.data.model.currencyInfoFor(settings.currencyCode).symbol,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                        )
                        OutlinedTextField(
                            value = salaryDayText,
                            onValueChange = {
                                val filtered = it.filter { c -> c.isDigit() }
                                val num = filtered.toIntOrNull()
                                if (num == null || num in 1..31) salaryDayText = filtered
                            },
                            label = { Text("Day (1–31)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            val amount = salaryAmountText.toDoubleOrNull() ?: 0.0
                            val day = salaryDayText.toIntOrNull()?.coerceIn(1, 31) ?: 1
                            onConfigureSalary(salaryEnabled, amount, day, salaryDescription)
                            Toast.makeText(
                                context,
                                if (salaryEnabled && amount > 0)
                                    "Salary of ${settings.currencyCode} ${String.format("%.0f", amount)} scheduled on day $day"
                                else "Salary scheduler disabled",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Salary Schedule", fontWeight = FontWeight.SemiBold)
                    }
                } else if (settings.scheduledSalaryEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { onConfigureSalary(false, 0.0, 1, "") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Text("Disable Salary Scheduler")
                    }
                }

                if (settings.scheduledSalaryEnabled && settings.scheduledSalaryAmount > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Active: ${settings.scheduledSalaryDescription} · " +
                            "${com.smartexpense.tracker.data.model.currencyInfoFor(settings.currencyCode).symbol}" +
                            "${String.format("%.2f", settings.scheduledSalaryAmount)} on day " +
                            "${settings.scheduledSalaryDayOfMonth} of each month",
                        style = MaterialTheme.typography.bodySmall,
                        color = GreenPrimary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        } // end AnimatedVisibility for Salary

        Spacer(modifier = Modifier.height(24.dp))

        // ─── Scheduled Expenses Section ─────────────────────────────
        CollapsibleSectionHeader(
            title = "SCHEDULED EXPENSES",
            expanded = scheduledExpensesExpanded,
            isPremium = !isSubscribed,
            onToggle = {
                if (isSubscribed) scheduledExpensesExpanded = !scheduledExpensesExpanded
                else onShowPaywall("Scheduled Expenses")
            }
        )

        AnimatedVisibility(
            visible = scheduledExpensesExpanded && isSubscribed,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            ScheduledExpensesSection(
                expenses = settings.scheduledExpenses,
                currencyCode = settings.currencyCode,
                onAdd = onAddScheduledExpense,
                onUpdate = onUpdateScheduledExpense,
                onDelete = onDeleteScheduledExpense
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ─── Categories Section ────────────────────────────────────
        CollapsibleSectionHeader(
            title = "CATEGORIES",
            expanded = categoriesExpanded,
            onToggle = { categoriesExpanded = !categoriesExpanded }
        )

        AnimatedVisibility(
            visible = categoriesExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Add new category row
                if (isSubscribed) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newCategoryText,
                            onValueChange = { newCategoryText = it },
                            label = { Text("New category name") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        FilledTonalIconButton(
                            onClick = {
                                val name = newCategoryText.trim()
                                if (name.isNotEmpty()) {
                                    onAddCategory(name)
                                    newCategoryText = ""
                                }
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Add category")
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = { onShowPaywall("Custom Categories") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        PremiumBadge()
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add Custom Categories")
                    }
                }

                if (categories.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))

                    categories.forEach { cat ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Label,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                cat.name,
                                modifier = Modifier.weight(1f),
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp
                            )
                            IconButton(
                                onClick = { onDeleteCategory(cat.id) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "Delete ${cat.name}",
                                    tint = RedExpense,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        } // end AnimatedVisibility for Categories

        Spacer(modifier = Modifier.height(24.dp))

        // ─── Local AI Section ──────────────────────────────────────
        CollapsibleSectionHeader("LOCAL AI", localAiExpanded) { localAiExpanded = !localAiExpanded }

        AnimatedVisibility(
            visible = localAiExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
        Column {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(4.dp)) {
                SettingsToggleItem(
                    icon = Icons.Filled.Psychology,
                    title = "On-Device AI",
                    subtitle = "Enhanced AI for smarter categorisation & financial insights. All processing happens on-device.",
                    checked = settings.localAiEnabled,
                    onCheckedChange = { enabled ->
                        onUpdateSettings(settings.copy(localAiEnabled = enabled))
                        if (enabled) onCheckLocalAi()
                    }
                )

                // Engine selector and status — shown when toggle is on
                if (settings.localAiEnabled) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    // ── Engine selector ──
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text("AI Engine", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(8.dp))

                        val engines = listOf(
                            AiEnginePreference.AUTO to "Auto-detect",
                            AiEnginePreference.OLLAMA to "Ollama",
                            AiEnginePreference.MEDIAPIPE_LLM to "MediaPipe LLM",
                            AiEnginePreference.GEMINI_NANO to "Gemini Nano / Galaxy AI",
                            AiEnginePreference.RULE_BASED to "Rule-based (no model)"
                        )
                        engines.forEach { (pref, label) ->
                            val desc = engineDescriptions[pref] ?: ""
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onSetAiEngine(pref) }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = settings.aiEnginePreference == pref,
                                    onClick = { onSetAiEngine(pref) }
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    if (desc.isNotEmpty()) {
                                        Text(desc, fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }

                    // ── Ollama configuration ──
                    if (settings.aiEnginePreference == AiEnginePreference.OLLAMA ||
                        settings.aiEnginePreference == AiEnginePreference.AUTO) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Dns, null, modifier = Modifier.size(18.dp),
                                    tint = PurpleAccent)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Ollama Server", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            }
                            Spacer(modifier = Modifier.height(6.dp))

                            // Server address input
                            var ollamaHostText by remember(settings.ollamaHost) {
                                mutableStateOf(settings.ollamaHost)
                            }

                            OutlinedTextField(
                                value = ollamaHostText,
                                onValueChange = { ollamaHostText = it.trim() },
                                label = { Text("Server address") },
                                placeholder = { Text("http://localhost:11434", fontSize = 12.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                onClick = { onConnectOllama(ollamaHostText) },
                                shape = RoundedCornerShape(10.dp),
                                enabled = !ollamaConnecting && ollamaHostText.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = BluePrimary),
                                modifier = Modifier.fillMaxWidth().height(40.dp)
                            ) {
                                if (ollamaConnecting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp), strokeWidth = 2.dp,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Connecting\u2026", fontSize = 13.sp)
                                } else {
                                    Icon(Icons.Filled.Wifi, null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Connect & Load Models", fontSize = 13.sp)
                                }
                            }

                            // Ollama models list
                            if (ollamaModels.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Available models:", fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(6.dp))

                                ollamaModels.forEach { model ->
                                    val isActive = settings.ollamaModel == model.name
                                    OutlinedCard(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp)
                                            .clickable { onSelectOllamaModel(model.name) },
                                        shape = RoundedCornerShape(8.dp),
                                        border = if (isActive) BorderStroke(1.5.dp, GreenPrimary)
                                                 else CardDefaults.outlinedCardBorder()
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                if (isActive) Icons.Filled.CheckCircle else Icons.Filled.SmartToy,
                                                null, modifier = Modifier.size(18.dp),
                                                tint = if (isActive) GreenPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(model.name, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                                val details = listOfNotNull(
                                                    model.parameterSize.takeIf { it.isNotEmpty() },
                                                    model.quantization.takeIf { it.isNotEmpty() },
                                                    model.sizeLabel
                                                ).joinToString(" \u00B7 ")
                                                if (details.isNotEmpty()) {
                                                    Text(details, fontSize = 10.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                            if (isActive) {
                                                Surface(shape = RoundedCornerShape(4.dp), color = GreenPrimary.copy(alpha = 0.15f)) {
                                                    Text("Active", fontSize = 10.sp, color = GreenPrimary,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Getting started info
                            if (ollamaModels.isEmpty() && !ollamaConnecting) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = BluePrimary.copy(alpha = 0.07f)
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("Getting started with Ollama", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            "1. Install Ollama on your device or computer\n" +
                                            "2. Run: ollama pull llama3.2:1b (or any model)\n" +
                                            "3. Enter the server address above and tap Connect",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ── MediaPipe model management (simplified) ──
                    if (settings.aiEnginePreference == AiEnginePreference.MEDIAPIPE_LLM) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Memory, null, modifier = Modifier.size(18.dp),
                                    tint = PurpleAccent)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Model", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            }
                            Spacer(modifier = Modifier.height(6.dp))

                            if (isLoadingModel) {
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = 6.dp)) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("Loading model\u2026", style = MaterialTheme.typography.bodySmall)
                                }
                            }

                            // ── Import model file (SAF file picker) ──
                            val modelFileLauncher = rememberLauncherForActivityResult(
                                ActivityResultContracts.OpenDocument()
                            ) { uri -> uri?.let { onImportModelFile(it) } }

                            Button(
                                onClick = { modelFileLauncher.launch(arrayOf("*/*")) },
                                shape = RoundedCornerShape(10.dp),
                                enabled = !isLoadingModel,
                                modifier = Modifier.fillMaxWidth().height(40.dp)
                            ) {
                                Icon(Icons.Filled.FolderOpen, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Import model file", fontSize = 13.sp)
                            }

                            if (modelImportMessage != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                val isError = modelImportMessage.contains("Failed", ignoreCase = true)
                                Text(modelImportMessage, fontSize = 11.sp,
                                    color = if (isError) RedExpense else GreenPrimary)
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // ── Scan for models on device ──
                            OutlinedButton(
                                onClick = onDiscoverModels,
                                shape = RoundedCornerShape(10.dp),
                                enabled = !isLoadingModel,
                                modifier = Modifier.fillMaxWidth().height(40.dp)
                            ) {
                                Icon(Icons.Filled.Search, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Scan for models on device", fontSize = 13.sp)
                            }

                            if (discoveredModels.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                discoveredModels.forEach { (name, path) ->
                                    val isActive = settings.mediapipeModelPath == path
                                    OutlinedCard(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp)
                                            .clickable(enabled = !isLoadingModel) { onLoadModel(path) },
                                        shape = RoundedCornerShape(8.dp),
                                        border = if (isActive) BorderStroke(1.5.dp, GreenPrimary)
                                                 else CardDefaults.outlinedCardBorder()
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                if (isActive) Icons.Filled.CheckCircle else Icons.Filled.SmartToy,
                                                null, modifier = Modifier.size(18.dp),
                                                tint = if (isActive) GreenPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(name, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                                Text(path.substringAfterLast("/"), fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            if (isActive) {
                                                Text("Active", fontSize = 11.sp, color = GreenPrimary,
                                                    fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    // ── Status row ──
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isActive = localAiStatus != null &&
                            (localAiStatus.contains("active", ignoreCase = true) ||
                             localAiStatus.contains("detected", ignoreCase = true))
                        when {
                            localAiStatus == null -> {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Detecting AI capabilities…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            isActive -> {
                                Icon(Icons.Filled.CheckCircle, null, tint = GreenPrimary,
                                    modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(localAiStatus, style = MaterialTheme.typography.bodySmall,
                                    color = GreenPrimary)
                            }
                            else -> {
                                Icon(Icons.Filled.Info, null, tint = OrangeWarning,
                                    modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(localAiStatus, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    // Suggestion card
                    if (localAiSuggestion != null) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(Icons.Filled.Lightbulb, null, tint = OrangeWarning,
                                modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Tip", fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp, color = OrangeWarning)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(localAiSuggestion, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    // Re-check button
                    if (localAiStatus != null && !localAiStatus.contains("Checking") &&
                        !localAiStatus.contains("Switching") && !localAiStatus.contains("Loading")) {
                        Row(modifier = Modifier.padding(start = 16.dp, bottom = 10.dp)) {
                            OutlinedButton(onClick = onCheckLocalAi, shape = RoundedCornerShape(10.dp)) {
                                Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Re-detect", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Info card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = PurpleAccent.copy(alpha = 0.07f))
        ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Filled.AutoAwesome, null, tint = PurpleAccent, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    "Connect to an Ollama server for powerful on-device AI. " +
                        "Install Ollama on your phone or local computer, pull a model (e.g. llama3.2:1b), " +
                        "and connect above. The app falls back to rule-based analysis automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 17.sp
                )
            }
        }
        } // end Column in AnimatedVisibility for Local AI
        } // end AnimatedVisibility for Local AI

        Spacer(modifier = Modifier.height(24.dp))

        // ─── Import & Export Section ───────────────────────────────
        CollapsibleSectionHeader("IMPORT & EXPORT", importExportExpanded) { importExportExpanded = !importExportExpanded }

        AnimatedVisibility(
            visible = importExportExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(4.dp)) {
                SettingsClickItem(
                    icon = Icons.Filled.FileUpload,
                    title = "Export Data as JSON",
                    subtitle = "Save all transactions, categories & settings to a file",
                    onClick = { exportLauncher.launch("smart_expense_backup.json") }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                SettingsClickItem(
                    icon = Icons.Filled.FileDownload,
                    title = "Import Data from JSON",
                    subtitle = "Restore data from a previously exported file",
                    onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) }
                )
            }
        }
        } // end AnimatedVisibility for Import/Export

        Spacer(modifier = Modifier.height(24.dp))

        // ─── Storage Section ───────────────────────────────────────
        CollapsibleSectionHeader("STORAGE", storageExpanded) { storageExpanded = !storageExpanded }

        AnimatedVisibility(
            visible = storageExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(4.dp)) {
                SettingsClickItem(
                    icon = Icons.Filled.Storage,
                    title = "Local Storage",
                    subtitle = "Data stored as JSON · File size: $storageInfo"
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                SettingsClickItem(
                    icon = Icons.Filled.DeleteForever,
                    title = "Clear All Data",
                    subtitle = "Delete all transactions and reset to defaults",
                    isDestructive = true,
                    onClick = { showClearDialog = true }
                )
            }
        }
        } // end AnimatedVisibility for Storage

        Spacer(modifier = Modifier.height(24.dp))

        // ─── Permissions Section ─────────────────────────────────────
        CollapsibleSectionHeader("PERMISSIONS", permissionsExpanded) { permissionsExpanded = !permissionsExpanded }

        AnimatedVisibility(
            visible = permissionsExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            PermissionsSectionContent(context)
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ─── Subscription Section ──────────────────────────────────
        Text(
            "SUBSCRIPTION",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(4.dp)) {
                SettingsClickItem(
                    icon = Icons.Filled.WorkspacePremium,
                    title = if (isSubscribed) {
                        when {
                            isTrialActive -> "Premium Trial Active"
                            activePlanName != null -> "Premium · $activePlanName"
                            else -> "Premium Active"
                        }
                    } else "Free Plan",
                    subtitle = if (isSubscribed) {
                        if (isTrialActive) "Your 3-day free trial is active"
                        else "You have full access to all premium features"
                    } else "Upgrade to unlock all premium features",
                    onClick = {
                        if (!isSubscribed) onShowPaywall("Premium")
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                SettingsClickItem(
                    icon = Icons.Filled.Restore,
                    title = "Restore Purchases",
                    subtitle = "Reinstalled the app? Recover your subscription here",
                    onClick = onRestorePurchases
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ─── About Section ─────────────────────────────────────────
        Text(
            "ABOUT",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.AccountBalanceWallet,
                        contentDescription = null,
                        tint = GreenPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("FlowSense", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Version ${BuildConfig.VERSION_NAME} · AI-Powered Finance Manager",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    "Your personal finance companion that works entirely on your device. " +
                            "No cloud accounts, no data sharing \u2014 just you and your money.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    "FlowSense automatically detects transactions from banking " +
                            "SMS messages and app notifications, scans paper receipts with your camera, " +
                            "and categorizes everything using on-device AI powered by Gemini Nano. " +
                            "It generates detailed reports with spending trends, savings insights, " +
                            "and personalized tips to help you spend smarter.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    "Supports 30+ currencies with live exchange rates, " +
                            "works offline, and keeps all your data in a single exportable file.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(100.dp))
    }

    // ─── Dialogs ───────────────────────────────────────────────────

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            icon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = RedExpense) },
            title = { Text("Clear All Data?") },
            text = {
                Text("This will permanently delete all transactions, budgets, and notifications. Your settings, categories, and connected banking apps will be kept. This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = { onClearData(); showClearDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = RedExpense)
                ) { Text("Clear Everything") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showImportConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showImportConfirmDialog = false; pendingImportUri = null },
            icon = { Icon(Icons.Filled.FileDownload, contentDescription = null, tint = BluePrimary) },
            title = { Text("Import Data?") },
            text = {
                Text("This will replace all current data with the contents of the selected file. Your existing transactions and settings will be overwritten.\n\nMake sure to export your current data first if you want to keep it.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingImportUri?.let { onImportFromUri(it) }
                        showImportConfirmDialog = false
                        pendingImportUri = null
                    }
                ) { Text("Import & Replace") }
            },
            dismissButton = {
                TextButton(onClick = { showImportConfirmDialog = false; pendingImportUri = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ─── Theme Mode Picker Component ───────────────────────────────────

@Composable
private fun ThemeModePicker(
    currentMode: ThemeMode,
    onModeSelected: (ThemeMode) -> Unit
) {
    val modes = listOf(
        Triple(ThemeMode.LIGHT, "Light", Icons.Filled.LightMode),
        Triple(ThemeMode.SYSTEM, "System", Icons.Filled.SettingsBrightness),
        Triple(ThemeMode.DARK, "Dark", Icons.Filled.DarkMode)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        modes.forEach { (mode, label, icon) ->
            val isSelected = currentMode == mode
            val backgroundColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                label = "theme_pill_bg"
            )
            val contentColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                label = "theme_pill_content"
            )

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onModeSelected(mode) },
                color = backgroundColor,
                shape = RoundedCornerShape(10.dp),
                shadowElevation = if (isSelected) 2.dp else 0.dp
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(icon, contentDescription = label, tint = contentColor, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        label,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = contentColor
                    )
                }
            }
        }
    }
}

// ─── Collapsible Section Header ─────────────────────────────────────

@Composable
private fun CollapsibleSectionHeader(
    title: String,
    expanded: Boolean,
    isPremium: Boolean = false,
    onToggle: () -> Unit
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "chevron_rotation"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        if (isPremium) {
            PremiumBadge()
            Spacer(modifier = Modifier.width(8.dp))
        }
        Icon(
            Icons.Filled.KeyboardArrowDown,
            contentDescription = if (expanded) "Collapse" else "Expand",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp).rotate(rotation)
        )
    }
}

// ─── Reusable Setting Row Components ───────────────────────────────

@Composable
private fun SettingsToggleItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsClickItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    isDestructive: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon, contentDescription = null,
            tint = if (isDestructive) RedExpense else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title, fontWeight = FontWeight.Medium, fontSize = 14.sp,
                color = if (isDestructive) RedExpense else MaterialTheme.colorScheme.onSurface
            )
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onClick != null) {
            Icon(
                Icons.Filled.ChevronRight, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ─── Permissions Section ─────────────────────────────────────────────

/**
 * Data class representing a permission to display in the Permissions section.
 */
private data class AppPermission(
    val permission: String,
    val label: String,
    val description: String,
    val icon: ImageVector,
    /** True for permissions that can't be checked via ContextCompat (e.g. Notification Listener). */
    val isSpecial: Boolean = false
)

/**
 * All the permissions the app needs, shown in the Settings > Permissions section.
 */
private val appPermissions = listOf(
    AppPermission(
        Manifest.permission.CAMERA,
        "Camera",
        "Required for scanning receipts via OCR",
        Icons.Filled.CameraAlt
    ),
    AppPermission(
        Manifest.permission.READ_SMS,
        "Read SMS",
        "Detect transactions from banking SMS messages",
        Icons.Filled.Sms
    ),
    AppPermission(
        Manifest.permission.RECEIVE_SMS,
        "Receive SMS",
        "Real-time capture of incoming banking SMS",
        Icons.Filled.Message
    ),
    AppPermission(
        Manifest.permission.ACCESS_FINE_LOCATION,
        "Fine Location",
        "Geo-tag transactions and pin stores on map",
        Icons.Filled.MyLocation
    ),
    AppPermission(
        Manifest.permission.ACCESS_COARSE_LOCATION,
        "Coarse Location",
        "Approximate location for store mapping",
        Icons.Filled.LocationOn
    ),
    AppPermission(
        "android.permission.ACCESS_BACKGROUND_LOCATION",
        "Background Location",
        "Geo-tag SMS/notification transactions in background",
        Icons.Filled.LocationOn
    ),
    AppPermission(
        "android.permission.POST_NOTIFICATIONS",
        "Notifications",
        "Budget alerts and transaction confirmations",
        Icons.Filled.Notifications
    ),
    AppPermission(
        "notification_listener",
        "Notification Listener",
        "Monitor banking app notifications for auto-capture",
        Icons.Filled.NotificationsActive,
        isSpecial = true
    )
)

/**
 * Checks whether the Notification Listener service is enabled for this app.
 */
private fun isNotificationListenerEnabled(context: android.content.Context): Boolean {
    val cn = ComponentName(context, "com.smartexpense.tracker.service.notification.BankingNotificationListener")
    val enabledListeners = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    return enabledListeners?.contains(cn.flattenToString()) == true
}

@Composable
private fun PermissionsSectionContent(context: android.content.Context) {
    // Permission launcher for runtime requests
    var permissionToRequest by remember { mutableStateOf<String?>(null) }
    var showDeniedAlert by remember { mutableStateOf(false) }
    var deniedPermissionLabel by remember { mutableStateOf("") }

    // Force recomposition when returning from settings
    var refreshKey by remember { mutableIntStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        refreshKey++
        if (!granted) {
            deniedPermissionLabel = appPermissions
                .find { it.permission == permissionToRequest }?.label ?: "Permission"
            showDeniedAlert = true
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            // Use refreshKey to force recheck of permission states
            key(refreshKey) {
                appPermissions.forEachIndexed { index, perm ->
                    val isGranted = if (perm.isSpecial) {
                        isNotificationListenerEnabled(context)
                    } else {
                        ContextCompat.checkSelfPermission(
                            context, perm.permission
                        ) == PackageManager.PERMISSION_GRANTED
                    }

                    PermissionRow(
                        icon = perm.icon,
                        label = perm.label,
                        description = perm.description,
                        isGranted = isGranted,
                        onClick = {
                            if (!isGranted) {
                                if (perm.isSpecial) {
                                    // Open notification listener settings
                                    context.startActivity(
                                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                    )
                                } else {
                                    permissionToRequest = perm.permission
                                    permissionLauncher.launch(perm.permission)
                                }
                            }
                        }
                    )

                    if (index < appPermissions.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
    }

    // Alert dialog when permission is denied
    if (showDeniedAlert) {
        AlertDialog(
            onDismissRequest = { showDeniedAlert = false },
            icon = {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = RedExpense)
            },
            title = { Text("Permission Denied") },
            text = {
                Text(
                    "\"$deniedPermissionLabel\" permission was denied. " +
                        "Some features may not work correctly without it. " +
                        "You can grant it from the app settings."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeniedAlert = false
                    // Open app settings so user can grant manually
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeniedAlert = false }) {
                    Text("Dismiss")
                }
            }
        )
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    label: String,
    description: String,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (isGranted) GreenPrimary.copy(alpha = 0.08f) else RedExpense.copy(alpha = 0.08f),
        label = "permission_bg"
    )
    val statusColor = if (isGranted) GreenPrimary else RedExpense

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isGranted) { onClick() }
            .background(bgColor)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(statusColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = statusColor, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = statusColor.copy(alpha = 0.12f)
        ) {
            Text(
                if (isGranted) "Granted" else "Denied",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = statusColor,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}

// ─── Scheduled Expenses Section ─────────────────────────────────────────

@Composable
private fun ScheduledExpensesSection(
    expenses: List<ScheduledExpense>,
    currencyCode: String,
    onAdd: (ScheduledExpense) -> Unit,
    onUpdate: (ScheduledExpense) -> Unit,
    onDelete: (String) -> Unit
) {
    val context = LocalContext.current
    val currencySymbol = currencyInfoFor(currencyCode).symbol

    // Form state for adding a new expense
    var newName by remember { mutableStateOf("") }
    var newAmountText by remember { mutableStateOf("") }
    var newDayText by remember { mutableStateOf("") }

    // Which expense ID is currently being edited (null = none)
    var editingId by remember { mutableStateOf<String?>(null) }
    var editName by remember { mutableStateOf("") }
    var editAmountText by remember { mutableStateOf("") }
    var editDayText by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.EventRepeat,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("Recurring Payments", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Text(
                        "Add loans, subscriptions, or any recurring payment. " +
                            "You'll be notified on the last working day before each payment date.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(14.dp))

            // ── Existing expenses list ──
            if (expenses.isEmpty()) {
                Text(
                    "No scheduled expenses yet. Add one below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                expenses.forEach { expense ->
                    if (editingId == expense.id) {
                        // Inline edit form
                        ScheduledExpenseEditRow(
                            name = editName,
                            onNameChange = { editName = it },
                            amountText = editAmountText,
                            onAmountChange = { editAmountText = it.filter { c -> c.isDigit() || c == '.' } },
                            dayText = editDayText,
                            onDayChange = {
                                val filtered = it.filter { c -> c.isDigit() }
                                val num = filtered.toIntOrNull()
                                if (num == null || num in 1..31) editDayText = filtered
                            },
                            currencyCode = currencyCode,
                            currencySymbol = currencySymbol,
                            onSave = {
                                val amount = editAmountText.toDoubleOrNull() ?: 0.0
                                val day = editDayText.toIntOrNull()?.coerceIn(1, 31) ?: 1
                                if (editName.isNotBlank() && amount > 0) {
                                    onUpdate(expense.copy(name = editName, amount = amount, dayOfMonth = day))
                                    editingId = null
                                    Toast.makeText(context, "Updated: ${editName}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onCancel = { editingId = null }
                        )
                    } else {
                        ScheduledExpenseRow(
                            expense = expense,
                            currencySymbol = currencySymbol,
                            onToggle = { onUpdate(expense.copy(enabled = it)) },
                            onEdit = {
                                editingId = expense.id
                                editName = expense.name
                                editAmountText = if (expense.amount > 0) expense.amount.toLong().toString() else ""
                                editDayText = expense.dayOfMonth.toString()
                            },
                            onDelete = { onDelete(expense.id) }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            HorizontalDivider()
            Spacer(modifier = Modifier.height(14.dp))

            // ── Add new expense form ──
            Text(
                "Add New Payment",
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("Name (e.g. Mortgage, Car Loan)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = newAmountText,
                    onValueChange = { newAmountText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Amount ($currencyCode)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(2f),
                    shape = RoundedCornerShape(10.dp),
                    leadingIcon = {
                        Text(currencySymbol, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp))
                    }
                )
                OutlinedTextField(
                    value = newDayText,
                    onValueChange = {
                        val filtered = it.filter { c -> c.isDigit() }
                        val num = filtered.toIntOrNull()
                        if (num == null || num in 1..31) newDayText = filtered
                    },
                    label = { Text("Pay Day (1–31)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    val amount = newAmountText.toDoubleOrNull() ?: 0.0
                    val day = newDayText.toIntOrNull()?.coerceIn(1, 31) ?: 1
                    if (newName.isBlank()) {
                        Toast.makeText(context, "Please enter a name", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (amount <= 0) {
                        Toast.makeText(context, "Please enter a valid amount", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (newDayText.isBlank()) {
                        Toast.makeText(context, "Please enter the payment day", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    onAdd(ScheduledExpense(name = newName.trim(), amount = amount, dayOfMonth = day))
                    Toast.makeText(
                        context,
                        "${newName.trim()} added — reminder on working day before day $day",
                        Toast.LENGTH_LONG
                    ).show()
                    newName = ""
                    newAmountText = ""
                    newDayText = ""
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Scheduled Payment", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ScheduledExpenseRow(
    expense: ScheduledExpense,
    currencySymbol: String,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (expense.enabled)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Filled.Payment,
                    contentDescription = null,
                    tint = if (expense.enabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        expense.name,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = if (expense.enabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "$currencySymbol${String.format("%.2f", expense.amount)} · Day ${expense.dayOfMonth} of each month",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = expense.enabled,
                    onCheckedChange = onToggle,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onEdit, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Edit", fontSize = 12.sp)
                }
                TextButton(
                    onClick = onDelete,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun ScheduledExpenseEditRow(
    name: String,
    onNameChange: (String) -> Unit,
    amountText: String,
    onAmountChange: (String) -> Unit,
    dayText: String,
    onDayChange: (String) -> Unit,
    currencyCode: String,
    currencySymbol: String,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = onAmountChange,
                    label = { Text("Amount ($currencyCode)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(2f),
                    shape = RoundedCornerShape(10.dp),
                    leadingIcon = {
                        Text(currencySymbol, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp))
                    }
                )
                OutlinedTextField(
                    value = dayText,
                    onValueChange = onDayChange,
                    label = { Text("Day (1–31)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onCancel) { Text("Cancel") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onSave,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Save")
                }
            }
        }
    }
}
