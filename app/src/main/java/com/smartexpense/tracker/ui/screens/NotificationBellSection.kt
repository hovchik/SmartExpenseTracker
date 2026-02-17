package com.smartexpense.tracker.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.smartexpense.tracker.data.model.InAppNotification
import com.smartexpense.tracker.data.model.InAppNotificationType
import com.smartexpense.tracker.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    onClearAll: () -> Unit
) {
    var showPanel by remember { mutableStateOf(false) }

    Box {
        // Bell icon button
        IconButton(onClick = {
            showPanel = true
            if (unreadCount > 0) onMarkAllRead()
        }) {
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
            onMarkRead = onMarkRead,
            onClearAll = {
                onClearAll()
                showPanel = false
            },
            onDismiss = { showPanel = false }
        )
    }
}

@Composable
private fun NotificationPanel(
    notifications: List<InAppNotification>,
    onMarkRead: (String) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit
) {
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
                    Row {
                        if (notifications.isNotEmpty()) {
                            TextButton(onClick = onClearAll, contentPadding = PaddingValues(4.dp)) {
                                Text("Clear all", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "Close", modifier = Modifier.size(18.dp))
                        }
                    }
                }

                HorizontalDivider()

                if (notifications.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Filled.NotificationsNone,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("No notifications yet",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Transactions detected via SMS or\nbanking apps will appear here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(notifications, key = { it.id }) { notif ->
                            NotificationRow(notif, onMarkRead)
                        }
                    }
                }
            }
        }
    }
}

private val timeFormatter = SimpleDateFormat("MMM d, HH:mm", Locale.US)

@Composable
private fun NotificationRow(
    notif: InAppNotification,
    onMarkRead: (String) -> Unit
) {
    val isUnread = !notif.isRead
    val bgColor = if (isUnread)
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
    else Color.Transparent

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
                timeFormatter.format(Date(notif.timestamp)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
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
