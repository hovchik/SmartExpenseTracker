package com.flowsense.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.flowsense.app.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanReceiptScreen(
    onOcrResult: (ocrText: String, qrData: String?) -> Unit,
    onConfirmOcr: (amount: Double, merchantName: String, category: String, items: List<Pair<String, Double>>) -> Unit,
    onClearOcr: () -> Unit,
    onSaveToSection: (label: String, merchantName: String, items: List<Pair<String, Double>>, totalAmount: Double, rawOcrText: String, detectedLanguages: String) -> Unit,
    ocrParsedData: com.flowsense.app.ui.viewmodel.OcrParsedData?,
    categories: List<String>,
    onNavigateBack: () -> Unit,
    onNavigateToSections: () -> Unit,
    lastResult: String?,
    /** Navigate to AddTransactionScreen pre-populated with the current OCR result. */
    onEditAsTransaction: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var isProcessing by remember { mutableStateOf(false) }
    var ocrText by remember { mutableStateOf("") }
    var qrText by remember { mutableStateOf<String?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val tempFile = remember {
        try {
            File.createTempFile("receipt_", ".jpg", context.cacheDir).apply { deleteOnExit() }
        } catch (_: Exception) { null }
    }
    val tempUri = remember(tempFile) {
        try {
            tempFile?.let { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it) }
        } catch (_: Exception) { null }
    }

    // Multi-language Tesseract OCR service — created once per screen lifecycle
    val tesseractOcr = remember { com.flowsense.app.service.ocr.ArmenianOcrService(context) }
    val coroutineScope = rememberCoroutineScope()

    /**
     * Runs OCR using ALL available ML Kit recognizers in parallel,
     * plus Tesseract for Armenian/Russian, plus barcode/QR scanning.
     * QR data is passed alongside OCR text so the ViewModel can use
     * it as a fallback if OCR parsing fails.
     */
    fun processImageMultiLang(uri: Uri) {
        isProcessing = true
        errorMsg = null
        qrText = null

        val image: InputImage
        try {
            image = InputImage.fromFilePath(context, uri)
        } catch (e: Exception) {
            isProcessing = false
            errorMsg = "Error loading image: ${e.message}"
            return
        }

        // Create all ML Kit recognizers.
        val recognizers: List<Pair<String, TextRecognizer>> = listOf(
            "Latin/Cyrillic" to TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS),
            "Chinese" to TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build()),
            "Devanagari" to TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build()),
            "Japanese" to TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build()),
            "Korean" to TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
        )

        val results = mutableMapOf<String, String>()
        // +1 for barcode scanner, +1 for Tesseract multi-lang
        val remaining = AtomicInteger(recognizers.size + 2)
        var qrResult: String? = null

        fun onAllDone() {
            if (remaining.get() > 0) return

            // Prioritize Tesseract results first — it handles Armenian and
            // produces better Cyrillic/CJK output when ML Kit misses characters.
            // Then add ML Kit results, skipping duplicate lines.
            val tesseractKey = "Tesseract"
            val tesseractEntries = results.entries.filter { it.key.contains(tesseractKey) }
            val mlKitEntries = results.entries
                .filter { !it.key.contains(tesseractKey) && it.value.isNotBlank() }
                .sortedByDescending { it.value.length }

            val mergedLines = mutableListOf<String>()
            val seenLines = mutableSetOf<String>()

            // Add Tesseract lines first (best for Armenian, Georgian, Arabic, etc.)
            for ((name, text) in tesseractEntries) {
                if (text.isBlank()) continue
                var addedFromThis = 0
                for (line in text.lines()) {
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty() && seenLines.add(trimmed.lowercase())) {
                        mergedLines.add(trimmed)
                        addedFromThis++
                    }
                }
                Log.d("OCR", "$name: ${text.length} chars, $addedFromThis new lines merged (priority)")
            }

            // Add ML Kit lines, skipping duplicates
            for ((name, text) in mlKitEntries) {
                var addedFromThis = 0
                for (line in text.lines()) {
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty() && seenLines.add(trimmed.lowercase())) {
                        mergedLines.add(trimmed)
                        addedFromThis++
                    }
                }
                Log.d("OCR", "$name recognizer: ${text.length} chars, $addedFromThis new lines merged")
            }
            val merged = mergedLines.joinToString("\n")

            isProcessing = false
            qrText = qrResult

            if (qrResult != null) {
                Log.d("OCR", "QR code detected: ${qrResult!!.take(200)}")
            }

            if (merged.isNotEmpty() || qrResult != null) {
                ocrText = merged
                onOcrResult(merged, qrResult)
            } else {
                errorMsg = "No text or QR code detected. Try a clearer photo with good lighting."
            }

            recognizers.forEach { (_, r) -> try { r.close() } catch (_: Exception) {} }
        }

        // Run barcode/QR scanner in parallel with OCR
        val barcodeScanner = BarcodeScanning.getClient()
        barcodeScanner.process(image)
            .addOnSuccessListener { barcodes ->
                // Collect all QR / barcode raw values, prefer QR_CODE type
                val qrCodes = barcodes.filter { it.format == Barcode.FORMAT_QR_CODE }
                val allCodes = qrCodes.ifEmpty { barcodes }
                val rawValues = allCodes.mapNotNull { it.rawValue }.filter { it.isNotBlank() }
                if (rawValues.isNotEmpty()) {
                    // Join multiple QR codes with newline (rare, but possible)
                    qrResult = rawValues.joinToString("\n")
                }
                try { barcodeScanner.close() } catch (_: Exception) {}
                if (remaining.decrementAndGet() == 0) onAllDone()
            }
            .addOnFailureListener { e ->
                Log.w("OCR", "Barcode scanner failed: ${e.message}")
                try { barcodeScanner.close() } catch (_: Exception) {}
                if (remaining.decrementAndGet() == 0) onAllDone()
            }

        for ((name, recognizer) in recognizers) {
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    synchronized(results) { results[name] = visionText.text }
                    if (remaining.decrementAndGet() == 0) onAllDone()
                }
                .addOnFailureListener { e ->
                    Log.w("OCR", "$name recognizer failed: ${e.message}")
                    synchronized(results) { results[name] = "" }
                    if (remaining.decrementAndGet() == 0) onAllDone()
                }
        }

        // Run Tesseract multi-language OCR in parallel (background coroutine)
        coroutineScope.launch {
            try {
                val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
                val bitmap = android.graphics.ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                }
                val tessText = tesseractOcr.recognizeText(bitmap)
                if (tessText.isNotBlank()) {
                    synchronized(results) { results["Tesseract Multi-Lang"] = tessText }
                    Log.d("OCR", "Tesseract multi-lang: ${tessText.length} chars")
                }
            } catch (e: Exception) {
                Log.w("OCR", "Tesseract OCR failed: ${e.message}")
            }
            if (remaining.decrementAndGet() == 0) onAllDone()
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && tempUri != null) processImageMultiLang(tempUri)
        else isProcessing = false
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) processImageMultiLang(uri)
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && tempUri != null) cameraLauncher.launch(tempUri)
        else if (!granted) Toast.makeText(context, "Camera permission is required", Toast.LENGTH_LONG).show()
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Scan Receipt", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.size(140.dp), shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = GreenPrimary.copy(alpha = 0.1f))
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (isProcessing) CircularProgressIndicator(color = GreenPrimary, modifier = Modifier.size(56.dp))
                else Icon(Icons.Filled.Receipt, null, tint = GreenPrimary, modifier = Modifier.size(64.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            if (isProcessing) "Scanning (Armenian, Russian, Chinese...)" else "Capture or select a receipt image",
            style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "Supports Armenian, Russian, Chinese, Latin, Japanese, Korean, Devanagari",
            style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Camera button
        Button(
            onClick = {
                if (tempUri == null) {
                    Toast.makeText(context, "Cannot create temp file", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                val hasCam = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                if (hasCam) cameraLauncher.launch(tempUri) else permissionLauncher.launch(Manifest.permission.CAMERA)
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp), enabled = !isProcessing
        ) {
            Icon(Icons.Filled.CameraAlt, null); Spacer(modifier = Modifier.width(12.dp))
            Text("Take Photo", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedButton(
            onClick = { galleryLauncher.launch("image/*") },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp), enabled = !isProcessing
        ) {
            Icon(Icons.Filled.Add, null); Spacer(modifier = Modifier.width(12.dp))
            Text("Choose from Gallery", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }

        // Error
        if (errorMsg != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = RedExpense.copy(alpha = 0.1f))
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, null, tint = RedExpense, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(errorMsg ?: "", fontSize = 13.sp)
                }
            }
        }

        // ── Editable review form (shown after OCR parse, before saving) ──
        if (ocrParsedData != null) {
            var editAmount by remember(ocrParsedData) { mutableStateOf(String.format("%.2f", ocrParsedData.amount)) }
            var editMerchant by remember(ocrParsedData) { mutableStateOf(ocrParsedData.merchantName) }
            var editCategory by remember(ocrParsedData) { mutableStateOf(ocrParsedData.category) }
            var categoryExpanded by remember { mutableStateOf(false) }
            // Editable items list — initialized from OCR-parsed items
            var editableItems by remember(ocrParsedData) {
                mutableStateOf(ocrParsedData.items.mapIndexed { idx, (name, price) ->
                    Triple(idx, name, String.format("%.2f", price))
                })
            }
            var nextItemId by remember(ocrParsedData) { mutableIntStateOf(ocrParsedData.items.size) }

            // AI suggestion card — shown when AI understood the receipt
            if (ocrParsedData.aiSuggestion != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = BluePrimary.copy(alpha = 0.08f))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Filled.AutoAwesome, null,
                            tint = BluePrimary, modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                "AI Suggestion",
                                fontWeight = FontWeight.Bold, fontSize = 13.sp,
                                color = BluePrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                ocrParsedData.aiSuggestion,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = GreenPrimary.copy(alpha = 0.06f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Edit, null, tint = GreenPrimary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Review Transaction", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        if (ocrParsedData.fromQr) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = BluePrimary.copy(alpha = 0.15f)
                            ) {
                                Text("QR", fontSize = 10.sp, color = BluePrimary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                        if (ocrParsedData.isTerminalReceipt) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = OrangeWarning.copy(alpha = 0.15f)
                            ) {
                                Text("Terminal", fontSize = 10.sp, color = OrangeWarning,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                        if (ocrParsedData.detectedCurrencyCode.isNotBlank()) {
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
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Edit the details below before saving",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Spacer(modifier = Modifier.height(16.dp))

                    // Amount field
                    OutlinedTextField(
                        value = editAmount,
                        onValueChange = { editAmount = it },
                        label = { Text("Amount (${ocrParsedData.currencySymbol})") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Merchant field
                    OutlinedTextField(
                        value = editMerchant,
                        onValueChange = { editMerchant = it },
                        label = { Text("Merchant / Description") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Category dropdown
                    ExposedDropdownMenuBox(
                        expanded = categoryExpanded,
                        onExpandedChange = { categoryExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = editCategory,
                            onValueChange = { editCategory = it },
                            label = { Text("Category") },
                            singleLine = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            shape = RoundedCornerShape(10.dp)
                        )
                        val filtered = categories.filter { it.contains(editCategory, ignoreCase = true) }
                        if (filtered.isNotEmpty()) {
                            ExposedDropdownMenu(
                                expanded = categoryExpanded,
                                onDismissRequest = { categoryExpanded = false }
                            ) {
                                filtered.forEach { cat ->
                                    DropdownMenuItem(
                                        text = { Text(cat, fontSize = 14.sp) },
                                        onClick = { editCategory = cat; categoryExpanded = false }
                                    )
                                }
                            }
                        }
                    }

                    // Terminal receipt notice
                    if (ocrParsedData.isTerminalReceipt) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Card(
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = OrangeWarning.copy(alpha = 0.1f))
                        ) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.CreditCard, null, tint = OrangeWarning, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Bank terminal receipt detected — no goods to track. You can still save the payment as a transaction.",
                                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }

                    // Editable items list
                    if (editableItems.isNotEmpty() || ocrParsedData.items.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Items (${editableItems.size}):", fontWeight = FontWeight.Medium,
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                Text("Add Item", fontSize = 11.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
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
                                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, textAlign = TextAlign.End)
                                )
                                IconButton(
                                    onClick = { editableItems = editableItems.filter { it.first != itemId } },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(Icons.Filled.Close, null, tint = RedExpense, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        // Items sum
                        if (editableItems.isNotEmpty()) {
                            val itemsSum = editableItems.sumOf { it.third.toDoubleOrNull() ?: 0.0 }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Items sum:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${ocrParsedData.currencySymbol}${String.format("%.2f", itemsSum)}",
                                    fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Save + Save to Sections + Discard buttons
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = onClearOcr,
                            modifier = Modifier.weight(1f).height(46.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Discard", fontWeight = FontWeight.SemiBold)
                        }
                        Button(
                            onClick = {
                                val amt = editAmount.toDoubleOrNull()
                                if (amt != null && amt > 0) {
                                    // Drop items with blank names or unparseable prices
                                    val finalItems = editableItems
                                        .filter { it.second.isNotBlank() && it.third.toDoubleOrNull() != null }
                                        .map { it.second to it.third.toDouble() }
                                    onConfirmOcr(amt, editMerchant, editCategory, finalItems)
                                    // Toast is intentionally not shown here; the ViewModel
                                    // saves asynchronously and the result card reports success.
                                }
                            },
                            modifier = Modifier.weight(1f).height(46.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
                        ) {
                            Icon(Icons.Filled.Check, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save Expense", fontWeight = FontWeight.SemiBold)
                        }
                    }

                    // Save to Sections button — hidden for terminal receipts (no goods to track)
                    if (!ocrParsedData.isTerminalReceipt) {
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = {
                                val amt = editAmount.toDoubleOrNull() ?: 0.0
                                val finalItems = editableItems
                                    .filter { it.second.isNotBlank() && it.third.toDoubleOrNull() != null }
                                    .map { it.second to it.third.toDouble() }
                                onSaveToSection(
                                    editMerchant,
                                    editMerchant,
                                    finalItems,
                                    amt,
                                    ocrParsedData.rawOcrText,
                                    ocrParsedData.detectedCurrencyCode
                                )
                                onClearOcr()
                                Toast.makeText(context, "Saved to Sections", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Filled.Inventory2, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Save to Sections", fontWeight = FontWeight.SemiBold)
                        }
                    }

                    // Edit as Transaction — opens AddTransactionScreen pre-filled with OCR data
                    if (onEditAsTransaction != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onEditAsTransaction,
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Filled.Edit, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Edit as Transaction", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // Result (shown after saving)
        if (lastResult != null && ocrParsedData == null) {
            Spacer(modifier = Modifier.height(16.dp))
            val isSuccess = lastResult.startsWith("Found")
            Card(
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSuccess) GreenIncome.copy(alpha = 0.1f) else OrangeWarning.copy(alpha = 0.1f)
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (isSuccess) Icons.Filled.Check else Icons.Filled.Info, null,
                            tint = if (isSuccess) GreenIncome else OrangeWarning, modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isSuccess) "Transaction Saved" else "Scan Result",
                            fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(lastResult, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // OCR raw text — show full text with scroll support for Armenian/multi-script
        if (ocrText.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            var showFullText by remember { mutableStateOf(false) }
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Extracted Text", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(onClick = { showFullText = !showFullText }) {
                            Text(if (showFullText) "Show Less" else "Show All", fontSize = 11.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    val displayText = if (showFullText) ocrText
                        else ocrText.take(800) + if (ocrText.length > 800) "\n..." else ""
                    Text(
                        displayText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        // Use default (sans-serif) font — Android's Noto fonts
                        // include Armenian, Cyrillic, CJK, Arabic, Georgian glyphs
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // QR code data
        if (qrText != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = BluePrimary.copy(alpha = 0.06f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.QrCodeScanner, null, tint = BluePrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("QR Code Detected", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = BluePrimary)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(qrText!!.take(200) + if (qrText!!.length > 200) "..." else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Navigate to Sections
        OutlinedButton(
            onClick = onNavigateToSections,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Filled.ViewList, null)
            Spacer(modifier = Modifier.width(10.dp))
            Text("View Scanned Sections", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Tips
        Card(
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = BluePrimary.copy(alpha = 0.05f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Tips for best results", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))
                listOf(
                    "Ensure good lighting on the receipt",
                    "Keep the receipt flat and aligned",
                    "Make sure the total amount is visible",
                    "QR codes on receipts are scanned automatically",
                    "Works with Armenian, Russian, Chinese & more"
                ).forEach { tip ->
                    Row(modifier = Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Check, null, modifier = Modifier.size(14.dp), tint = GreenPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(tip, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}
