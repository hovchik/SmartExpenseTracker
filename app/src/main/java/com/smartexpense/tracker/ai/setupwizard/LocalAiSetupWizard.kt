package com.smartexpense.tracker.ai.setupwizard

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import com.smartexpense.tracker.ai.benchmark.LocalAiBenchmarkRunner
import com.smartexpense.tracker.ai.capability.DeviceAiCapabilityDetector
import com.smartexpense.tracker.ai.capability.DeviceAiCapabilityDetector.Companion.PerformanceClass
import com.smartexpense.tracker.ai.capability.DeviceAiCapabilityDetector.RecommendedAiMode
import com.smartexpense.tracker.ai.modelmanager.InstallState
import com.smartexpense.tracker.ai.modelmanager.LocalAiModel
import com.smartexpense.tracker.ai.modelmanager.ModelDownloadManager
import com.smartexpense.tracker.data.model.AiModePreference

/**
 * Wizard step enumeration.
 */
enum class WizardStep {
    INTRO,
    DEVICE_COMPATIBILITY,
    RECOMMENDED_MODE,
    MODEL_INSTALL_OPTIONS,
    MODEL_DOWNLOAD,
    IMPORT_MODEL,
    LOCAL_AI_READY
}

// ─── Intro Screen ────────────────────────────────────────────────────────────

@Composable
fun LocalAiIntroScreen(onNext: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Psychology,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(24.dp))

        Text(
            "Local AI Setup",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(16.dp))

        Text(
            "FlowSense supports three AI modes:\n\n" +
                    "1. Cloud AI — Powered by Claude for best quality\n" +
                    "2. System AI — Uses your device's built-in AI\n" +
                    "3. Local Model — Download a small AI model\n\n" +
                    "This wizard will check your device and recommend the best option.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Check My Device")
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.ArrowForward, contentDescription = null)
        }
    }
}

// ─── Device Compatibility Screen ─────────────────────────────────────────────

@Composable
fun DeviceCompatibilityScreen(
    capability: DeviceAiCapabilityDetector.DeviceCapability?,
    isScanning: Boolean,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Device Compatibility",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(24.dp))

        if (isScanning || capability == null) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text("Scanning device capabilities...")
        } else {
            // Device info card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    CapabilityRow("Device", "${capability.manufacturer} ${capability.model}")
                    CapabilityRow("Android", "${capability.androidVersionName} (API ${capability.androidVersion})")
                    CapabilityRow("RAM", "${capability.totalRamMb} MB (${capability.ramTier.label})")
                    CapabilityRow("Free Storage", "${capability.freeStorageMb} MB")
                    CapabilityRow("Performance", capability.performanceClass.label)

                    Spacer(Modifier.height(12.dp))

                    CapabilityCheckRow("AICore", capability.aiCoreAvailable)
                    CapabilityCheckRow("ML Kit GenAI", capability.mlKitGenAiAvailable)
                    CapabilityCheckRow("System AI Support", capability.supportsSystemAi)
                    CapabilityCheckRow("Local Model Support", capability.supportsCustomLocalModel)
                    CapabilityCheckRow("Sufficient Storage", capability.hasEnoughStorage)
                }
            }

            Spacer(Modifier.height(24.dp))

            // Capability message
            val (containerColor, messageIcon) = when (capability.recommendedMode) {
                RecommendedAiMode.SYSTEM_AI ->
                    MaterialTheme.colorScheme.primaryContainer to Icons.Default.CheckCircle
                RecommendedAiMode.CUSTOM_LOCAL ->
                    MaterialTheme.colorScheme.secondaryContainer to Icons.Default.Info
                RecommendedAiMode.CLOUD_ONLY ->
                    MaterialTheme.colorScheme.errorContainer to Icons.Default.Warning
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = containerColor)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(messageIcon, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        when (capability.recommendedMode) {
                            RecommendedAiMode.SYSTEM_AI ->
                                "This device supports built-in on-device AI. The app can use the system AI engine."
                            RecommendedAiMode.CUSTOM_LOCAL ->
                                "This device does not expose built-in AI to apps, but it can run a downloadable local model."
                            RecommendedAiMode.CLOUD_ONLY ->
                                "This device is not ideal for local AI. Cloud AI is recommended."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(onClick = onBack) { Text("Back") }
            Button(onClick = onNext, enabled = capability != null) { Text("Next") }
        }
    }
}

@Composable
private fun CapabilityRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CapabilityCheckRow(label: String, available: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Icon(
            if (available) Icons.Default.CheckCircle else Icons.Default.Cancel,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (available) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.error
        )
    }
}

// ─── Recommended AI Mode Screen ──────────────────────────────────────────────

@Composable
fun RecommendedAiModeScreen(
    recommendedMode: RecommendedAiMode?,
    onSelectMode: (AiModePreference) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Choose AI Mode",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "Select how the app should perform AI analysis:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))

        // System AI option
        AiModeOption(
            title = "System AI",
            description = "Uses your device's built-in AI engine (if available)",
            icon = Icons.Default.PhoneAndroid,
            recommended = recommendedMode == RecommendedAiMode.SYSTEM_AI,
            enabled = recommendedMode == RecommendedAiMode.SYSTEM_AI ||
                    recommendedMode == RecommendedAiMode.CUSTOM_LOCAL,
            onClick = { onSelectMode(AiModePreference.SYSTEM_AI) }
        )

        Spacer(Modifier.height(12.dp))

        // Local Model option
        AiModeOption(
            title = "Local Model",
            description = "Download a small AI model to run on-device",
            icon = Icons.Default.Storage,
            recommended = recommendedMode == RecommendedAiMode.CUSTOM_LOCAL,
            enabled = recommendedMode != RecommendedAiMode.CLOUD_ONLY,
            onClick = { onSelectMode(AiModePreference.LOCAL_MODEL) }
        )

        Spacer(Modifier.height(12.dp))

        // Cloud AI option
        AiModeOption(
            title = "Cloud AI (Claude)",
            description = "Best quality — requires internet and API key",
            icon = Icons.Default.Cloud,
            recommended = recommendedMode == RecommendedAiMode.CLOUD_ONLY,
            enabled = true,
            onClick = { onSelectMode(AiModePreference.CLOUD_AI) }
        )

        Spacer(Modifier.weight(1f))

        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back")
        }
    }
}

@Composable
private fun AiModeOption(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    recommended: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (recommended) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (recommended) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                "Recommended",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                if (!enabled) {
                    Text(
                        "Not supported on this device",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}

// ─── Model Install Options Screen ────────────────────────────────────────────

@Composable
fun ModelInstallOptionsScreen(
    catalogModels: List<LocalAiModel>,
    hasHuggingFaceToken: Boolean,
    huggingFaceUsername: String?,
    onDownloadModel: (LocalAiModel) -> Unit,
    onSaveToken: (String) -> Unit,
    onImportModel: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var showTokenDialog by remember { mutableStateOf(false) }
    var pendingGatedModel by remember { mutableStateOf<LocalAiModel?>(null) }

    val ungatedModels = catalogModels.filter { !it.isGated }
    val gatedModels = catalogModels.filter { it.isGated }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Install AI Model",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "Choose a model to download or import your own:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))

        // ── Ungated models section ──
        if (ungatedModels.isNotEmpty()) {
            Text(
                "Free Models (no account needed)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            ungatedModels.forEach { model ->
                ModelCatalogCard(
                    model = model,
                    onDownload = { onDownloadModel(model) }
                )
            }
        }

        // ── Gated models section ──
        if (gatedModels.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Gated Models (HuggingFace token required)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(4.dp))

            // Token status banner
            if (hasHuggingFaceToken && huggingFaceUsername != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Signed in as $huggingFaceUsername",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "These models require a free HuggingFace account and access token.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            gatedModels.forEach { model ->
                ModelCatalogCard(
                    model = model,
                    onDownload = {
                        if (hasHuggingFaceToken) {
                            onDownloadModel(model)
                        } else {
                            pendingGatedModel = model
                            showTokenDialog = true
                        }
                    }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Import option
        OutlinedButton(
            onClick = onImportModel,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.FolderOpen, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Import Model from Device")
        }

        Spacer(Modifier.weight(1f))

        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }

    // ── HuggingFace Token Dialog ──
    if (showTokenDialog) {
        HuggingFaceTokenDialog(
            onDismiss = {
                showTokenDialog = false
                pendingGatedModel = null
            },
            onTokenSubmit = { token ->
                showTokenDialog = false
                onSaveToken(token)
                // After saving token, proceed to download the pending model
                pendingGatedModel?.let { onDownloadModel(it) }
                pendingGatedModel = null
            },
            onOpenBrowser = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://huggingface.co/settings/tokens"))
                context.startActivity(intent)
            }
        )
    }
}

/** Single model card used in both ungated and gated sections. */
@Composable
private fun ModelCatalogCard(
    model: LocalAiModel,
    onDownload: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            model.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        if (model.isGated) {
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = "Requires token",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        "${model.sizeMb} MB | ${model.quantization} | ${model.runtimeType.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "RAM: ${model.requiredRamMb} MB min, ${model.recommendedRamMb} MB recommended",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            if (model.installState == InstallState.INSTALLED) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Installed", style = MaterialTheme.typography.labelMedium)
                }
            } else {
                Button(
                    onClick = onDownload,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Download (${model.sizeMb} MB)")
                }
            }
        }
    }
}

// ─── HuggingFace Token Dialog ────────────────────────────────────────────────

/**
 * Dialog that guides the user through obtaining and entering a HuggingFace token.
 * Provides a "Get Token" button that opens HuggingFace in the browser,
 * and a text field for pasting the token back.
 */
@Composable
fun HuggingFaceTokenDialog(
    onDismiss: () -> Unit,
    onTokenSubmit: (String) -> Unit,
    onOpenBrowser: () -> Unit
) {
    var token by remember { mutableStateOf("") }
    var currentStep by remember { mutableIntStateOf(1) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Key, contentDescription = null) },
        title = { Text("HuggingFace Token") },
        text = {
            Column {
                // Step 1: Create account & accept terms
                TokenStep(
                    stepNumber = 1,
                    title = "Create a free HuggingFace account",
                    description = "If you don't have one already, sign up at huggingface.co",
                    isActive = currentStep == 1
                )

                Spacer(Modifier.height(8.dp))

                // Step 2: Accept model terms
                TokenStep(
                    stepNumber = 2,
                    title = "Accept the model license",
                    description = "Visit the model page (e.g. google/gemma-3-1b-it) and accept the usage terms",
                    isActive = currentStep == 2
                )

                Spacer(Modifier.height(8.dp))

                // Step 3: Create token
                TokenStep(
                    stepNumber = 3,
                    title = "Create an access token",
                    description = "Go to Settings > Access Tokens and create a token with 'Read' permission",
                    isActive = currentStep == 3
                )

                Spacer(Modifier.height(16.dp))

                // "Get Token" button — opens browser to HuggingFace tokens page
                OutlinedButton(
                    onClick = {
                        currentStep = 3
                        onOpenBrowser()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Open HuggingFace Token Page")
                }

                Spacer(Modifier.height(16.dp))

                // Token input field
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it.trim() },
                    label = { Text("Paste token here") },
                    placeholder = { Text("hf_...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )

                if (token.isNotBlank() && !token.startsWith("hf_")) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Token should start with \"hf_\"",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onTokenSubmit(token) },
                enabled = token.startsWith("hf_") && token.length > 8
            ) {
                Text("Save & Download")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun TokenStep(
    stepNumber: Int,
    title: String,
    description: String,
    isActive: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = if (isActive) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(24.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(
                    "$stepNumber",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isActive) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(description, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ─── Model Download Screen ───────────────────────────────────────────────────

@Composable
fun ModelDownloadScreen(
    downloadState: ModelDownloadManager.DownloadState,
    modelName: String,
    onCancel: () -> Unit,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            if (downloadState.error != null) "Download Failed"
            else if (downloadState.progress >= 1f) "Download Complete"
            else "Downloading Model",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        Text(modelName, style = MaterialTheme.typography.bodyLarge)

        Spacer(Modifier.height(32.dp))

        if (downloadState.error != null) {
            Icon(
                Icons.Default.Error,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(16.dp))
            Text(
                downloadState.error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        } else if (downloadState.progress >= 1f) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(16.dp))
            Text("Model ready to use!", style = MaterialTheme.typography.bodyMedium)
        } else {
            LinearProgressIndicator(
                progress = { downloadState.progress },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))

            val downloadedMb = downloadState.downloadedBytes / (1024 * 1024)
            val totalMb = downloadState.totalBytes / (1024 * 1024)
            val speedMbps = downloadState.speedBytesPerSec / (1024.0 * 1024.0)
            val pct = (downloadState.progress * 100).toInt()

            Text(
                "$downloadedMb / $totalMb MB ($pct%)",
                style = MaterialTheme.typography.bodyMedium
            )
            if (speedMbps > 0.01) {
                Text(
                    "${String.format("%.1f", speedMbps)} MB/s",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        if (downloadState.progress >= 1f || downloadState.error != null) {
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                Text(if (downloadState.error != null) "Try Again" else "Continue")
            }
        } else {
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel")
            }
        }
    }
}

// ─── Import Model Screen ─────────────────────────────────────────────────────

@Composable
fun ImportModelScreen(
    importMessage: String?,
    onPickFile: () -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.FolderOpen,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(24.dp))

        Text(
            "Import Model File",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(16.dp))

        Text(
            "Select a compatible model file from your device.\n\n" +
                    "Supported formats: .task, .bin, .tflite\n" +
                    "The file will be copied to app storage.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))

        Button(onClick = onPickFile, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.FileOpen, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Choose File")
        }

        if (importMessage != null) {
            Spacer(Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (importMessage.contains("success", ignoreCase = true))
                        MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    importMessage,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (importMessage.contains("success", ignoreCase = true)) {
                Spacer(Modifier.height(16.dp))
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                    Text("Continue")
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back")
        }
    }
}

// ─── Local AI Ready Screen ───────────────────────────────────────────────────

@Composable
fun LocalAiReadyScreen(
    providerName: String,
    benchmarkResult: LocalAiBenchmarkRunner.BenchmarkResult?,
    isRunningBenchmark: Boolean,
    onRunBenchmark: () -> Unit,
    onFinish: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(24.dp))

        Text(
            "AI is Ready!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "Active: $providerName",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(16.dp))

        // Privacy message for local providers
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    "All analysis is performed locally on your device. No data is sent to external servers.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // Benchmark section
        Text("Performance Test", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        Spacer(Modifier.height(12.dp))

        if (isRunningBenchmark) {
            CircularProgressIndicator()
            Spacer(Modifier.height(8.dp))
            Text("Running benchmark...", style = MaterialTheme.typography.bodySmall)
        } else if (benchmarkResult != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    BenchmarkRow("Avg Latency", "${benchmarkResult.averageLatencyMs} ms")
                    BenchmarkRow("Min / Max", "${benchmarkResult.minLatencyMs} / ${benchmarkResult.maxLatencyMs} ms")
                    BenchmarkRow("Tokens/sec", String.format("%.1f", benchmarkResult.approximateTokensPerSecond))
                    BenchmarkRow("Memory", "${benchmarkResult.memoryEstimateMb} MB")
                    BenchmarkRow("Success", "${benchmarkResult.successfulPrompts}/${benchmarkResult.promptsRun}")
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = onRunBenchmark,
            enabled = !isRunningBenchmark,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Speed, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (benchmarkResult != null) "Re-run Benchmark" else "Run Benchmark")
        }

        Spacer(Modifier.weight(1f))

        Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
            Text("Finish Setup")
        }
    }
}

@Composable
private fun BenchmarkRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

// ─── Main Wizard Composable ──────────────────────────────────────────────────

@Composable
fun LocalAiSetupWizardScreen(
    capability: DeviceAiCapabilityDetector.DeviceCapability?,
    isScanning: Boolean,
    catalogModels: List<LocalAiModel>,
    downloadState: ModelDownloadManager.DownloadState,
    benchmarkResult: LocalAiBenchmarkRunner.BenchmarkResult?,
    isRunningBenchmark: Boolean,
    importMessage: String?,
    activeProviderName: String,
    hasHuggingFaceToken: Boolean,
    huggingFaceUsername: String?,
    onScanDevice: () -> Unit,
    onSelectMode: (AiModePreference) -> Unit,
    onDownloadModel: (LocalAiModel) -> Unit,
    onSaveHuggingFaceToken: (String) -> Unit,
    onCancelDownload: () -> Unit,
    onImportModel: () -> Unit,
    onRunBenchmark: () -> Unit,
    onFinish: () -> Unit,
    onBack: () -> Unit
) {
    var currentStep by remember { mutableStateOf(WizardStep.INTRO) }

    // Trigger device scan when entering compatibility step
    LaunchedEffect(currentStep) {
        if (currentStep == WizardStep.DEVICE_COMPATIBILITY && capability == null) {
            onScanDevice()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Progress indicator
        val stepIndex = WizardStep.entries.indexOf(currentStep)
        LinearProgressIndicator(
            progress = { (stepIndex + 1).toFloat() / WizardStep.entries.size },
            modifier = Modifier.fillMaxWidth()
        )

        AnimatedContent(
            targetState = currentStep,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "wizard_step"
        ) { step ->
            when (step) {
                WizardStep.INTRO -> LocalAiIntroScreen(
                    onNext = { currentStep = WizardStep.DEVICE_COMPATIBILITY }
                )
                WizardStep.DEVICE_COMPATIBILITY -> DeviceCompatibilityScreen(
                    capability = capability,
                    isScanning = isScanning,
                    onNext = { currentStep = WizardStep.RECOMMENDED_MODE },
                    onBack = { currentStep = WizardStep.INTRO }
                )
                WizardStep.RECOMMENDED_MODE -> RecommendedAiModeScreen(
                    recommendedMode = capability?.recommendedMode,
                    onSelectMode = { mode ->
                        onSelectMode(mode)
                        when (mode) {
                            AiModePreference.LOCAL_MODEL -> currentStep = WizardStep.MODEL_INSTALL_OPTIONS
                            AiModePreference.SYSTEM_AI -> currentStep = WizardStep.LOCAL_AI_READY
                            AiModePreference.CLOUD_AI -> onFinish()
                            AiModePreference.AUTO -> currentStep = WizardStep.LOCAL_AI_READY
                        }
                    },
                    onBack = { currentStep = WizardStep.DEVICE_COMPATIBILITY }
                )
                WizardStep.MODEL_INSTALL_OPTIONS -> ModelInstallOptionsScreen(
                    catalogModels = catalogModels,
                    hasHuggingFaceToken = hasHuggingFaceToken,
                    huggingFaceUsername = huggingFaceUsername,
                    onDownloadModel = { model ->
                        onDownloadModel(model)
                        currentStep = WizardStep.MODEL_DOWNLOAD
                    },
                    onSaveToken = onSaveHuggingFaceToken,
                    onImportModel = { currentStep = WizardStep.IMPORT_MODEL },
                    onBack = { currentStep = WizardStep.RECOMMENDED_MODE }
                )
                WizardStep.MODEL_DOWNLOAD -> ModelDownloadScreen(
                    downloadState = downloadState,
                    modelName = downloadState.modelId,
                    onCancel = {
                        onCancelDownload()
                        currentStep = WizardStep.MODEL_INSTALL_OPTIONS
                    },
                    onDone = {
                        if (downloadState.error != null) {
                            currentStep = WizardStep.MODEL_INSTALL_OPTIONS
                        } else {
                            currentStep = WizardStep.LOCAL_AI_READY
                        }
                    }
                )
                WizardStep.IMPORT_MODEL -> ImportModelScreen(
                    importMessage = importMessage,
                    onPickFile = onImportModel,
                    onBack = { currentStep = WizardStep.MODEL_INSTALL_OPTIONS },
                    onDone = { currentStep = WizardStep.LOCAL_AI_READY }
                )
                WizardStep.LOCAL_AI_READY -> LocalAiReadyScreen(
                    providerName = activeProviderName,
                    benchmarkResult = benchmarkResult,
                    isRunningBenchmark = isRunningBenchmark,
                    onRunBenchmark = onRunBenchmark,
                    onFinish = onFinish
                )
            }
        }
    }
}
