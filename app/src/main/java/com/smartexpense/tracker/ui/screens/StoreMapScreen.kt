package com.smartexpense.tracker.ui.screens

import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.Bitmap
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.smartexpense.tracker.data.model.StoreLocation
import com.smartexpense.tracker.data.model.Transaction
import com.smartexpense.tracker.data.model.TransactionType
import com.smartexpense.tracker.ui.theme.GreenIncome
import com.smartexpense.tracker.ui.theme.RedExpense
import com.smartexpense.tracker.util.CurrencyUtils
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.infowindow.InfoWindow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Screen that displays store locations on an OpenStreetMap (osmdroid).
 * Each marker represents a store (merchant) where the user has transactions.
 * Tapping a marker shows a bubble with the store's transaction summary.
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
    val context = LocalContext.current

    // Initialise osmdroid configuration once
    LaunchedEffect(Unit) {
        Configuration.getInstance().userAgentValue = context.packageName
    }

    // Default map centre – adjusts to markers if available
    val defaultCenter = if (storeLocations.isNotEmpty()) {
        val avgLat = storeLocations.map { it.latitude }.average()
        val avgLng = storeLocations.map { it.longitude }.average()
        GeoPoint(avgLat, avgLng)
    } else {
        GeoPoint(40.1872, 44.5152) // Default: Yerevan, Armenia
    }

    // Group transactions by merchant name (case-insensitive) for quick lookup
    val transactionsByMerchant: Map<String, List<Transaction>> = remember(allTransactions) {
        allTransactions
            .filter { it.merchantName.isNotBlank() }
            .groupBy { it.merchantName.lowercase() }
    }

    // State for the "add store" dialog
    var showAddDialog by remember { mutableStateOf(false) }
    var pendingLat by remember { mutableDoubleStateOf(0.0) }
    var pendingLng by remember { mutableDoubleStateOf(0.0) }

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

    // Hold a reference to the MapView so we can update markers reactively
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }

    // Update markers whenever storeLocations or transactions change
    LaunchedEffect(storeLocations, transactionsByMerchant, mapViewRef) {
        val mapView = mapViewRef ?: return@LaunchedEffect
        // Close any open info windows and remove old markers (keep the MapEventsOverlay at index 0)
        InfoWindow.closeAllInfoWindowsOn(mapView)
        mapView.overlays.removeAll { it is Marker }

        storeLocations.forEach { store ->
            val merchantTx = transactionsByMerchant[store.merchantName.lowercase()] ?: emptyList()
            val totalSpent = merchantTx
                .filter { it.type == TransactionType.EXPENSE }
                .sumOf { it.amount }
            val txCount = merchantTx.size

            val marker = Marker(mapView).apply {
                position = GeoPoint(store.latitude, store.longitude)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = store.merchantName
                snippet = buildString {
                    if (store.address.isNotBlank()) appendLine(store.address)
                    if (txCount > 0) {
                        appendLine("$txCount transaction${if (txCount != 1) "s" else ""}")
                        append("Total: ${CurrencyUtils.format(totalSpent, currencyCode)}")
                    } else {
                        append("No transactions yet")
                    }
                }
                subDescription = "Tap for details"

                // Create a simple coloured pin icon
                icon = createPinDrawable(mapView, if (txCount > 0) 0xFFE91E63.toInt() else 0xFF9E9E9E.toInt())

                setOnMarkerClickListener { m, _ ->
                    if (m.isInfoWindowShown) {
                        // Second tap on an open info window → show detail panel
                        selectedStoreId = store.id
                        m.closeInfoWindow()
                    } else {
                        InfoWindow.closeAllInfoWindowsOn(mapView)
                        m.showInfoWindow()
                    }
                    true
                }
            }
            mapView.overlays.add(marker)
        }
        mapView.invalidate()
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
            // ── osmdroid MapView ─────────────────────────────────────
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        controller.setZoom(13.0)
                        controller.setCenter(defaultCenter)

                        // Long-press to add a new store
                        val eventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
                            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean = false
                            override fun longPressHelper(p: GeoPoint?): Boolean {
                                p?.let {
                                    pendingLat = it.latitude
                                    pendingLng = it.longitude
                                    showAddDialog = true
                                }
                                return true
                            }
                        })
                        overlays.add(0, eventsOverlay)

                        mapViewRef = this
                    }
                },
                update = { mapView ->
                    // Keep reference up-to-date (for recompositions)
                    mapViewRef = mapView
                }
            )

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

            // FAB to add store at current map centre
            FloatingActionButton(
                onClick = {
                    val center = mapViewRef?.mapCenter
                    pendingLat = center?.latitude ?: defaultCenter.latitude
                    pendingLng = center?.longitude ?: defaultCenter.longitude
                    showAddDialog = true
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Filled.AddLocation, contentDescription = "Add store location")
            }

            // Bottom panel for selected store details
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
    if (showAddDialog) {
        AddStoreDialog(
            latitude = pendingLat,
            longitude = pendingLng,
            availableMerchants = availableMerchants,
            onConfirm = { merchantName, address ->
                onAddStoreLocation(merchantName, pendingLat, pendingLng, address)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }
}

// ─── Helper: simple coloured pin bitmap ──────────────────────────────

private fun createPinDrawable(mapView: MapView, color: Int): BitmapDrawable {
    val size = 48
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Pin body (filled circle)
    paint.color = color
    paint.style = Paint.Style.FILL
    canvas.drawCircle(size / 2f, size / 2f - 6f, size / 3f, paint)

    // Pin border
    paint.color = AndroidColor.WHITE
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 3f
    canvas.drawCircle(size / 2f, size / 2f - 6f, size / 3f, paint)

    // Pin bottom triangle (simple line)
    paint.color = color
    paint.style = Paint.Style.FILL
    paint.strokeWidth = 4f
    canvas.drawLine(size / 2f, size / 2f + 10f, size / 2f, size.toFloat() - 2f, paint)

    return BitmapDrawable(mapView.resources, bmp)
}

// ─── Bottom panel: transaction details for a store ───────────────────

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
    val totalExpenses = transactions
        .filter { it.type == TransactionType.EXPENSE }
        .sumOf { it.amount }
    val totalIncome = transactions
        .filter { it.type == TransactionType.INCOME }
        .sumOf { it.amount }

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

// ─── Add-store dialog ────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddStoreDialog(
    latitude: Double,
    longitude: Double,
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
                    "Location: ${String.format("%.4f", latitude)}, ${String.format("%.4f", longitude)}",
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
