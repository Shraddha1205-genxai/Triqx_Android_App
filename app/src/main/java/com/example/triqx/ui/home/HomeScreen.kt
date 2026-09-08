package com.example.triqx.ui.home

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Grid3x3
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.triqx.data.local.ChatMessage
import com.example.triqx.ui.components.AppIcon
import com.example.triqx.ui.notifications.Conversation
import com.example.triqx.ui.notifications.NotificationViewModel
import com.example.triqx.utils.EmailUtils
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: NotificationViewModel,
    onConversationClick: (String) -> Unit,
    onViewContact: (String) -> Unit,
    onNavigateToContacts: () -> Unit,
    onNavigateToApps: () -> Unit
) {
    val context = LocalContext.current
    val groups by viewModel.groupedPriorityNotifications.collectAsStateWithLifecycle()
    val isEnabled = viewModel.isNotificationServiceEnabled()
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    val filteredGroups = remember(groups, searchQuery) {
        if (searchQuery.isBlank()) {
            groups
        } else {
            groups.filter { group ->
                val titleMatch = group.title.contains(searchQuery, ignoreCase = true)
                val contactMatch = group.contact?.displayName?.contains(searchQuery, ignoreCase = true) == true ||
                                   group.contact?.officialName?.contains(searchQuery, ignoreCase = true) == true ||
                                   group.contact?.phoneNumbers?.any { it.contains(searchQuery, ignoreCase = true) } == true ||
                                   group.contact?.emails?.any { it.contains(searchQuery, ignoreCase = true) } == true

                val idMatch = group.senderIdentifier?.contains(searchQuery, ignoreCase = true) == true ||
                               group.receiverIdentifier?.contains(searchQuery, ignoreCase = true) == true
                val pkgMatch = group.packageName.contains(searchQuery, ignoreCase = true)
                val msgMatch = group.messages.any {
                    it.senderName.contains(searchQuery, ignoreCase = true) ||
                    it.bodyText.contains(searchQuery, ignoreCase = true)
                }

                titleMatch || contactMatch || idMatch || pkgMatch || msgMatch
            }
        }
    }

    val totalMessages = remember(groups) {
        groups.sumOf { it.messages.size }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .statusBarsPadding()
        ) {
            // Google Drive Style Floating Search Bar Pill
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .height(54.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )

                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = if (groups.isEmpty()) "Search in Triqx" else "Search $totalMessages messages in ${groups.size} VIPs...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "Clear search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // VIP Count Avatar Badge
                    Surface(
                        modifier = Modifier.size(32.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = if (totalMessages > 0) "$totalMessages" else "VIP",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            // Section Header: Title + Count + Action Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Priority Feed",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (filteredGroups.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "(${filteredGroups.size})",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (groups.isNotEmpty()) {
                        IconButton(
                            onClick = { showClearConfirmDialog = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.DeleteSweep,
                                contentDescription = "Clear All",
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // Permission Warning Banner if listener disabled
            if (!isEnabled) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(22.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Notification Access Required",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = "Enable access to capture priority messages",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Button(
                            onClick = {
                                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                context.startActivity(intent)
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Turn On", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Main Content: Empty State vs Conversation List
            if (groups.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            modifier = Modifier.size(80.dp),
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.NotificationsActive,
                                    contentDescription = null,
                                    modifier = Modifier.size(38.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Text(
                            text = "No Priority Messages",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Notifications from your Priority Contacts and Important Apps will appear here as WhatsApp-style chats.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = onNavigateToContacts,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                            ) {
                                Icon(Icons.Default.Contacts, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Contacts", fontWeight = FontWeight.SemiBold)
                            }
                            Button(
                                onClick = onNavigateToApps,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                            ) {
                                Icon(Icons.Default.Grid3x3, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Apps", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            } else if (filteredGroups.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No conversations match \"$searchQuery\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val onViewContactStable = remember(onViewContact) { onViewContact }
                val onDismissGroupAction = remember(viewModel, context) {
                    { group: Conversation ->
                        viewModel.dismissGroup(group)
                        Toast.makeText(context, "Dismissed", Toast.LENGTH_SHORT).show()
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = filteredGroups,
                        key = { it.groupKey },
                        contentType = { "conversation_card" }
                    ) { group ->
                        val dismissHandler = remember(group.groupKey, onDismissGroupAction) {
                            { onDismissGroupAction(group) }
                        }
                        val clickHandler = remember(group.groupKey, onConversationClick) {
                            { onConversationClick(group.groupKey) }
                        }

                        WhatsAppConversationCard(
                            group = group,
                            onClick = clickHandler,
                            onDismissGroup = dismissHandler,
                            onViewContact = onViewContactStable
                        )
                    }
                }
            }
        }
    }

    // Clear Confirmation Dialog
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    text = "Clear Priority Feed?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = { Text("This will dismiss all current priority notifications from your device.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllPriorityNotifications()
                        showClearConfirmDialog = false
                        Toast.makeText(context, "All cleared", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun WhatsAppConversationCard(
    group: Conversation,
    onClick: () -> Unit,
    onDismissGroup: () -> Unit,
    onViewContact: (String) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val formattedDate = remember(group.latestTimestamp) {
        dateFormat.format(Date(group.latestTimestamp))
    }
    val latest = group.messages.firstOrNull()
    val groupTitle = group.title

    val recentMessagePreview = remember(latest, groupTitle) {
        if (latest == null) ""
        else {
            val prefix = when {
                latest.isFromYou -> "You: "
                latest.senderName.isNotBlank() &&
                        !latest.senderName.equals(groupTitle, ignoreCase = true) &&
                        !latest.senderName.equals("You", ignoreCase = true) -> "${latest.senderName}: "
                else -> ""
            }
            val content = if (!latest.subText.isNullOrBlank()) {
                latest.subText
            } else {
                latest.bodyText.ifBlank { "No content" }
            }
            val cleanBody = content.replace('\n', ' ').trim()
            "$prefix$cleanBody"
        }
    }

    val appName = remember(group.packageName) {
        when {
            group.packageName.contains("whatsapp", ignoreCase = true) -> "WhatsApp"
            group.packageName.contains("messaging", ignoreCase = true) || group.packageName.contains("mms", ignoreCase = true) -> "Messages"
            group.packageName.contains("gm", ignoreCase = true) || group.packageName.contains("gmail", ignoreCase = true) -> "Gmail"
            group.packageName.contains("outlook", ignoreCase = true) -> "Outlook"
            group.packageName.contains("slack", ignoreCase = true) -> "Slack"
            group.packageName.contains("telegram", ignoreCase = true) -> "Telegram"
            else -> group.packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Squircle Avatar
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clickable {
                        if (group.contact != null) {
                            onViewContact("contact_id_${group.contact.id}")
                        } else {
                            onClick()
                        }
                    }
            ) {
                Surface(
                    modifier = Modifier
                        .size(46.dp)
                        .shadow(2.dp, RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    AppIcon(
                        packageName = group.packageName,
                        appName = groupTitle,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(1.dp)
                    )
                }
            }

            // Middle: Name, Email tag, VIP badge, App Name, and Recent Message Preview
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = groupTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    val senderEmail = EmailUtils.cleanEmail(group.senderIdentifier)
                    if (!senderEmail.isNullOrBlank() && !senderEmail.equals(groupTitle, ignoreCase = true)) {
                        Text(
                            text = "($senderEmail)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (group.contact != null) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "VIP",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                val cleanReceiver = EmailUtils.cleanEmail(group.receiverIdentifier)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 1.dp)
                ) {
                    Text(
                        text = appName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!cleanReceiver.isNullOrBlank()) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Text(
                            text = cleanReceiver,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (recentMessagePreview.isNotBlank()) {
                    Text(
                        text = recentMessagePreview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            // Right Column: Timestamp + Dismiss Button
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(
                    onClick = onDismissGroup,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Dismiss conversation",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun GoogleMessageBubble(
    message: ChatMessage,
    groupTitle: String
) {
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val formattedTime = remember(message.timestamp) {
        timeFormat.format(Date(message.timestamp))
    }
    val isFromYou = message.isFromYou
    val isGroupMember = !isFromYou && message.senderName.isNotBlank() &&
                        !message.senderName.equals(groupTitle, ignoreCase = true) &&
                        !message.senderName.equals("You", ignoreCase = true)

    val bubbleShape = if (isFromYou) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomEnd = 16.dp, bottomStart = 16.dp)
    } else {
        RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp)
    }

    val bubbleColor = if (isFromYou) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }

    val textColor = if (isFromYou) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val subTextColor = if (isFromYou) {
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.outline
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalAlignment = if (isFromYou) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = bubbleShape,
            color = bubbleColor,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (isFromYou) {
                    Text(
                        text = "You",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                } else if (isGroupMember) {
                    Text(
                        text = message.senderName,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }

                if (!message.subText.isNullOrBlank()) {
                    val subjectDisplay = message.subText.removePrefix("Subject:").trim()
                    Text(
                        text = subjectDisplay,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isFromYou) {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }

                Text(
                    text = message.bodyText.ifEmpty { "No Content" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor
                )

                Row(
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = subTextColor
                    )
                }
            }
        }
    }
}
