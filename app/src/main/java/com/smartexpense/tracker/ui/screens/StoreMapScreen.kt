package com.smartexpense.tracker.ui.screens

import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import com.smartexpense.tracker.data.model.TransactionSource
import com.smartexpense.tracker.data.model.TransactionType
import com.smartexpense.tracker.data.model.currencyInfoFor
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
 * Screen that displays store locations and geo-tagged transactions on an
 * OpenStreetMap (osmdroid) with filters for type, source, and category.
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

    // ── Filter state ─────────────────────────────────────────────────
    var showFilters by remember { mutableStateOf(false) }
    var filterType by remember { mutableStateOf<TransactionType?>(null) }       // null = All
    var filterSource by remember { mutableStateOf<TransactionSource?>(null) }   // null = All
    var filterCategory by remember { mutableStateOf("") }                       // "" = All
    var showCategoryDropdown by remember { mutableStateOf(false) }

    // Distinct categories from all transactions
    val categories = remember(allTransactions) {
        allTransactions.map { it.category }.distinct().sorted()
    }

    // Apply filters to transactions
    val filteredTransactions = remember(allTransactions, filterType, filterSource, filterCategory) {
        allTransactions.filter { tx ->
            (filterType == null || tx.type == filterType) &&
                (filterSource == null || tx.source == filterSource) &&
                (filterCategory.isEmpty() || tx.category == filterCategory)
        }
    }

    val activeFilterCount = listOfNotNull(filterType, filterSource).size +
        (if (filterCategory.isNotEmpty()) 1 else 0)

    // Transactions that carry a GPS fix
    val geoTaggedTransactions = remember(filteredTransactions) {
        filteredTransactions.filter { it.latitude != null && it.longitude != null }
    }

    // Default map centre – adjusts to markers if available
    val defaultCenter = run {
        val allLats = storeLocations.map { it.latitude } +
                geoTaggedTransactions.mapNotNull { it.latitude }
        val allLngs = storeLocations.map { it.longitude } +
                geoTaggedTransactions.mapNotNull { it.longitude }
        if (allLats.isNotEmpty()) GeoPoint(allLats.average(), allLngs.average())
        else GeoPoint(40.1872, 44.5152) // Default: Yerevan, Armenia
    }

    // Group filtered transactions by merchant name for quick lookup
    val transactionsByMerchant: Map<String, List<Transaction>> = remember(filteredTransactions) {
        filteredTransactions
            .filter { it.merchantName.isNotBlank() }
            .groupBy { it.merchantName.lowercase() }
    }

    // State for the "add store" dialog
    var showAddDialog by remember { mutableStateOf(false) }
    var pendingLat by remember { mutableDoubleStateOf(0.0) }
    var pendingLng by remember { mutableDoubleStateOf(0.0) }

    // State for selected store / transaction detail panel
    var selectedStoreId by remember { mutableStateOf<String?>(null) }
    var selectedTransactionId by remember { mutableStateOf<String?>(null) }
    val selectedStore = storeLocations.find { it.id == selectedStoreId }
    val selectedTransaction = allTransactions.find { it.id == selectedTransactionId }

    // Merchant names that already have a store location
    val existingMerchantNames = remember(storeLocations) {
        storeLocations.map { it.merchantName.lowercase() }.toSet()
    }
    val availableMerchants = remember(transactionsByMerchant, existingMerchantNames) {
        transactionsByMerchant.keys
            .filter { it !in existingMerchantNames }
            .map { key -> transactionsByMerchant[key]?.firstOrNull()?.merchantName ?: key }
            .sorted()
    }

    // Hold a reference to the MapView
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }

    // Update markers whenever data or filters change
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.US) }
    LaunchedEffect(storeLocations, transactionsByMerchant, geoTaggedTransactions, mapViewRef) {
        val mapView = mapViewRef ?: return@LaunchedEffect
        InfoWindow.closeAllInfoWindowsOn(mapView)
        mapView.overlays.removeAll { it is Marker }

        // ── Store-location markers (pink / grey) ─────────────────
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
                        append("No matching transactions")
                    }
                }
                subDescription = "Tap for details"
                icon = createPinDrawable(mapView, if (txCount > 0) 0xFFE91E63.toInt() else 0xFF9E9E9E.toInt())

                setOnMarkerClickListener { m, _ ->
                    if (m.isInfoWindowShown) {
                        selectedStoreId = store.id
                        selectedTransactionId = null
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

        // ── Geo-tagged transaction markers ────────────────────────
        val storeCoords = storeLocations.map { "${it.latitude},${it.longitude}" }.toSet()
        geoTaggedTransactions.forEach { tx ->
            val coord = "${tx.latitude},${tx.longitude}"
            if (coord in storeCoords) return@forEach

            val isExpense = tx.type == TransactionType.EXPENSE
            val pinColor = if (isExpense) 0xFFEF4444.toInt() else 0xFF10B981.toInt()

            val marker = Marker(mapView).apply {
                position = GeoPoint(tx.latitude!!, tx.longitude!!)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = tx.merchantName.ifBlank { tx.description.take(30) }
                snippet = buildString {
                    val sign = if (isExpense) "-" else "+"
                    append("$sign${CurrencyUtils.format(tx.amount, currencyCode)}")
                    // Show original currency if converted
                    if (tx.originalAmount > 0.0 && tx.originalCurrencyCode.isNotEmpty()) {
                        val origSym = currencyInfoFor(tx.originalCurrencyCode).symbol
                        append(" ($origSym${String.format("%.2f", tx.originalAmount)} ${tx.originalCurrencyCode})")
                    }
                    append("\n${tx.category} · ${dateFormatter.format(Date(tx.timestamp))}")
                    val srcLabel = when (tx.source) {
                        TransactionSource.SMS -> "SMS"
                        TransactionSource.NOTIFICATION -> "Notification"
                        TransactionSource.OCR_SCAN -> "OCR"
                        TransactionSource.MANUAL -> "Manual"
                        TransactionSource.IMPORT -> "Imported"
                    }
                    append(" · $srcLabel")
                }
                icon = createPinDrawable(mapView, pinColor)

                setOnMarkerClickListener { m, _ ->
                    if (m.isInfoWindowShown) {
                        selectedTransactionId = tx.id
                        selectedStoreId = null
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
                },
                actions = {
                    BadgedBox(
                        badge = {
                            if (activeFilterCount > 0) {
                                Badge { Text("$activeFilterCount") }
                            }
                        }
                    ) {
                        IconButton(onClick = { showFilters = !showFilters }) {
                            Icon(Icons.Filled.FilterList, contentDescription = "Filter transactions")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Filter bar ───────────────────────────────────────────
            AnimatedVisibility(visible = showFilters) {
                Surface(
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        // Row 1: Type filter
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Filled.SwapVert, null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Type:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FilterChip(
                                selected = filterType == null,
                                onClick = { filterType = null },
                                label = { Text("All", fontSize = 11.sp) },
                                leadingIcon = if (filterType == null) {{ Icon(Icons.Filled.Check, null, Modifier.size(14.dp)) }} else null
                            )
                            FilterChip(
                                selected = filterType == TransactionType.EXPENSE,
                                onClick = { filterType = if (filterType == TransactionType.EXPENSE) null else TransactionType.EXPENSE },
                                label = { Text("Expense", fontSize = 11.sp) },
                                leadingIcon = if (filterType == TransactionType.EXPENSE) {{ Icon(Icons.Filled.Check, null, Modifier.size(14.dp)) }} else null
                            )
                            FilterChip(
                                selected = filterType == TransactionType.INCOME,
                                onClick = { filterType = if (filterType == TransactionType.INCOME) null else TransactionType.INCOME },
                                label = { Text("Income", fontSize = 11.sp) },
                                leadingIcon = if (filterType == TransactionType.INCOME) {{ Icon(Icons.Filled.Check, null, Modifier.size(14.dp)) }} else null
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Row 2: Source filter (horizontally scrollable)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Filled.Sensors, null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Source:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FilterChip(
                                selected = filterSource == null,
                                onClick = { filterSource = null },
                                label = { Text("All", fontSize = 11.sp) },
                                leadingIcon = if (filterSource == null) {{ Icon(Icons.Filled.Check, null, Modifier.size(14.dp)) }} else null
                            )
                            val sources = listOf(
                                TransactionSource.SMS to "SMS",
                                TransactionSource.NOTIFICATION to "Notification",
                                TransactionSource.MANUAL to "Manual",
                                TransactionSource.OCR_SCAN to "OCR"
                            )
                            sources.forEach { (src, label) ->
                                FilterChip(
                                    selected = filterSource == src,
                                    onClick = { filterSource = if (filterSource == src) null else src },
                                    label = { Text(label, fontSize = 11.sp) },
                                    leadingIcon = if (filterSource == src) {{ Icon(Icons.Filled.Check, null, Modifier.size(14.dp)) }} else null
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Row 3: Category filter + result count
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.Category, null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Box {
                                OutlinedButton(
                                    onClick = { showCategoryDropdown = true },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        if (filterCategory.isEmpty()) "All Categories" else filterCategory,
                                        fontSize = 12.sp, maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Icon(Icons.Filled.ArrowDropDown, null, Modifier.size(16.dp))
                                }
                                DropdownMenu(
                                    expanded = showCategoryDropdown,
                                    onDismissRequest = { showCategoryDropdown = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("All Categories", fontWeight = FontWeight.Medium) },
                                        onClick = { filterCategory = ""; showCategoryDropdown = false }
                                    )
                                    HorizontalDivider()
                                    categories.forEach { cat ->
                                        DropdownMenuItem(
                                            text = { Text(cat) },
                                            onClick = { filterCategory = cat; showCategoryDropdown = false }
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    "${geoTaggedTransactions.size} on map",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            if (activeFilterCount > 0) {
                                TextButton(
                                    onClick = { filterType = null; filterSource = null; filterCategory = "" },
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Text("Clear", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            // ── Map + overlays ───────────────────────────────────────
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        MapView(ctx).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)
                            controller.setZoom(13.0)
                            controller.setCenter(defaultCenter)

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
                    update = { mapView -> mapViewRef = mapView }
                )

                // Hint text when no stores exist
                if (storeLocations.isEmpty() && geoTaggedTransactions.isEmpty()) {
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
                            Icon(Icons.Filled.TouchApp, null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Long-press on the map to add a store location",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                // FAB
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

                // Bottom panel for selected individual transaction
                selectedTransaction?.let { tx ->
                    TransactionMapDetailPanel(
                        transaction = tx,
                        currencyCode = currencyCode,
                        onDismiss = { selectedTransactionId = null },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
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

    paint.color = color
    paint.style = Paint.Style.FILL
    canvas.drawCircle(size / 2f, size / 2f - 6f, size / 3f, paint)

    paint.color = AndroidColor.WHITE
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 3f
    canvas.drawCircle(size / 2f, size / 2f - 6f, size / 3f, paint)

    paint.color = color
    paint.style = Paint.Style.FILL
    paint.strokeWidth = 4f
    canvas.drawLine(size / 2f, size / 2f + 10f, size / 2f, size.toFloat() - 2f, paint)

    return BitmapDrawable(mapView.resources, bmp)
}

// ─── Bottom panel: individual transaction detail on map ──────────────

@Composable
private fun TransactionMapDetailPanel(
    transaction: Transaction,
    currencyCode: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy  HH:mm", Locale.US) }
    val isExpense = transaction.type == TransactionType.EXPENSE
    val amountColor = if (isExpense) RedExpense else GreenIncome

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        transaction.merchantName.ifBlank { transaction.description },
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    if (transaction.merchantName.isNotBlank()) {
                        Text(
                            transaction.description,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Amount + conversion info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${if (isExpense) "-" else "+"}${CurrencyUtils.format(transaction.amount, currencyCode)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = amountColor
                )
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = amountColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        transaction.category,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = amountColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // Foreign currency info
            if (transaction.originalAmount > 0.0 && transaction.originalCurrencyCode.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                val origSym = currencyInfoFor(transaction.originalCurrencyCode).symbol
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.CurrencyExchange, null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Original: ${origSym}${String.format("%.2f", transaction.originalAmount)} ${transaction.originalCurrencyCode}" +
                            " · Rate: ${String.format("%.4f", transaction.exchangeRate)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Date + source
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CalendarToday, null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    dateFormatter.format(Date(transaction.timestamp)),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(12.dp))
                val srcIcon = when (transaction.source) {
                    TransactionSource.SMS -> Icons.Filled.Sms
                    TransactionSource.NOTIFICATION -> Icons.Filled.Notifications
                    TransactionSource.OCR_SCAN -> Icons.Filled.CameraAlt
                    else -> Icons.Filled.Edit
                }
                Icon(srcIcon, null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    transaction.source.name.lowercase().replace("_", " "),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(store.merchantName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    if (store.address.isNotBlank()) {
                        Text(store.address, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.DeleteOutline, "Delete store",
                        tint = MaterialTheme.colorScheme.error)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, "Close")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${transactions.size}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Transactions", style = MaterialTheme.typography.labelSmall)
                }
                if (totalExpenses > 0) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(CurrencyUtils.format(totalExpenses, currencyCode),
                            fontWeight = FontWeight.Bold, fontSize = 18.sp, color = RedExpense)
                        Text("Expenses", style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (totalIncome > 0) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(CurrencyUtils.format(totalIncome, currencyCode),
                            fontWeight = FontWeight.Bold, fontSize = 18.sp, color = GreenIncome)
                        Text("Income", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            if (transactions.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No matching transactions",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(transactions.sortedByDescending { it.timestamp }, key = { it.id }) { tx ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(tx.description, fontWeight = FontWeight.Medium, fontSize = 13.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${tx.category} · ${dateFormatter.format(Date(tx.timestamp))}",
                                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (tx.originalAmount > 0.0 && tx.originalCurrencyCode.isNotEmpty()) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(Icons.Filled.CurrencyExchange, null,
                                            modifier = Modifier.size(12.dp),
                                            tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "${if (tx.type == TransactionType.EXPENSE) "-" else "+"}${CurrencyUtils.format(tx.amount, currencyCode)}",
                                    fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                                    color = if (tx.type == TransactionType.EXPENSE) RedExpense else GreenIncome
                                )
                                if (tx.originalAmount > 0.0 && tx.originalCurrencyCode.isNotEmpty()) {
                                    val origSym = currencyInfoFor(tx.originalCurrencyCode).symbol
                                    Text(
                                        "${origSym}${String.format("%.2f", tx.originalAmount)} ${tx.originalCurrencyCode}",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
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
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        trailingIcon = {
                            if (availableMerchants.isNotEmpty()) {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = showMerchantDropdown)
                            }
                        }
                    )

                    val filteredMerchants = if (merchantName.isBlank()) availableMerchants
                    else availableMerchants.filter { it.lowercase().contains(merchantName.lowercase()) }
                    if (filteredMerchants.isNotEmpty()) {
                        ExposedDropdownMenu(
                            expanded = showMerchantDropdown,
                            onDismissRequest = { showMerchantDropdown = false }
                        ) {
                            filteredMerchants.forEach { name ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = { merchantName = name; showMerchantDropdown = false }
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
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
