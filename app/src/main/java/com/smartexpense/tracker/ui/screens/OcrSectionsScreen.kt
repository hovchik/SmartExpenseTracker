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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartexpense.tracker.data.model.OcrSection
import com.smartexpense.tracker.data.model.currencyInfoFor
import com.smartexpense.tracker.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrSectionsScreen(
    sections: List<OcrSection>,
    onDeleteSection: (String) -> Unit,
    onClearAll: () -> Unit,
    onGenerateReport: () -> String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var showDeleteConfirm by remember { mutableStateOf<String?>(null) }
    var showClearAllConfirm by remember { mutableStateOf(false) }
    var showReport by remember { mutableStateOf(false) }
    var reportText by remember { mutableStateOf("") }
    var expandedSectionId by remember { mutableStateOf<String?>(null) }
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy  HH:mm", Locale.getDefault()) }

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
                IconButton(onClick = {
                    reportText = onGenerateReport()
                    showReport = true
                }) {
                    Icon(Icons.Filled.Assessment, contentDescription = "Generate Report", tint = GreenPrimary)
                }
                IconButton(onClick = { showClearAllConfirm = true }) {
                    Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear All", tint = RedExpense)
                }
            }
        }

        if (sections.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Inventory2, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No scanned sections yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Scan a receipt and choose \"Save to Sections\"\nto store items and costs here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Total Sections", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${sections.size}", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Total Items", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${sections.sumOf { it.items.size }}", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Grand Total", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val firstCurrency = sections.firstOrNull()?.currencyCode ?: "AMD"
                        val symbol = currencyInfoFor(firstCurrency).symbol
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
                        onClick = {
                            expandedSectionId = if (isExpanded) null else section.id
                        }
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            // Section header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.Receipt, null,
                                    tint = GreenPrimary, modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        section.label,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        dateFormat.format(Date(section.timestamp)),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    "$symbol${String.format("%.2f", section.totalAmount)}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = GreenPrimary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                    null, modifier = Modifier.size(20.dp)
                                )
                            }

                            // Merchant & language info
                            if (section.merchantName != section.label) {
                                Text(
                                    "Merchant: ${section.merchantName}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 34.dp, top = 2.dp)
                                )
                            }
                            if (section.detectedLanguages.isNotBlank()) {
                                Text(
                                    "Languages: ${section.detectedLanguages}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 34.dp)
                                )
                            }

                            // Expanded: show items
                            if (isExpanded) {
                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider()
                                Spacer(modifier = Modifier.height(8.dp))

                                if (section.items.isNotEmpty()) {
                                    Text(
                                        "Items (${section.items.size}):",
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 13.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    section.items.forEach { item ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(item.name, fontSize = 13.sp)
                                                if (item.category.isNotBlank()) {
                                                    Text(
                                                        item.category,
                                                        fontSize = 10.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            Text(
                                                "$symbol${String.format("%.2f", item.price)}",
                                                fontSize = 13.sp, fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))
                                    HorizontalDivider()
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Items sum:", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                        Text(
                                            "$symbol${String.format("%.2f", section.itemsTotal)}",
                                            fontSize = 13.sp, fontWeight = FontWeight.Bold
                                        )
                                    }
                                } else {
                                    Text(
                                        "No individual items detected",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (section.notes.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        "Notes: ${section.notes}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                // Delete button
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

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }

    // Delete single section confirmation
    if (showDeleteConfirm != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Delete Section") },
            text = { Text("Are you sure you want to delete this scanned section? This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteSection(showDeleteConfirm!!)
                    showDeleteConfirm = null
                }) { Text("Delete", color = RedExpense) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) { Text("Cancel") }
            }
        )
    }

    // Clear all confirmation
    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            title = { Text("Clear All Sections") },
            text = { Text("This will permanently delete all ${sections.size} scanned sections. Continue?") },
            confirmButton = {
                TextButton(onClick = {
                    onClearAll()
                    showClearAllConfirm = false
                }) { Text("Clear All", color = RedExpense) }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirm = false }) { Text("Cancel") }
            }
        )
    }

    // Report dialog
    if (showReport) {
        AlertDialog(
            onDismissRequest = { showReport = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Assessment, null, tint = GreenPrimary, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Goods Report")
                }
            },
            text = {
                Column {
                    Text(
                        reportText,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        modifier = Modifier
                            .heightIn(max = 400.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboardManager.setText(AnnotatedString(reportText))
                    Toast.makeText(context, "Report copied to clipboard", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReport = false }) { Text("Close") }
            }
        )
    }
}
