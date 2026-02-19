package com.smartexpense.tracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartexpense.tracker.R
import com.smartexpense.tracker.data.model.ExpenseReport
import com.smartexpense.tracker.data.model.ReportPeriod
import com.smartexpense.tracker.ui.theme.*
import com.smartexpense.tracker.util.CurrencyUtils
import com.smartexpense.tracker.util.DateUtils
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    generateReport: (ReportPeriod) -> ExpenseReport,
    currentPeriod: ReportPeriod,
    onPeriodChange: (ReportPeriod) -> Unit,
    currencyCode: String = "USD"
) {
    val report = remember(currentPeriod) { generateReport(currentPeriod) }

    val categoryColors = listOf(
        Color(0xFFE91E63), Color(0xFF2196F3), Color(0xFF9C27B0),
        Color(0xFFFF9800), Color(0xFF4CAF50), Color(0xFF607D8B),
        Color(0xFFF44336), Color(0xFF3F51B5), Color(0xFF00BCD4)
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // Header
        item {
            Text(stringResource(R.string.reports), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }

        // Period selector
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReportPeriod.entries.forEach { period ->
                    FilterChip(
                        selected = currentPeriod == period,
                        onClick = { onPeriodChange(period) },
                        label = {
                            Text(when (period) {
                                ReportPeriod.DAILY -> stringResource(R.string.today)
                                ReportPeriod.WEEKLY -> stringResource(R.string.this_week)
                                ReportPeriod.MONTHLY -> stringResource(R.string.this_month)
                            })
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // ─── AI Insight Banner ─────────────────────────
        if (report.aiInsight.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = PurpleAccent.copy(alpha = 0.08f))
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Filled.Star, null, tint = PurpleAccent, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(stringResource(R.string.ai_insights), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = PurpleAccent)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(report.aiInsight, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 18.sp)
                        }
                    }
                }
            }
        }

        // ─── Summary Cards ─────────────────────────────
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(stringResource(R.string.expenses), CurrencyUtils.format(report.totalExpenses, currencyCode), RedExpense, Modifier.weight(1f))
                StatCard(stringResource(R.string.income), CurrencyUtils.format(report.totalIncome, currencyCode), GreenIncome, Modifier.weight(1f))
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(stringResource(R.string.net_balance), CurrencyUtils.format(report.netBalance, currencyCode),
                    if (report.netBalance >= 0) GreenIncome else RedExpense, Modifier.weight(1f))
                StatCard(stringResource(R.string.avg_daily), CurrencyUtils.format(report.averageDailySpend, currencyCode), BluePrimary, Modifier.weight(1f))
            }
        }

        // ─── Comparison ────────────────────────────────
        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (report.comparisonWithPrevious <= 0) Icons.Filled.TrendingDown else Icons.Filled.TrendingUp,
                        null, tint = if (report.comparisonWithPrevious <= 0) GreenIncome else RedExpense,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.vs_previous_period), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "${if (report.comparisonWithPrevious > 0) "+" else ""}${String.format("%.1f", report.comparisonWithPrevious)}%",
                            fontWeight = FontWeight.Bold, fontSize = 18.sp,
                            color = if (report.comparisonWithPrevious <= 0) GreenIncome else RedExpense
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            stringResource(R.string.transactions_count_format, report.transactionCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            if (report.comparisonWithPrevious <= 0) stringResource(R.string.spending_down) else stringResource(R.string.spending_up),
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // ─── Category Breakdown ────────────────────────
        if (report.categoryBreakdown.isNotEmpty()) {
            item {
                Text(stringResource(R.string.spending_by_category), style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
            }
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        report.categoryBreakdown.entries.sortedByDescending { it.value }
                            .forEachIndexed { index, (category, amount) ->
                                val pct = if (report.totalExpenses > 0) amount / report.totalExpenses else 0.0
                                val color = categoryColors[index % categoryColors.size]
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(category, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                        Spacer(modifier = Modifier.height(3.dp))
                                        LinearProgressIndicator(
                                            progress = { pct.toFloat().coerceIn(0f, 1f) },
                                            modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                                            color = color, trackColor = MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(CurrencyUtils.format(amount, currencyCode), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text("${(pct * 100).roundToInt()}%", fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                    }
                }
            }
        }

        // ─── Day of Week Spending ──────────────────────
        if (report.dayOfWeekSpending.isNotEmpty() && report.dayOfWeekSpending.values.any { it > 0 }) {
            item {
                Text(stringResource(R.string.spending_by_day), style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
            }
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        val maxSpend = report.dayOfWeekSpending.values.maxOrNull() ?: 1.0
                        val dayOrder = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                        dayOrder.forEach { day ->
                            val amount = report.dayOfWeekSpending[day] ?: 0.0
                            val pct = if (maxSpend > 0) amount / maxSpend else 0.0
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(day, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                    modifier = Modifier.width(36.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                LinearProgressIndicator(
                                    progress = { pct.toFloat().coerceIn(0f, 1f) },
                                    modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)),
                                    color = if (day == "Sat" || day == "Sun") OrangeWarning else BluePrimary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(CurrencyUtils.format(amount, currencyCode), fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium, modifier = Modifier.width(70.dp))
                            }
                        }
                    }
                }
            }
        }

        // ─── Top Merchants ─────────────────────────────
        if (report.topMerchants.isNotEmpty()) {
            item {
                Text(stringResource(R.string.top_merchants), style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
            }
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        report.topMerchants.entries.forEachIndexed { index, (merchant, amount) ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${index + 1}", fontWeight = FontWeight.Bold, fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(24.dp))
                                Icon(Icons.Filled.Store, null, modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(merchant, modifier = Modifier.weight(1f), fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium)
                                Text(CurrencyUtils.format(amount, currencyCode), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }

        // ─── Transaction Sources ───────────────────────
        if (report.sourceBreakdown.isNotEmpty()) {
            item {
                Text(stringResource(R.string.transaction_sources), style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
            }
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        report.sourceBreakdown.forEach { (source, count) ->
                            val label = when (source) {
                                "MANUAL" -> stringResource(R.string.source_manual)
                                "OCR_SCAN" -> stringResource(R.string.source_ocr)
                                "SMS" -> stringResource(R.string.source_sms)
                                "NOTIFICATION" -> stringResource(R.string.source_notif)
                                "IMPORT" -> stringResource(R.string.source_import)
                                else -> source
                            }
                            val icon = when (source) {
                                "MANUAL" -> Icons.Filled.Edit
                                "OCR_SCAN" -> Icons.Filled.CameraAlt
                                "SMS" -> Icons.Filled.Email
                                "NOTIFICATION" -> Icons.Filled.Notifications
                                else -> Icons.Filled.Description
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(icon, null, modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("$count", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        // ─── Top Expenses ──────────────────────────────
        if (report.topExpenses.isNotEmpty()) {
            item {
                Text(stringResource(R.string.top_expenses), style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
            }
            items(report.topExpenses) { transaction ->
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(transaction.description, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            Text("${transaction.category} · ${DateUtils.formatShortDate(transaction.timestamp)}",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(CurrencyUtils.format(transaction.amount, currencyCode), fontWeight = FontWeight.Bold, color = RedExpense)
                    }
                }
            }
        }

        // ─── Transactions by Date ──────────────────────
        if (report.transactionsByDate.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.transactions_by_date),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            report.transactionsByDate.entries
                .sortedByDescending { it.key }
                .forEach { (dateStr, txList) ->
                    // Date header
                    item(key = "date_$dateStr") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.CalendarToday,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    dateStr,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            val dayTotal = txList.filter {
                                it.type == com.smartexpense.tracker.data.model.TransactionType.EXPENSE
                            }.sumOf { it.amount }
                            if (dayTotal > 0) {
                                Text(
                                    CurrencyUtils.format(dayTotal, currencyCode),
                                    fontSize = 12.sp,
                                    color = RedExpense,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                    // Transactions for that date
                    items(txList, key = { it.id }) { tx ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Type indicator dot
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (tx.type == com.smartexpense.tracker.data.model.TransactionType.EXPENSE)
                                                RedExpense else GreenIncome
                                        )
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(tx.description, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    Text(
                                        "${tx.category} · ${tx.dateTime.take(16).replace("T", " ")}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    CurrencyUtils.format(tx.amount, currencyCode),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (tx.type == com.smartexpense.tracker.data.model.TransactionType.EXPENSE)
                                        RedExpense else GreenIncome
                                )
                            }
                        }
                    }
                }
        }

        // ─── Empty state ───────────────────────────────
        if (report.transactionCount == 0) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Receipt, null, modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(stringResource(R.string.no_transactions_this_period), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.add_expenses_to_see_reports), fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
private fun StatCard(title: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(14.dp)) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = color)
        }
    }
}
