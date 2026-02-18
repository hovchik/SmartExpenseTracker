package com.smartexpense.tracker.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.smartexpense.tracker.data.model.TransactionType
import com.smartexpense.tracker.ui.theme.*
import com.smartexpense.tracker.ui.viewmodel.SmsScanState
import com.smartexpense.tracker.util.CurrencyUtils

@Composable
fun SmsScanScreen(
    scanState: SmsScanState,
    onStartScan: () -> Unit,
    onConfirmAll: () -> Unit,
    onDiscard: (String) -> Unit,
    onReset: () -> Unit,
    onNavigateBack: () -> Unit,
    currencyCode: String = "USD"
) {
    val context = LocalContext.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var pendingScan by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasPermission = granted
            if (granted && pendingScan) {
                pendingScan = false
                onStartScan()
            } else if (!granted) {
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
            onStartScan()
        } else {
            pendingScan = true
            permissionLauncher.launch(Manifest.permission.READ_SMS)
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

                    // Save all button
                    Button(
                        onClick = onConfirmAll,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save All ${scanState.pendingTransactions.size} Transactions", fontWeight = FontWeight.SemiBold)
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
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Icon(Icons.Filled.Email, null, tint = BluePrimary, modifier = Modifier.size(80.dp))
                        Spacer(modifier = Modifier.height(24.dp))
                        Text("Scan SMS Inbox", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Search your SMS messages for banking and payment notifications. " +
                                "Transactions are detected and categorized automatically.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("All processing happens on-device.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(32.dp))
                        Button(onClick = { doScan() },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BluePrimary)) {
                            Text(
                                if (hasPermission) "Start Scanning" else "Grant Permission & Scan",
                                fontWeight = FontWeight.SemiBold, fontSize = 16.sp
                            )
                        }
                    }
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
