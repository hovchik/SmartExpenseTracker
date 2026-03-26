package com.flowsense.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.flowsense.app.data.model.InAppNotification
import com.flowsense.app.data.model.InAppNotificationType
import com.flowsense.app.ui.theme.*

/**
 * Bell icon that shows an unread-count badge.
 * Tapping it opens a full-screen dialog listing all in-app notifications.
 */
@Composable
fun NotificationBell(
    notifications: List<InAppNotification>,
    unreadCount: Int,
    onMarkRead: (String) -> Unit,
    onMarkAllRead: () -> Unit,
    onDelete: (String) -> Unit,
    onClearAll: () -> Unit
) {
    var showPanel by remember { mutableStateOf(false) }

    Box {
        // Bell icon button
        IconButton(onClick = { showPanel = true }) {
            Icon(
                imageVector = if (unreadCount > 0) Icons.Filled.NotificationsActive else Icons.Filled.Notifications,
                contentDescription = "Notifications",
                tint = if (unreadCount > 0) OrangeWarning else MaterialTheme.colorScheme.onSurface
            )
        }

        // Red badge with count
        if (unreadCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 8.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(RedExpense),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (unreadCount > 99) "99+" else "$unreadCount",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    // Notification panel dialog
    if (showPanel) {
        NotificationPanel(
            notifications = notifications,
            unreadCount = unreadCount,
            onMarkRead = onMarkRead,
            onMarkAllRead = onMarkAllRead,
            onDelete = onDelete,
            onClearAll = {
                onClearAll()
                showPanel = false
            },
            onDismiss = { showPanel = false }
        )
    }
}

private enum class NotifFilter { ALL, UNREAD }

@Composable
private fun NotificationPanel(
    notifications: List<InAppNotification>,
    unreadCount: Int,
    onMarkRead: (String) -> Unit,
    onMarkAllRead: () -> Unit,
    onDelete: (String) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit
) {
    var filter by remember { mutableStateOf(NotifFilter.ALL) }

    val filtered = remember(notifications, filter) {
        when (filter) {
            NotifFilter.ALL -> notifications
            NotifFilter.UNREAD -> notifications.filter { !it.isRead }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
        ) {
            Column {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Notifications, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Notifications", style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", modifier = Modifier.size(18.dp))
                    }
                }

                // Filter tabs + action buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = filter == NotifFilter.ALL,
                        onClick = { filter = NotifFilter.ALL },
                        label = { Text("All", fontSize = 12.sp) },
                        modifier = Modifier.height(32.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = filter == NotifFilter.UNREAD,
                        onClick = { filter = NotifFilter.UNREAD },
                        label = {
                            Text(
                                if (unreadCount > 0) "Unread ($unreadCount)" else "Unread",
                                fontSize = 12.sp
                            )
                        },
                        modifier = Modifier.height(32.dp)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    if (unreadCount > 0) {
                        TextButton(
                            onClick = onMarkAllRead,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Filled.DoneAll, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Read all", fontSize = 11.sp)
                        }
                    }
                    if (notifications.isNotEmpty()) {
                        TextButton(
                            onClick = onClearAll,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Clear all", fontSize = 11.sp, color = RedExpense)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                HorizontalDivider()

                if (filtered.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Filled.NotificationsNone,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                if (filter == NotifFilter.UNREAD) "No unread notifications" else "No notifications yet",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                if (filter == NotifFilter.UNREAD) "All caught up!"
                                else "Transactions detected via SMS or\nbanking apps will appear here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(filtered, key = { it.id }) { notif ->
                            SwipeToDismissNotification(
                                notif = notif,
                                onMarkRead = onMarkRead,
                                onDelete = onDelete
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDismissNotification(
    notif: InAppNotification,
    onMarkRead: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete(notif.id)
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val color by animateColorAsState(
                when (dismissState.targetValue) {
                    SwipeToDismissBoxValue.EndToStart -> RedExpense
                    else -> Color.Transparent
                },
                label = "swipeBg"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color.White)
                }
            }
        },
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true
    ) {
        NotificationRow(notif, onMarkRead)
    }
}

@Composable
private fun NotificationRow(
    notif: InAppNotification,
    onMarkRead: (String) -> Unit
) {
    val isUnread = !notif.isRead
    val bgColor = if (isUnread)
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
    else MaterialTheme.colorScheme.surface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .clickable { if (isUnread) onMarkRead(notif.id) }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Type icon
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(notifIconBg(notif.type)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = notifIcon(notif.type),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(
                    notif.title,
                    fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
                if (isUnread) {
                    Box(
                        modifier = Modifier.size(8.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                notif.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                relativeTime(notif.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

private fun relativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60_000 -> "Just now"
        diff < 3_600_000 -> "${diff / 60_000}m ago"
        diff < 86_400_000 -> "${diff / 3_600_000}h ago"
        diff < 172_800_000 -> "Yesterday"
        diff < 604_800_000 -> "${diff / 86_400_000}d ago"
        else -> {
            val sdf = java.text.SimpleDateFormat("MMM d", java.util.Locale.US)
            sdf.format(java.util.Date(timestamp))
        }
    }
}

private fun notifIcon(type: InAppNotificationType) = when (type) {
    InAppNotificationType.TRANSACTION_DETECTED -> Icons.Filled.Notifications
    InAppNotificationType.CATEGORY_CREATED     -> Icons.Filled.Label
    InAppNotificationType.SMS_PARSED           -> Icons.Filled.Sms
    InAppNotificationType.BUDGET_ALERT         -> Icons.Filled.Warning
}

@Composable
private fun notifIconBg(type: InAppNotificationType): Color = when (type) {
    InAppNotificationType.TRANSACTION_DETECTED -> BluePrimary
    InAppNotificationType.CATEGORY_CREATED     -> PurpleAccent
    InAppNotificationType.SMS_PARSED           -> GreenPrimary
    InAppNotificationType.BUDGET_ALERT         -> OrangeWarning
}
