package com.flowsense.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.flowsense.app.data.model.TransactionType
import com.flowsense.app.ui.theme.*
import com.flowsense.app.ui.viewmodel.SmsScanState
import com.flowsense.app.util.CurrencyUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsScanScreen(
    scanState: SmsScanState,
    totalSmsCount: Int,
    onLoadSmsCount: () -> Unit,
    onStartScan: (maxMessages: Int, startDate: Long?, endDate: Long?) -> Unit,
    onConfirmAll: () -> Unit,
    onDiscard: (String) -> Unit,
    onReset: () -> Unit,
    onNavigateBack: () -> Unit,
    currencyCode: String = "USD"
) {
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
                PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var pendingScan by remember { mutableStateOf(false) }

    // Scan configuration state
    var useMessageLimit by remember { mutableStateOf(true) }
    var messageLimit by remember { mutableFloatStateOf(500f) }
    var startDate by remember { mutableStateOf<Long?>(null) }
    var endDate by remember { mutableStateOf<Long?>(null) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    // Load SMS count when permission is granted
    LaunchedEffect(hasPermission) {
        if (hasPermission) onLoadSmsCount()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { results ->
            val allGranted = results.values.all { it }
            hasPermission = allGranted
            if (allGranted) {
                onLoadSmsCount()
                if (pendingScan) {
                    pendingScan = false
                    onStartScan(
                        if (useMessageLimit) messageLimit.toInt() else Int.MAX_VALUE,
                        if (!useMessageLimit) startDate else null,
                        if (!useMessageLimit) endDate else null
                    )
                }
            } else {
                pendingScan = false
                Toast.makeText(context, "SMS permission is required to scan messages", Toast.LENGTH_LONG).show()
            }
        }
    )

    fun doScan() {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
        hasPermission = granted
        if (granted) {
            onStartScan(
                if (useMessageLimit) messageLimit.toInt() else Int.MAX_VALUE,
                if (!useMessageLimit) startDate else null,
                if (!useMessageLimit) endDate else null
            )
        } else {
            pendingScan = true
            permissionLauncher.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS))
        }
    }

    // Date picker dialogs
    if (showStartDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = startDate ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    startDate = pickerState.selectedDateMillis
                    showStartDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showEndDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = endDate ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    // Set end date to end of the selected day
                    val sel = pickerState.selectedDateMillis
                    endDate = if (sel != null) {
                        val cal = Calendar.getInstance()
                        cal.timeInMillis = sel
                        cal.set(Calendar.HOUR_OF_DAY, 23)
                        cal.set(Calendar.MINUTE, 59)
                        cal.set(Calendar.SECOND, 59)
                        cal.set(Calendar.MILLISECOND, 999)
                        cal.timeInMillis
                    } else sel
                    showEndDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { onReset(); onNavigateBack() }) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("SMS Scanner", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        when {
            // ─── Scanning ──────────────────────────────────
            scanState.isScanning -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(modifier = Modifier.size(64.dp), color = BluePrimary)
                        Spacer(modifier = Modifier.height(24.dp))
                        Text("Scanning SMS messages...", fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Looking for banking transactions",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    }
                }
            }

            // ─── Results found ─────────────────────────────
            scanState.isComplete && scanState.pendingTransactions.isNotEmpty() -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Stats row
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            StatItem("${scanState.totalScanned}", "Scanned")
                            StatItem("${scanState.financialFound}", "Financial")
                            StatItem("${scanState.pendingTransactions.size}", "Parsed", GreenPrimary)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Save all button with loading state + progress bar
                    Button(
                        onClick = onConfirmAll,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                        enabled = !scanState.isSaving
                    ) {
                        if (scanState.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                "Saving ${scanState.savingProgress} of ${scanState.savingTotal}…",
                                fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            Icon(Icons.Filled.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Save All ${scanState.pendingTransactions.size} Transactions", fontWeight = FontWeight.SemiBold)
                        }
                    }

                    // Progress bar during saving
                    if (scanState.isSaving && scanState.savingTotal > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { if (scanState.savingTotal > 0) scanState.savingProgress.toFloat() / scanState.savingTotal.toFloat() else 0f },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(6.dp),
                            color = GreenPrimary,
                            trackColor = GreenPrimary.copy(alpha = 0.15f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Transaction list
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(items = scanState.pendingTransactions, key = { it.id }) { tx ->
                            // Show original parsed currency (before app-currency conversion)
                            val parsedCurrency = tx.notes.lines()
                                .find { it.startsWith("parsedCurrency:") }?.removePrefix("parsedCurrency:") ?: ""
                            val displayAmount = if (parsedCurrency.isNotEmpty()) {
                                val sym = CurrencyUtils.symbolFor(parsedCurrency)
                                "$sym${String.format("%.2f", tx.amount)} $parsedCurrency"
                            } else {
                                CurrencyUtils.format(tx.amount, currencyCode)
                            }

                            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (tx.type == TransactionType.EXPENSE)
                                                    RedExpense.copy(alpha = 0.1f)
                                                else GreenIncome.copy(alpha = 0.1f)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            if (tx.type == TransactionType.EXPENSE) "-" else "+",
                                            fontWeight = FontWeight.Bold,
                                            color = if (tx.type == TransactionType.EXPENSE) RedExpense else GreenIncome
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(tx.description, fontWeight = FontWeight.Medium, fontSize = 13.sp,
                                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Text(tx.category, fontSize = 11.sp, color = BluePrimary)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        displayAmount, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                                        color = if (tx.type == TransactionType.EXPENSE) RedExpense else GreenIncome
                                    )
                                    IconButton(onClick = { onDiscard(tx.id) }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Filled.Close, "Remove", modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }
                }
            }

            // ─── Saved successfully ────────────────────────
            scanState.isComplete && scanState.savedCount > 0 -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Icon(Icons.Filled.Check, null, tint = GreenPrimary, modifier = Modifier.size(72.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("${scanState.savedCount} transactions saved!",
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = { onReset(); onNavigateBack() },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp)) {
                            Text("Done", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // ─── No results / error ────────────────────────
            scanState.isComplete -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Text(
                            if (scanState.errorMessage != null) "Scan Error" else "No Transactions Found",
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            scanState.errorMessage
                                ?: "Scanned ${scanState.totalScanned} messages but no new banking transactions found.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = { onReset(); onNavigateBack() },
                                modifier = Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(12.dp)) {
                                Text("Go Back")
                            }
                            Button(onClick = { onReset(); doScan() },
                                modifier = Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(12.dp)) {
                                Text("Retry")
                            }
                        }
                    }
                }
            }

            // ─── Initial state ─────────────────────────────
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Filled.Email, null, tint = BluePrimary, modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Scan SMS Inbox", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Search your SMS messages for banking and payment notifications. " +
                            "Transactions are detected and categorized automatically.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Total SMS count card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = BluePrimary.copy(alpha = 0.08f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Filled.Email, null, tint = BluePrimary, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            if (totalSmsCount > 0) {
                                Text(
                                    "$totalSmsCount SMS messages in inbox",
                                    fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                                    color = BluePrimary
                                )
                            } else if (hasPermission) {
                                Text(
                                    "Loading SMS count...",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp
                                )
                            } else {
                                Text(
                                    "Grant permission to see SMS count",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Scan mode toggle
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Scan Mode", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Spacer(modifier = Modifier.height(12.dp))

                            // Message limit option
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { useMessageLimit = true }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = useMessageLimit,
                                    onClick = { useMessageLimit = true }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("By message count", fontSize = 14.sp)
                            }

                            // Slider for message limit
                            if (useMessageLimit) {
                                val effectiveMax = if (totalSmsCount > 0) totalSmsCount.toFloat() else 2000f
                                Column(modifier = Modifier.padding(start = 48.dp, end = 8.dp)) {
                                    Slider(
                                        value = messageLimit.coerceAtMost(effectiveMax),
                                        onValueChange = { messageLimit = it },
                                        valueRange = 50f..effectiveMax,
                                        steps = ((effectiveMax - 50f) / 50f).toInt().coerceAtLeast(0),
                                        colors = SliderDefaults.colors(
                                            thumbColor = BluePrimary,
                                            activeTrackColor = BluePrimary
                                        )
                                    )
                                    Text(
                                        "Scan latest ${messageLimit.toInt()} messages" +
                                            if (totalSmsCount > 0) " of $totalSmsCount" else "",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Date range option
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { useMessageLimit = false }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = !useMessageLimit,
                                    onClick = { useMessageLimit = false }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("By date range", fontSize = 14.sp)
                            }

                            // Date range pickers
                            if (!useMessageLimit) {
                                Column(modifier = Modifier.padding(start = 48.dp, end = 8.dp)) {
                                    // Start date
                                    OutlinedCard(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { showStartDatePicker = true },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Filled.DateRange, null,
                                                modifier = Modifier.size(20.dp),
                                                tint = BluePrimary
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text("From", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text(
                                                    startDate?.let { dateFormat.format(Date(it)) }
                                                        ?: "Select start date",
                                                    fontSize = 14.sp,
                                                    fontWeight = if (startDate != null) FontWeight.Medium else FontWeight.Normal,
                                                    color = if (startDate != null) MaterialTheme.colorScheme.onSurface
                                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            if (startDate != null) {
                                                Spacer(modifier = Modifier.weight(1f))
                                                IconButton(
                                                    onClick = { startDate = null },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(Icons.Filled.Close, "Clear", modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // End date
                                    OutlinedCard(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { showEndDatePicker = true },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Filled.DateRange, null,
                                                modifier = Modifier.size(20.dp),
                                                tint = BluePrimary
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text("To", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text(
                                                    endDate?.let { dateFormat.format(Date(it)) }
                                                        ?: "Select end date",
                                                    fontSize = 14.sp,
                                                    fontWeight = if (endDate != null) FontWeight.Medium else FontWeight.Normal,
                                                    color = if (endDate != null) MaterialTheme.colorScheme.onSurface
                                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            if (endDate != null) {
                                                Spacer(modifier = Modifier.weight(1f))
                                                IconButton(
                                                    onClick = { endDate = null },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(Icons.Filled.Close, "Clear", modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))
                                    val sd = startDate
                                    val ed = endDate
                                    Text(
                                        when {
                                            sd == null && ed == null -> "Select dates to filter SMS messages"
                                            sd != null && ed != null -> "Scanning messages from ${dateFormat.format(Date(sd))} to ${dateFormat.format(Date(ed))}"
                                            sd != null -> "Scanning messages from ${dateFormat.format(Date(sd))}"
                                            ed != null -> "Scanning messages until ${dateFormat.format(Date(ed))}"
                                            else -> "Select dates to filter SMS messages"
                                        },
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("All processing happens on-device.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = { doScan() },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BluePrimary)
                    ) {
                        Text(
                            if (hasPermission) "Start Scanning" else "Grant Permission & Scan",
                            fontWeight = FontWeight.SemiBold, fontSize = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun StatItem(value: String, label: String, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = color)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
