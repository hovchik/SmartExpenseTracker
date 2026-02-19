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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartexpense.tracker.R
import com.smartexpense.tracker.data.model.AppSettings
import com.smartexpense.tracker.data.model.DEFAULT_EXPENSE_KEYWORDS
import com.smartexpense.tracker.data.model.DEFAULT_INCOME_KEYWORDS
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
    onLanguageChanged: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var showClearDialog by remember { mutableStateOf(false) }
    var showImportConfirmDialog by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

    // Currency selector state
    var showCurrencyDropdown by remember { mutableStateOf(false) }

    // Language selector state
    var showLanguageDropdown by remember { mutableStateOf(false) }

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

    // Language options: code -> label string resource
    data class LanguageOption(val code: String, val labelResId: Int)
    val languageOptions = listOf(
        LanguageOption(LocaleHelper.SYSTEM, R.string.lang_system),
        LanguageOption("en", R.string.lang_english),
        LanguageOption("ru", R.string.lang_russian),
        LanguageOption("hy", R.string.lang_armenian),
        LanguageOption("zh", R.string.lang_chinese),
        LanguageOption("es", R.string.lang_spanish)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            stringResource(R.string.settings),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ─── Appearance Section ────────────────────────────────────
        Text(
            stringResource(R.string.section_appearance),
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
                    Icon(Icons.Filled.Palette, contentDescription = null,
                        tint = PurpleAccent, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(stringResource(R.string.theme), fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Text(stringResource(R.string.theme_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
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

                    // Language selector button
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
            stringResource(R.string.section_currency),
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
                    Icon(Icons.Filled.AttachMoney, contentDescription = null,
                        tint = GreenPrimary, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.display_currency), fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Text(stringResource(R.string.currency_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = stringResource(R.string.select_currency),
                                modifier = Modifier.size(18.dp))
                        }

                        DropdownMenu(
                            expanded = showCurrencyDropdown,
                            onDismissRequest = { showCurrencyDropdown = false }
                        ) {
                            SUPPORTED_CURRENCIES.forEach { info ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(info.symbol, fontWeight = FontWeight.Bold, modifier = Modifier.width(30.dp))
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
                                        onUpdateSettings(settings.copy(currencyCode = info.code, currency = info.symbol))
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
                    Text(stringResource(R.string.currency_converter), fontWeight = FontWeight.Medium, fontSize = 14.sp)
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = convertAmount,
                        onValueChange = { convertAmount = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text(stringResource(R.string.amount)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1.8f),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { showFromDropdown = true },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(convertFromCode, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        DropdownMenu(expanded = showFromDropdown, onDismissRequest = { showFromDropdown = false }) {
                            SUPPORTED_CURRENCIES.forEach { info ->
                                DropdownMenuItem(
                                    text = { Text("${info.symbol} ${info.code}") },
                                    onClick = { convertFromCode = info.code; showFromDropdown = false }
                                )
                            }
                        }
                    }

                    Icon(Icons.Filled.ArrowForward, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))

                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { showToDropdown = true },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(convertToCode, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        DropdownMenu(expanded = showToDropdown, onDismissRequest = { showToDropdown = false }) {
                            SUPPORTED_CURRENCIES.forEach { info ->
                                DropdownMenuItem(
                                    text = { Text("${info.symbol} ${info.code}") },
                                    onClick = { convertToCode = info.code; showToDropdown = false }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                when {
                    convertAmount.isNotEmpty() && exchangeRates.isEmpty() -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.fetching_live_rates), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    convertedResult != null -> {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null,
                                    tint = GreenPrimary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "$convertAmount ${currencyInfoFor(convertFromCode).symbol} = $convertedResult",
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Text(stringResource(R.string.rates_source),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ─── Data Sources Section ──────────────────────────────────
        Text(
            stringResource(R.string.section_data_sources),
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
                    title = stringResource(R.string.sms_transaction_detection),
                    subtitle = stringResource(R.string.sms_detection_description),
                    checked = settings.smsParsingEnabled,
                    onCheckedChange = { onUpdateSettings(settings.copy(smsParsingEnabled = it)) }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                SettingsToggleItem(
                    icon = Icons.Filled.Notifications,
                    title = stringResource(R.string.banking_app_notifications),
                    subtitle = stringResource(R.string.banking_notifications_description),
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
                    title = stringResource(R.string.ai_auto_categorization),
                    subtitle = stringResource(R.string.ai_categorization_description),
                    checked = settings.autoCategorizationEnabled,
                    onCheckedChange = { onUpdateSettings(settings.copy(autoCategorizationEnabled = it)) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onScanSms,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BluePrimary)
        ) {
            Icon(Icons.Filled.Sms, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(stringResource(R.string.scan_sms_inbox_for_transactions), fontWeight = FontWeight.SemiBold)
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
            stringResource(R.string.section_import_export),
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
                    title = stringResource(R.string.export_data_json),
                    subtitle = stringResource(R.string.export_description),
                    onClick = { exportLauncher.launch("smart_expense_backup.json") }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                SettingsClickItem(
                    icon = Icons.Filled.FileDownload,
                    title = stringResource(R.string.import_data_json),
                    subtitle = stringResource(R.string.import_description),
                    onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ─── Storage Section ───────────────────────────────────────
        Text(
            stringResource(R.string.section_storage),
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
                    title = stringResource(R.string.local_storage),
                    subtitle = stringResource(R.string.storage_info_format, storageInfo)
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                SettingsClickItem(
                    icon = Icons.Filled.DeleteForever,
                    title = stringResource(R.string.clear_all_data),
                    subtitle = stringResource(R.string.clear_all_description),
                    isDestructive = true,
                    onClick = { showClearDialog = true }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ─── About Section ─────────────────────────────────────────
        Text(
            stringResource(R.string.section_about),
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
                    Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null,
                        tint = GreenPrimary, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(stringResource(R.string.app_name_full), fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.version_info),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    stringResource(R.string.features_list),
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
            title = { Text(stringResource(R.string.clear_all_data_question)) },
            text = { Text(stringResource(R.string.clear_data_warning)) },
            confirmButton = {
                TextButton(
                    onClick = { onClearData(); showClearDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = RedExpense)
                ) { Text(stringResource(R.string.clear_everything)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showImportConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showImportConfirmDialog = false; pendingImportUri = null },
            icon = { Icon(Icons.Filled.FileDownload, contentDescription = null, tint = BluePrimary) },
            title = { Text(stringResource(R.string.import_data_question)) },
            text = { Text(stringResource(R.string.import_warning)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingImportUri?.let { onImportFromUri(it) }
                        showImportConfirmDialog = false
                        pendingImportUri = null
                    }
                ) { Text(stringResource(R.string.import_replace)) }
            },
            dismissButton = {
                TextButton(onClick = { showImportConfirmDialog = false; pendingImportUri = null }) {
                    Text(stringResource(R.string.cancel))
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
    val lightLabel = stringResource(R.string.light)
    val systemLabel = stringResource(R.string.system_default)
    val darkLabel = stringResource(R.string.dark)

    val modes = listOf(
        Triple(ThemeMode.LIGHT, lightLabel, Icons.Filled.LightMode),
        Triple(ThemeMode.SYSTEM, systemLabel, Icons.Filled.SettingsBrightness),
        Triple(ThemeMode.DARK, darkLabel, Icons.Filled.DarkMode)
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
                    Text(label, fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = contentColor)
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
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
        Icon(icon, contentDescription = null,
            tint = if (isDestructive) RedExpense else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, fontSize = 14.sp,
                color = if (isDestructive) RedExpense else MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onClick != null) {
            Icon(Icons.Filled.ChevronRight, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
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

            // Income keywords
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

            // Expense keywords
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

            // Reset to defaults
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

    // Keyword chips
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
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.remove),
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

    // Add new keyword input
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
