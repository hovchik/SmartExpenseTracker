package com.smartexpense.tracker.ui.screens

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
import com.smartexpense.tracker.ui.theme.*
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanReceiptScreen(
    onOcrResult: (ocrText: String, qrData: String?) -> Unit,
    onConfirmOcr: (amount: Double, merchantName: String, category: String) -> Unit,
    onClearOcr: () -> Unit,
    onSaveToSection: (label: String, merchantName: String, items: List<Pair<String, Double>>, totalAmount: Double, rawOcrText: String, detectedLanguages: String) -> Unit,
    ocrParsedData: com.smartexpense.tracker.ui.viewmodel.OcrParsedData?,
    categories: List<String>,
    onNavigateBack: () -> Unit,
    onNavigateToSections: () -> Unit,
    lastResult: String?
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

    /**
     * Runs OCR using ALL available ML Kit recognizers in parallel,
     * plus barcode/QR scanning. QR data is passed alongside OCR text
     * so the ViewModel can use it as a fallback if OCR parsing fails.
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

        // Create all recognizers.
        // The default Latin recognizer also handles Cyrillic (Russian) and Armenian scripts.
        val recognizers: List<Pair<String, TextRecognizer>> = listOf(
            "Latin/Cyrillic/Armenian" to TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS),
            "Chinese" to TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build()),
            "Devanagari" to TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build()),
            "Japanese" to TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build()),
            "Korean" to TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
        )

        val results = mutableMapOf<String, String>()
        // +1 for barcode scanner
        val remaining = AtomicInteger(recognizers.size + 1)
        var qrResult: String? = null

        fun onAllDone() {
            if (remaining.get() > 0) return

            val nonEmpty = results.entries
                .filter { it.value.isNotBlank() }
                .sortedByDescending { it.value.length }

            val mergedLines = mutableListOf<String>()
            val seenLines = mutableSetOf<String>()
            for ((name, text) in nonEmpty) {
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

                    // Items breakdown (read-only)
                    if (ocrParsedData.items.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Items detected:", fontWeight = FontWeight.Medium, fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        ocrParsedData.items.forEach { (name, price) ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                                horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(name, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f))
                                Text("${ocrParsedData.currencySymbol}${String.format("%.2f", price)}",
                                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                    onConfirmOcr(amt, editMerchant, editCategory)
                                }
                            },
                            modifier = Modifier.weight(1f).height(46.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
                        ) {
                            Icon(Icons.Filled.Check, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save", fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Save to Sections button
                    OutlinedButton(
                        onClick = {
                            val amt = editAmount.toDoubleOrNull() ?: 0.0
                            onSaveToSection(
                                editMerchant,
                                editMerchant,
                                ocrParsedData.items,
                                amt,
                                ocrParsedData.rawOcrText,
                                "" // detectedLanguages filled by caller
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

        // OCR raw text
        if (ocrText.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Extracted Text", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(ocrText.take(400) + if (ocrText.length > 400) "..." else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
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
