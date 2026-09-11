package com.example.triqx.ui.home

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.triqx.data.local.ChatMessage
import com.example.triqx.ui.components.AppIcon
import com.example.triqx.ui.components.ProfileAvatarBadge
import com.example.triqx.ui.components.TriqxSearchBar
import com.example.triqx.ui.theme.Dimens
import com.example.triqx.ui.notifications.Conversation
import com.example.triqx.ui.notifications.NotificationViewModel
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves human-readable app display names with in-memory caching.
 */
object AppNameResolver {
    private val nameCache = ConcurrentHashMap<String, String>()

    fun getDisplayName(context: Context, packageName: String): String {
        return nameCache.getOrPut(packageName) {
            when {
                packageName.contains("whatsapp", ignoreCase = true) -> "WhatsApp"
                packageName.contains("gm", ignoreCase = true) || packageName.contains("gmail", ignoreCase = true) -> "Gmail"
                packageName.contains("outlook", ignoreCase = true) -> "Outlook"
                packageName.contains("slack", ignoreCase = true) -> "Slack"
                packageName.contains("telegram", ignoreCase = true) -> "Telegram"
                packageName.contains("messaging", ignoreCase = true) || packageName.contains("mms", ignoreCase = true) -> "Messages"
                else -> {
                    try {
                        val pm = context.packageManager
                        val appInfo = pm.getApplicationInfo(packageName, 0)
                        val label = pm.getApplicationLabel(appInfo).toString()
                        if (label.isNotBlank()) label else fallback(packageName)
                    } catch (_: Exception) {
                        fallback(packageName)
                    }
                }
            }
        }
    }

    private fun fallback(packageName: String): String {
        return packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
    }
}

/**
 * Model representing an app filter chip.
 */
data class AppFilter(
    val id: String,
    val displayName: String,
    val packageName: String?,
    val latestTimestamp: Long
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: NotificationViewModel,
    onConversationClick: (String) -> Unit,
    onViewContact: (String) -> Unit,
    onNavigateToContacts: () -> Unit,
    onNavigateToApps: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    userInitials: String = "U"
) {
    val context = LocalContext.current
    val groups by viewModel.groupedPriorityNotifications.collectAsStateWithLifecycle()
    val isEnabled = viewModel.isNotificationServiceEnabled()
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedAppId by rememberSaveable { mutableStateOf("ALL") }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    // Dynamic app filters: "All" first, then only apps with active notifications, ordered by most recent notification
    val availableAppFilters = remember(groups, context) {
        if (groups.isEmpty()) {
            emptyList()
        } else {
            val appGroups = groups.groupBy { conv ->
                AppNameResolver.getDisplayName(context, conv.packageName)
            }

            val sortedApps = appGroups.map { (name, convList) ->
                val mostRecentTimestamp = convList.maxOfOrNull { it.latestTimestamp } ?: 0L
                val representativePackage = convList.first().packageName
                AppFilter(
                    id = name,
                    displayName = name,
                    packageName = representativePackage,
                    latestTimestamp = mostRecentTimestamp
                )
            }.sortedByDescending { it.latestTimestamp }

            listOf(
                AppFilter(
                    id = "ALL",
                    displayName = "All",
                    packageName = null,
                    latestTimestamp = Long.MAX_VALUE
                )
            ) + sortedApps
        }
    }

    // Reset filter to "ALL" if the selected app has no remaining notifications
    LaunchedEffect(availableAppFilters) {
        if (selectedAppId != "ALL" && availableAppFilters.none { it.id == selectedAppId }) {
            selectedAppId = "ALL"
        }
    }

    val filteredGroups = remember(groups, searchQuery, selectedAppId, context) {
        var result = groups

        if (selectedAppId != "ALL") {
            result = result.filter { conv ->
                AppNameResolver.getDisplayName(context, conv.packageName) == selectedAppId
            }
        }

        if (searchQuery.isNotBlank()) {
            result = result.filter { group ->
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

        result
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
            // Top App Bar: "Triqx" on Top Left, Account Logo on Top Right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.SpacingStandard, vertical = Dimens.SpacingSmall),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Triqx",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall)
                ) {
                    if (groups.isNotEmpty()) {
                        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                            IconButton(
                                onClick = { showClearConfirmDialog = true },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    Icons.Default.DeleteSweep,
                                    contentDescription = "Clear All",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    ProfileAvatarBadge(
                        initials = userInitials,
                        onClick = onNavigateToSettings
                    )
                }
            }

            Spacer(modifier = Modifier.height(Dimens.SpacingNano))

            // Google Drive Style Floating Search Bar Pill
            TriqxSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholder = "Search in Triqx",
                contentPadding = PaddingValues(
                    start = Dimens.SpacingStandard,
                    end = Dimens.SpacingStandard,
                    top = Dimens.SpacingNano,
                    bottom = Dimens.SpacingNano
                )
            )

            // App Filter Chips (App-wise filter: All, WhatsApp, Gmail, etc. with most recent first)
            if (availableAppFilters.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Dimens.SpacingPico, bottom = Dimens.SpacingPico),
                    contentPadding = PaddingValues(horizontal = Dimens.SpacingStandard),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall)
                ) {
                    items(
                        items = availableAppFilters,
                        key = { it.id }
                    ) { filter ->
                        val isSelected = filter.id == selectedAppId
                        TriqxFilterChip(
                            label = filter.displayName,
                            packageName = filter.packageName,
                            isSelected = isSelected,
                            onClick = {
                                selectedAppId = if (isSelected && filter.id != "ALL") "ALL" else filter.id
                            }
                        )
                    }
                }
            }

            // Permission Warning Banner if listener disabled
            if (!isEnabled) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.SpacingStandard, vertical = Dimens.SpacingMicro),
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
                        .weight(1f)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 80.dp)
                        .offset(y = (-16).dp)
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            modifier = Modifier.size(72.dp),
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.NotificationsActive,
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(Dimens.SpacingStandard))

                        Text(
                            text = "No Messages",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(Dimens.SpacingSmall))
                        Text(
                            text = "Important conversations will appear here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = Dimens.SpacingStandard)
                        )

                        Spacer(modifier = Modifier.height(Dimens.SpacingLarge))

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
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 80.dp)
                        .offset(y = (-16).dp)
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val emptyMessage = when {
                        searchQuery.isNotBlank() && selectedAppId != "ALL" ->
                            "No $selectedAppId messages match \"$searchQuery\""
                        searchQuery.isNotBlank() ->
                            "No messages match \"$searchQuery\""
                        selectedAppId != "ALL" ->
                            "No messages from $selectedAppId"
                        else ->
                            "No messages found"
                    }
                    Text(
                        text = emptyMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val onViewContactStable = remember(onViewContact) { onViewContact }
                val onDeleteConversationAction = remember(viewModel, context) {
                    { group: Conversation ->
                        viewModel.deleteConversation(group)
                        Toast.makeText(context, "Conversation deleted", Toast.LENGTH_SHORT).show()
                    }
                }
                val onDeleteChatsAction = remember(viewModel, context) {
                    { group: Conversation ->
                        viewModel.deleteChats(group)
                        Toast.makeText(context, "Chats deleted", Toast.LENGTH_SHORT).show()
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = Dimens.SpacingNano,
                        bottom = 100.dp
                    )
                ) {
                    items(
                        items = filteredGroups,
                        key = { it.groupKey },
                        contentType = { "conversation_card" }
                    ) { group ->
                        val deleteConversationHandler = remember(group.groupKey, onDeleteConversationAction) {
                            { onDeleteConversationAction(group) }
                        }
                        val deleteChatsHandler = remember(group.groupKey, onDeleteChatsAction) {
                            { onDeleteChatsAction(group) }
                        }
                        val clickHandler = remember(group.groupKey, onConversationClick) {
                            { onConversationClick(group.groupKey) }
                        }

                        WhatsAppConversationCard(
                            group = group,
                            onClick = clickHandler,
                            onDeleteConversation = deleteConversationHandler,
                            onDeleteChats = deleteChatsHandler,
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
            shape = Dimens.DialogShape,
            title = {
                Text(
                    text = "Clear All Messages?",
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

/**
 * Compact, calm filter chip for app-wise filtering.
 */
@Composable
private fun TriqxFilterChip(
    label: String,
    packageName: String?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (isSelected) {
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            }
        ),
        tonalElevation = if (isSelected) 1.dp else 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (packageName != null) {
                AppIcon(
                    packageName = packageName,
                    appName = label,
                    modifier = Modifier
                        .size(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WhatsAppConversationCard(
    group: Conversation,
    onClick: () -> Unit,
    onDeleteConversation: () -> Unit,
    onDeleteChats: () -> Unit,
    onViewContact: (String) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val formattedDate = remember(group.latestTimestamp) {
        dateFormat.format(Date(group.latestTimestamp))
    }
    val latest = group.messages.firstOrNull()
    val groupTitle = group.title

    val density = LocalDensity.current
    var showContextMenu by remember { mutableStateOf(false) }
    var pressOffset by remember { mutableStateOf(DpOffset.Zero) }

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

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(group.groupKey) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    pressOffset = DpOffset(
                        x = with(density) { down.position.x.toDp() },
                        y = with(density) { down.position.y.toDp() }
                    )
                }
            }
            .combinedClickable(
                onClick = { onClick() },
                onLongClick = { showContextMenu = true }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.SpacingStandard, vertical = Dimens.SpacingSemiMedium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingMedium)
        ) {
            // Squircle Avatar (No shadow, no extra Surface/div wrapper)
            AppIcon(
                packageName = group.packageName,
                appName = groupTitle,
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable {
                        if (group.contact != null) {
                            onViewContact("contact_id_${group.contact.id}")
                        } else {
                            onClick()
                        }
                    }
            )

            // Middle: Name, VIP badge, and Recent Message Preview
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

                if (recentMessagePreview.isNotBlank()) {
                    Text(
                        text = recentMessagePreview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                } else {
                    Text(
                        text = "No messages",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        fontStyle = FontStyle.Italic,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            // Right Column: Timestamp
            Text(
                text = formattedDate,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Long-press Context Menu anchored at touch position
        Box(
            modifier = Modifier
                .offset(x = pressOffset.x, y = pressOffset.y)
                .size(0.dp)
        ) {
            DropdownMenu(
                expanded = showContextMenu,
                onDismissRequest = { showContextMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Delete") },
                    onClick = {
                        showContextMenu = false
                        onDeleteConversation()
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete conversation",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                )
                DropdownMenuItem(
                    text = { Text("Delete Chats") },
                    onClick = {
                        showContextMenu = false
                        onDeleteChats()
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.DeleteSweep,
                            contentDescription = "Delete chats",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
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
