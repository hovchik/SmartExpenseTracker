package com.flowsense.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FiberNew
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowsense.app.data.model.MessagePattern
import com.flowsense.app.data.model.PatternKind
import com.flowsense.app.data.model.PatternStatus
import com.flowsense.app.ui.theme.GreenPrimary
import com.flowsense.app.ui.theme.OrangeWarning
import com.flowsense.app.ui.theme.RedExpense
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manage learned message-source patterns: SMS senders and notification apps
 * that produced financial messages. Each source can be included (tracked)
 * or excluded (ignored); newly detected sources await the user's decision.
 */
@Composable
fun MessagePatternsScreen(
    patterns: List<MessagePattern>,
    onInclude: (String) -> Unit,
    onExclude: (String) -> Unit,
    onDelete: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    val smsPatterns = remember(patterns) {
        patterns.filter { it.kind == PatternKind.SMS }.sortedWith(patternOrder)
    }
    val notifPatterns = remember(patterns) {
        patterns.filter { it.kind == PatternKind.NOTIFICATION }.sortedWith(patternOrder)
    }
    val smsPending = smsPatterns.count { it.status == PatternStatus.PENDING }
    val notifPending = notifPatterns.count { it.status == PatternStatus.PENDING }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
            Column {
                Text("Message Sources", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "Choose which senders and apps to track",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { TabLabel("SMS Senders", smsPatterns.size, smsPending) },
                icon = { Icon(Icons.Filled.Sms, contentDescription = null, modifier = Modifier.size(20.dp)) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { TabLabel("App Notifications", notifPatterns.size, notifPending) },
                icon = { Icon(Icons.Filled.Apps, contentDescription = null, modifier = Modifier.size(20.dp)) }
            )
        }

        val shown = if (selectedTab == 0) smsPatterns else notifPatterns
        if (shown.isEmpty()) {
            EmptyPatternsHint(isSms = selectedTab == 0)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items = shown, key = { it.id }) { pattern ->
                    PatternCard(
                        pattern = pattern,
                        onInclude = { onInclude(pattern.id) },
                        onExclude = { onExclude(pattern.id) },
                        onDelete = { onDelete(pattern.id) }
                    )
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

/** Pending first (newest first), then included, then excluded. */
private val patternOrder = compareBy<MessagePattern>(
    { it.status != PatternStatus.PENDING },
    { it.status == PatternStatus.EXCLUDED },
    { -it.lastSeen }
)

@Composable
private fun TabLabel(title: String, count: Int, pendingCount: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$title ($count)", fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (pendingCount > 0) {
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(OrangeWarning)
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            ) {
                Text("$pendingCount", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun EmptyPatternsHint(isSms: Boolean) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                if (isSms) Icons.Filled.Sms else Icons.Filled.Apps,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "No sources detected yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                if (isSms)
                    "Run an SMS inbox scan or wait for a banking SMS to arrive. " +
                        "Each financial sender appears here so you can keep or ignore it."
                else
                    "When a banking app posts a transaction notification, the app is " +
                        "detected here so you can decide whether to keep tracking it.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun PatternCard(
    pattern: MessagePattern,
    onInclude: () -> Unit,
    onExclude: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }

    val (statusColor, statusLabel) = when (pattern.status) {
        PatternStatus.PENDING -> OrangeWarning to "New — decide to keep or ignore"
        PatternStatus.INCLUDED -> GreenPrimary to "Tracking"
        PatternStatus.EXCLUDED -> RedExpense to "Ignored"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = if (pattern.status == PatternStatus.PENDING)
            CardDefaults.cardColors(containerColor = OrangeWarning.copy(alpha = 0.08f))
        else CardDefaults.cardColors()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    when (pattern.status) {
                        PatternStatus.PENDING -> Icons.Filled.FiberNew
                        PatternStatus.INCLUDED -> Icons.Filled.CheckCircle
                        PatternStatus.EXCLUDED -> Icons.Filled.Block
                    },
                    contentDescription = statusLabel,
                    tint = statusColor,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        pattern.displayName.ifBlank { pattern.sourceKey },
                        fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        statusLabel + " · ${pattern.matchCount} message" + (if (pattern.matchCount != 1) "s" else ""),
                        fontSize = 12.sp, color = statusColor
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.Delete, contentDescription = "Forget this source",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    if (pattern.kind == PatternKind.NOTIFICATION && pattern.sourceKey != pattern.displayName.lowercase()) {
                        Text(
                            pattern.sourceKey,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    if (pattern.sampleText.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                pattern.sampleText,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    Text(
                        "Last seen ${dateFormat.format(Date(pattern.lastSeen))}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Include / Exclude actions
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val included = pattern.status == PatternStatus.INCLUDED
                val excluded = pattern.status == PatternStatus.EXCLUDED
                Button(
                    onClick = onInclude,
                    enabled = !included,
                    modifier = Modifier.weight(1f).height(38.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GreenPrimary,
                        disabledContainerColor = GreenPrimary.copy(alpha = 0.35f),
                        disabledContentColor = Color.White
                    )
                ) {
                    Text(if (included) "Tracking" else "Keep tracking", fontSize = 13.sp)
                }
                OutlinedButton(
                    onClick = onExclude,
                    enabled = !excluded,
                    modifier = Modifier.weight(1f).height(38.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RedExpense)
                ) {
                    Text(if (excluded) "Ignored" else "Ignore", fontSize = 13.sp)
                }
            }
        }
    }
}
