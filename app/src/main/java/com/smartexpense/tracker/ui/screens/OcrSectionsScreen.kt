package com.smartexpense.tracker.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartexpense.tracker.data.model.GoodsReportItem
import com.smartexpense.tracker.data.model.OcrItem
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
    CUSTOM("Custom", null);

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
    onUpdateSection: (OcrSection) -> Unit,
    onClearAll: () -> Unit,
    onGenerateReport: (sinceTimestamp: Long?) -> String,
    onGetGoodsReportItems: (sinceTimestamp: Long?) -> List<GoodsReportItem>,
    onNavigateBack: () -> Unit,
    onNavigateToScan: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showClearAllConfirm by remember { mutableStateOf(false) }
    // Section being edited (null = no edit dialog shown)
    var editingSection by remember { mutableStateOf<OcrSection?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToScan,
                containerColor = GreenPrimary
            ) {
                Icon(Icons.Filled.CameraAlt, contentDescription = "Scan Receipt")
            }
        }
    ) { scaffoldPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(scaffoldPadding)) {
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
                1 -> ReportsTab(sections, onGenerateReport, onGetGoodsReportItems)
            }
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
                    "Tap the camera button to scan a receipt\nand save items here.",
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
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { editingSection = section },
                                    modifier = Modifier.weight(1f).height(38.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BluePrimary)
                                ) {
                                    Icon(Icons.Filled.Edit, null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Edit", fontSize = 13.sp)
                                }
                                OutlinedButton(
                                    onClick = { showDeleteConfirm = section.id },
                                    modifier = Modifier.weight(1f).height(38.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RedExpense)
                                ) {
                                    Icon(Icons.Filled.Delete, null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Delete", fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(80.dp)) } // space for FAB
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

    // Edit section dialog
    editingSection?.let { section ->
        EditSectionDialog(
            section = section,
            onDismiss = { editingSection = null },
            onSave = { updated ->
                onUpdateSection(updated)
                editingSection = null
            }
        )
    }
}

/**
 * Full-screen dialog for editing a saved OcrSection:
 * label, merchant, total amount, and individual items (name + price).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditSectionDialog(
    section: OcrSection,
    onDismiss: () -> Unit,
    onSave: (OcrSection) -> Unit
) {
    val context = LocalContext.current
    val symbol = currencyInfoFor(section.currencyCode).symbol

    var editLabel by remember { mutableStateOf(section.label) }
    var editMerchant by remember { mutableStateOf(section.merchantName) }
    var editTotal by remember { mutableStateOf(String.format("%.2f", section.totalAmount)) }
    var editNotes by remember { mutableStateOf(section.notes) }

    // Mutable list of items: each entry is Triple(uniqueId, name, priceString)
    var editableItems by remember {
        mutableStateOf(section.items.mapIndexed { idx, item ->
            Triple(idx, item.name, String.format("%.2f", item.price))
        })
    }
    var nextItemId by remember { mutableIntStateOf(section.items.size) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Edit, null, tint = GreenPrimary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Edit Section", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Label
                OutlinedTextField(
                    value = editLabel,
                    onValueChange = { editLabel = it },
                    label = { Text("Label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Merchant
                OutlinedTextField(
                    value = editMerchant,
                    onValueChange = { editMerchant = it },
                    label = { Text("Merchant") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Total amount
                OutlinedTextField(
                    value = editTotal,
                    onValueChange = { editTotal = it },
                    label = { Text("Total Amount ($symbol)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Items header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Items (${editableItems.size}):", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = {
                            editableItems = editableItems + Triple(nextItemId, "", "0.00")
                            nextItemId++
                        },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Filled.Add, null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("Add", fontSize = 12.sp)
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))

                // Editable items
                editableItems.forEach { (itemId, itemName, itemPrice) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = itemName,
                            onValueChange = { newName ->
                                editableItems = editableItems.map {
                                    if (it.first == itemId) Triple(it.first, newName, it.third) else it
                                }
                            },
                            placeholder = { Text("Name", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f).height(46.dp),
                            shape = RoundedCornerShape(8.dp),
                            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        OutlinedTextField(
                            value = itemPrice,
                            onValueChange = { newPrice ->
                                editableItems = editableItems.map {
                                    if (it.first == itemId) Triple(it.first, it.second, newPrice) else it
                                }
                            },
                            placeholder = { Text("0.00", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.width(72.dp).height(46.dp),
                            shape = RoundedCornerShape(8.dp),
                            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, textAlign = TextAlign.End)
                        )
                        IconButton(
                            onClick = { editableItems = editableItems.filter { it.first != itemId } },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Filled.Close, null, tint = RedExpense, modifier = Modifier.size(14.dp))
                        }
                    }
                }

                // Items sum
                if (editableItems.isNotEmpty()) {
                    val itemsSum = editableItems.sumOf { it.third.toDoubleOrNull() ?: 0.0 }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Items sum:", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Text("$symbol${String.format("%.2f", itemsSum)}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Notes
                OutlinedTextField(
                    value = editNotes,
                    onValueChange = { editNotes = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    minLines = 2,
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalItems = editableItems
                        .filter { it.second.isNotBlank() }
                        .map { (_, name, price) ->
                            OcrItem(name = name, price = price.toDoubleOrNull() ?: 0.0)
                        }
                    val updatedSection = section.copy(
                        label = editLabel.ifBlank { editMerchant },
                        merchantName = editMerchant,
                        totalAmount = editTotal.toDoubleOrNull() ?: section.totalAmount,
                        items = finalItems,
                        notes = editNotes
                    )
                    onSave(updatedSection)
                    android.widget.Toast.makeText(context, "Section updated", android.widget.Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportsTab(
    sections: List<OcrSection>,
    onGenerateReport: (sinceTimestamp: Long?) -> String,
    onGetGoodsReportItems: (sinceTimestamp: Long?) -> List<GoodsReportItem>
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var selectedPeriod by remember { mutableStateOf(ReportPeriod.WEEK) }
    var reportText by remember { mutableStateOf<String?>(null) }

    // Custom date range state
    var customStartMillis by remember { mutableStateOf<Long?>(null) }
    var customEndMillis by remember { mutableStateOf<Long?>(null) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    val dateFmt = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    // Compute the effective sinceTimestamp for the selected period
    val sinceTimestamp = remember(selectedPeriod, customStartMillis) {
        if (selectedPeriod == ReportPeriod.CUSTOM) customStartMillis
        else selectedPeriod.sinceTimestamp()
    }

    // Filter sections based on period
    val filteredSections = remember(sections, sinceTimestamp, customEndMillis, selectedPeriod) {
        val since = sinceTimestamp
        if (selectedPeriod == ReportPeriod.CUSTOM && since != null) {
            val end = customEndMillis ?: System.currentTimeMillis()
            sections.filter { it.timestamp in since..end }
        } else if (since != null) {
            sections.filter { it.timestamp >= since }
        } else sections
    }

    // Goods frequency data
    val goodsItems = remember(filteredSections, sinceTimestamp) {
        onGetGoodsReportItems(sinceTimestamp)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Period selector chips
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ReportPeriod.entries.forEach { period ->
                FilterChip(
                    selected = selectedPeriod == period,
                    onClick = { selectedPeriod = period; reportText = null },
                    label = { Text(period.label, fontSize = 11.sp) },
                    modifier = Modifier.height(32.dp)
                )
            }
        }

        // Custom date range pickers
        if (selectedPeriod == ReportPeriod.CUSTOM) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { showStartPicker = true },
                    modifier = Modifier.weight(1f).height(42.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Filled.CalendarMonth, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        if (customStartMillis != null) dateFmt.format(Date(customStartMillis!!)) else "Start Date",
                        fontSize = 12.sp
                    )
                }
                OutlinedButton(
                    onClick = { showEndPicker = true },
                    modifier = Modifier.weight(1f).height(42.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Filled.CalendarMonth, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        if (customEndMillis != null) dateFmt.format(Date(customEndMillis!!)) else "End Date",
                        fontSize = 12.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Date picker dialogs
        if (showStartPicker) {
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = customStartMillis ?: System.currentTimeMillis()
            )
            DatePickerDialog(
                onDismissRequest = { showStartPicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        customStartMillis = datePickerState.selectedDateMillis
                        showStartPicker = false
                        reportText = null
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { showStartPicker = false }) { Text("Cancel") } }
            ) {
                DatePicker(state = datePickerState)
            }
        }
        if (showEndPicker) {
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = customEndMillis ?: System.currentTimeMillis()
            )
            DatePickerDialog(
                onDismissRequest = { showEndPicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        customEndMillis = datePickerState.selectedDateMillis
                        showEndPicker = false
                        reportText = null
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { showEndPicker = false }) { Text("Cancel") } }
            ) {
                DatePicker(state = datePickerState)
            }
        }

        if (filteredSections.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Assessment, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(56.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        if (selectedPeriod == ReportPeriod.CUSTOM && customStartMillis == null)
                            "Select a date range above"
                        else "No scanned data for this period",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                SummaryCard("Total", "$symbol${String.format("%.0f", totalAmount)}", Modifier.weight(1.4f), valueColor = GreenPrimary)
            }

            // ── Goods Spending Diagram (horizontal bar chart) ──
            if (goodsItems.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Top Goods by Spending",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                GoodsBarChart(
                    items = goodsItems.take(8),
                    currencySymbol = symbol,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((goodsItems.take(8).size * 44 + 16).dp)
                        .padding(horizontal = 16.dp)
                )

                // Frequency table
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Purchase Frequency",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        // Header row
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Item", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1.5f))
                            Text("Count", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(45.dp), textAlign = TextAlign.Center)
                            Text("Total", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(80.dp), textAlign = TextAlign.End)
                            Text("Avg", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(70.dp), textAlign = TextAlign.End)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(4.dp))
                        goodsItems.take(15).forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    item.name,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1.5f)
                                )
                                Text(
                                    "${item.count}x",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = BluePrimary,
                                    modifier = Modifier.width(45.dp),
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    "$symbol${String.format("%.0f", item.totalSpent)}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.width(80.dp),
                                    textAlign = TextAlign.End
                                )
                                Text(
                                    "$symbol${String.format("%.0f", item.totalSpent / item.count)}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(70.dp),
                                    textAlign = TextAlign.End
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Generate full report button
            Button(
                onClick = { reportText = onGenerateReport(sinceTimestamp) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
            ) {
                Icon(Icons.Filled.Assessment, null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Generate Full Report", fontWeight = FontWeight.SemiBold)
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
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Text(
                        reportText!!,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(12.dp),
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(80.dp)) // space for FAB
        }
    }
}

/** Horizontal bar chart showing top goods by total spending. */
@Composable
private fun GoodsBarChart(
    items: List<GoodsReportItem>,
    currencySymbol: String,
    modifier: Modifier = Modifier
) {
    val barColors = listOf(
        GreenPrimary, BluePrimary, OrangeWarning, RedExpense,
        Color(0xFF9C27B0), Color(0xFF00BCD4), Color(0xFFFF9800), Color(0xFF607D8B)
    )
    val maxSpent = items.maxOfOrNull { it.totalSpent } ?: 1.0
    val textColor = MaterialTheme.colorScheme.onSurface

    Canvas(modifier = modifier) {
        val barHeight = 28f
        val spacing = 16f
        val labelWidth = size.width * 0.30f  // 30% for labels
        val valueWidth = size.width * 0.18f  // 18% for values on right
        val chartWidth = size.width - labelWidth - valueWidth

        items.forEachIndexed { i, item ->
            val y = i * (barHeight + spacing)
            val barW = (item.totalSpent / maxSpent * chartWidth).toFloat().coerceAtLeast(4f)
            val color = barColors[i % barColors.size]

            // Item name label (left)
            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    this.color = textColor.hashCode()
                    textSize = 28f
                    isAntiAlias = true
                }
                val label = if (item.name.length > 14) item.name.take(13) + ".." else item.name
                drawText(label, 0f, y + barHeight * 0.75f, paint)
            }

            // Bar
            drawRoundRect(
                color = color,
                topLeft = Offset(labelWidth, y),
                size = Size(barW, barHeight),
                cornerRadius = CornerRadius(6f, 6f)
            )

            // Count badge on bar
            if (item.count > 1) {
                drawContext.canvas.nativeCanvas.apply {
                    val paint = android.graphics.Paint().apply {
                        this.color = android.graphics.Color.WHITE
                        textSize = 22f
                        isFakeBoldText = true
                        isAntiAlias = true
                    }
                    if (barW > 50f) {
                        drawText("${item.count}x", labelWidth + 6f, y + barHeight * 0.72f, paint)
                    }
                }
            }

            // Value label (right)
            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    this.color = textColor.hashCode()
                    textSize = 24f
                    isFakeBoldText = true
                    isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.RIGHT
                }
                drawText(
                    "$currencySymbol${String.format("%.0f", item.totalSpent)}",
                    size.width,
                    y + barHeight * 0.75f,
                    paint
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
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
