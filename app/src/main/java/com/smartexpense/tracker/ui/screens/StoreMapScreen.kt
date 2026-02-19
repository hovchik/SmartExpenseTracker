package com.smartexpense.tracker.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.smartexpense.tracker.data.model.StoreLocation
import com.smartexpense.tracker.data.model.Transaction
import com.smartexpense.tracker.data.model.TransactionType
import com.smartexpense.tracker.ui.theme.GreenIncome
import com.smartexpense.tracker.ui.theme.RedExpense
import com.smartexpense.tracker.util.CurrencyUtils
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Screen that displays store locations on a Google Map.
 * Each marker represents a store (merchant) where the user has transactions.
 * Tapping a marker shows a hint window with the store's transaction summary.
 * Long-pressing the map lets the user add a new store location.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreMapScreen(
    storeLocations: List<StoreLocation>,
    allTransactions: List<Transaction>,
    currencyCode: String,
    onAddStoreLocation: (merchantName: String, latitude: Double, longitude: Double, address: String) -> Unit,
    onDeleteStoreLocation: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val scope = rememberCoroutineScope()

    // Default camera position (centered on a reasonable default; adjusts to markers if available)
    val defaultPosition = if (storeLocations.isNotEmpty()) {
        val avgLat = storeLocations.map { it.latitude }.average()
        val avgLng = storeLocations.map { it.longitude }.average()
        LatLng(avgLat, avgLng)
    } else {
        LatLng(40.1872, 44.5152) // Default: Yerevan, Armenia (matching AMD currency default)
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultPosition, 12f)
    }

    // Group transactions by merchant name (case-insensitive) for quick lookup
    val transactionsByMerchant: Map<String, List<Transaction>> = remember(allTransactions) {
        allTransactions
            .filter { it.merchantName.isNotBlank() }
            .groupBy { it.merchantName.lowercase() }
    }

    // State for the "add store" dialog
    var showAddDialog by remember { mutableStateOf(false) }
    var pendingLatLng by remember { mutableStateOf<LatLng?>(null) }

    // State for selected store detail panel
    var selectedStoreId by remember { mutableStateOf<String?>(null) }
    val selectedStore = storeLocations.find { it.id == selectedStoreId }

    // Merchant names that already have a store location (for the dropdown)
    val existingMerchantNames = remember(storeLocations) {
        storeLocations.map { it.merchantName.lowercase() }.toSet()
    }
    // Merchant names from transactions that don't yet have a store location
    val availableMerchants = remember(transactionsByMerchant, existingMerchantNames) {
        transactionsByMerchant.keys
            .filter { it !in existingMerchantNames }
            .map { key -> transactionsByMerchant[key]?.firstOrNull()?.merchantName ?: key }
            .sorted()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Store Map", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Google Map
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(mapType = MapType.NORMAL),
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = true,
                    myLocationButtonEnabled = false
                ),
                onMapLongClick = { latLng ->
                    pendingLatLng = latLng
                    showAddDialog = true
                }
            ) {
                // Place markers for each store location
                storeLocations.forEach { store ->
                    val position = LatLng(store.latitude, store.longitude)
                    val merchantTransactions = transactionsByMerchant[store.merchantName.lowercase()]
                        ?: emptyList()
                    val totalSpent = merchantTransactions
                        .filter { it.type == TransactionType.EXPENSE }
                        .sumOf { it.amount }
                    val txCount = merchantTransactions.size

                    MarkerInfoWindowContent(
                        state = MarkerState(position = position),
                        title = store.merchantName,
                        onInfoWindowClick = {
                            selectedStoreId = store.id
                        }
                    ) { marker ->
                        // Custom info window content (hint window)
                        StoreInfoWindowContent(
                            storeName = store.merchantName,
                            address = store.address,
                            transactionCount = txCount,
                            totalSpent = totalSpent,
                            currencyCode = currencyCode
                        )
                    }
                }
            }

            // Hint text when no stores exist
            if (storeLocations.isEmpty()) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    tonalElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.TouchApp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Long-press on the map to add a store location",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            // FAB to add store
            FloatingActionButton(
                onClick = {
                    // Use map center as the default location
                    pendingLatLng = cameraPositionState.position.target
                    showAddDialog = true
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Filled.AddLocation, contentDescription = "Add store location")
            }

            // Bottom sheet for selected store details
            selectedStore?.let { store ->
                val merchantTx = transactionsByMerchant[store.merchantName.lowercase()] ?: emptyList()
                StoreDetailPanel(
                    store = store,
                    transactions = merchantTx,
                    currencyCode = currencyCode,
                    onDismiss = { selectedStoreId = null },
                    onDelete = {
                        onDeleteStoreLocation(store.id)
                        selectedStoreId = null
                    },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }

    // Add store dialog
    if (showAddDialog && pendingLatLng != null) {
        AddStoreDialog(
            latLng = pendingLatLng!!,
            availableMerchants = availableMerchants,
            onConfirm = { merchantName, address ->
                onAddStoreLocation(merchantName, pendingLatLng!!.latitude, pendingLatLng!!.longitude, address)
                showAddDialog = false
                pendingLatLng = null
            },
            onDismiss = {
                showAddDialog = false
                pendingLatLng = null
            }
        )
    }
}

/**
 * Custom content for the map marker info window (the "hint" popup).
 */
@Composable
private fun StoreInfoWindowContent(
    storeName: String,
    address: String,
    transactionCount: Int,
    totalSpent: Double,
    currencyCode: String
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                storeName,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (address.isNotBlank()) {
                Text(
                    address,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(4.dp))
            if (transactionCount > 0) {
                Text(
                    "$transactionCount transaction${if (transactionCount != 1) "s" else ""}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Total: ${CurrencyUtils.format(totalSpent, currencyCode)}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = RedExpense
                )
            } else {
                Text(
                    "No transactions yet",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                "Tap for details",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Bottom panel showing detailed transactions for a selected store.
 */
@Composable
private fun StoreDetailPanel(
    store: StoreLocation,
    transactions: List<Transaction>,
    currencyCode: String,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.US) }
    val expenses = transactions.filter { it.type == TransactionType.EXPENSE }
    val income = transactions.filter { it.type == TransactionType.INCOME }
    val totalExpenses = expenses.sumOf { it.amount }
    val totalIncome = income.sumOf { it.amount }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.45f),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        store.merchantName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    if (store.address.isNotBlank()) {
                        Text(
                            store.address,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.DeleteOutline,
                        contentDescription = "Delete store",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }

            // Summary row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${transactions.size}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Transactions", style = MaterialTheme.typography.labelSmall)
                }
                if (totalExpenses > 0) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            CurrencyUtils.format(totalExpenses, currencyCode),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = RedExpense
                        )
                        Text("Expenses", style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (totalIncome > 0) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            CurrencyUtils.format(totalIncome, currencyCode),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = GreenIncome
                        )
                        Text("Income", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // Transaction list
            if (transactions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No transactions from this store",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(
                        transactions.sortedByDescending { it.timestamp },
                        key = { it.id }
                    ) { tx ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    tx.description,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${tx.category} · ${dateFormatter.format(Date(tx.timestamp))}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                "${if (tx.type == TransactionType.EXPENSE) "-" else "+"}${CurrencyUtils.format(tx.amount, currencyCode)}",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = if (tx.type == TransactionType.EXPENSE) RedExpense else GreenIncome
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dialog for adding a new store location.
 * Allows the user to pick a merchant name (from existing transaction merchants
 * or type a custom name) and optionally enter an address.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddStoreDialog(
    latLng: LatLng,
    availableMerchants: List<String>,
    onConfirm: (merchantName: String, address: String) -> Unit,
    onDismiss: () -> Unit
) {
    var merchantName by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var showMerchantDropdown by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Store Location") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Location: ${String.format("%.4f", latLng.latitude)}, ${String.format("%.4f", latLng.longitude)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Merchant name input with dropdown suggestions
                ExposedDropdownMenuBox(
                    expanded = showMerchantDropdown && availableMerchants.isNotEmpty(),
                    onExpandedChange = { showMerchantDropdown = it }
                ) {
                    OutlinedTextField(
                        value = merchantName,
                        onValueChange = {
                            merchantName = it
                            showMerchantDropdown = true
                        },
                        label = { Text("Store / Merchant Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        trailingIcon = {
                            if (availableMerchants.isNotEmpty()) {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = showMerchantDropdown)
                            }
                        }
                    )

                    // Dropdown with merchant suggestions from transactions
                    val filteredMerchants = if (merchantName.isBlank()) {
                        availableMerchants
                    } else {
                        availableMerchants.filter {
                            it.lowercase().contains(merchantName.lowercase())
                        }
                    }
                    if (filteredMerchants.isNotEmpty()) {
                        ExposedDropdownMenu(
                            expanded = showMerchantDropdown,
                            onDismissRequest = { showMerchantDropdown = false }
                        ) {
                            filteredMerchants.forEach { name ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        merchantName = name
                                        showMerchantDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(merchantName.trim(), address.trim()) },
                enabled = merchantName.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
