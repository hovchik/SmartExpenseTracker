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

@Composable
fun ScanReceiptScreen(
    onOcrResult: (String) -> Unit,
    onNavigateBack: () -> Unit,
    lastResult: String?
) {
    val context = LocalContext.current
    var isProcessing by remember { mutableStateOf(false) }
    var ocrText by remember { mutableStateOf("") }
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
     * Runs OCR using ALL available ML Kit recognizers in parallel.
     * Returns the longest result (best coverage for any script).
     */
    fun processImageMultiLang(uri: Uri) {
        isProcessing = true
        errorMsg = null

        val image: InputImage
        try {
            image = InputImage.fromFilePath(context, uri)
        } catch (e: Exception) {
            isProcessing = false
            errorMsg = "Error loading image: ${e.message}"
            return
        }

        // Create all recognizers
        val recognizers: List<Pair<String, TextRecognizer>> = listOf(
            "Latin" to TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS),
            "Chinese" to TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build()),
            "Devanagari" to TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build()),
            "Japanese" to TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build()),
            "Korean" to TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
        )

        val results = mutableMapOf<String, String>()
        val remaining = AtomicInteger(recognizers.size)

        fun onAllDone() {
            if (remaining.get() > 0) return

            // Pick the longest non-empty result
            val best = results.entries
                .filter { it.value.isNotBlank() }
                .maxByOrNull { it.value.length }

            isProcessing = false

            if (best != null && best.value.isNotEmpty()) {
                ocrText = best.value
                Log.d("OCR", "Best result from ${best.key}: ${best.value.length} chars")
                onOcrResult(best.value)
            } else {
                errorMsg = "No text detected. Try a clearer photo with good lighting."
            }

            // Close all recognizers
            recognizers.forEach { (_, r) -> try { r.close() } catch (_: Exception) {} }
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
            if (isProcessing) "Scanning in all languages..." else "Capture or select a receipt image",
            style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "Supports Latin, Chinese, Japanese, Korean, Devanagari scripts",
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

        // Result
        if (lastResult != null) {
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

        Spacer(modifier = Modifier.height(24.dp))

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
                    "Works with any language or script"
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
