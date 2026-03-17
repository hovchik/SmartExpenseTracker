package com.smartexpense.tracker.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PAGE_COUNT = 4

/**
 * Multi-page onboarding screen shown on first launch.
 * Page 1: App info & key features
 * Page 2: Permissions explanation
 * Page 3: AI model choice
 * Page 4: Ready to go
 */
@Composable
fun SplashScreen(onGetStarted: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
    val scope = rememberCoroutineScope()
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(100); visible = true }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.primaryContainer
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Pages
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> AppInfoPage(visible)
                1 -> PermissionsPage(visible)
                2 -> AiModelPage(visible)
                3 -> ReadyPage(visible)
            }
        }

        // Bottom controls
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Page indicators
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(PAGE_COUNT) { index ->
                    val isActive = pagerState.currentPage == index
                    val width by animateFloatAsState(
                        targetValue = if (isActive) 24f else 8f,
                        animationSpec = tween(300),
                        label = "dot"
                    )
                    Box(
                        modifier = Modifier
                            .height(8.dp)
                            .width(width.dp)
                            .clip(CircleShape)
                            .background(
                                if (isActive) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.35f)
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Navigation buttons
            val isLastPage = pagerState.currentPage == PAGE_COUNT - 1
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Skip / Back
                if (pagerState.currentPage > 0) {
                    TextButton(onClick = {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                    }) {
                        Text("Back", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))
                    }
                } else {
                    TextButton(onClick = onGetStarted) {
                        Text("Skip", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f))
                    }
                }

                // Next / Get Started
                Button(
                    onClick = {
                        if (isLastPage) {
                            onGetStarted()
                        } else {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onPrimary,
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    elevation = ButtonDefaults.buttonElevation(4.dp)
                ) {
                    Text(
                        text = if (isLastPage) "Get Started" else "Next",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                    if (!isLastPage) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

// ── Page 1: App Info ────────────────────────────────────────────────

@Composable
private fun AppInfoPage(visible: Boolean) {
    val onPrimary = MaterialTheme.colorScheme.onPrimary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 120.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(56.dp))

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(600)) + slideInVertically(tween(600)) { -40 }
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(onPrimary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.AccountBalance,
                    contentDescription = null,
                    tint = onPrimary,
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        AnimatedVisibility(visible = visible, enter = fadeIn(tween(700, 100))) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "FlowSense",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold, fontSize = 32.sp
                    ),
                    color = onPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Smart Expense Tracking",
                    style = MaterialTheme.typography.bodyLarge,
                    color = onPrimary.copy(alpha = 0.8f)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(600, 200)) + slideInVertically(tween(600, 200)) { 60 }
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        "Key Features",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    FeatureRow(Icons.Filled.SmartToy, "AI-Powered Categorization",
                        "Automatically categorize expenses using on-device AI or cloud models")
                    FeatureRow(Icons.Filled.BarChart, "Reports & Insights",
                        "Detailed spending reports with AI-generated financial insights")
                    FeatureRow(Icons.Filled.CameraAlt, "Receipt Scanner",
                        "Scan receipts with OCR to extract amounts and merchant info")
                    FeatureRow(Icons.Filled.Sms, "SMS Parsing",
                        "Auto-detect transactions from banking SMS messages")
                    FeatureRow(Icons.Filled.Map, "Store Map",
                        "Visualize where you spend with geo-tagged transactions")
                    FeatureRow(Icons.Filled.CurrencyExchange, "Multi-Currency",
                        "Track expenses in multiple currencies with live exchange rates")
                }
            }
        }
    }
}

// ── Page 2: Permissions ─────────────────────────────────────────────

@Composable
private fun PermissionsPage(visible: Boolean) {
    val onPrimary = MaterialTheme.colorScheme.onPrimary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 120.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(56.dp))

        AnimatedVisibility(visible = visible, enter = fadeIn(tween(600))) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(onPrimary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Security, contentDescription = null, tint = onPrimary,
                    modifier = Modifier.size(40.dp))
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            "Permissions",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = onPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "We request permissions only when needed.\nThe app works without them.",
            style = MaterialTheme.typography.bodyMedium,
            color = onPrimary.copy(alpha = 0.75f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(28.dp))

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(600, 200)) + slideInVertically(tween(600, 200)) { 60 }
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    PermissionRow(Icons.Filled.CameraAlt, "Camera",
                        "Scan receipts and capture expense documents")
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    PermissionRow(Icons.Filled.Sms, "SMS",
                        "Read banking messages to auto-detect transactions")
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    PermissionRow(Icons.Filled.Notifications, "Notifications",
                        "Budget alerts, threshold warnings, and transaction reminders")
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    PermissionRow(Icons.Filled.LocationOn, "Location",
                        "Geo-tag transactions and show spending on a map")
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    PermissionRow(Icons.Filled.Storage, "Storage",
                        "Import/export data backups and download AI models")
                }
            }
        }
    }
}

// ── Page 3: AI Model ────────────────────────────────────────────────

@Composable
private fun AiModelPage(visible: Boolean) {
    val onPrimary = MaterialTheme.colorScheme.onPrimary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 120.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(56.dp))

        AnimatedVisibility(visible = visible, enter = fadeIn(tween(600))) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(onPrimary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Psychology, contentDescription = null, tint = onPrimary,
                    modifier = Modifier.size(40.dp))
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            "AI Engine",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = onPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "FlowSense uses AI to categorize transactions,\ngenerate insights, and analyze spending.",
            style = MaterialTheme.typography.bodyMedium,
            color = onPrimary.copy(alpha = 0.75f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(28.dp))

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(600, 200)) + slideInVertically(tween(600, 200)) { 60 }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AiOptionCard(
                    icon = Icons.Filled.PhoneAndroid,
                    title = "On-Device AI (Recommended)",
                    description = "Download a small AI model (500 MB – 2 GB) that runs entirely on your device. " +
                        "Your data never leaves your phone. Models include Qwen 2.5, Gemma, and more.",
                    highlight = true
                )
                AiOptionCard(
                    icon = Icons.Filled.Devices,
                    title = "System AI",
                    description = "Use your device's built-in AI runtime (Google AICore on Pixel, Samsung Galaxy AI). " +
                        "No download needed if your device supports it."
                )
                AiOptionCard(
                    icon = Icons.Filled.Cloud,
                    title = "Cloud AI",
                    description = "Use Claude AI via API for the highest quality analysis. " +
                        "Requires an API key. Data is sent to Anthropic servers."
                )

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "You can change this anytime in Settings > AI Engine.",
                    style = MaterialTheme.typography.bodySmall,
                    color = onPrimary.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// ── Page 4: Ready to Go ─────────────────────────────────────────────

@Composable
private fun ReadyPage(visible: Boolean) {
    val onPrimary = MaterialTheme.colorScheme.onPrimary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .padding(bottom = 120.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(600)) + slideInVertically(tween(600)) { -30 }
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(onPrimary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.RocketLaunch, contentDescription = null, tint = onPrimary,
                    modifier = Modifier.size(52.dp))
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            "You're All Set!",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = onPrimary
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            "Start tracking your expenses and\nlet AI handle the rest.",
            style = MaterialTheme.typography.bodyLarge,
            color = onPrimary.copy(alpha = 0.8f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(600, 300)) + slideInVertically(tween(600, 300)) { 40 }
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = onPrimary.copy(alpha = 0.12f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    QuickTipRow(Icons.Filled.AddCircle, "Tap the + button to add your first expense")
                    QuickTipRow(Icons.Filled.CameraAlt, "Use the receipt scanner for hands-free entry")
                    QuickTipRow(Icons.Filled.AutoAwesome, "AI will auto-categorize your transactions")
                    QuickTipRow(Icons.Filled.Settings, "Visit Settings to configure AI models and SMS parsing")
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            "Your data stays on your device.\nNo account required.",
            style = MaterialTheme.typography.bodySmall,
            color = onPrimary.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
    }
}

// ── Shared Components ───────────────────────────────────────────────

@Composable
private fun FeatureRow(icon: ImageVector, title: String, description: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface)
            Text(description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PermissionRow(icon: ImageVector, name: String, reason: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(name,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface)
            Text(reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AiOptionCard(
    icon: ImageVector,
    title: String,
    description: String,
    highlight: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (highlight)
                MaterialTheme.colorScheme.surface
            else
                MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
        ),
        elevation = CardDefaults.cardElevation(if (highlight) 4.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (highlight) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null,
                    tint = if (highlight) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface)
                    if (highlight) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        ) {
                            Text(
                                "Private",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp)
            }
        }
    }
}

@Composable
private fun QuickTipRow(icon: ImageVector, text: String) {
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = onPrimary.copy(alpha = 0.8f),
            modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium,
            color = onPrimary.copy(alpha = 0.85f))
    }
}
