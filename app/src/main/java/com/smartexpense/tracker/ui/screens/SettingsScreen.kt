package com.smartexpense.tracker.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartexpense.tracker.data.model.AppSettings
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
    onCheckLocalAi: () -> Unit = {}
) {
    val context = LocalContext.current
    var showClearDialog by remember { mutableStateOf(false) }
    var showImportConfirmDialog by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

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
        Text(
            "APPEARANCE",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

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

        Spacer(modifier = Modifier.height(24.dp))

        // ─── Currency Section ──────────────────────────────────────
        Text(
            "CURRENCY",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

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

        Spacer(modifier = Modifier.height(24.dp))

        // ─── Data Sources Section ──────────────────────────────────
        Text(
            "DATA SOURCES",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

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

        Spacer(modifier = Modifier.height(24.dp))

        // ─── Local AI Section ──────────────────────────────────────
        Text(
            "LOCAL AI",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

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

        Spacer(modifier = Modifier.height(24.dp))

        // ─── Import & Export Section ───────────────────────────────
        Text(
            "IMPORT & EXPORT",
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

        Spacer(modifier = Modifier.height(24.dp))

        // ─── Storage Section ───────────────────────────────────────
        Text(
            "STORAGE",
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
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "OCR Receipt Scanning · SMS & Notification Tracking · " +
                            "AI Categorization · Gemini Nano On-Device AI · " +
                            "Smart Optimization Suggestions · " +
                            "Monthly Reports with Month Selector · Local JSON Storage · " +
                            "Multi-Currency Support · Import & Export · Dark Mode",
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
                Text("This will permanently delete all transactions, categories, budgets, and settings. This action cannot be undone.")
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
