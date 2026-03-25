package com.smartexpense.tracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartexpense.tracker.data.model.Transaction
import com.smartexpense.tracker.data.model.TransactionSource
import com.smartexpense.tracker.data.model.TransactionType
import com.smartexpense.tracker.data.model.currencyInfoFor
import com.smartexpense.tracker.ui.theme.*
import com.smartexpense.tracker.util.CurrencyUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailDialog(
    transaction: Transaction,
    currencyCode: String,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        tonalElevation = 6.dp,
        dragHandle = {
            Box(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp).height(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            // ── Header ──────────────────────────────────────────
            val isExpense = transaction.type == TransactionType.EXPENSE
            val amountColor = if (isExpense) RedExpense else GreenIncome

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(amountColor.copy(alpha = 0.06f))
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Type icon
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(amountColor.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            when (transaction.source) {
                                TransactionSource.OCR_SCAN -> Icons.Filled.CameraAlt
                                TransactionSource.SMS -> Icons.Filled.Sms
                                TransactionSource.NOTIFICATION -> Icons.Filled.Notifications
                                TransactionSource.VOICE -> Icons.Filled.Mic
                                else -> if (isExpense) Icons.Filled.ShoppingCart
                                else Icons.Filled.AccountBalance
                            },
                            contentDescription = null,
                            tint = amountColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    // Amount
                    Text(
                        "${if (isExpense) "−" else "+"}${CurrencyUtils.format(transaction.amount, currencyCode)}",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = amountColor
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        transaction.description,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = amountColor.copy(alpha = 0.12f)
                    ) {
                        Text(
                            transaction.category,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = amountColor,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // ── Detail rows ──────────────────────────────────────
            val isExpenseType = transaction.type == TransactionType.EXPENSE
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val dtDisplay = (transaction.dateTime ?: "").take(16).replace("T", "  ")
                DetailRow(Icons.Filled.CalendarToday, "Date & Time", dtDisplay)

                if (transaction.merchantName.isNotEmpty()) {
                    DetailRow(Icons.Filled.Store, "Merchant", transaction.merchantName)
                }

                DetailRow(
                    Icons.Filled.Category, "Type",
                    if (isExpenseType) "Expense" else "Income"
                )

                val sourceLabel = when (transaction.source) {
                    TransactionSource.MANUAL -> "Manual entry"
                    TransactionSource.OCR_SCAN -> "OCR receipt scan"
                    TransactionSource.SMS -> "SMS auto-detected"
                    TransactionSource.NOTIFICATION -> "Notification auto-detected"
                    TransactionSource.IMPORT -> "Imported from file"
                    TransactionSource.VOICE -> "Voice input"
                }
                DetailRow(Icons.Filled.Source, "Source", sourceLabel)

                // ── Foreign currency conversion details ──────────
                if (transaction.originalAmount > 0.0 && transaction.originalCurrencyCode.orEmpty().isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    val origSym = currencyInfoFor(transaction.originalCurrencyCode.orEmpty()).symbol
                    val appSym = currencyInfoFor(currencyCode).symbol
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.CurrencyExchange, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Currency Conversion",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Original amount", fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        "${origSym}${String.format("%.2f", transaction.originalAmount)} ${transaction.originalCurrencyCode.orEmpty()}",
                                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Converted to", fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        "${appSym}${String.format("%.2f", transaction.amount)} $currencyCode",
                                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Rate: 1 ${transaction.originalCurrencyCode.orEmpty()} = ${String.format("%.4f", transaction.exchangeRate)} $currencyCode",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (transaction.tags.isNotEmpty()) {
                    DetailRow(Icons.Filled.Label, "Tags", transaction.tags.joinToString(", "))
                }

                if (transaction.isRecurring) {
                    DetailRow(Icons.Filled.Repeat, "Recurring", "Yes")
                }

                if (transaction.notes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Notes", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            transaction.notes,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 19.sp
                        )
                    }
                }

                // Transaction ID (small, for debugging / support)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "ID: ${transaction.id}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }

            // ── Action buttons ───────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("Close") }

                Button(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RedExpense)
                ) {
                    Icon(Icons.Filled.Delete, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Delete")
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = { Icon(Icons.Filled.Warning, null, tint = RedExpense) },
            title = { Text("Delete Transaction?") },
            text = { Text("This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = RedExpense)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun DetailRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
