package com.smartexpense.tracker.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartexpense.tracker.data.model.*
import com.smartexpense.tracker.ui.components.dragDropItem
import com.smartexpense.tracker.ui.components.rememberDragDropListState
import com.smartexpense.tracker.ui.theme.*
import com.smartexpense.tracker.ui.viewmodel.UiState
import com.smartexpense.tracker.util.CurrencyUtils
import com.smartexpense.tracker.util.DateUtils

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(
    uiState: UiState,
    weeklyChartData: List<Pair<String, Double>>,
    onDismissSuggestion: (String) -> Unit,
    onDeleteTransaction: (String) -> Unit,
    sectionOrder: List<DashboardSection>,
    onMoveSections: (DashboardSection, DashboardSection) -> Unit,
    currencyCode: String = "USD"
) {
    val visibleSections = remember(
        sectionOrder,
        uiState.suggestions.isEmpty(),
        uiState.categoryBreakdown.isEmpty()
    ) {
        sectionOrder.filter { section ->
            when (section) {
                DashboardSection.AI_INSIGHTS -> uiState.suggestions.isNotEmpty()
                DashboardSection.CATEGORY_BREAKDOWN -> uiState.categoryBreakdown.isNotEmpty()
                else -> true
            }
        }
    }

    val lazyListState = rememberLazyListState()
    val dragDropState = rememberDragDropListState(lazyListState) { fromIndex, toIndex ->
        if (fromIndex in visibleSections.indices && toIndex in visibleSections.indices) {
            onMoveSections(visibleSections[fromIndex], visibleSections[toIndex])
        }
    }

    LazyColumn(
        state = lazyListState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        itemsIndexed(visibleSections, key = { _, section -> section.name }) { index, section ->
            val isDragged = index == dragDropState.draggedIndex
            Box(
                modifier = Modifier
                    .then(if (!isDragged) Modifier.animateItemPlacement() else Modifier)
                    .dragDropItem(dragDropState, index)
            ) {
                when (section) {
                    DashboardSection.BALANCE_SUMMARY ->
                        BalanceSummaryCard(uiState, currencyCode)
                    DashboardSection.QUICK_STATS ->
                        QuickStatsRow(uiState, currencyCode)
                    DashboardSection.WEEKLY_CHART ->
                        WeeklySpendingChart(weeklyChartData, currencyCode)
                    DashboardSection.AI_INSIGHTS ->
                        AiInsightsSection(uiState.suggestions, currencyCode, onDismissSuggestion)
                    DashboardSection.CATEGORY_BREAKDOWN ->
                        CategoryBreakdownSection(
                            uiState.categoryBreakdown, uiState.monthlyExpenses, currencyCode
                        )
                    DashboardSection.RECENT_TRANSACTIONS ->
                        RecentTransactionsSection(
                            uiState.recentTransactions, currencyCode, onDeleteTransaction
                        )
                }
            }
        }
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

// ── Section wrappers (group title + content into a single draggable unit) ────

@Composable
private fun AiInsightsSection(
    suggestions: List<AiSuggestion>,
    currencyCode: String,
    onDismissSuggestion: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                "AI Insights",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
        }
        suggestions.take(3).forEach { suggestion ->
            AiSuggestionCard(
                suggestion, currencyCode,
                onDismiss = { onDismissSuggestion(suggestion.id) }
            )
        }
    }
}

@Composable
private fun CategoryBreakdownSection(
    breakdown: Map<String, Double>,
    totalExpenses: Double,
    currencyCode: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Spending by Category",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        CategoryBreakdownCard(breakdown, totalExpenses, currencyCode)
    }
}

@Composable
private fun RecentTransactionsSection(
    transactions: List<Transaction>,
    currencyCode: String,
    onDeleteTransaction: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Recent Transactions",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        if (transactions.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Outlined.ReceiptLong, contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No transactions yet", style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "Add one manually, scan a receipt, or enable SMS/notification tracking",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            transactions.take(10).forEach { transaction ->
                TransactionItem(
                    transaction, currencyCode,
                    onDelete = { onDeleteTransaction(transaction.id) }
                )
            }
        }
    }
}

// ── Individual card composables (unchanged) ──────────────────────────────────

@Composable
fun BalanceSummaryCard(uiState: UiState, currencyCode: String = "USD") {
    var balanceHidden by remember { mutableStateOf(true) }
    val mask = "••••••"
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(colors = listOf(GreenPrimary, GreenDark)),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(24.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Monthly Balance", color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodyMedium)
                    IconButton(
                        onClick = { balanceHidden = !balanceHidden },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            if (balanceHidden) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (balanceHidden) "Show balance" else "Hide balance",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    if (balanceHidden) mask else CurrencyUtils.format(uiState.netBalance, currencyCode),
                    color = Color.White, style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.ArrowDownward, contentDescription = null,
                                tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Income", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                        }
                        Text(
                            if (balanceHidden) mask else CurrencyUtils.format(uiState.monthlyIncome, currencyCode),
                            color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.ArrowUpward, contentDescription = null,
                                tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Expenses", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                        }
                        Text(
                            if (balanceHidden) mask else CurrencyUtils.format(uiState.monthlyExpenses, currencyCode),
                            color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun QuickStatsRow(uiState: UiState, currencyCode: String = "USD") {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatCard("Today", CurrencyUtils.formatCompact(uiState.todayExpenses, currencyCode),
            Icons.Filled.Today, BluePrimary, Modifier.weight(1f))
        StatCard("This Week", CurrencyUtils.formatCompact(uiState.weeklyExpenses, currencyCode),
            Icons.Filled.DateRange, PurpleAccent, Modifier.weight(1f))
        StatCard("Transactions", "${uiState.transactionCount}",
            Icons.Filled.Receipt, OrangeWarning, Modifier.weight(1f))
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier, shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(14.dp)) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(title, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun WeeklySpendingChart(data: List<Pair<String, Double>>, currencyCode: String = "USD") {
    val sym = CurrencyUtils.symbolFor(currencyCode)
    val todayLabel = remember { DateUtils.formatDay(System.currentTimeMillis()) }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("This Week", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(12.dp))
            val maxValue = data.maxOfOrNull { it.second } ?: 1.0
            Row(
                modifier = Modifier.fillMaxWidth().height(150.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                data.forEach { (day, amount) ->
                    val isToday = day == todayLabel
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    ) {
                        // Push content to the bottom so bars grow upward
                        Spacer(modifier = Modifier.weight(1f))
                        if (amount > 0) {
                            Text(
                                "$sym${String.format("%.0f", amount)}",
                                fontSize = 9.sp,
                                color = if (isToday) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        val barHeight = if (maxValue > 0) (amount / maxValue * 100).coerceAtLeast(6.0) else 6.0
                        val cornerDp = kotlin.math.min(6.0, barHeight / 2.0).dp
                        Box(
                            modifier = Modifier
                                .width(28.dp).height(barHeight.dp)
                                .clip(RoundedCornerShape(topStart = cornerDp, topEnd = cornerDp))
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = if (isToday) listOf(GreenLight, GreenPrimary)
                                                 else listOf(GreenPrimary.copy(alpha = 0.55f), GreenPrimary.copy(alpha = 0.35f))
                                    )
                                )
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            day, fontSize = 11.sp,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            color = if (isToday) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AiSuggestionCard(suggestion: AiSuggestion, currencyCode: String = "USD", onDismiss: () -> Unit) {
    val accentColor = when (suggestion.priority) {
        SuggestionPriority.HIGH -> RedExpense
        SuggestionPriority.MEDIUM -> OrangeWarning
        SuggestionPriority.LOW -> BluePrimary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // Colored accent bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(accentColor, RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp)
            ) {
                // Title row with priority chip and dismiss
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        suggestion.title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = accentColor.copy(alpha = 0.15f),
                        modifier = Modifier.padding(horizontal = 6.dp)
                    ) {
                        Text(
                            when (suggestion.priority) {
                                SuggestionPriority.HIGH -> "High"
                                SuggestionPriority.MEDIUM -> "Med"
                                SuggestionPriority.LOW -> "Low"
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = accentColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Dismiss",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                // Description
                Text(
                    suggestion.description,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                // Saving amount
                if (suggestion.potentialSaving > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.TrendingDown,
                            contentDescription = null,
                            tint = GreenIncome,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Save ${CurrencyUtils.format(suggestion.potentialSaving, currencyCode)}/mo",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            color = GreenIncome
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryBreakdownCard(breakdown: Map<String, Double>, total: Double, currencyCode: String = "USD") {
    val colors = listOf(
        Color(0xFFE91E63), Color(0xFF2196F3), Color(0xFF9C27B0),
        Color(0xFFFF9800), Color(0xFF4CAF50), Color(0xFF607D8B),
        Color(0xFFF44336), Color(0xFF3F51B5), Color(0xFF795548)
    )
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            breakdown.entries.take(6).forEachIndexed { index, (category, amount) ->
                val percentage = if (total > 0) amount / total else 0.0
                val color = colors[index % colors.size]
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(category, modifier = Modifier.weight(1f), fontSize = 13.sp)
                    Text("${(percentage * 100).toInt()}%", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(CurrencyUtils.format(amount, currencyCode), fontWeight = FontWeight.Medium, fontSize = 13.sp)
                }
                LinearProgressIndicator(
                    progress = { percentage.toFloat() },
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = color, trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

@Composable
fun TransactionItem(
    transaction: Transaction,
    currencyCode: String = "USD",
    onDelete: () -> Unit
) {
    var showDetail by remember { mutableStateOf(false) }
    val isExpense = transaction.type == TransactionType.EXPENSE
    // Show a small exchange icon when this transaction was auto-converted from another currency
    val isConverted = transaction.originalAmount > 0.0 && transaction.originalCurrencyCode.orEmpty().isNotEmpty()
    Card(
        modifier = Modifier.fillMaxWidth().clickable { showDetail = true },
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isExpense) RedExpense.copy(alpha = 0.1f) else GreenIncome.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    when (transaction.source) {
                        TransactionSource.OCR_SCAN -> Icons.Filled.CameraAlt
                        TransactionSource.SMS -> Icons.Filled.Sms
                        TransactionSource.NOTIFICATION -> Icons.Filled.Notifications
                        else -> if (isExpense) Icons.Filled.ShoppingCart else Icons.Filled.AccountBalance
                    },
                    contentDescription = null,
                    tint = if (isExpense) RedExpense else GreenIncome,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(transaction.description, fontWeight = FontWeight.Medium, fontSize = 14.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(transaction.category, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(" · ", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(DateUtils.formatShortDate(transaction.timestamp), fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (transaction.source != TransactionSource.MANUAL) {
                        Text(" · ", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(transaction.source.name.lowercase().replace("_", " "),
                            fontSize = 11.sp, color = BluePrimary)
                    }
                    if (isConverted) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Filled.CurrencyExchange,
                            contentDescription = "Currency converted",
                            modifier = Modifier.size(11.dp),
                            tint = OrangeWarning
                        )
                    }
                }
            }
            Text(
                "${if (isExpense) "−" else "+"}${CurrencyUtils.format(transaction.amount, currencyCode)}",
                fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                color = if (isExpense) RedExpense else GreenIncome
            )
        }
    }

    if (showDetail) {
        TransactionDetailDialog(
            transaction = transaction,
            currencyCode = currencyCode,
            onDismiss = { showDetail = false },
            onDelete = { onDelete(); showDetail = false }
        )
    }
}
