package com.smartexpense.tracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartexpense.tracker.data.model.Transaction
import com.smartexpense.tracker.data.model.TransactionType
import com.smartexpense.tracker.ui.theme.*
import com.smartexpense.tracker.util.CurrencyUtils
import com.smartexpense.tracker.util.DateUtils

/**
 * Trash screen showing soft-deleted transactions.
 * Users can restore or permanently delete individual items, or empty the trash.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    deletedTransactions: List<Transaction>,
    currencyCode: String = "USD",
    onRestore: (String) -> Unit,
    onPermanentlyDelete: (String) -> Unit,
    onEmptyTrash: () -> Unit,
    onNavigateBack: () -> Unit,
    trashRetentionDays: Int = 30
) {
    var showEmptyConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trash", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (deletedTransactions.isNotEmpty()) {
                        IconButton(onClick = { showEmptyConfirm = true }) {
                            Icon(Icons.Filled.DeleteForever, contentDescription = "Empty trash",
                                tint = RedExpense)
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Info banner
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Info, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Deleted items are kept for $trashRetentionDays days, then permanently removed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (deletedTransactions.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Trash is empty",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Deleted transactions will appear here",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                // Count summary
                Text(
                    "${deletedTransactions.size} item${if (deletedTransactions.size != 1) "s" else ""} in trash",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )

                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(deletedTransactions, key = { it.id }) { tx ->
                        TrashTransactionCard(
                            transaction = tx,
                            currencyCode = currencyCode,
                            onRestore = { onRestore(tx.id) },
                            onPermanentlyDelete = { showDeleteConfirm = tx.id }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }

    // Empty trash confirmation dialog
    if (showEmptyConfirm) {
        AlertDialog(
            onDismissRequest = { showEmptyConfirm = false },
            icon = { Icon(Icons.Filled.DeleteForever, contentDescription = null, tint = RedExpense) },
            title = { Text("Empty trash?") },
            text = {
                Text("This will permanently delete ${deletedTransactions.size} transaction${if (deletedTransactions.size != 1) "s" else ""}. This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onEmptyTrash()
                        showEmptyConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = RedExpense)
                ) { Text("Empty Trash") }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyConfirm = false }) { Text("Cancel") }
            }
        )
    }

    // Single item permanent delete confirmation
    showDeleteConfirm?.let { txId ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Permanently delete?") },
            text = { Text("This transaction will be permanently removed and cannot be recovered.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onPermanentlyDelete(txId)
                        showDeleteConfirm = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = RedExpense)
                ) { Text("Delete Forever") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun TrashTransactionCard(
    transaction: Transaction,
    currencyCode: String,
    onRestore: () -> Unit,
    onPermanentlyDelete: () -> Unit
) {
    val isExpense = transaction.type == TransactionType.EXPENSE
    val deletedAt = transaction.deletedAt ?: 0L
    val daysAgo = if (deletedAt > 0) {
        ((System.currentTimeMillis() - deletedAt) / (24 * 60 * 60 * 1000)).toInt()
    } else 0

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isExpense) RedExpense.copy(alpha = 0.1f)
                            else GreenIncome.copy(alpha = 0.1f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isExpense) Icons.Filled.ShoppingCart else Icons.Filled.AccountBalance,
                        contentDescription = null,
                        tint = if (isExpense) RedExpense else GreenIncome,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))

                // Details
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        transaction.description,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            transaction.category,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(" · ", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            DateUtils.formatShortDate(transaction.timestamp),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Amount
                Text(
                    "${if (isExpense) "-" else "+"}${CurrencyUtils.format(transaction.amount, currencyCode)}",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = if (isExpense) RedExpense else GreenIncome
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Deletion info + action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Deleted ${if (daysAgo == 0) "today" else if (daysAgo == 1) "yesterday" else "$daysAgo days ago"}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = onRestore,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Filled.RestoreFromTrash, contentDescription = null,
                            modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Restore", fontSize = 12.sp)
                    }
                    TextButton(
                        onClick = onPermanentlyDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = RedExpense),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Filled.DeleteForever, contentDescription = null,
                            modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
