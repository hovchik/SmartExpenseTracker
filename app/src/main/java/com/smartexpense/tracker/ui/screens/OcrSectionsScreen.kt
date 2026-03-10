package com.smartexpense.tracker.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartexpense.tracker.data.model.OcrSection
import com.smartexpense.tracker.data.model.currencyInfoFor
import com.smartexpense.tracker.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Time period filter options for scanned-goods reports. */
private enum class ReportPeriod(val label: String, val daysBack: Long?) {
    DAY("1 Day", 1),
    WEEK("1 Week", 7),
    MONTH("1 Month", 30),
    YEAR("1 Year", 365),
    ALL("All Time", null);

    fun sinceTimestamp(): Long? {
        val d = daysBack ?: return null
        return System.currentTimeMillis() - d * 24 * 60 * 60 * 1000
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrSectionsScreen(
    sections: List<OcrSection>,
    onDeleteSection: (String) -> Unit,
    onClearAll: () -> Unit,
    onGenerateReport: (sinceTimestamp: Long?) -> String,
    onNavigateBack: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showClearAllConfirm by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                "Scanned Sections",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.weight(1f))
            if (sections.isNotEmpty()) {
                IconButton(onClick = { showClearAllConfirm = true }) {
                    Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear All", tint = RedExpense)
                }
            }
        }

        // Tabs
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Scanned Goods", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) },
                icon = { Icon(Icons.Filled.Inventory2, null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Reports", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) },
                icon = { Icon(Icons.Filled.Assessment, null, modifier = Modifier.size(18.dp)) }
            )
        }

        when (selectedTab) {
            0 -> ScannedGoodsTab(sections, onDeleteSection)
            1 -> ReportsTab(sections, onGenerateReport)
        }
    }

    // Clear all confirmation
    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            title = { Text("Clear All Sections") },
            text = { Text("This will permanently delete all scanned sections. Continue?") },
            confirmButton = {
                TextButton(onClick = { onClearAll(); showClearAllConfirm = false }) {
                    Text("Clear All", color = RedExpense)
                }
            },
            dismissButton = { TextButton(onClick = { showClearAllConfirm = false }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScannedGoodsTab(
    sections: List<OcrSection>,
    onDeleteSection: (String) -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf<String?>(null) }
    var expandedSectionId by remember { mutableStateOf<String?>(null) }
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy  HH:mm", Locale.getDefault()) }

    if (sections.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.Inventory2, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(72.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("No scanned sections yet", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Scan a receipt and choose \"Save to Sections\"\nto store items and costs here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        // Summary card
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = GreenPrimary.copy(alpha = 0.08f))
        ) {
            Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Sections", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${sections.size}", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Items", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${sections.sumOf { it.items.size }}", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Grand Total", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val symbol = currencyInfoFor(sections.first().currencyCode).symbol
                    Text(
                        "$symbol${String.format("%.2f", sections.sumOf { it.totalAmount })}",
                        fontWeight = FontWeight.Bold, fontSize = 20.sp, color = GreenPrimary
                    )
                }
            }
        }

        // Sections list
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(sections.sortedByDescending { it.timestamp }, key = { it.id }) { section ->
                val isExpanded = expandedSectionId == section.id
                val symbol = currencyInfoFor(section.currencyCode).symbol

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    onClick = { expandedSectionId = if (isExpanded) null else section.id }
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Receipt, null, tint = GreenPrimary, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(section.label, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(dateFormat.format(Date(section.timestamp)), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("$symbol${String.format("%.2f", section.totalAmount)}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = GreenPrimary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, modifier = Modifier.size(20.dp))
                        }

                        if (section.merchantName != section.label) {
                            Text("Merchant: ${section.merchantName}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 34.dp, top = 2.dp))
                        }
                        if (section.detectedLanguages.isNotBlank()) {
                            Text("Languages: ${section.detectedLanguages}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 34.dp))
                        }

                        if (isExpanded) {
                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(8.dp))

                            if (section.items.isNotEmpty()) {
                                Text("Items (${section.items.size}):", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                section.items.forEach { item ->
                                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(item.name, fontSize = 13.sp)
                                            if (item.category.isNotBlank()) {
                                                Text(item.category, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                        Text("$symbol${String.format("%.2f", item.price)}", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                HorizontalDivider()
                                Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Items sum:", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    Text("$symbol${String.format("%.2f", section.itemsTotal)}", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Text("No individual items detected", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            if (section.notes.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Notes: ${section.notes}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { showDeleteConfirm = section.id },
                                modifier = Modifier.fillMaxWidth().height(38.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = RedExpense)
                            ) {
                                Icon(Icons.Filled.Delete, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Delete Section", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    // Delete confirmation
    if (showDeleteConfirm != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Delete Section") },
            text = { Text("Are you sure you want to delete this scanned section?") },
            confirmButton = {
                TextButton(onClick = { onDeleteSection(showDeleteConfirm!!); showDeleteConfirm = null }) {
                    Text("Delete", color = RedExpense)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ReportsTab(
    sections: List<OcrSection>,
    onGenerateReport: (sinceTimestamp: Long?) -> String
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var selectedPeriod by remember { mutableStateOf(ReportPeriod.ALL) }
    var reportText by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Period selector chips
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ReportPeriod.entries.forEach { period ->
                FilterChip(
                    selected = selectedPeriod == period,
                    onClick = { selectedPeriod = period; reportText = null },
                    label = { Text(period.label, fontSize = 12.sp) },
                    modifier = Modifier.height(32.dp)
                )
            }
        }

        // Filtered summary
        val filteredSections = remember(sections, selectedPeriod) {
            val since = selectedPeriod.sinceTimestamp()
            if (since != null) sections.filter { it.timestamp >= since } else sections
        }

        if (filteredSections.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Assessment, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(56.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No scanned data for ${selectedPeriod.label.lowercase()}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            val symbol = currencyInfoFor(filteredSections.first().currencyCode).symbol
            val totalAmount = filteredSections.sumOf { it.totalAmount }
            val totalItems = filteredSections.sumOf { it.items.size }

            // Summary cards
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SummaryCard("Receipts", "${filteredSections.size}", Modifier.weight(1f))
                SummaryCard("Items", "$totalItems", Modifier.weight(1f))
                SummaryCard("Total", "$symbol${String.format("%.2f", totalAmount)}", Modifier.weight(1.4f), valueColor = GreenPrimary)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Generate report button
            Button(
                onClick = { reportText = onGenerateReport(selectedPeriod.sinceTimestamp()) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
            ) {
                Icon(Icons.Filled.Assessment, null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Generate ${selectedPeriod.label} Report", fontWeight = FontWeight.SemiBold)
            }

            // Show report text
            if (reportText != null) {
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = {
                        clipboardManager.setText(AnnotatedString(reportText!!))
                        Toast.makeText(context, "Report copied to clipboard", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy Report")
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Text(
                        reportText!!,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(12.dp).fillMaxSize().verticalScroll(rememberScrollState())
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun SummaryCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = valueColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
