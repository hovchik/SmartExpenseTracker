package com.flowsense.app.ui.screens

import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.flowsense.app.data.model.StoreLocation
import com.flowsense.app.data.model.Transaction
import com.flowsense.app.data.model.TransactionSource
import com.flowsense.app.data.model.TransactionType
import com.flowsense.app.data.model.currencyInfoFor
import com.flowsense.app.ui.theme.*
import com.flowsense.app.util.CurrencyUtils
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
    onUpdateStoreLocation: (StoreLocation) -> Unit,
    onClearAllStoreLocations: () -> Unit,
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
        filteredTransactions.filter { it.hasLocation }
    }

    // Default map centre – adjusts to markers if available
    val defaultCenter = run {
        val allLats = storeLocations.map { it.latitude } +
                geoTaggedTransactions.mapNotNull { it.resolvedLat }
        val allLngs = storeLocations.map { it.longitude } +
                geoTaggedTransactions.mapNotNull { it.resolvedLng }
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
                isDraggable = true
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
                subDescription = "Hold & drag to move"
                icon = createModernPinDrawable(
                    mapView,
                    if (txCount > 0) 0xFFE91E63.toInt() else 0xFF9E9E9E.toInt(),
                    if (txCount > 0) 0xFFC2185B.toInt() else 0xFF757575.toInt()
                )

                setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                    override fun onMarkerDragStart(marker: Marker) {}
                    override fun onMarkerDrag(marker: Marker) {}
                    override fun onMarkerDragEnd(marker: Marker) {
                        val newPos = marker.position
                        onUpdateStoreLocation(
                            store.copy(
                                latitude = newPos.latitude,
                                longitude = newPos.longitude
                            )
                        )
                    }
                })

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
            val txLat = tx.resolvedLat ?: return@forEach
            val txLng = tx.resolvedLng ?: return@forEach
            val coord = "$txLat,$txLng"
            if (coord in storeCoords) return@forEach

            val isExpense = tx.type == TransactionType.EXPENSE
            val pinColor = if (isExpense) 0xFFEF4444.toInt() else 0xFF10B981.toInt()
            val pinDarkColor = if (isExpense) 0xFFDC2626.toInt() else 0xFF059669.toInt()

            val marker = Marker(mapView).apply {
                position = GeoPoint(txLat, txLng)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = tx.merchantName.ifBlank { tx.description.take(30) }
                snippet = buildString {
                    val sign = if (isExpense) "-" else "+"
                    append("$sign${CurrencyUtils.format(tx.amount, currencyCode)}")
                    if (tx.originalAmount > 0.0 && tx.originalCurrencyCode.orEmpty().isNotEmpty()) {
                        val origSym = currencyInfoFor(tx.originalCurrencyCode.orEmpty()).symbol
                        append(" ($origSym${String.format("%.2f", tx.originalAmount)} ${tx.originalCurrencyCode.orEmpty()})")
                    }
                    append("\n${tx.category} · ${dateFormatter.format(Date(tx.timestamp))}")
                    val srcLabel = when (tx.source) {
                        TransactionSource.SMS -> "SMS"
                        TransactionSource.NOTIFICATION -> "Notification"
                        TransactionSource.OCR_SCAN -> "OCR"
                        TransactionSource.MANUAL -> "Manual"
                        TransactionSource.IMPORT -> "Imported"
                        TransactionSource.VOICE -> "Voice"
                    }
                    append(" · $srcLabel")
                }
                icon = createModernPinDrawable(mapView, pinColor, pinDarkColor)

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
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Store Map", fontWeight = FontWeight.Bold)
                        if (storeLocations.isNotEmpty() || geoTaggedTransactions.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    "${storeLocations.size + geoTaggedTransactions.size}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                },
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
                            Icon(
                                if (showFilters) Icons.Filled.FilterListOff else Icons.Filled.FilterList,
                                contentDescription = "Filter transactions"
                            )
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
            AnimatedVisibility(
                visible = showFilters,
                enter = expandVertically(
                    animationSpec = spring(stiffness = Spring.StiffnessMedium)
                ) + fadeIn(),
                exit = shrinkVertically(
                    animationSpec = spring(stiffness = Spring.StiffnessMedium)
                ) + fadeOut()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(0.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                            .animateContentSize()
                    ) {
                        // Row 1: Type filter
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(BluePrimary.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.SwapVert, null,
                                    modifier = Modifier.size(16.dp),
                                    tint = BluePrimary
                                )
                            }
                            FilterChip(
                                selected = filterType == null,
                                onClick = { filterType = null },
                                label = { Text("All", fontSize = 12.sp) },
                                leadingIcon = if (filterType == null) {
                                    { Icon(Icons.Filled.Check, null, Modifier.size(14.dp)) }
                                } else null,
                                shape = RoundedCornerShape(10.dp)
                            )
                            FilterChip(
                                selected = filterType == TransactionType.EXPENSE,
                                onClick = { filterType = if (filterType == TransactionType.EXPENSE) null else TransactionType.EXPENSE },
                                label = { Text("Expense", fontSize = 12.sp) },
                                leadingIcon = if (filterType == TransactionType.EXPENSE) {
                                    { Icon(Icons.Filled.Check, null, Modifier.size(14.dp)) }
                                } else null,
                                shape = RoundedCornerShape(10.dp)
                            )
                            FilterChip(
                                selected = filterType == TransactionType.INCOME,
                                onClick = { filterType = if (filterType == TransactionType.INCOME) null else TransactionType.INCOME },
                                label = { Text("Income", fontSize = 12.sp) },
                                leadingIcon = if (filterType == TransactionType.INCOME) {
                                    { Icon(Icons.Filled.Check, null, Modifier.size(14.dp)) }
                                } else null,
                                shape = RoundedCornerShape(10.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Row 2: Source filter (horizontally scrollable)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(PurpleAccent.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.Sensors, null,
                                    modifier = Modifier.size(16.dp),
                                    tint = PurpleAccent
                                )
                            }
                            FilterChip(
                                selected = filterSource == null,
                                onClick = { filterSource = null },
                                label = { Text("All", fontSize = 12.sp) },
                                leadingIcon = if (filterSource == null) {
                                    { Icon(Icons.Filled.Check, null, Modifier.size(14.dp)) }
                                } else null,
                                shape = RoundedCornerShape(10.dp)
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
                                    label = { Text(label, fontSize = 12.sp) },
                                    leadingIcon = if (filterSource == src) {
                                        { Icon(Icons.Filled.Check, null, Modifier.size(14.dp)) }
                                    } else null,
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Row 3: Category filter + result count + clear
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(OrangeWarning.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.Category, null,
                                    modifier = Modifier.size(16.dp),
                                    tint = OrangeWarning
                                )
                            }
                            Box {
                                OutlinedButton(
                                    onClick = { showCategoryDropdown = true },
                                    shape = RoundedCornerShape(10.dp),
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
                                        onClick = { filterCategory = ""; showCategoryDropdown = false },
                                        leadingIcon = { Icon(Icons.Filled.SelectAll, null, Modifier.size(18.dp)) }
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
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    "${geoTaggedTransactions.size} on map",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            if (activeFilterCount > 0) {
                                FilledTonalButton(
                                    onClick = { filterType = null; filterSource = null; filterCategory = "" },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Icon(Icons.Filled.ClearAll, null, Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Clear", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            // ── Map card ──────────────────────────────────────────────
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.55f)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
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

                    // Map controls: re-center + add
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (storeLocations.isNotEmpty() || geoTaggedTransactions.isNotEmpty()) {
                            FilledTonalIconButton(
                                onClick = {
                                    mapViewRef?.controller?.animateTo(defaultCenter)
                                },
                                modifier = Modifier.size(38.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    Icons.Filled.MyLocation,
                                    contentDescription = "Center map",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Compact FAB on the map
                    FloatingActionButton(
                        onClick = {
                            val center = mapViewRef?.mapCenter
                            pendingLat = center?.latitude ?: defaultCenter.latitude
                            pendingLng = center?.longitude ?: defaultCenter.longitude
                            showAddDialog = true
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp)
                            .size(48.dp),
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Filled.AddLocation, contentDescription = "Add store location")
                    }
                }
            }

            // ── Bottom content area ──────────────────────────────────────
            // Shows selected detail OR store/transaction list
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.45f)
            ) {
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
                        onUpdate = onUpdateStoreLocation,
                        modifier = Modifier.fillMaxSize()
                    )
                } ?: selectedTransaction?.let { tx ->
                    TransactionMapDetailPanel(
                        transaction = tx,
                        currencyCode = currencyCode,
                        onDismiss = { selectedTransactionId = null },
                        modifier = Modifier.fillMaxWidth()
                    )
                } ?: run {
                    // Default: show list of stores + recent geo-tagged transactions
                    StoreAndTransactionList(
                        storeLocations = storeLocations,
                        geoTaggedTransactions = geoTaggedTransactions,
                        transactionsByMerchant = transactionsByMerchant,
                        currencyCode = currencyCode,
                        dateFormatter = dateFormatter,
                        onStoreClick = { storeId ->
                            selectedStoreId = storeId
                            selectedTransactionId = null
                            // Pan to store
                            storeLocations.find { it.id == storeId }?.let { store ->
                                mapViewRef?.controller?.animateTo(
                                    GeoPoint(store.latitude, store.longitude)
                                )
                            }
                        },
                        onTransactionClick = { txId ->
                            selectedTransactionId = txId
                            selectedStoreId = null
                            allTransactions.find { it.id == txId }?.let { tx ->
                                if (tx.hasLocation) {
                                    mapViewRef?.controller?.animateTo(
                                        GeoPoint(tx.resolvedLat!!, tx.resolvedLng!!)
                                    )
                                }
                            }
                        },
                        onAddStore = {
                            val center = mapViewRef?.mapCenter
                            pendingLat = center?.latitude ?: defaultCenter.latitude
                            pendingLng = center?.longitude ?: defaultCenter.longitude
                            showAddDialog = true
                        },
                        onClearAllStoreLocations = onClearAllStoreLocations
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

// ─── Helper: modern teardrop pin bitmap with gradient ─────────────────

private fun createModernPinDrawable(
    mapView: MapView,
    color: Int,
    darkColor: Int
): BitmapDrawable {
    val size = 64
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)

    // Shadow
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = AndroidColor.argb(60, 0, 0, 0)
        style = Paint.Style.FILL
        maskFilter = android.graphics.BlurMaskFilter(4f, android.graphics.BlurMaskFilter.Blur.NORMAL)
    }
    canvas.drawCircle(size / 2f + 1f, size / 2f - 6f + 2f, size / 3f, shadowPaint)

    // Teardrop body using a path
    val path = Path().apply {
        val cx = size / 2f
        val r = size / 3f
        val tipY = size.toFloat() - 4f
        // Arc for the circle portion
        addArc(cx - r, cx - r - 8f, cx + r, cx + r - 8f, -30f, -120f)
        // Lines to the tip
        lineTo(cx, tipY)
        close()
    }

    // Gradient fill
    val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        shader = LinearGradient(
            size / 2f, 0f, size / 2f, size.toFloat(),
            color, darkColor, Shader.TileMode.CLAMP
        )
    }
    canvas.drawCircle(size / 2f, size / 2f - 8f, size / 3f, gradientPaint)
    canvas.drawPath(path, gradientPaint)

    // White border
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = AndroidColor.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }
    canvas.drawCircle(size / 2f, size / 2f - 8f, size / 3f, borderPaint)

    // Inner white dot
    val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = AndroidColor.WHITE
        style = Paint.Style.FILL
    }
    canvas.drawCircle(size / 2f, size / 2f - 8f, size / 7f, dotPaint)

    return BitmapDrawable(mapView.resources, bmp)
}

// ─── Bottom content: individual transaction detail ───────────────────

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

    Column(modifier = modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
        // Header row with close
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(amountColor.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isExpense) Icons.Outlined.ShoppingCart else Icons.Outlined.AccountBalanceWallet,
                    contentDescription = null,
                    tint = amountColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
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

        Spacer(modifier = Modifier.height(12.dp))

        // Amount + category
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${if (isExpense) "-" else "+"}${CurrencyUtils.format(transaction.amount, currencyCode)}",
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = amountColor
            )
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = amountColor.copy(alpha = 0.1f)
            ) {
                Text(
                    transaction.category,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = amountColor,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }

        // Foreign currency info
        if (transaction.originalAmount > 0.0 && transaction.originalCurrencyCode.orEmpty().isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            val origSym = currencyInfoFor(transaction.originalCurrencyCode.orEmpty()).symbol
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.CurrencyExchange, null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Original: ${origSym}${String.format("%.2f", transaction.originalAmount)} ${transaction.originalCurrencyCode.orEmpty()}" +
                            " · Rate: ${String.format("%.4f", transaction.exchangeRate)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Date + source row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
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

// ─── Bottom panel: transaction details for a store ───────────────────

@Composable
private fun StoreDetailPanel(
    store: StoreLocation,
    transactions: List<Transaction>,
    currencyCode: String,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onUpdate: (StoreLocation) -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.US) }
    val totalExpenses = transactions
        .filter { it.type == TransactionType.EXPENSE }
        .sumOf { it.amount }
    val totalIncome = transactions
        .filter { it.type == TransactionType.INCOME }
        .sumOf { it.amount }

    var historyExpanded by remember { mutableStateOf(true) }
    var editingCoords by remember { mutableStateOf(false) }
    var editLat by remember(store.id) { mutableStateOf(String.format("%.6f", store.latitude)) }
    var editLng by remember(store.id) { mutableStateOf(String.format("%.6f", store.longitude)) }

    Column(modifier = modifier) {
        // Header with gradient accent
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            GreenPrimary.copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    )
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(GreenPrimary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Store,
                        contentDescription = null,
                        tint = GreenPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        store.merchantName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    if (store.address.isNotBlank()) {
                        Text(
                            store.address,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(
                    onClick = { editingCoords = !editingCoords },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Filled.Edit,
                        "Edit location",
                        tint = if (editingCoords) MaterialTheme.colorScheme.tertiary
                               else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Outlined.DeleteOutline, "Delete store",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Close, "Close", modifier = Modifier.size(20.dp))
                }
            }
        }

        // Editable coordinates section
        AnimatedVisibility(
            visible = editingCoords,
            enter = expandVertically(
                animationSpec = spring(stiffness = Spring.StiffnessMedium)
            ) + fadeIn(),
            exit = shrinkVertically(
                animationSpec = spring(stiffness = Spring.StiffnessMedium)
            ) + fadeOut()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = editLat,
                            onValueChange = { editLat = it },
                            label = { Text("Latitude", fontSize = 11.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f),
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                        )
                        OutlinedTextField(
                            value = editLng,
                            onValueChange = { editLng = it },
                            label = { Text("Longitude", fontSize = 11.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f),
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                    ) {
                        TextButton(onClick = {
                            editLat = String.format("%.6f", store.latitude)
                            editLng = String.format("%.6f", store.longitude)
                            editingCoords = false
                        }) {
                            Text("Cancel", fontSize = 12.sp)
                        }
                        val parsedLat = editLat.toDoubleOrNull()
                        val parsedLng = editLng.toDoubleOrNull()
                        val isValid = parsedLat != null && parsedLng != null &&
                            parsedLat in -90.0..90.0 && parsedLng in -180.0..180.0
                        Button(
                            onClick = {
                                if (isValid) {
                                    onUpdate(store.copy(latitude = parsedLat!!, longitude = parsedLng!!))
                                    editingCoords = false
                                }
                            },
                            enabled = isValid,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Filled.Check, null, Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save", fontSize = 12.sp)
                        }
                    }
                    Text(
                        "Tip: You can also drag the pin on the map to move it",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }

        // Stat cards row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MiniStatCard(
                value = "${transactions.size}",
                label = "Transactions",
                icon = Icons.Filled.Receipt,
                color = BluePrimary,
                modifier = Modifier.weight(1f)
            )
            if (totalExpenses > 0) {
                MiniStatCard(
                    value = CurrencyUtils.format(totalExpenses, currencyCode),
                    label = "Spent",
                    icon = Icons.Filled.ArrowUpward,
                    color = RedExpense,
                    modifier = Modifier.weight(1f)
                )
            }
            if (totalIncome > 0) {
                MiniStatCard(
                    value = CurrencyUtils.format(totalIncome, currencyCode),
                    label = "Income",
                    icon = Icons.Filled.ArrowDownward,
                    color = GreenIncome,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Collapsible transaction history header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { historyExpanded = !historyExpanded }
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Transaction History",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Icon(
                if (historyExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (historyExpanded) "Collapse" else "Expand",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp))

        AnimatedVisibility(
            visible = historyExpanded,
            enter = expandVertically(
                animationSpec = spring(stiffness = Spring.StiffnessMedium)
            ) + fadeIn(),
            exit = shrinkVertically(
                animationSpec = spring(stiffness = Spring.StiffnessMedium)
            ) + fadeOut()
        ) {
            if (transactions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.ReceiptLong,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "No matching transactions",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(
                        transactions.sortedByDescending { it.timestamp },
                        key = { it.id }
                    ) { tx ->
                        TransactionHistoryRow(tx, currencyCode, dateFormatter)
                    }
                }
            }
        }
    }
}

// ─── Mini stat card for store detail panel ────────────────────────────

@Composable
private fun MiniStatCard(
    value: String,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.08f)
        )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                value,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─── Transaction row in store detail history ──────────────────────────

@Composable
private fun TransactionHistoryRow(
    tx: Transaction,
    currencyCode: String,
    dateFormatter: SimpleDateFormat
) {
    val isExpense = tx.type == TransactionType.EXPENSE
    val accentColor = if (isExpense) RedExpense else GreenIncome

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(start = 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left accent bar
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(48.dp)
                .clip(RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp))
                .background(accentColor)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(
                tx.description,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${tx.category} · ${dateFormatter.format(Date(tx.timestamp))}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (tx.originalAmount > 0.0 && tx.originalCurrencyCode.orEmpty().isNotEmpty()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.Filled.CurrencyExchange, null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.padding(end = 10.dp)
        ) {
            Text(
                "${if (isExpense) "-" else "+"}${CurrencyUtils.format(tx.amount, currencyCode)}",
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = accentColor
            )
            if (tx.originalAmount > 0.0 && tx.originalCurrencyCode.orEmpty().isNotEmpty()) {
                val origSym = currencyInfoFor(tx.originalCurrencyCode.orEmpty()).symbol
                Text(
                    "${origSym}${String.format("%.2f", tx.originalAmount)} ${tx.originalCurrencyCode.orEmpty()}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ─── Default bottom list: stores + geo-tagged transactions ───────────

@Composable
private fun StoreAndTransactionList(
    storeLocations: List<StoreLocation>,
    geoTaggedTransactions: List<Transaction>,
    transactionsByMerchant: Map<String, List<Transaction>>,
    currencyCode: String,
    dateFormatter: SimpleDateFormat,
    onStoreClick: (String) -> Unit,
    onTransactionClick: (String) -> Unit,
    onAddStore: () -> Unit,
    onClearAllStoreLocations: () -> Unit
) {
    if (storeLocations.isEmpty() && geoTaggedTransactions.isEmpty()) {
        // Empty state
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.LocationOff,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "No pinned stores",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Add your first store location",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                FilledTonalButton(
                    onClick = onAddStore,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.AddLocation, null, Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Store")
                }
            }
        }
    } else {
        var storesExpanded by remember { mutableStateOf(true) }
        var nearbyExpanded by remember { mutableStateOf(true) }
        var showClearStoresDialog by remember { mutableStateOf(false) }

        // Confirmation dialog for clearing all stores
        if (showClearStoresDialog) {
            AlertDialog(
                onDismissRequest = { showClearStoresDialog = false },
                icon = { Icon(Icons.Filled.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                title = { Text("Clear All Stores") },
                text = { Text("Remove all ${storeLocations.size} store locations? This action cannot be undone.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onClearAllStoreLocations()
                            showClearStoresDialog = false
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text("Clear All") }
                },
                dismissButton = {
                    TextButton(onClick = { showClearStoresDialog = false }) { Text("Cancel") }
                }
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Stores section
            if (storeLocations.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { storesExpanded = !storesExpanded }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Store, null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Stores",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                "${storeLocations.size}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(2.dp))
                        IconButton(
                            onClick = { showClearStoresDialog = true },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Filled.Clear,
                                contentDescription = "Clear all stores",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(
                            if (storesExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (storesExpanded) "Collapse" else "Expand",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (storesExpanded) {
                    items(storeLocations, key = { it.id }) { store ->
                    val txCount = transactionsByMerchant[store.merchantName.lowercase()]?.size ?: 0
                    val totalSpent = transactionsByMerchant[store.merchantName.lowercase()]
                        ?.filter { it.type == TransactionType.EXPENSE }
                        ?.sumOf { it.amount } ?: 0.0

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onStoreClick(store.id) },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (txCount > 0) GreenPrimary.copy(alpha = 0.1f)
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.Store, null,
                                    modifier = Modifier.size(18.dp),
                                    tint = if (txCount > 0) GreenPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    store.merchantName,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    if (store.address.isNotBlank()) store.address
                                    else "$txCount transaction${if (txCount != 1) "s" else ""}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (totalSpent > 0) {
                                Text(
                                    CurrencyUtils.format(totalSpent, currencyCode),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = RedExpense
                                )
                            }
                        }
                    }
                }
                }
            }

            // Recent geo-tagged transactions section
            val recentGeoTx = geoTaggedTransactions
                .sortedByDescending { it.timestamp }
                .take(10)
            if (recentGeoTx.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { nearbyExpanded = !nearbyExpanded }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.NearMe, null,
                            modifier = Modifier.size(18.dp),
                            tint = BluePrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Recent Nearby",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = BluePrimary.copy(alpha = 0.1f)
                        ) {
                            Text(
                                "${geoTaggedTransactions.size}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = BluePrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            if (nearbyExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (nearbyExpanded) "Collapse" else "Expand",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (nearbyExpanded) {
                    items(recentGeoTx, key = { it.id }) { tx ->
                        TransactionHistoryRow(tx, currencyCode, dateFormatter)
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(4.dp)) }
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
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(GreenPrimary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.AddLocation,
                    contentDescription = null,
                    tint = GreenPrimary,
                    modifier = Modifier.size(26.dp)
                )
            }
        },
        title = {
            Text(
                "Add Store Location",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.LocationOn, null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "${String.format("%.4f", latitude)}, ${String.format("%.4f", longitude)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

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
                        shape = RoundedCornerShape(12.dp),
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
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(merchantName.trim(), address.trim()) },
                enabled = merchantName.isNotBlank(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.Check, null, Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Store")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        shape = RoundedCornerShape(24.dp)
    )
}
