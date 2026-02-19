package com.smartexpense.tracker.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartexpense.tracker.data.model.AppSettings
import com.smartexpense.tracker.data.model.Category
import com.smartexpense.tracker.data.model.SUPPORTED_CURRENCIES
import com.smartexpense.tracker.data.model.ThemeMode
import com.smartexpense.tracker.data.model.currencyInfoFor
import com.smartexpense.tracker.ui.theme.*

@Composable
fun SettingsScreen(
    settings: AppSettings,
    storageInfo: String,
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
    /** Discovered banking apps from device scan. */
    discoveredBankingApps: List<com.smartexpense.tracker.ui.viewmodel.MainViewModel.DiscoveredApp> = emptyList(),
    isScanningBankingApps: Boolean = false,
    onScanBankingApps: () -> Unit = {},
    onAddBankingApp: (String) -> Unit = {},
    onRemoveBankingApp: (String) -> Unit = {},
    /** All user-installed apps on the device. */
    allInstalledApps: List<com.smartexpense.tracker.ui.viewmodel.MainViewModel.InstalledApp> = emptyList(),
    onLoadAllInstalledApps: () -> Unit = {},
    onUpdateScanKeywords: (List<String>) -> Unit = {}
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

    // Section expanded state (all open by default)
    var appearanceExpanded by remember { mutableStateOf(true) }
    var currencyExpanded by remember { mutableStateOf(true) }
    var dataSourcesExpanded by remember { mutableStateOf(true) }
    var appScannerExpanded by remember { mutableStateOf(true) }
    var connectedAppsExpanded by remember { mutableStateOf(false) }
    var budgetExpanded by remember { mutableStateOf(true) }
    var salaryExpanded by remember { mutableStateOf(true) }
    var categoriesExpanded by remember { mutableStateOf(true) }
    var localAiExpanded by remember { mutableStateOf(true) }
    var importExportExpanded by remember { mutableStateOf(true) }
    var storageExpanded by remember { mutableStateOf(true) }

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
                        Text(
                            "Rates from open.er-api.com · refreshed hourly",
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
        CollapsibleSectionHeader("APPLICATION SCANNER", appScannerExpanded) { appScannerExpanded = !appScannerExpanded }

        AnimatedVisibility(
            visible = appScannerExpanded,
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
        CollapsibleSectionHeader("BUDGET THRESHOLD", budgetExpanded) { budgetExpanded = !budgetExpanded }

        AnimatedVisibility(
            visible = budgetExpanded,
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
        CollapsibleSectionHeader("SALARY SCHEDULER", salaryExpanded) { salaryExpanded = !salaryExpanded }

        AnimatedVisibility(
            visible = salaryExpanded,
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

        // ─── Categories Section ────────────────────────────────────
        CollapsibleSectionHeader("CATEGORIES", categoriesExpanded) { categoriesExpanded = !categoriesExpanded }

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
                    subtitle = "Enhanced AI for smarter categorisation & financial insights. Works on Samsung Galaxy S24+, Pixel 8+ and other compatible devices.",
                    checked = settings.localAiEnabled,
                    onCheckedChange = { enabled ->
                        onUpdateSettings(settings.copy(localAiEnabled = enabled))
                        if (enabled) onCheckLocalAi()
                    }
                )

                // Status row — only shown when the toggle is on
                if (settings.localAiEnabled) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isActive = localAiStatus != null &&
                            (localAiStatus.contains("Samsung") ||
                             localAiStatus.contains("Google") ||
                             localAiStatus.contains("AICore") ||
                             localAiStatus.contains("Gemini"))
                        when {
                            localAiStatus == null -> {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    "Detecting AI capabilities…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            isActive -> {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = GreenPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    localAiStatus,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = GreenPrimary
                                )
                            }
                            else -> {
                                Icon(
                                    Icons.Filled.Info,
                                    contentDescription = null,
                                    tint = OrangeWarning,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    localAiStatus,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Alternative AI suggestion card
                    if (localAiSuggestion != null) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                Icons.Filled.Lightbulb,
                                contentDescription = null,
                                tint = OrangeWarning,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    "Tip: Better AI available",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = OrangeWarning
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    localAiSuggestion,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Re-check button
                    if (localAiStatus != null && !localAiStatus.startsWith("Detecting")) {
                        Row(modifier = Modifier.padding(start = 16.dp, bottom = 10.dp)) {
                            OutlinedButton(
                                onClick = onCheckLocalAi,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Re-detect", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Info card about what local AI does
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = PurpleAccent.copy(alpha = 0.07f)
            )
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = PurpleAccent,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    "When enabled, Gemini Nano runs entirely on-device — no data " +
                        "is sent to the cloud. The app falls back to rule-based AI " +
                        "automatically when the model is not available.",
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
                        Text("Smart Expense Tracker", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Version 1.0 · AI-Powered Finance Manager",
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
                    "Smart Expense Tracker automatically detects transactions from banking " +
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
