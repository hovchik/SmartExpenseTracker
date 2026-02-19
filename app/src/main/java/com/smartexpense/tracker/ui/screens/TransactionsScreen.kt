package com.smartexpense.tracker.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartexpense.tracker.data.model.Transaction
import com.smartexpense.tracker.data.model.TransactionType
import com.smartexpense.tracker.ui.theme.*
import com.smartexpense.tracker.util.CurrencyUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Full all-transactions screen with:
 *  - Live search (description / merchant / category / notes)
 *  - Transactions grouped by month, each month collapsible
 *  - Month header shows total expense & income for the month
 *  - Individual transaction delete (swipe or tap-to-reveal)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    allTransactions: List<Transaction>,
    currencyCode: String = "USD",
    onDeleteTransaction: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    // Filter by search query (case-insensitive, matches description/merchant/category/notes)
    val filtered: List<Transaction> = remember(allTransactions, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) allTransactions
        else allTransactions.filter { tx ->
            tx.description.lowercase().contains(q) ||
            tx.merchantName.lowercase().contains(q) ||
            tx.category.lowercase().contains(q) ||
            tx.notes.lowercase().contains(q)
        }
    }

    // Group by "MMMM yyyy" label (e.g. "February 2026"), sorted newest-first
    val monthFormatter = SimpleDateFormat("MMMM yyyy", Locale.US)
    val monthKey     = SimpleDateFormat("yyyy-MM", Locale.US)   // stable sort key

    val byMonth: List<Pair<String, List<Transaction>>> = remember(filtered) {
        filtered
            .groupBy { monthKey.format(Date(it.timestamp)) }
            .entries
            .sortedByDescending { it.key }
            .map { (key, txs) ->
                val label = monthFormatter.format(
                    SimpleDateFormat("yyyy-MM", Locale.US).parse(key) ?: Date()
                )
                label to txs.sortedByDescending { it.timestamp }
            }
    }

    // Track which month sections are expanded; default = all collapsed
    val expandedMonths = remember { mutableStateMapOf<String, Boolean>() }

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Top bar ────────────────────────────────────────────────
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("Transactions", style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))

            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search by name, merchant, category…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Summary chip
            val totalFiltered = filtered.size
            val expenseSum = filtered.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
            Text(
                "$totalFiltered transaction${if (totalFiltered != 1) "s" else ""}" +
                if (expenseSum > 0) " · ${CurrencyUtils.format(expenseSum, currencyCode)} expenses" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        HorizontalDivider()

        // ── Month-grouped list ─────────────────────────────────────
        if (byMonth.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.SearchOff, contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        if (searchQuery.isEmpty()) "No transactions yet"
                        else "No results for \"$searchQuery\"",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                byMonth.forEach { (monthLabel, txList) ->
                    val isCollapsed = expandedMonths[monthLabel] != true
                    val monthExpenses = txList.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
                    val monthIncome  = txList.filter { it.type == TransactionType.INCOME  }.sumOf { it.amount }

                    // ── Month header ───────────────────────────────
                    item(key = "hdr_$monthLabel") {
                        Surface(
                            onClick = {
                                expandedMonths[monthLabel] = isCollapsed
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (isCollapsed) Icons.Filled.ChevronRight
                                        else Icons.Filled.ExpandMore,
                                        contentDescription = if (isCollapsed) "Expand" else "Collapse",
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            monthLabel,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        Text(
                                            "${txList.size} transaction${if (txList.size != 1) "s" else ""}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                        )
                                    }
                                }

                                // Month totals
                                Column(horizontalAlignment = Alignment.End) {
                                    if (monthExpenses > 0) {
                                        Text(
                                            "-${CurrencyUtils.format(monthExpenses, currencyCode)}",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = RedExpense
                                        )
                                    }
                                    if (monthIncome > 0) {
                                        Text(
                                            "+${CurrencyUtils.format(monthIncome, currencyCode)}",
                                            fontSize = 12.sp,
                                            color = GreenIncome
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ── Collapsible transaction rows ───────────────
                    if (!isCollapsed) {
                        items(txList, key = { it.id }) { tx ->
                            TransactionItem(
                                transaction = tx,
                                currencyCode = currencyCode,
                                onDelete = { onDeleteTransaction(tx.id) }
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}
