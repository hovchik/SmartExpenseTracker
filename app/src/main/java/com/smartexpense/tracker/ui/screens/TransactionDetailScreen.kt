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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.smartexpense.tracker.data.model.Transaction
import com.smartexpense.tracker.data.model.TransactionSource
import com.smartexpense.tracker.data.model.TransactionType
import com.smartexpense.tracker.ui.theme.*
import com.smartexpense.tracker.util.CurrencyUtils
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun TransactionDetailDialog(
    transaction: Transaction,
    currencyCode: String,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 8.dp),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp,
                                      bottomStart = 20.dp, bottomEnd = 20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // ── Drag handle ──────────────────────────────────────
                Box(modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier
                        .width(40.dp).height(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)))
                }

                // ── Header ──────────────────────────────────────────
                val isExpense = transaction.type == TransactionType.EXPENSE
                val amountColor = if (isExpense) RedExpense else GreenIncome

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(amountColor.copy(alpha = 0.06f))
                        .padding(horizontal = 24.dp, vertical = 20.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                           modifier = Modifier.fillMaxWidth()) {
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
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val dtDisplay = transaction.dateTime.take(16).replace("T", "  ")
                    DetailRow(Icons.Filled.CalendarToday, "Date & Time", dtDisplay)

                    if (transaction.merchantName.isNotEmpty()) {
                        DetailRow(Icons.Filled.Store, "Merchant", transaction.merchantName)
                    }

                    DetailRow(
                        Icons.Filled.Category, "Type",
                        if (isExpense) "Expense" else "Income"
                    )

                    val sourceLabel = when (transaction.source) {
                        TransactionSource.MANUAL -> "Manual entry"
                        TransactionSource.OCR_SCAN -> "OCR receipt scan"
                        TransactionSource.SMS -> "SMS auto-detected"
                        TransactionSource.NOTIFICATION -> "Notification auto-detected"
                        TransactionSource.IMPORT -> "Imported from file"
                    }
                    DetailRow(Icons.Filled.Source, "Source", sourceLabel)

                    if (transaction.tags.isNotEmpty()) {
                        DetailRow(Icons.Filled.Label, "Tags", transaction.tags.joinToString(", "))
                    }

                    if (transaction.isRecurring) {
                        DetailRow(Icons.Filled.Repeat, "Recurring", "Yes")
                    }

                    if (transaction.notes.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Notes", style = MaterialTheme.typography.labelMedium,
                             color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant)
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
                        onClick = onDismiss,
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
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = { Icon(Icons.Filled.Warning, null, tint = RedExpense) },
            title = { Text("Delete Transaction?") },
            text = { Text("This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = { onDelete(); onDismiss() },
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
        Icon(icon, contentDescription = null,
             tint = MaterialTheme.colorScheme.onSurfaceVariant,
             modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                 color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
