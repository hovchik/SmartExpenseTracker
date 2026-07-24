package com.flowsense.app.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowsense.app.data.model.TransactionType
import com.flowsense.app.ui.theme.*
import com.flowsense.app.ui.viewmodel.RecategorizationOutcome
import com.flowsense.app.ui.viewmodel.RecategorizedItem
import com.flowsense.app.util.CurrencyUtils
import com.flowsense.app.util.DateUtils

/**
 * Dedicated screen for running smart categorization over a chosen day's
 * transactions. The user picks a date and taps "Run categorization"; the result
 * lists every transaction on that day with its (new) category, highlighting the
 * ones whose category changed (old → new).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategorizeScreen(
    outcome: RecategorizationOutcome?,
    isRecategorizing: Boolean,
    currencyCode: String = "USD",
    onRunForDate: (Long) -> Unit,
    onNavigateBack: () -> Unit
) {
    var selectedDate by remember { mutableStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Categorize", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Date + run controls ────────────────────────────────────
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Re-run categorization on a day's transactions",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Smart categorization will re-evaluate every transaction on the " +
                            "selected day and update any whose category has changed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))

                    // Selected-day pill
                    Surface(
                        onClick = { showDatePicker = true },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.CalendarMonth, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Day", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(DateUtils.formatShortDate(selectedDate),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold)
                            }
                            Icon(Icons.Filled.Edit, contentDescription = "Change day",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp))
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Button(
                        onClick = { onRunForDate(selectedDate) },
                        enabled = !isRecategorizing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isRecategorizing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("Categorizing…")
                        } else {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null,
                                modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Run categorization")
                        }
                    }
                }
            }

            HorizontalDivider()

            // ── Results ────────────────────────────────────────────────
            when {
                outcome == null -> {
                    EmptyHint(
                        icon = Icons.Filled.AutoAwesome,
                        title = "Ready to categorize",
                        subtitle = "Pick a day and run categorization to see the results here."
                    )
                }
                outcome.items.isEmpty() -> {
                    EmptyHint(
                        icon = Icons.Filled.EventBusy,
                        title = "No transactions",
                        subtitle = "There are no transactions on ${outcome.dateLabel}."
                    )
                }
                else -> {
                    // Summary line
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val changed = outcome.changedCount
                        Text(
                            if (changed == 0)
                                "No changes · ${outcome.items.size} checked on ${outcome.dateLabel}"
                            else
                                "$changed updated · ${outcome.items.size} checked on ${outcome.dateLabel}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(outcome.items, key = { it.transaction.id }) { item ->
                            CategorizeResultRow(item, currencyCode)
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDate)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { selectedDate = it }
                        showDatePicker = false
                    },
                    enabled = datePickerState.selectedDateMillis != null
                ) { Text("Select") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun CategorizeResultRow(item: RecategorizedItem, currencyCode: String) {
    val tx = item.transaction
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (item.changed)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        else
            MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    tx.description.ifBlank { tx.merchantName.ifBlank { "Transaction" } },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                // Category: old → new when changed, else just the category
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.changed) {
                        CategoryChip(item.previousCategory, muted = true, strikethrough = true)
                        Icon(Icons.Filled.ArrowForward, contentDescription = "changed to",
                            modifier = Modifier.size(14.dp).padding(horizontal = 2.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        CategoryChip(tx.category, muted = false, strikethrough = false)
                    } else {
                        CategoryChip(tx.category, muted = true, strikethrough = false)
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(
                CurrencyUtils.format(tx.amount, currencyCode),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (tx.type == TransactionType.INCOME) GreenIncome else RedExpense
            )
        }
    }
}

@Composable
private fun CategoryChip(name: String, muted: Boolean, strikethrough: Boolean) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (muted)
            MaterialTheme.colorScheme.surface
        else
            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    ) {
        Text(
            name.ifBlank { "Other" },
            style = MaterialTheme.typography.labelSmall,
            fontSize = 11.sp,
            color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.primary,
            textDecoration = if (strikethrough) TextDecoration.LineThrough else null,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun ColumnScope.EmptyHint(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(icon, contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
        }
    }
}
