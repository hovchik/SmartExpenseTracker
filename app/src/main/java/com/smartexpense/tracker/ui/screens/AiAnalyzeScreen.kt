package com.smartexpense.tracker.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartexpense.tracker.data.model.Category
import com.smartexpense.tracker.ui.theme.*
import com.smartexpense.tracker.util.CurrencyUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAnalyzeScreen(
    categories: List<Category>,
    currencyCode: String,
    onAnalyze: (startMillis: Long, endMillis: Long, category: String?) -> String,
    onAnalyzeAsync: suspend (startMillis: Long, endMillis: Long, category: String?) -> String = { s, e, c -> onAnalyze(s, e, c) },
    onNavigateBack: () -> Unit,
    onNavigateToHistory: () -> Unit = {}
) {
    val cal = remember { Calendar.getInstance() }
    var startMillis by remember {
        mutableLongStateOf(
            Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        )
    }
    var endMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var analysisResult by remember { mutableStateOf<String?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    var showCategoryMenu by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.US) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("AI Analysis", fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(Icons.Filled.Forum, contentDescription = "Chat History")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Filter Card ──────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Select Filters", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)

                    // Date range row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Start date
                        OutlinedCard(
                            onClick = { showStartPicker = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("From", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    dateFormat.format(Date(startMillis)),
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp
                                )
                            }
                        }
                        // End date
                        OutlinedCard(
                            onClick = { showEndPicker = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("To", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    dateFormat.format(Date(endMillis)),
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    // Quick range chips
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QuickRangeChip("This Week") {
                            startMillis = com.smartexpense.tracker.util.DateUtils.getStartOfWeek()
                            endMillis = System.currentTimeMillis()
                        }
                        QuickRangeChip("This Month") {
                            startMillis = com.smartexpense.tracker.util.DateUtils.getStartOfMonth()
                            endMillis = System.currentTimeMillis()
                        }
                        QuickRangeChip("Last 30 Days") {
                            startMillis = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
                            endMillis = System.currentTimeMillis()
                        }
                        QuickRangeChip("Last 90 Days") {
                            startMillis = System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000
                            endMillis = System.currentTimeMillis()
                        }
                    }

                    HorizontalDivider()

                    // Category filter
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Category,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Category", fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Box {
                            FilterChip(
                                selected = selectedCategory != null,
                                onClick = { showCategoryMenu = true },
                                label = {
                                    Text(
                                        selectedCategory ?: "All Categories",
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                trailingIcon = {
                                    if (selectedCategory != null) {
                                        IconButton(
                                            onClick = { selectedCategory = null },
                                            modifier = Modifier.size(18.dp)
                                        ) {
                                            Icon(Icons.Filled.Close, contentDescription = "Clear",
                                                modifier = Modifier.size(14.dp))
                                        }
                                    } else {
                                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null,
                                            modifier = Modifier.size(18.dp))
                                    }
                                }
                            )
                            DropdownMenu(
                                expanded = showCategoryMenu,
                                onDismissRequest = { showCategoryMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("All Categories") },
                                    onClick = { selectedCategory = null; showCategoryMenu = false }
                                )
                                categories.forEach { cat ->
                                    DropdownMenuItem(
                                        text = { Text(cat.name) },
                                        onClick = { selectedCategory = cat.name; showCategoryMenu = false }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Analyze Button ───────────────────────────────────
            Button(
                onClick = {
                    if (!isAnalyzing) {
                        isAnalyzing = true
                        analysisResult = null
                        scope.launch {
                            try {
                                analysisResult = onAnalyzeAsync(startMillis, endMillis, selectedCategory)
                            } finally {
                                isAnalyzing = false
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                enabled = !isAnalyzing
            ) {
                if (isAnalyzing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (isAnalyzing) "Analyzing..." else "Analyze Transactions",
                    fontWeight = FontWeight.SemiBold
                )
            }

            // ── Loading Progress Card ────────────────────────────
            AnimatedVisibility(
                visible = isAnalyzing,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                AnalysisProgressCard()
            }

            // ── Analysis Result ──────────────────────────────────
            AnimatedVisibility(
                visible = analysisResult != null && !isAnalyzing,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                analysisResult?.let { result ->
                    AnalysisResultCard(result)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // ── Date Pickers ─────────────────────────────────────────────
    if (showStartPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = startMillis)
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { startMillis = it }
                    showStartPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showStartPicker = false }) { Text("Cancel") }
            }
        ) { DatePicker(state = state) }
    }
    if (showEndPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = endMillis)
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { endMillis = it }
                    showEndPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showEndPicker = false }) { Text("Cancel") }
            }
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun QuickRangeChip(label: String, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label, fontSize = 11.sp) },
        shape = RoundedCornerShape(8.dp),
        border = null
    )
}

@Composable
private fun AnalysisProgressCard() {
    val steps = listOf(
        "Gathering transactions..." to Icons.Filled.Receipt,
        "Analyzing spending patterns..." to Icons.Filled.TrendingUp,
        "Generating insights..." to Icons.Filled.AutoAwesome
    )

    var currentStep by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        currentStep = 0
        delay(1500)
        currentStep = 1
        delay(2000)
        currentStep = 2
    }

    val pulseAlpha by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header with animated spinner
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.5.dp,
                    color = GreenPrimary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "AI is analyzing your data",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = GreenPrimary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )

            // Progress steps
            steps.forEachIndexed { index, (label, icon) ->
                val isActive = index == currentStep
                val isDone = index < currentStep
                val alpha = when {
                    isDone -> 1f
                    isActive -> pulseAlpha
                    else -> 0.35f
                }

                Row(
                    modifier = Modifier.alpha(alpha),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isDone) GreenPrimary
                                else if (isActive) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isDone) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = androidx.compose.ui.graphics.Color.White
                            )
                        } else {
                            Icon(
                                icon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (isActive) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        label,
                        fontSize = 13.sp,
                        fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
                        color = if (isDone || isActive) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Detects whether a line is a section heading.
 * Matches patterns like:
 *   - "## Heading" or "### Heading"  (markdown)
 *   - "1. HEADING" or "2. Heading"   (numbered)
 *   - "FINANCIAL HEALTH OVERVIEW"     (all-caps)
 *   - "**Heading**"                   (bold markdown)
 */
private fun isSectionHeading(line: String): Boolean {
    val t = line.trim()
    if (t.length < 3) return false
    // Markdown headings
    if (t.startsWith("#")) return true
    // Bold-only line **text**
    if (t.startsWith("**") && t.endsWith("**") && t.length > 4) return true
    // Numbered heading like "1. EXPENSE ANALYSIS"
    if (t.matches(Regex("^\\d+\\.\\s+[A-Z].*"))) return true
    // All-caps line (at least 3 word characters, no lowercase)
    val stripped = t.replace(Regex("[^A-Za-z ]"), "").trim()
    if (stripped.length > 2 && stripped == stripped.uppercase() && stripped.any { it.isLetter() }) return true
    return false
}

/** Strips markdown heading markers and bold markers for display. */
private fun cleanHeading(line: String): String {
    return line.trim()
        .replace(Regex("^#+\\s*"), "")     // ## Heading -> Heading
        .replace(Regex("^\\d+\\.\\s*"), "") // 1. Heading -> Heading
        .removeSurrounding("**")
        .trim()
}

/** Strips leading bullet/dash markers for display. */
private fun cleanBullet(line: String): String {
    return line.trim()
        .removePrefix("-").removePrefix("•").removePrefix("*")
        .replace(Regex("^\\d+[.):]\\s*"), "")
        .trim()
}

/** Renders inline bold (**text**) using AnnotatedString. */
@Composable
private fun RichText(
    text: String,
    fontSize: androidx.compose.ui.unit.TextUnit = 13.sp,
    lineHeight: androidx.compose.ui.unit.TextUnit = 20.sp,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    val annotated = remember(text) {
        val builder = androidx.compose.ui.text.AnnotatedString.Builder()
        var remaining = text
        val boldPattern = Regex("\\*\\*(.+?)\\*\\*")
        while (remaining.isNotEmpty()) {
            val match = boldPattern.find(remaining)
            if (match != null) {
                builder.append(remaining.substring(0, match.range.first))
                builder.pushStyle(
                    androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold)
                )
                builder.append(match.groupValues[1])
                builder.pop()
                remaining = remaining.substring(match.range.last + 1)
            } else {
                builder.append(remaining)
                break
            }
        }
        builder.toAnnotatedString()
    }
    Text(
        text = annotated,
        fontSize = fontSize,
        lineHeight = lineHeight,
        color = color
    )
}

@Composable
private fun AnalysisResultCard(result: String) {
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            Brush.linearGradient(listOf(GreenPrimary, BluePrimary))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text("Analysis Result", fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                // Copy button
                IconButton(
                    onClick = {
                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(result))
                        copied = true
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                        contentDescription = "Copy",
                        modifier = Modifier.size(16.dp),
                        tint = if (copied) GreenPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider()

            // Parse and render the result with rich formatting
            val lines = result.lines()
            var i = 0
            while (i < lines.size) {
                val line = lines[i]
                val trimmed = line.trim()

                when {
                    trimmed.isBlank() -> {
                        // Skip blank lines (spacing is handled by Arrangement.spacedBy)
                        i++
                    }

                    isSectionHeading(trimmed) -> {
                        val heading = cleanHeading(trimmed)
                        if (heading.isNotBlank()) {
                            // Section heading with accent color and divider
                            if (i > 0) Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(16.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(GreenPrimary)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    heading.uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 0.8.sp
                                )
                            }
                        }
                        i++
                    }

                    trimmed.startsWith("-") || trimmed.startsWith("•") || trimmed.startsWith("*") ||
                    trimmed.matches(Regex("^\\d+[.)]\\s+.*")) -> {
                        // Bullet point or numbered item
                        val bulletText = cleanBullet(trimmed)
                        if (bulletText.isNotBlank()) {
                            Row(
                                modifier = Modifier.padding(start = 8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 7.dp)
                                        .size(5.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                RichText(
                                    text = bulletText,
                                    fontSize = 13.sp,
                                    lineHeight = 20.sp
                                )
                            }
                        }
                        i++
                    }

                    else -> {
                        // Regular paragraph text
                        RichText(
                            text = trimmed,
                            fontSize = 13.sp,
                            lineHeight = 20.sp
                        )
                        i++
                    }
                }
            }
        }
    }
}

