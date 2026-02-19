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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartexpense.tracker.R
import com.smartexpense.tracker.data.model.AppSettings
import com.smartexpense.tracker.data.model.DEFAULT_EXPENSE_KEYWORDS
import com.smartexpense.tracker.data.model.DEFAULT_INCOME_KEYWORDS
import com.smartexpense.tracker.data.model.Category
import com.smartexpense.tracker.data.model.SUPPORTED_CURRENCIES
import com.smartexpense.tracker.data.model.ThemeMode
import com.smartexpense.tracker.data.model.currencyInfoFor
import com.smartexpense.tracker.ui.theme.*
import com.smartexpense.tracker.util.LocaleHelper

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
    onLanguageChanged: (String) -> Unit = {}
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

    // Language selector state
    var showLanguageDropdown by remember { mutableStateOf(false) }

    // Language options
    data class LanguageOption(val code: String, val labelResId: Int)
    val languageOptions = listOf(
        LanguageOption(LocaleHelper.SYSTEM, R.string.lang_system),
        LanguageOption("en", R.string.lang_english),
        LanguageOption("ru", R.string.lang_russian),
        LanguageOption("hy", R.string.lang_armenian),
        LanguageOption("zh", R.string.lang_chinese),
        LanguageOption("es", R.string.lang_spanish)
    )

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

        // ─── Language Section ───────────────────────────────────────
        Text(
            stringResource(R.string.section_language),
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
                    Icon(Icons.Filled.Language, contentDescription = null,
                        tint = BluePrimary, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.app_language), fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Text(stringResource(R.string.language_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Box {
                        val currentLangLabel = languageOptions
                            .firstOrNull { it.code == settings.language }?.labelResId
                            ?: R.string.lang_system
                        OutlinedButton(
                            onClick = { showLanguageDropdown = true },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(stringResource(currentLangLabel), fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null,
                                modifier = Modifier.size(18.dp))
                        }

                        DropdownMenu(
                            expanded = showLanguageDropdown,
                            onDismissRequest = { showLanguageDropdown = false }
                        ) {
                            languageOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(stringResource(option.labelResId),
                                                fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                            if (option.code == settings.language) {
                                                Spacer(modifier = Modifier.weight(1f))
                                                Icon(Icons.Filled.Check, contentDescription = null,
                                                    tint = GreenPrimary, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    },
                                    onClick = {
                                        showLanguageDropdown = false
                                        if (option.code != settings.language) {
                                            onUpdateSettings(settings.copy(language = option.code))
                                            onLanguageChanged(option.code)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
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

        // ─── Monthly Expense Limit Section ────────────────────────
        Text(
            "BUDGET THRESHOLD",
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

        Spacer(modifier = Modifier.height(24.dp))

        // ─── Salary Scheduler Section ──────────────────────────────
        Text(
            "SALARY SCHEDULER",
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

        Spacer(modifier = Modifier.height(24.dp))

        // ─── Categories Section ────────────────────────────────────
        Text(
            "CATEGORIES",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

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

        // ─── Notification Keywords Section ──────────────────────────
        Text(
            stringResource(R.string.section_notification_keywords),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        NotificationKeywordsCard(
            incomeKeywords = settings.notificationIncomeKeywords,
            expenseKeywords = settings.notificationExpenseKeywords,
            onUpdateIncomeKeywords = { keywords ->
                onUpdateSettings(settings.copy(notificationIncomeKeywords = keywords))
            },
            onUpdateExpenseKeywords = { keywords ->
                onUpdateSettings(settings.copy(notificationExpenseKeywords = keywords))
            },
            onResetDefaults = {
                onUpdateSettings(settings.copy(
                    notificationIncomeKeywords = DEFAULT_INCOME_KEYWORDS,
                    notificationExpenseKeywords = DEFAULT_EXPENSE_KEYWORDS
                ))
                Toast.makeText(context, context.getString(R.string.keywords_reset), Toast.LENGTH_SHORT).show()
            }
        )

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

// ─── Notification Keywords Customization ────────────────────────────

@Composable
private fun NotificationKeywordsCard(
    incomeKeywords: List<String>,
    expenseKeywords: List<String>,
    onUpdateIncomeKeywords: (List<String>) -> Unit,
    onUpdateExpenseKeywords: (List<String>) -> Unit,
    onResetDefaults: () -> Unit
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
                Icon(Icons.Filled.Tune, contentDescription = null,
                    tint = OrangeWarning, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.notification_keywords),
                        fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Text(stringResource(R.string.notification_keywords_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            KeywordSection(
                title = stringResource(R.string.income_keywords),
                hint = stringResource(R.string.income_keywords_hint),
                keywords = incomeKeywords,
                chipColor = GreenIncome,
                onAdd = { keyword -> onUpdateIncomeKeywords(incomeKeywords + keyword) },
                onRemove = { keyword -> onUpdateIncomeKeywords(incomeKeywords - keyword) }
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            KeywordSection(
                title = stringResource(R.string.expense_keywords),
                hint = stringResource(R.string.expense_keywords_hint),
                keywords = expenseKeywords,
                chipColor = RedExpense,
                onAdd = { keyword -> onUpdateExpenseKeywords(expenseKeywords + keyword) },
                onRemove = { keyword -> onUpdateExpenseKeywords(expenseKeywords - keyword) }
            )

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = onResetDefaults,
                modifier = Modifier.align(Alignment.End)
            ) {
                Icon(Icons.Filled.RestartAlt, contentDescription = null,
                    modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.reset_to_defaults), fontSize = 13.sp)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeywordSection(
    title: String,
    hint: String,
    keywords: List<String>,
    chipColor: Color,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit
) {
    var newKeyword by remember { mutableStateOf("") }

    Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    Text(hint, style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)

    Spacer(modifier = Modifier.height(8.dp))

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        keywords.forEach { keyword ->
            InputChip(
                selected = false,
                onClick = { onRemove(keyword) },
                label = { Text(keyword, fontSize = 12.sp) },
                trailingIcon = {
                    Icon(Icons.Filled.Close, contentDescription = null,
                        modifier = Modifier.size(14.dp))
                },
                colors = InputChipDefaults.inputChipColors(
                    containerColor = chipColor.copy(alpha = 0.1f)
                ),
                shape = RoundedCornerShape(8.dp)
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = newKeyword,
            onValueChange = { newKeyword = it },
            placeholder = { Text(stringResource(R.string.add_keyword_hint), fontSize = 13.sp) },
            singleLine = true,
            modifier = Modifier.weight(1f).height(50.dp),
            shape = RoundedCornerShape(10.dp),
            textStyle = MaterialTheme.typography.bodySmall
        )
        FilledTonalButton(
            onClick = {
                val trimmed = newKeyword.trim()
                if (trimmed.isNotEmpty() && trimmed !in keywords) {
                    onAdd(trimmed)
                    newKeyword = ""
                }
            },
            enabled = newKeyword.trim().isNotEmpty(),
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 12.dp)
        ) {
            Text(stringResource(R.string.add), fontSize = 13.sp)
        }
    }
}
