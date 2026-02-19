package com.smartexpense.tracker.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartexpense.tracker.R
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.smartexpense.tracker.data.model.ExpenseReport
import com.smartexpense.tracker.data.model.ReportPeriod
import com.smartexpense.tracker.data.model.Transaction
import com.smartexpense.tracker.data.model.TransactionType
import com.smartexpense.tracker.ui.theme.*
import com.smartexpense.tracker.util.CurrencyUtils
import com.smartexpense.tracker.util.DateUtils
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    generateReport: (ReportPeriod) -> ExpenseReport,
    generateMonthlyReport: (year: Int, month: Int) -> ExpenseReport = { _, _ -> generateReport(ReportPeriod.MONTHLY) },
    currentPeriod: ReportPeriod,
    onPeriodChange: (ReportPeriod) -> Unit,
    allTransactions: List<Transaction> = emptyList(),
    currencyCode: String = "USD"
) {
    // Month selector state – defaults to current month
    val nowCal = remember { Calendar.getInstance() }
    var selectedYear  by remember { mutableIntStateOf(nowCal.get(Calendar.YEAR)) }
    var selectedMonth by remember { mutableIntStateOf(nowCal.get(Calendar.MONTH)) }

    val report = remember(currentPeriod, selectedYear, selectedMonth) {
        if (currentPeriod == ReportPeriod.MONTHLY) generateMonthlyReport(selectedYear, selectedMonth)
        else generateReport(currentPeriod)
    }

    val monthYearFormatter = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }

    val categoryColors = listOf(
        Color(0xFFE91E63), Color(0xFF2196F3), Color(0xFF9C27B0),
        Color(0xFFFF9800), Color(0xFF4CAF50), Color(0xFF607D8B),
        Color(0xFFF44336), Color(0xFF3F51B5), Color(0xFF00BCD4)
    )

    val context = LocalContext.current
    var showSharePicker by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
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

        // ─── Month selector (visible only when MONTHLY period is active) ──
        if (currentPeriod == ReportPeriod.MONTHLY) {
            item {
                val isCurrentMonth = run {
                    val now = Calendar.getInstance()
                    selectedYear == now.get(Calendar.YEAR) && selectedMonth == now.get(Calendar.MONTH)
                }
                val displayCal = remember(selectedYear, selectedMonth) {
                    Calendar.getInstance().apply {
                        set(Calendar.YEAR, selectedYear)
                        set(Calendar.MONTH, selectedMonth)
                        set(Calendar.DAY_OF_MONTH, 1)
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Previous month
                        IconButton(onClick = {
                            val cal = Calendar.getInstance().apply {
                                set(Calendar.YEAR, selectedYear)
                                set(Calendar.MONTH, selectedMonth)
                                add(Calendar.MONTH, -1)
                            }
                            selectedYear  = cal.get(Calendar.YEAR)
                            selectedMonth = cal.get(Calendar.MONTH)
                        }) {
                            Icon(
                                Icons.Filled.ChevronLeft,
                                contentDescription = "Previous month",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }

                        // Month + year label
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                monthYearFormatter.format(displayCal.time),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            if (isCurrentMonth) {
                                Text(
                                    "Current month",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }

                        // Next month (disabled when on current month)
                        IconButton(
                            onClick = {
                                val cal = Calendar.getInstance().apply {
                                    set(Calendar.YEAR, selectedYear)
                                    set(Calendar.MONTH, selectedMonth)
                                    add(Calendar.MONTH, 1)
                                }
                                selectedYear  = cal.get(Calendar.YEAR)
                                selectedMonth = cal.get(Calendar.MONTH)
                            },
                            enabled = !isCurrentMonth
                        ) {
                            Icon(
                                Icons.Filled.ChevronRight,
                                contentDescription = "Next month",
                                tint = if (isCurrentMonth)
                                    MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.3f)
                                else
                                    MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
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

        // ─── Spending Breakdown (Donut) ────────────────────────────────────────
        if (report.categoryBreakdown.size >= 2) {
            item {
                Text("Spending Breakdown",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp))
            }
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Column(modifier = Modifier.padding(16.dp),
                           horizontalAlignment = Alignment.CenterHorizontally) {
                        CategoryDonutChart(
                            categoryBreakdown = report.categoryBreakdown,
                            categoryColors = categoryColors)
                        Spacer(modifier = Modifier.height(12.dp))
                        report.categoryBreakdown.entries.sortedByDescending { it.value }
                            .take(5).forEachIndexed { idx, (cat, amt) ->
                                val pct = if (report.totalExpenses > 0)
                                    (amt / report.totalExpenses * 100).roundToInt() else 0
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(10.dp).clip(CircleShape)
                                        .background(categoryColors[idx % categoryColors.size]))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(cat, modifier = Modifier.weight(1f), fontSize = 13.sp)
                                    Text("$pct%", fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(CurrencyUtils.format(amt, currencyCode),
                                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                    }
                }
            }
        }

        // ─── 6-Month Income vs Expense Trend ──────────────────────────────────
        if (allTransactions.isNotEmpty()) {
            item {
                Text("6-Month Trend",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp))
            }
            item {
                val monthlyData = remember(allTransactions) { buildMonthlyTrend(allTransactions) }
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(GreenIncome))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Income", fontSize = 12.sp)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(RedExpense))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Expenses", fontSize = 12.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        MonthlyTrendChart(monthlyData = monthlyData, currencyCode = currencyCode)
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

        // ─── Savings Rate ─────────────────────────────────────
        if (report.totalIncome > 0) {
            item {
                val savingsRate = ((report.totalIncome - report.totalExpenses) /
                    report.totalIncome * 100).coerceIn(0.0, 100.0)
                val savingsColor = when {
                    savingsRate >= 20 -> GreenIncome
                    savingsRate >= 10 -> OrangeWarning
                    else -> RedExpense
                }
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Savings, null, tint = savingsColor,
                                modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Savings Rate", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.weight(1f))
                            Text("${savingsRate.roundToInt()}%",
                                fontWeight = FontWeight.Bold, fontSize = 18.sp, color = savingsColor)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { (savingsRate / 100).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = savingsColor,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            when {
                                savingsRate >= 20 -> "Great! You're meeting the 20% savings goal."
                                savingsRate >= 10 -> "Getting closer — aim for 20% to build a safety net."
                                else -> "Spending exceeds recommended limits. Review your expenses."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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

    // ─── Share format picker dialog ─────────────────────────────────
    if (showSharePicker) {
        val periodLabel = when (currentPeriod) {
            ReportPeriod.DAILY -> "Today"
            ReportPeriod.WEEKLY -> "This Week"
            ReportPeriod.MONTHLY -> {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, selectedYear)
                    set(Calendar.MONTH, selectedMonth)
                    set(Calendar.DAY_OF_MONTH, 1)
                }
                monthYearFormatter.format(cal.time)
            }
        }
        AlertDialog(
            onDismissRequest = { showSharePicker = false },
            icon = { Icon(Icons.Filled.Share, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Share Report") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Choose format:", style = MaterialTheme.typography.bodyMedium)
                    // Text
                    OutlinedCard(
                        onClick = {
                            showSharePicker = false
                            val text = buildShareText(report, currencyCode, currentPeriod,
                                selectedYear, selectedMonth, monthYearFormatter)
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Smart Expense Report")
                                putExtra(Intent.EXTRA_TEXT, text)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share Report"))
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.TextSnippet, null,
                                tint = BluePrimary, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Text", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("Plain text summary", fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    // Image
                    OutlinedCard(
                        onClick = {
                            showSharePicker = false
                            shareReportAsImage(context, report, currencyCode, periodLabel)
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Image, null,
                                tint = GreenIncome, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Image", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("PNG image for messaging apps", fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    // PDF
                    OutlinedCard(
                        onClick = {
                            showSharePicker = false
                            shareReportAsPdf(context, report, currencyCode, periodLabel)
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.PictureAsPdf, null,
                                tint = RedExpense, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("PDF", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("PDF document for email & printing", fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSharePicker = false }) { Text("Cancel") }
            }
        )
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

// ─── Share text builder ───────────────────────────────────────────────

private fun buildShareText(
    report: ExpenseReport,
    currencyCode: String,
    period: ReportPeriod,
    selectedYear: Int,
    selectedMonth: Int,
    monthFmt: SimpleDateFormat
): String {
    val periodLabel = when (period) {
        ReportPeriod.DAILY   -> "Today"
        ReportPeriod.WEEKLY  -> "This Week"
        ReportPeriod.MONTHLY -> {
            val cal = Calendar.getInstance().apply {
                set(Calendar.YEAR, selectedYear)
                set(Calendar.MONTH, selectedMonth)
                set(Calendar.DAY_OF_MONTH, 1)
            }
            monthFmt.format(cal.time)
        }
    }

    val sb = StringBuilder()
    sb.appendLine("📊 Smart Expense Report — $periodLabel")
    sb.appendLine("─".repeat(34))
    sb.appendLine("💸 Expenses : ${CurrencyUtils.format(report.totalExpenses, currencyCode)}")
    sb.appendLine("💰 Income   : ${CurrencyUtils.format(report.totalIncome, currencyCode)}")
    sb.appendLine("📈 Net Bal  : ${CurrencyUtils.format(report.netBalance, currencyCode)}")
    sb.appendLine("📅 Avg Daily: ${CurrencyUtils.format(report.averageDailySpend, currencyCode)}")
    sb.appendLine("🔢 Txns     : ${report.transactionCount}")

    if (report.categoryBreakdown.isNotEmpty()) {
        sb.appendLine()
        sb.appendLine("Top Categories:")
        report.categoryBreakdown.entries
            .sortedByDescending { it.value }
            .take(5)
            .forEach { (cat, amt) ->
                sb.appendLine("  • $cat: ${CurrencyUtils.format(amt, currencyCode)}")
            }
    }

    if (report.totalIncome > 0) {
        val savingsRate = ((report.totalIncome - report.totalExpenses) / report.totalIncome * 100)
            .coerceIn(0.0, 100.0).roundToInt()
        sb.appendLine()
        sb.appendLine("💾 Savings Rate: $savingsRate%")
    }

    val change = report.comparisonWithPrevious
    if (change != 0.0) {
        val arrow = if (change <= 0) "▼" else "▲"
        sb.appendLine("$arrow vs Previous: ${if (change > 0) "+" else ""}${String.format("%.1f", change)}%")
    }

    if (report.aiInsight.isNotEmpty()) {
        sb.appendLine()
        sb.appendLine("🤖 AI Insight: ${report.aiInsight}")
    }

    sb.appendLine()
    sb.append("Shared from Smart Expense Tracker")
    return sb.toString()
}

// ─── Category Donut Chart ─────────────────────────────────────────────

@Composable
private fun CategoryDonutChart(
    categoryBreakdown: Map<String, Double>,
    categoryColors: List<Color>
) {
    val total = categoryBreakdown.values.sum().takeIf { it > 0 } ?: return
    val slices = categoryBreakdown.entries
        .sortedByDescending { it.value }
        .take(8)
        .mapIndexed { i, (_, amt) -> Pair((amt / total * 360.0).toFloat(), categoryColors[i % categoryColors.size]) }

    val strokeWidth = 36f
    Canvas(modifier = Modifier
        .fillMaxWidth()
        .height(180.dp)) {
        val diameter = minOf(size.width, size.height) * 0.72f
        val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
        val arcSize = Size(diameter, diameter)
        var startAngle = -90f
        slices.forEach { (sweep, color) ->
            drawArc(
                color = color,
                startAngle = startAngle,
                sweepAngle = sweep - 2f,   // 2° gap between slices
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
            )
            startAngle += sweep
        }
    }
}

// ─── 6-Month Trend Chart ─────────────────────────────────────────────

data class MonthBucket(val label: String, val income: Double, val expense: Double)

private fun buildMonthlyTrend(transactions: List<Transaction>): List<MonthBucket> {
    val fmt = SimpleDateFormat("MMM", Locale.getDefault())
    val cal = Calendar.getInstance()
    return (5 downTo 0).map { monthsBack ->
        val c = Calendar.getInstance().apply { add(Calendar.MONTH, -monthsBack) }
        val year = c.get(Calendar.YEAR); val month = c.get(Calendar.MONTH)
        val startCal = Calendar.getInstance().apply {
            set(year, month, 1, 0, 0, 0); set(Calendar.MILLISECOND, 0)
        }
        val endCal = Calendar.getInstance().apply {
            set(year, month, c.getActualMaximum(Calendar.DAY_OF_MONTH), 23, 59, 59)
            set(Calendar.MILLISECOND, 999)
        }
        val inRange = transactions.filter { it.timestamp in startCal.timeInMillis..endCal.timeInMillis }
        cal.set(year, month, 1)
        MonthBucket(
            label = fmt.format(cal.time),
            income = inRange.filter { it.type == TransactionType.INCOME }.sumOf { it.amount },
            expense = inRange.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        )
    }
}

@Composable
private fun MonthlyTrendChart(
    monthlyData: List<MonthBucket>,
    currencyCode: String
) {
    if (monthlyData.isEmpty()) return
    val maxVal = monthlyData.maxOf { maxOf(it.income, it.expense) }.coerceAtLeast(1.0)
    val barWidth = 14.dp
    val chartHeight = 120.dp
    val labelStyle = MaterialTheme.typography.labelSmall
    val incomeColor = GreenIncome
    val expenseColor = RedExpense
    val surfaceVar = MaterialTheme.colorScheme.surfaceVariant

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        monthlyData.forEach { bucket ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                // Bars
                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.height(chartHeight)
                ) {
                    // Income bar
                    val incomeFrac = (bucket.income / maxVal).toFloat().coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .width(barWidth)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(incomeFrac.coerceAtLeast(0.02f))
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(incomeColor))
                    }
                    // Expense bar
                    val expFrac = (bucket.expense / maxVal).toFloat().coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .width(barWidth)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(expFrac.coerceAtLeast(0.02f))
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(expenseColor))
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(bucket.label, style = labelStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ─── Image / PDF report sharing ──────────────────────────────────────

/** Builds a multi-line report text for rendering on a canvas (image/PDF). */
private fun buildReportLines(
    report: ExpenseReport,
    currencyCode: String,
    periodLabel: String
): List<Pair<String, Boolean>> {
    // Pair<text, isBold>
    val lines = mutableListOf<Pair<String, Boolean>>()
    lines += "Smart Expense Report" to true
    lines += periodLabel to false
    lines += "" to false
    lines += "Expenses:   ${CurrencyUtils.format(report.totalExpenses, currencyCode)}" to false
    lines += "Income:     ${CurrencyUtils.format(report.totalIncome, currencyCode)}" to false
    lines += "Net Balance: ${CurrencyUtils.format(report.netBalance, currencyCode)}" to false
    lines += "Avg Daily:   ${CurrencyUtils.format(report.averageDailySpend, currencyCode)}" to false
    lines += "Transactions: ${report.transactionCount}" to false

    if (report.comparisonWithPrevious != 0.0) {
        val sign = if (report.comparisonWithPrevious > 0) "+" else ""
        lines += "vs Previous: $sign${String.format("%.1f", report.comparisonWithPrevious)}%" to false
    }

    if (report.categoryBreakdown.isNotEmpty()) {
        lines += "" to false
        lines += "Top Categories" to true
        report.categoryBreakdown.entries.sortedByDescending { it.value }.take(5)
            .forEach { (cat, amt) ->
                val pct = if (report.totalExpenses > 0) (amt / report.totalExpenses * 100).roundToInt() else 0
                lines += "  $cat: ${CurrencyUtils.format(amt, currencyCode)} ($pct%)" to false
            }
    }

    if (report.topMerchants.isNotEmpty()) {
        lines += "" to false
        lines += "Top Merchants" to true
        report.topMerchants.entries.take(5).forEach { (merchant, amt) ->
            lines += "  $merchant: ${CurrencyUtils.format(amt, currencyCode)}" to false
        }
    }

    if (report.totalIncome > 0) {
        val savingsRate = ((report.totalIncome - report.totalExpenses) / report.totalIncome * 100)
            .coerceIn(0.0, 100.0).roundToInt()
        lines += "" to false
        lines += "Savings Rate: $savingsRate%" to false
    }

    if (report.aiInsight.isNotEmpty()) {
        lines += "" to false
        lines += "AI Insight" to true
        lines += report.aiInsight to false
    }

    lines += "" to false
    lines += "Shared from Smart Expense Tracker" to false
    return lines
}

/** Renders report lines onto a Bitmap canvas. */
private fun renderReportBitmap(
    report: ExpenseReport,
    currencyCode: String,
    periodLabel: String
): Bitmap {
    val lines = buildReportLines(report, currencyCode, periodLabel)
    val width = 1080
    val lineHeight = 48
    val topMargin = 60
    val leftMargin = 50
    val height = topMargin + lines.size * lineHeight + 60

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)

    val paintNormal = Paint().apply {
        color = android.graphics.Color.parseColor("#212121")
        textSize = 34f
        isAntiAlias = true
    }
    val paintBold = Paint().apply {
        color = android.graphics.Color.parseColor("#1565C0")
        textSize = 38f
        isFakeBoldText = true
        isAntiAlias = true
    }

    var y = topMargin.toFloat()
    for ((text, isBold) in lines) {
        y += lineHeight
        canvas.drawText(text, leftMargin.toFloat(), y, if (isBold) paintBold else paintNormal)
    }
    return bitmap
}

/** Shares the report as a PNG image via the system share sheet. */
private fun shareReportAsImage(
    context: Context,
    report: ExpenseReport,
    currencyCode: String,
    periodLabel: String
) {
    try {
        val bitmap = renderReportBitmap(report, currencyCode, periodLabel)
        val file = File(context.cacheDir, "report_share.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Report Image"))
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to create image: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

/** Shares the report as a PDF document via the system share sheet. */
private fun shareReportAsPdf(
    context: Context,
    report: ExpenseReport,
    currencyCode: String,
    periodLabel: String
) {
    try {
        val lines = buildReportLines(report, currencyCode, periodLabel)
        val pageWidth = 595  // A4 in pts at 72 dpi
        val pageHeight = 842
        val leftMargin = 40f
        val lineHeight = 22f
        val topMargin = 50f

        val document = PdfDocument()
        var pageNumber = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas
        var y = topMargin

        val paintNormal = Paint().apply {
            color = android.graphics.Color.parseColor("#212121")
            textSize = 14f
            isAntiAlias = true
        }
        val paintBold = Paint().apply {
            color = android.graphics.Color.parseColor("#1565C0")
            textSize = 16f
            isFakeBoldText = true
            isAntiAlias = true
        }

        for ((text, isBold) in lines) {
            y += lineHeight
            if (y > pageHeight - 40) {
                document.finishPage(page)
                pageNumber++
                page = document.startPage(
                    PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                )
                canvas = page.canvas
                y = topMargin + lineHeight
            }
            canvas.drawText(text, leftMargin, y, if (isBold) paintBold else paintNormal)
        }
        document.finishPage(page)

        val file = File(context.cacheDir, "report_share.pdf")
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Report PDF"))
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to create PDF: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
