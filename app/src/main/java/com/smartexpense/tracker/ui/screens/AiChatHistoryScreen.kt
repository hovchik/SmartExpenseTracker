package com.smartexpense.tracker.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartexpense.tracker.data.model.AiConversation
import com.smartexpense.tracker.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatHistoryScreen(
    conversations: List<AiConversation>,
    onDeleteConversation: (String) -> Unit,
    onClearAll: () -> Unit,
    onNavigateBack: () -> Unit
) {
    var showClearDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Forum,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("AI Chat History", fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (conversations.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear all")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (conversations.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Forum,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No conversations yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Run an AI analysis to see results here",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            // Group conversations by dateKey
            val grouped = remember(conversations) {
                conversations
                    .sortedByDescending { it.timestamp }
                    .groupBy { it.dateKey }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                grouped.forEach { (dateKey, convos) ->
                    item(key = "header_$dateKey") {
                        DateGroupHeader(dateKey = dateKey, count = convos.size)
                    }
                    items(convos, key = { it.id }) { conversation ->
                        ConversationCard(
                            conversation = conversation,
                            onDelete = { onDeleteConversation(conversation.id) }
                        )
                    }
                }
                // Bottom spacer
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }

    // Clear all confirmation dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear all conversations?") },
            text = { Text("This will permanently delete all AI conversation history.") },
            confirmButton = {
                TextButton(onClick = {
                    onClearAll()
                    showClearDialog = false
                }) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun DateGroupHeader(dateKey: String, count: Int) {
    val displayDate = remember(dateKey) {
        try {
            val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateKey)
            val today = Calendar.getInstance()
            val dateCal = Calendar.getInstance().apply { time = parsed!! }

            when {
                today.get(Calendar.YEAR) == dateCal.get(Calendar.YEAR) &&
                    today.get(Calendar.DAY_OF_YEAR) == dateCal.get(Calendar.DAY_OF_YEAR) -> "Today"
                today.get(Calendar.YEAR) == dateCal.get(Calendar.YEAR) &&
                    today.get(Calendar.DAY_OF_YEAR) - dateCal.get(Calendar.DAY_OF_YEAR) == 1 -> "Yesterday"
                else -> SimpleDateFormat("MMMM dd, yyyy", Locale.US).format(parsed!!)
            }
        } catch (_: Exception) {
            dateKey
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.CalendarToday,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            displayDate,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            "($count)",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ConversationCard(
    conversation: AiConversation,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.US) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Prompt header row — always visible, tappable to expand/collapse
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { expanded = !expanded }
                    .padding(2.dp),
                verticalAlignment = Alignment.Top
            ) {
                // User icon
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(
                            Brush.linearGradient(listOf(BluePrimary, PurpleAccent))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = null,
                        tint = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "You",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        conversation.prompt,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        maxLines = if (expanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 18.sp
                    )
                    Text(
                        timeFormat.format(Date(conversation.timestamp)),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
                // Expand/collapse indicator
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // AI response — collapsible
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(10.dp))

                    // AI response header
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(
                                    Brush.linearGradient(listOf(GreenPrimary, GreenDark))
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
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            conversation.aiModelName ?: "AI",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        // Delete button
                        IconButton(
                            onClick = { showDeleteDialog = true },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Delete",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Response text
                    Text(
                        conversation.response,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        fontFamily = FontFamily.Default,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }

    // Delete confirmation
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete conversation?") },
            text = { Text("This conversation will be permanently removed.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteDialog = false
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
