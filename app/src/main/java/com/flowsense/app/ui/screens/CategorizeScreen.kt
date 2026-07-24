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
 * Dedicated screen for running smart categorization over a chosen date range's
 * transactions. The user picks a start/end day and taps "Run categorization";
 * the result lists every transaction in range with its (new) category,
 * highlighting the ones whose category changed (old → new).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategorizeScreen(
    outcome: RecategorizationOutcome?,
    isRecategorizing: Boolean,
    currencyCode: String = "USD",
    onRunForRange: (Long, Long) -> Unit,
    onNavigateBack: () -> Unit
) {
    // Local-noon millis for the selected day range (defaults to today only).
    // Noon (not midnight) keeps the calendar date stable when the picker maps
    // it through UTC in any timezone.
    val todayNoon = remember { DateUtils.getStartOfDay() + 12L * 60 * 60 * 1000 }
    var rangeStart by remember { mutableStateOf(todayNoon) }
    var rangeEnd by remember { mutableStateOf(todayNoon) }
    var showRangePicker by remember { mutableStateOf(false) }

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
            // ── Range + run controls ───────────────────────────────────
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Re-run categorization on a date range",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Categorization will re-evaluate every transaction in the " +
                            "selected range and update any whose category has changed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))

                    // Selected-range pill
                    Surface(
                        onClick = { showRangePicker = true },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.DateRange, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Range", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(rangeLabel(rangeStart, rangeEnd),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold)
                            }
                            Icon(Icons.Filled.Edit, contentDescription = "Change range",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp))
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Button(
                        onClick = { onRunForRange(rangeStart, rangeEnd) },
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
                        subtitle = "Pick a range and run categorization to see the results here."
                    )
                }
                outcome.items.isEmpty() -> {
                    EmptyHint(
                        icon = Icons.Filled.SearchOff,
                        title = "No transactions",
                        subtitle = "There are no transactions in ${outcome.rangeLabel}."
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
                                "No changes · ${outcome.items.size} checked in ${outcome.rangeLabel}"
                            else
                                "$changed updated · ${outcome.items.size} checked in ${outcome.rangeLabel}",
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

    if (showRangePicker) {
        val pickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = rangeStart,
            initialSelectedEndDateMillis = rangeEnd
        )
        DatePickerDialog(
            onDismissRequest = { showRangePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val start = pickerState.selectedStartDateMillis
                        val end = pickerState.selectedEndDateMillis
                        if (start != null) {
                            // Picker reports UTC midnight; normalise to local day.
                            rangeStart = DateUtils.pickerUtcToLocalMillis(start)
                            rangeEnd = DateUtils.pickerUtcToLocalMillis(end ?: start)
                        }
                        showRangePicker = false
                    },
                    enabled = pickerState.selectedStartDateMillis != null
                ) { Text("Select") }
            },
            dismissButton = {
                TextButton(onClick = { showRangePicker = false }) { Text("Cancel") }
            }
        ) {
            DateRangePicker(
                state = pickerState,
                title = {
                    Text(
                        "Select a date range",
                        modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp)
                    )
                },
                modifier = Modifier.height(480.dp)
            )
        }
    }
}

/** "Jul 24, 2026" for a single day, else "Jul 20 – Jul 24, 2026". */
private fun rangeLabel(startMillis: Long, endMillis: Long): String {
    val lo = minOf(startMillis, endMillis)
    val hi = maxOf(startMillis, endMillis)
    return if (DateUtils.getStartOfDay(lo) == DateUtils.getStartOfDay(hi)) {
        DateUtils.formatDate(lo)
    } else {
        "${DateUtils.formatShortDate(lo)} – ${DateUtils.formatDate(hi)}"
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
                Spacer(Modifier.height(2.dp))
                Text(
                    DateUtils.formatShortDate(tx.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
