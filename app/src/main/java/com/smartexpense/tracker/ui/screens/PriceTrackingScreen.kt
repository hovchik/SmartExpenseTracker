package com.smartexpense.tracker.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.smartexpense.tracker.R
import com.smartexpense.tracker.data.model.PriceEntry
import com.smartexpense.tracker.data.model.TrackedItem
import com.smartexpense.tracker.ui.theme.*
import com.smartexpense.tracker.util.CurrencyUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriceTrackingScreen(
    trackedItems: List<TrackedItem>,
    currencyCode: String,
    onAddItem: (name: String, imageUri: String, price: Double, storeName: String) -> Unit,
    onAddPriceEntry: (itemId: String, entry: PriceEntry) -> Unit,
    onDeleteItem: (itemId: String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }
    var expandedItemId by remember { mutableStateOf<String?>(null) }

    // Add-price-entry dialog state
    var addPriceForItemId by remember { mutableStateOf<String?>(null) }
    var newEntryPrice by remember { mutableStateOf("") }
    var newEntryStore by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.price_tracking)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_price_entry))
            }
        }
    ) { padding ->
        if (trackedItems.isEmpty()) {
            // Empty state
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.ShoppingCart,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.no_tracked_items),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.no_tracked_items_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(trackedItems, key = { it.id }) { item ->
                    TrackedItemCard(
                        item = item,
                        currencyCode = currencyCode,
                        isExpanded = expandedItemId == item.id,
                        onToggleExpand = {
                            expandedItemId = if (expandedItemId == item.id) null else item.id
                        },
                        onAddPriceEntry = { addPriceForItemId = item.id },
                        onDelete = { onDeleteItem(item.id) }
                    )
                }
            }
        }
    }

    // ── Add new tracked item dialog ──
    if (showAddDialog) {
        AddTrackedItemDialog(
            currencyCode = currencyCode,
            onDismiss = { showAddDialog = false },
            onConfirm = { name, imageUri, price, store ->
                onAddItem(name, imageUri, price, store)
                showAddDialog = false
            }
        )
    }

    // ── Add price entry dialog ──
    if (addPriceForItemId != null) {
        AlertDialog(
            onDismissRequest = {
                addPriceForItemId = null
                newEntryPrice = ""
                newEntryStore = ""
            },
            title = { Text(stringResource(R.string.add_price_entry)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newEntryPrice,
                        onValueChange = { newEntryPrice = it },
                        label = { Text(stringResource(R.string.enter_price)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newEntryStore,
                        onValueChange = { newEntryStore = it },
                        label = { Text(stringResource(R.string.store_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val price = newEntryPrice.toDoubleOrNull()
                        if (price != null && price > 0) {
                            onAddPriceEntry(
                                addPriceForItemId!!,
                                PriceEntry(
                                    price = price,
                                    storeName = newEntryStore.trim(),
                                    currencyCode = currencyCode
                                )
                            )
                            addPriceForItemId = null
                            newEntryPrice = ""
                            newEntryStore = ""
                        }
                    }
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    addPriceForItemId = null
                    newEntryPrice = ""
                    newEntryStore = ""
                }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun TrackedItemCard(
    item: TrackedItem,
    currencyCode: String,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onAddPriceEntry: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.clickable { onToggleExpand() }) {
            // Main row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = CurrencyUtils.format(item.currentPrice, currencyCode),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (item.priceCount > 0) {
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                imageVector = if (item.hasPriceDrop) Icons.Default.TrendingDown else Icons.Default.TrendingUp,
                                contentDescription = stringResource(R.string.price_trend),
                                modifier = Modifier.size(18.dp),
                                tint = if (item.hasPriceDrop) GreenIncome else RedExpense
                            )
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${stringResource(R.string.lowest_price)}: ${CurrencyUtils.format(item.lowestPrice, currencyCode)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${item.priceCount} ${if (item.priceCount == 1) "entry" else "entries"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline
                )
            }

            // Expanded details
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = stringResource(R.string.price_history),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))

                    if (item.priceEntries.isEmpty()) {
                        Text(
                            text = stringResource(R.string.no_data),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    } else {
                        item.priceEntries.sortedByDescending { it.timestamp }.forEach { entry ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = dateFormat.format(Date(entry.timestamp)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Text(
                                    text = CurrencyUtils.format(entry.price, currencyCode),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = entry.storeName.ifEmpty { "-" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        OutlinedButton(onClick = onAddPriceEntry) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.add_price_entry))
                        }
                        TextButton(
                            onClick = onDelete,
                            colors = ButtonDefaults.textButtonColors(contentColor = RedExpense)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.delete_item))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTrackedItemDialog(
    currencyCode: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, imageUri: String, price: Double, storeName: String) -> Unit
) {
    val context = LocalContext.current
    var itemName by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("") }
    var storeName by remember { mutableStateOf("") }
    var capturedImageUri by remember { mutableStateOf<Uri?>(null) }
    var extractedPrice by remember { mutableStateOf<Double?>(null) }

    // Temp file for camera capture
    val tempFile = remember {
        try {
            File.createTempFile("price_", ".jpg", context.cacheDir).apply { deleteOnExit() }
        } catch (_: Exception) { null }
    }
    val tempUri = remember(tempFile) {
        try {
            tempFile?.let {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it)
            }
        } catch (_: Exception) { null }
    }

    // ML Kit text recognition for price extraction
    val textRecognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    fun extractPriceFromImage(uri: Uri) {
        try {
            val image = InputImage.fromFilePath(context, uri)
            textRecognizer.process(image)
                .addOnSuccessListener { result ->
                    val fullText = result.text
                    // Look for currency patterns: $12.99, 12.99$, USD 12.99, 12,99, etc.
                    val priceRegex = Regex(
                        """(?:[\$\u20AC\u00A3\u00A5])\s*(\d{1,7}[.,]\d{2})|(\d{1,7}[.,]\d{2})\s*(?:[\$\u20AC\u00A3\u00A5])|(?:USD|EUR|GBP)\s*(\d{1,7}[.,]\d{2})"""
                    )
                    val matches = priceRegex.findAll(fullText)
                    val prices = matches.mapNotNull { match ->
                        val raw = (match.groupValues[1].ifEmpty { null }
                            ?: match.groupValues[2].ifEmpty { null }
                            ?: match.groupValues[3].ifEmpty { null })
                            ?: return@mapNotNull null
                        raw.replace(",", ".").toDoubleOrNull()
                    }.toList()

                    if (prices.isNotEmpty()) {
                        // Use the largest price found (likely the total)
                        val bestPrice = prices.max()
                        extractedPrice = bestPrice
                        priceText = String.format("%.2f", bestPrice)
                    }
                    Log.d("PriceTracking", "OCR text: $fullText, found prices: $prices")
                }
                .addOnFailureListener { e ->
                    Log.e("PriceTracking", "OCR failed", e)
                }
        } catch (e: Exception) {
            Log.e("PriceTracking", "Failed to process image", e)
        }
    }

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempUri != null) {
            capturedImageUri = tempUri
            extractPriceFromImage(tempUri)
        }
    }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && tempUri != null) {
            cameraLauncher.launch(tempUri)
        } else {
            Toast.makeText(context, context.getString(R.string.camera_permission_required), Toast.LENGTH_SHORT).show()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.scan_item_price)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = itemName,
                    onValueChange = { itemName = it },
                    label = { Text(stringResource(R.string.enter_item_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                            == PackageManager.PERMISSION_GRANTED
                        ) {
                            if (tempUri != null) cameraLauncher.launch(tempUri)
                        } else {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.take_photo))
                }

                if (extractedPrice != null) {
                    Text(
                        text = "${stringResource(R.string.price_saved)}: ${CurrencyUtils.format(extractedPrice!!, currencyCode)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = GreenIncome
                    )
                }

                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text(stringResource(R.string.enter_price)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = storeName,
                    onValueChange = { storeName = it },
                    label = { Text(stringResource(R.string.store_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val name = itemName.trim()
                    val price = priceText.toDoubleOrNull()
                    if (name.isNotEmpty() && price != null && price > 0) {
                        onConfirm(
                            name,
                            capturedImageUri?.toString() ?: "",
                            price,
                            storeName.trim()
                        )
                    }
                },
                enabled = itemName.isNotBlank() && priceText.toDoubleOrNull()?.let { it > 0 } == true
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
