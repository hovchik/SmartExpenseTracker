package com.smartexpense.tracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartexpense.tracker.data.model.Category
import com.smartexpense.tracker.data.model.TransactionSource
import com.smartexpense.tracker.data.model.TransactionType
import com.smartexpense.tracker.ui.theme.*
import com.smartexpense.tracker.ui.viewmodel.OcrParsedData
import com.smartexpense.tracker.util.CurrencyUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(
    categories: List<Category>,
    onAdd: (Double, String, String, TransactionType, TransactionSource, String, String, Long) -> Unit,
    onScanReceipt: () -> Unit,
    onNavigateBack: () -> Unit,
    currencyCode: String = "USD",
    /** Pre-populated OCR data; when non-null the form is filled from the scan result. */
    ocrParsedData: OcrParsedData? = null,
    /** Called after saving when OCR items are present; saves items as a goods section. */
    onSaveItems: ((items: List<Pair<String, Double>>, merchantName: String, totalAmount: Double) -> Unit)? = null
) {
    val currencySymbol = CurrencyUtils.symbolFor(currencyCode)
    val isFromOcr = ocrParsedData != null

    var amount by remember(ocrParsedData) {
        mutableStateOf(if (ocrParsedData != null) String.format("%.2f", ocrParsedData.amount) else "")
    }
    var description by remember(ocrParsedData) {
        mutableStateOf(ocrParsedData?.merchantName ?: "")
    }
    var selectedCategory by remember(ocrParsedData) {
        mutableStateOf(ocrParsedData?.category ?: "")
    }
    var isExpense by remember { mutableStateOf(true) }
    var merchantName by remember(ocrParsedData) {
        mutableStateOf(ocrParsedData?.merchantName ?: "")
    }
    var notes by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    // Date/time selection
    var selectedDateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }
    val dateDisplayFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    val displayDate = dateDisplayFormatter.format(Date(selectedDateMillis))

    // Editable items list — populated from OCR items when available
    var editableItems by remember(ocrParsedData) {
        mutableStateOf(
            ocrParsedData?.items?.mapIndexed { idx, (name, price) ->
                Triple(idx, name, String.format("%.2f", price))
            } ?: emptyList()
        )
    }
    var nextItemId by remember(ocrParsedData) { mutableIntStateOf(ocrParsedData?.items?.size ?: 0) }
    var showItemsSection by remember(ocrParsedData) {
        mutableStateOf(ocrParsedData?.items?.isNotEmpty() == true)
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { selectedDateMillis = it }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                "Add Transaction",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(48.dp))
        }

        // OCR source badge — shown when form is pre-populated from a receipt scan
        if (isFromOcr) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = GreenPrimary.copy(alpha = 0.08f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Receipt, null,
                        tint = GreenPrimary, modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Pre-filled from OCR scan — edit before saving",
                        fontSize = 12.sp,
                        color = GreenPrimary,
                        fontWeight = FontWeight.Medium
                    )
                    if (ocrParsedData?.fromQr == true) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = BluePrimary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                "QR", fontSize = 10.sp, color = BluePrimary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (ocrParsedData?.detectedCurrencyCode?.isNotBlank() == true) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = GreenPrimary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                "${ocrParsedData.currencySymbol} ${ocrParsedData.detectedCurrencyCode}",
                                fontSize = 10.sp, color = GreenPrimary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(if (isFromOcr) 16.dp else 24.dp))

        // Type Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilterChip(
                selected = isExpense,
                onClick = { isExpense = true },
                label = { Text("Expense") },
                leadingIcon = {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                modifier = Modifier.weight(1f),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = RedExpense.copy(alpha = 0.15f),
                    selectedLabelColor = RedExpense
                )
            )
            FilterChip(
                selected = !isExpense,
                onClick = { isExpense = false },
                label = { Text("Income") },
                leadingIcon = {
                    Icon(Icons.Filled.ArrowDownward, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                modifier = Modifier.weight(1f),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = GreenIncome.copy(alpha = 0.15f),
                    selectedLabelColor = GreenIncome
                )
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Amount
        OutlinedTextField(
            value = amount,
            onValueChange = {
                if (it.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                    amount = it
                    showError = false
                }
            },
            label = { Text("Amount") },
            leadingIcon = { Text(currencySymbol, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            isError = showError && amount.isEmpty(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Description
        OutlinedTextField(
            value = description,
            onValueChange = { description = it; showError = false },
            label = { Text("Description") },
            leadingIcon = { Icon(Icons.Filled.Description, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            isError = showError && description.isEmpty(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Date field (tap to open picker)
        OutlinedTextField(
            value = displayDate,
            onValueChange = {},
            label = { Text("Date") },
            leadingIcon = { Icon(Icons.Filled.CalendarToday, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = { showDatePicker = true }) {
                    Icon(Icons.Filled.EditCalendar, contentDescription = "Pick date")
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            readOnly = true,
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Merchant Name
        OutlinedTextField(
            value = merchantName,
            onValueChange = { merchantName = it },
            label = { Text("Merchant (optional)") },
            leadingIcon = { Icon(Icons.Filled.Store, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Category Selection
        Text(
            "Category",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categories.filter {
                if (isExpense) !listOf("Salary", "Freelance", "Investment").contains(it.name)
                else listOf("Salary", "Freelance", "Investment", "Other").contains(it.name)
            }) { category ->
                FilterChip(
                    selected = selectedCategory == category.name,
                    onClick = { selectedCategory = category.name },
                    label = { Text(category.name, fontSize = 12.sp) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Notes
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Notes (optional)") },
            leadingIcon = { Icon(Icons.Filled.Notes, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            minLines = 2,
            maxLines = 3
        )

        Spacer(modifier = Modifier.height(20.dp))

        // ── Goods / Items section ────────────────────────────────
        // Shown automatically when OCR items are present; can also be toggled on manually.
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (showItemsSection)
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                else
                    MaterialTheme.colorScheme.surface
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Inventory2, null,
                        tint = if (showItemsSection) GreenPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Goods / Items" + if (editableItems.isNotEmpty()) " (${editableItems.size})" else "",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    if (showItemsSection) {
                        TextButton(
                            onClick = {
                                editableItems = editableItems + Triple(nextItemId, "", "0.00")
                                nextItemId++
                            },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) {
                            Icon(Icons.Filled.Add, null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Add Item", fontSize = 11.sp)
                        }
                    }
                    Switch(
                        checked = showItemsSection,
                        onCheckedChange = { showItemsSection = it },
                        modifier = Modifier.height(24.dp)
                    )
                }

                if (showItemsSection) {
                    if (ocrParsedData?.isTerminalReceipt == true) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.CreditCard, null,
                                tint = OrangeWarning, modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Bank terminal receipt — no goods detected",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        if (editableItems.isEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "No items yet. Tap \"Add Item\" to add goods.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        } else {
                            Spacer(modifier = Modifier.height(8.dp))
                            editableItems.forEach { (itemId, itemName, itemPrice) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = itemName,
                                        onValueChange = { newName ->
                                            editableItems = editableItems.map {
                                                if (it.first == itemId) Triple(it.first, newName, it.third) else it
                                            }
                                        },
                                        placeholder = { Text("Item name", fontSize = 12.sp) },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(8.dp),
                                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    OutlinedTextField(
                                        value = itemPrice,
                                        onValueChange = { newPrice ->
                                            editableItems = editableItems.map {
                                                if (it.first == itemId) Triple(it.first, it.second, newPrice) else it
                                            }
                                        },
                                        placeholder = { Text("0.00", fontSize = 12.sp) },
                                        singleLine = true,
                                        modifier = Modifier.width(80.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        textStyle = LocalTextStyle.current.copy(
                                            fontSize = 13.sp,
                                            textAlign = TextAlign.End
                                        )
                                    )
                                    IconButton(
                                        onClick = { editableItems = editableItems.filter { it.first != itemId } },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Close, null,
                                            tint = RedExpense, modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                            // Items sum
                            val itemsSum = editableItems.sumOf { it.third.toDoubleOrNull() ?: 0.0 }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "Items sum:",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "$currencySymbol${String.format("%.2f", itemsSum)}",
                                    fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Items will be saved to the Goods Sections for tracking.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Scan Receipt Button — hidden when already pre-filled from OCR
        if (!isFromOcr) {
            OutlinedButton(
                onClick = onScanReceipt,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Filled.CameraAlt, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Scan Receipt Instead")
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Add Button
        Button(
            onClick = {
                val amountVal = amount.toDoubleOrNull()
                if (amountVal != null && amountVal > 0 && description.isNotEmpty()) {
                    val source = if (isFromOcr) TransactionSource.OCR_SCAN else TransactionSource.MANUAL
                    onAdd(
                        amountVal,
                        description,
                        selectedCategory,
                        if (isExpense) TransactionType.EXPENSE else TransactionType.INCOME,
                        source,
                        merchantName,
                        notes,
                        selectedDateMillis
                    )
                    // Save OCR items to goods sections when items are present and it's not a terminal receipt
                    if (showItemsSection && editableItems.isNotEmpty() && ocrParsedData?.isTerminalReceipt != true) {
                        val finalItems = editableItems
                            .filter { it.second.isNotBlank() && it.third.toDoubleOrNull() != null }
                            .map { it.second to it.third.toDouble() }
                        if (finalItems.isNotEmpty()) {
                            onSaveItems?.invoke(finalItems, merchantName.ifBlank { description }, amountVal)
                        }
                    }
                    onNavigateBack()
                } else {
                    showError = true
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isExpense) RedExpense else GreenIncome
            )
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                if (isExpense) "Add Expense" else "Add Income",
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
        }

        if (showError) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Please enter a valid amount and description",
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}
