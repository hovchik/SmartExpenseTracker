package com.flowsense.app.ai.setupwizard

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import com.flowsense.app.ai.benchmark.LocalAiBenchmarkRunner
import com.flowsense.app.ai.capability.DeviceAiCapabilityDetector
import com.flowsense.app.ai.capability.DeviceAiCapabilityDetector.RecommendedAiMode
import com.flowsense.app.ai.modelmanager.InstallState
import com.flowsense.app.ai.modelmanager.LocalAiModel
import com.flowsense.app.ai.modelmanager.ModelDownloadManager
import com.flowsense.app.data.model.AiModePreference

/**
 * Simplified wizard steps - reduced from 7 to 4 essential steps.
 */
enum class WizardStep {
    SMART_SETUP,        // Combines: intro + device scan + recommendation
    MODEL_SELECTION,    // Simplified model picker
    DOWNLOADING,        // Download progress
    READY               // Success screen
}

// ─── Smart Setup Screen (Combined Intro + Device Scan + Recommendation) ──────

@Composable
fun SmartSetupScreen(
    capability: DeviceAiCapabilityDetector.DeviceCapability?,
    isScanning: Boolean,
    isAiTrialEligible: Boolean = false,
    onUseSystemAi: () -> Unit,
    onDownloadModel: () -> Unit,
    onUseCloudAi: () -> Unit,
    onStartAiTrial: () -> Unit = {},
    onBack: () -> Unit
) {
    var showDeviceDetails by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Hero icon
        Icon(
            Icons.Default.Psychology,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(16.dp))

        Text(
            "Set Up On-Device AI",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "Analyze expenses privately without sending data to the cloud",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(32.dp))

        // Scanning or recommendation
        if (isScanning || capability == null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(16.dp))
                    Text("Checking your device...")
                }
            }
        } else {
            // Recommendation card based on device capability
            val (recommendationTitle, recommendationDesc, recommendationIcon, containerColor) =
                when (capability.recommendedMode) {
                    RecommendedAiMode.SYSTEM_AI -> Quadruple(
                        "Your device has built-in AI!",
                        "Use the fast, system-level AI engine — no download needed.",
                        Icons.Default.CheckCircle,
                        MaterialTheme.colorScheme.primaryContainer
                    )
                    RecommendedAiMode.CUSTOM_LOCAL -> Quadruple(
                        "Ready for local AI",
                        "Download a small AI model (500 MB – 2 GB) to run on your device.",
                        Icons.Default.Download,
                        MaterialTheme.colorScheme.secondaryContainer
                    )
                    RecommendedAiMode.CLOUD_ONLY -> Quadruple(
                        "Cloud AI recommended",
                        "Your device works best with cloud-based AI for optimal performance.",
                        Icons.Default.Cloud,
                        MaterialTheme.colorScheme.tertiaryContainer
                    )
                }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = containerColor)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            recommendationIcon,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            recommendationTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        recommendationDesc,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Main action buttons based on recommendation
            when (capability.recommendedMode) {
                RecommendedAiMode.SYSTEM_AI -> {
                    Button(
                        onClick = onUseSystemAi,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Use System AI")
                    }

                    Spacer(Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = onDownloadModel,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Or download a custom model")
                    }

                    // 7-day trial option for premium Cloud AI features
                    if (isAiTrialEligible) {
                        Spacer(Modifier.height(16.dp))
                        
                        TrialBanner(
                            onStartTrial = onStartAiTrial
                        )
                    }
                }
                RecommendedAiMode.CUSTOM_LOCAL -> {
                    Button(
                        onClick = onDownloadModel,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Choose & Download Model")
                    }

                    // 7-day trial option as alternative
                    if (isAiTrialEligible) {
                        Spacer(Modifier.height(16.dp))
                        
                        TrialBanner(
                            onStartTrial = onStartAiTrial
                        )
                    }
                }
                RecommendedAiMode.CLOUD_ONLY -> {
                    // 7-day trial banner
                    if (isAiTrialEligible) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.CardGiftcard,
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                        tint = MaterialTheme.colorScheme.tertiary
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            "Try Cloud AI free for 7 days",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            "Full access to premium AI features — no card required.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                                Button(
                                    onClick = onStartAiTrial,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.tertiary
                                    )
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Start 7-Day Free Trial")
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        Text(
                            "Or continue without trial",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(Modifier.height(8.dp))
                    }

                    Button(
                        onClick = onUseCloudAi,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Cloud, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Set Up Cloud AI")
                    }

                    Spacer(Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = onDownloadModel,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Try local AI anyway")
                    }
                }
            }

            // Expandable device details
            Spacer(Modifier.height(24.dp))

            TextButton(
                onClick = { showDeviceDetails = !showDeviceDetails }
            ) {
                Icon(
                    if (showDeviceDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(if (showDeviceDetails) "Hide device info" else "Show device info")
            }

            AnimatedVisibility(visible = showDeviceDetails) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        DeviceInfoRow("Device", "${capability.manufacturer} ${capability.model}")
                        DeviceInfoRow("RAM", "${capability.totalRamMb / 1024} GB")
                        DeviceInfoRow("Free Storage", "${capability.freeStorageMb / 1024} GB")
                        DeviceInfoRow("System AI", if (capability.supportsSystemAi) "Available" else "Not available")
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // Back button
        TextButton(onClick = onBack) {
            Text("Cancel")
        }
    }
}

// Helper data class for quadruple values
private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
private fun DeviceInfoRow(label: String, value: String) {
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

/**
 * Reusable 7-day trial banner component.
 */
@Composable
private fun TrialBanner(
    onStartTrial: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.CardGiftcard,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.tertiary
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Or try Cloud AI free",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "7 days, no card required",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledTonalButton(
                onClick = onStartTrial,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("Try Free", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

// ─── Simplified Model Selection Screen ───────────────────────────────────────

@Composable
fun SimpleModelSelectionScreen(
    catalogModels: List<LocalAiModel>,
    deviceRamMb: Int,
    hasHuggingFaceToken: Boolean,
    onDownloadModel: (LocalAiModel) -> Unit,
    onSaveToken: (String) -> Unit,
    onImportModel: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var showTokenDialog by remember { mutableStateOf(false) }
    var pendingGatedModel by remember { mutableStateOf<LocalAiModel?>(null) }
    var showAllModels by remember { mutableStateOf(false) }

    // Filter models that fit in device RAM and categorize them
    val compatibleModels = catalogModels.filter { it.requiredRamMb <= deviceRamMb }
    val recommendedModel = compatibleModels
        .filter { !it.isGated } // Prefer ungated for quick start
        .sortedByDescending { it.sizeMb }
        .firstOrNull { it.recommendedRamMb <= deviceRamMb }
        ?: compatibleModels.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Choose AI Model",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "Select a model based on your needs",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))

        // Recommended model (if available)
        if (recommendedModel != null) {
            Text(
                "Recommended for your device",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            SimpleModelCard(
                model = recommendedModel,
                isRecommended = true,
                onDownload = { onDownloadModel(recommendedModel) }
            )

            Spacer(Modifier.height(20.dp))
        }

        // Quick picks - show 2-3 models in different tiers
        val quickPicks = getQuickPicks(compatibleModels, recommendedModel)
        if (quickPicks.isNotEmpty()) {
            Text(
                "Other options",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            quickPicks.forEach { model ->
                SimpleModelCard(
                    model = model,
                    isRecommended = false,
                    onDownload = {
                        if (model.isGated && !hasHuggingFaceToken) {
                            pendingGatedModel = model
                            showTokenDialog = true
                        } else {
                            onDownloadModel(model)
                        }
                    }
                )
                Spacer(Modifier.height(8.dp))
            }
        }

        // Show all models toggle
        if (catalogModels.size > (quickPicks.size + 1)) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { showAllModels = !showAllModels }) {
                Icon(
                    if (showAllModels) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(if (showAllModels) "Show fewer models" else "Show all ${catalogModels.size} models")
            }

            AnimatedVisibility(visible = showAllModels) {
                Column {
                    catalogModels
                        .filter { it != recommendedModel && it !in quickPicks }
                        .forEach { model ->
                            SimpleModelCard(
                                model = model,
                                isRecommended = false,
                                isCompatible = model.requiredRamMb <= deviceRamMb,
                                onDownload = {
                                    if (model.isGated && !hasHuggingFaceToken) {
                                        pendingGatedModel = model
                                        showTokenDialog = true
                                    } else {
                                        onDownloadModel(model)
                                    }
                                }
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Import option
        OutlinedButton(
            onClick = onImportModel,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Import from device")
        }

        Spacer(Modifier.weight(1f))

        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back")
        }
    }

    // Simplified token dialog
    if (showTokenDialog) {
        SimpleTokenDialog(
            modelName = pendingGatedModel?.displayName ?: "",
            licenseUrl = pendingGatedModel?.licenseUrl,
            onDismiss = {
                showTokenDialog = false
                pendingGatedModel = null
            },
            onTokenSubmit = { token ->
                showTokenDialog = false
                onSaveToken(token)
                pendingGatedModel?.let { onDownloadModel(it) }
                pendingGatedModel = null
            }
        )
    }
}

/** Returns 2-3 quick pick models in different size tiers */
private fun getQuickPicks(models: List<LocalAiModel>, excludeModel: LocalAiModel?): List<LocalAiModel> {
    val filtered = models.filter { it != excludeModel }

    // Get one small, one medium model (if available)
    val small = filtered.filter { it.sizeMb < 600 && !it.isGated }.firstOrNull()
    val medium = filtered.filter { it.sizeMb in 600..2000 && !it.isGated }.firstOrNull()
    val large = filtered.filter { it.sizeMb > 2000 && !it.isGated }.firstOrNull()

    return listOfNotNull(small, medium, large).take(3)
}

/** Simplified model card with quality tier instead of technical specs */
@Composable
private fun SimpleModelCard(
    model: LocalAiModel,
    isRecommended: Boolean,
    isCompatible: Boolean = true,
    onDownload: () -> Unit
) {
    val qualityTier = getQualityTier(model.sizeMb)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isRecommended -> MaterialTheme.colorScheme.primaryContainer
                !isCompatible -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Quality indicator
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = qualityTier.color.copy(alpha = 0.15f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        qualityTier.icon,
                        contentDescription = null,
                        tint = qualityTier.color,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        model.displayName.replace(Regex("\\s*\\(.*?\\)\\s*"), "").trim(), // Remove technical suffix
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (model.isGated) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Requires sign-in",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    "${qualityTier.label} • ${formatSize(model.sizeMb)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (!isCompatible) {
                    Text(
                        "May be too large for your device",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            if (model.installState == InstallState.INSTALLED) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Installed",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            } else if (isCompatible) {
                FilledTonalButton(
                    onClick = onDownload,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("Get")
                }
            }
        }
    }
}

private data class QualityTier(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val color: androidx.compose.ui.graphics.Color
)

@Composable
private fun getQualityTier(sizeMb: Int): QualityTier {
    return when {
        sizeMb < 300 -> QualityTier("Ultra-fast", Icons.Default.Bolt, MaterialTheme.colorScheme.tertiary)
        sizeMb < 800 -> QualityTier("Fast", Icons.Default.Speed, MaterialTheme.colorScheme.primary)
        sizeMb < 2000 -> QualityTier("Balanced", Icons.Default.Balance, MaterialTheme.colorScheme.secondary)
        sizeMb < 4000 -> QualityTier("High quality", Icons.Default.AutoAwesome, MaterialTheme.colorScheme.primary)
        else -> QualityTier("Best quality", Icons.Default.Star, MaterialTheme.colorScheme.tertiary)
    }
}

private fun formatSize(sizeMb: Int): String {
    return if (sizeMb >= 1024) {
        String.format("%.1f GB", sizeMb / 1024f)
    } else {
        "$sizeMb MB"
    }
}

// ─── Simplified Token Dialog ─────────────────────────────────────────────────

@Composable
fun SimpleTokenDialog(
    modelName: String,
    licenseUrl: String?,
    onDismiss: () -> Unit,
    onTokenSubmit: (String) -> Unit
) {
    val context = LocalContext.current
    var token by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Lock, contentDescription = null) },
        title = { Text("Sign in required") },
        text = {
            Column {
                Text(
                    "$modelName requires a free HuggingFace account.",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(Modifier.height(16.dp))

                // Step 1: Get token
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://huggingface.co/settings/tokens/new?tokenType=fineGrained"))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Get access token")
                }

                if (licenseUrl != null) {
                    Spacer(Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(licenseUrl))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Policy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Accept model license")
                    }
                }

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it.trim() },
                    label = { Text("Paste token") },
                    placeholder = { Text("hf_...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
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
                Text("Download")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// ─── Legacy Token Dialog (for SettingsScreen compatibility) ─────────────────

/**
 * HuggingFace token dialog with legacy API for backward compatibility.
 * Used by SettingsScreen for general token management.
 */
@Composable
fun HuggingFaceTokenDialog(
    onDismiss: () -> Unit,
    onTokenSubmit: (String) -> Unit,
    onOpenBrowser: () -> Unit,
    licenseUrl: String? = null
) {
    val context = LocalContext.current
    var token by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Key, contentDescription = null) },
        title = { Text("HuggingFace Token") },
        text = {
            Column {
                Text(
                    "Enter your HuggingFace access token to download gated models.",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(Modifier.height(16.dp))

                OutlinedButton(
                    onClick = onOpenBrowser,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Get access token")
                }

                if (licenseUrl != null) {
                    Spacer(Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(licenseUrl))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Policy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Accept model license")
                    }
                }

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it.trim() },
                    label = { Text("Paste token") },
                    placeholder = { Text("hf_...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
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
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// ─── Simplified Download Screen ──────────────────────────────────────────────

@Composable
fun SimpleDownloadScreen(
    downloadState: ModelDownloadManager.DownloadState,
    modelName: String,
    licenseUrl: String? = null,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val isGatedRepoError = downloadState.hfErrorCode == "GatedRepo"
    var sentToBrowser by remember { mutableStateOf(false) }

    // Auto-retry when user returns from browser after accepting license
    @Suppress("DEPRECATION")
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME && sentToBrowser) {
                sentToBrowser = false
                onRetry()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when {
            isGatedRepoError -> {
                // License required
                Icon(
                    Icons.Default.Policy,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    "Accept license first",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "This model requires you to accept its license on HuggingFace.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = {
                        sentToBrowser = true
                        val url = licenseUrl ?: "https://huggingface.co"
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.OpenInBrowser, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Accept License")
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                    Text("I've accepted — retry")
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDone) {
                    Text("Cancel")
                }
            }
            downloadState.error != null -> {
                // Error state
                Icon(
                    Icons.Default.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    "Download failed",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    downloadState.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                    Text("Try again")
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDone) {
                    Text("Choose different model")
                }
            }
            downloadState.progress >= 1f -> {
                // Success
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    "Download complete!",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    modelName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                    Text("Continue")
                }
            }
            else -> {
                // Downloading
                Text(
                    "Downloading...",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    modelName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(32.dp))

                LinearProgressIndicator(
                    progress = { downloadState.progress },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(16.dp))

                val downloadedMb = downloadState.downloadedBytes / (1024 * 1024)
                val totalMb = downloadState.totalBytes / (1024 * 1024)
                val pct = (downloadState.progress * 100).toInt()

                Text(
                    "$downloadedMb / $totalMb MB ($pct%)",
                    style = MaterialTheme.typography.bodyMedium
                )

                val speedMbps = downloadState.speedBytesPerSec / (1024.0 * 1024.0)
                if (speedMbps > 0.01) {
                    Text(
                        String.format("%.1f MB/s", speedMbps),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(32.dp))

                OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel")
                }
            }
        }
    }
}

// ─── Simple Ready Screen ─────────────────────────────────────────────────────

@Composable
fun SimpleReadyScreen(
    providerName: String,
    benchmarkResult: LocalAiBenchmarkRunner.BenchmarkResult?,
    isRunningBenchmark: Boolean,
    onRunBenchmark: () -> Unit,
    onFinish: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(24.dp))

        Text(
            "You're all set!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "AI is ready: $providerName",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(24.dp))

        // Privacy message
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Security,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "All analysis happens on your device. Your data stays private.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // Optional benchmark
        if (isRunningBenchmark) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text("Testing performance...", style = MaterialTheme.typography.bodyMedium)
            }
        } else if (benchmarkResult != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Performance test",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Speed: ${String.format("%.1f", benchmarkResult.approximateTokensPerSecond)} tokens/sec",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "Latency: ${benchmarkResult.averageLatencyMs}ms average",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            TextButton(onClick = onRunBenchmark) {
                Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Test performance")
            }
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Done")
        }
    }
}

// ─── Import Model Screen (Simplified) ────────────────────────────────────────

@Composable
fun SimpleImportScreen(
    importMessage: String?,
    onPickFile: () -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
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
            "Import Model",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "Select a .task, .bin, or .tflite file",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))

        Button(onClick = onPickFile, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.FileOpen, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Choose file")
        }

        if (importMessage != null) {
            Spacer(Modifier.height(16.dp))
            Card(
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

        Spacer(Modifier.weight(1f))

        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back")
        }
    }
}

// ─── Main Wizard Composable (Simplified) ─────────────────────────────────────

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
    isAiTrialEligible: Boolean = false,
    onScanDevice: () -> Unit,
    onSelectMode: (AiModePreference) -> Unit,
    onDownloadModel: (LocalAiModel) -> Unit,
    onSaveHuggingFaceToken: (String) -> Unit,
    onCancelDownload: () -> Unit,
    onImportModel: () -> Unit,
    onRunBenchmark: () -> Unit,
    onStartAiTrial: () -> Unit = {},
    onFinish: () -> Unit,
    onBack: () -> Unit
) {
    var currentStep by remember { mutableStateOf(WizardStep.SMART_SETUP) }
    var pendingDownloadModel by remember { mutableStateOf<LocalAiModel?>(null) }
    var showImportScreen by remember { mutableStateOf(false) }

    // Trigger device scan when entering
    LaunchedEffect(Unit) {
        if (capability == null) {
            onScanDevice()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Simplified progress indicator (4 steps)
        val stepIndex = WizardStep.entries.indexOf(currentStep)
        LinearProgressIndicator(
            progress = { (stepIndex + 1).toFloat() / WizardStep.entries.size },
            modifier = Modifier.fillMaxWidth()
        )

        AnimatedContent(
            targetState = if (showImportScreen) "import" else currentStep.name,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "wizard_step"
        ) { step ->
            when (step) {
                "import" -> SimpleImportScreen(
                    importMessage = importMessage,
                    onPickFile = onImportModel,
                    onBack = { showImportScreen = false },
                    onDone = {
                        showImportScreen = false
                        currentStep = WizardStep.READY
                    }
                )
                WizardStep.SMART_SETUP.name -> SmartSetupScreen(
                    capability = capability,
                    isScanning = isScanning,
                    isAiTrialEligible = isAiTrialEligible,
                    onUseSystemAi = {
                        onSelectMode(AiModePreference.SYSTEM_AI)
                        currentStep = WizardStep.READY
                    },
                    onDownloadModel = {
                        onSelectMode(AiModePreference.LOCAL_MODEL)
                        currentStep = WizardStep.MODEL_SELECTION
                    },
                    onUseCloudAi = {
                        onSelectMode(AiModePreference.CLOUD_AI)
                        onFinish()
                    },
                    onStartAiTrial = {
                        onStartAiTrial()
                        onSelectMode(AiModePreference.CLOUD_AI)
                        onFinish()
                    },
                    onBack = onBack
                )
                WizardStep.MODEL_SELECTION.name -> SimpleModelSelectionScreen(
                    catalogModels = catalogModels,
                    deviceRamMb = (capability?.totalRamMb ?: 4096L).toInt(),
                    hasHuggingFaceToken = hasHuggingFaceToken,
                    onDownloadModel = { model ->
                        pendingDownloadModel = model
                        onDownloadModel(model)
                        currentStep = WizardStep.DOWNLOADING
                    },
                    onSaveToken = onSaveHuggingFaceToken,
                    onImportModel = { showImportScreen = true },
                    onBack = { currentStep = WizardStep.SMART_SETUP }
                )
                WizardStep.DOWNLOADING.name -> SimpleDownloadScreen(
                    downloadState = downloadState,
                    modelName = pendingDownloadModel?.displayName ?: downloadState.modelId,
                    licenseUrl = pendingDownloadModel?.licenseUrl?.ifBlank { null },
                    onCancel = {
                        onCancelDownload()
                        currentStep = WizardStep.MODEL_SELECTION
                    },
                    onRetry = {
                        pendingDownloadModel?.let { onDownloadModel(it) }
                    },
                    onDone = {
                        if (downloadState.progress >= 1f) {
                            currentStep = WizardStep.READY
                        } else {
                            currentStep = WizardStep.MODEL_SELECTION
                        }
                    }
                )
                WizardStep.READY.name -> SimpleReadyScreen(
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
