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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartexpense.tracker.data.model.Category
import com.smartexpense.tracker.data.model.TransactionSource
import com.smartexpense.tracker.data.model.TransactionType
import com.smartexpense.tracker.ui.theme.*
import com.smartexpense.tracker.util.CurrencyUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(
    categories: List<Category>,
    onAdd: (Double, String, String, TransactionType, TransactionSource, String) -> Unit,
    onScanReceipt: () -> Unit,
    onNavigateBack: () -> Unit,
    currencyCode: String = "USD"
) {
    val currencySymbol = CurrencyUtils.symbolFor(currencyCode)
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("") }
    var isExpense by remember { mutableStateOf(true) }
    var merchantName by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

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

        Spacer(modifier = Modifier.height(24.dp))

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

        Spacer(modifier = Modifier.height(24.dp))

        // Scan Receipt Button
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

        // Add Button
        Button(
            onClick = {
                val amountVal = amount.toDoubleOrNull()
                if (amountVal != null && amountVal > 0 && description.isNotEmpty()) {
                    onAdd(
                        amountVal,
                        description,
                        selectedCategory.ifEmpty { "Other" },
                        if (isExpense) TransactionType.EXPENSE else TransactionType.INCOME,
                        TransactionSource.MANUAL,
                        merchantName
                    )
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
