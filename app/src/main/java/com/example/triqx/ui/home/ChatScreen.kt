package com.example.triqx.ui.home

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    groupKey: String,
    viewModel: NotificationViewModel,
    onNavigateBack: () -> Unit,
    onViewContact: (String) -> Unit,
    onNavigateToSettings: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val groups by viewModel.groupedPriorityNotifications.collectAsStateWithLifecycle()
    val cachedReplies by viewModel.cachedReplies.collectAsStateWithLifecycle()
    val loadingGroups by viewModel.loadingGroups.collectAsStateWithLifecycle()

    // Find active conversation matching groupKey
    val conversation = groups.find { it.groupKey == groupKey }
    val smartReplies = cachedReplies[groupKey] ?: emptyList()
    val isLoadingReplies = loadingGroups[groupKey] == true

    var manualReplyText by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var showDismissDialog by remember { mutableStateOf(false) }
    var showNotConnectedDialog by remember { mutableStateOf(false) }
    var pendingUnconnectedReplyText by remember { mutableStateOf("") }

    val listState = rememberLazyListState()

    // Chronological messages (oldest first -> newest last at bottom)
    val chronologicalMessages = remember(conversation?.messages) {
        conversation?.messages?.reversed() ?: emptyList()
    }

    // Auto-scroll to latest message when messages update or keyboard opens
    val isImeVisible = WindowInsets.isImeVisible
    LaunchedEffect(chronologicalMessages.size, isImeVisible) {
        if (chronologicalMessages.isNotEmpty()) {
            listState.animateScrollToItem(chronologicalMessages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (conversation != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (conversation.contact != null) {
                                        onViewContact("contact_id_${conversation.contact.id}")
                                    }
                                }
                        ) {
                            // Avatar
                            Surface(
                                modifier = Modifier
                                    .size(40.dp)
                                    .shadow(2.dp, RoundedCornerShape(12.dp)),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                AppIcon(
                                    packageName = conversation.packageName,
                                    appName = conversation.title,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(1.dp)
                                )
                            }

                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = conversation.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )

                                    val senderEmail = EmailUtils.cleanEmail(conversation.senderIdentifier)
                                    if (!senderEmail.isNullOrBlank() && !senderEmail.equals(conversation.title, ignoreCase = true)) {
                                        Text(
                                            text = "($senderEmail)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    if (conversation.contact != null) {
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

                                val appName = remember(conversation.packageName) {
                                    when {
                                        conversation.packageName.contains("whatsapp", ignoreCase = true) -> "WhatsApp"
                                        conversation.packageName.contains("messaging", ignoreCase = true) || conversation.packageName.contains("mms", ignoreCase = true) -> "Messages"
                                        conversation.packageName.contains("gm", ignoreCase = true) || conversation.packageName.contains("gmail", ignoreCase = true) -> "Gmail"
                                        conversation.packageName.contains("outlook", ignoreCase = true) -> "Outlook"
                                        conversation.packageName.contains("slack", ignoreCase = true) -> "Slack"
                                        conversation.packageName.contains("telegram", ignoreCase = true) -> "Telegram"
                                        else -> conversation.packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
                                    }
                                }

                                val subject = remember(conversation.messages) {
                                    conversation.messages.firstOrNull { !it.subText.isNullOrBlank() }?.subText
                                }

                                val cleanReceiver = EmailUtils.cleanEmail(conversation.receiverIdentifier)
                                val appWithAccount = if (!cleanReceiver.isNullOrBlank()) "$appName • $cleanReceiver" else appName
                                val fullSubtitle = if (!subject.isNullOrBlank()) "$appWithAccount • $subject" else appWithAccount

                                Text(
                                    text = fullSubtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    } else {
                        Text("Chat", fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (conversation != null) {
                        if (conversation.contact != null) {
                            IconButton(onClick = { onViewContact("contact_id_${conversation.contact.id}") }) {
                                Icon(Icons.Default.Contacts, contentDescription = "View Contact")
                            }
                        }
                        IconButton(onClick = { showDismissDialog = true }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Chat")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        contentWindowInsets = WindowInsets.navigationBars.union(WindowInsets.ime),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (conversation == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Conversation no longer active",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // Messages Scroll Area
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = chronologicalMessages,
                        key = { "${it.timestamp}_${it.senderName}_${it.bodyText.hashCode()}" }
                    ) { message ->
                        GoogleMessageBubble(
                            message = message,
                            groupTitle = conversation.title
                        )
                    }
                }

                // AI Smart Replies Bar
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Smart Replies",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        IconButton(
                            onClick = { viewModel.regenerateRepliesForGroup(conversation) },
                            enabled = !isLoadingReplies,
                            modifier = Modifier.size(22.dp)
                        ) {
                            if (isLoadingReplies) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Regenerate Replies",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Shiny Gemini-style suggestion chips
                    val shinyBorderBrush = remember {
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF4285F4).copy(alpha = 0.70f),
                                Color(0xFF8AB4F8).copy(alpha = 0.90f),
                                Color(0xFFC58AF9).copy(alpha = 0.75f),
                                Color(0xFF4285F4).copy(alpha = 0.70f)
                            )
                        )
                    }
                    val shinyBackgroundBrush = remember {
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF0B57D0).copy(alpha = 0.10f),
                                Color(0xFF8AB4F8).copy(alpha = 0.18f),
                                Color(0xFFC58AF9).copy(alpha = 0.10f)
                            )
                        )
                    }

                    if (isLoadingReplies) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Generating smart replies...",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else if (smartReplies.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            smartReplies.forEach { replyText ->
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(shinyBackgroundBrush)
                                        .border(
                                            width = 1.2.dp,
                                            brush = shinyBorderBrush,
                                            shape = CircleShape
                                        )
                                        .clickable { manualReplyText = TextFieldValue(replyText, TextRange(replyText.length)) }
                                        .padding(horizontal = 14.dp, vertical = 7.dp)
                                ) {
                                    Text(
                                        text = replyText,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                // WhatsApp Style Bottom Input Composer
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )

                        BasicTextField(
                            value = manualReplyText,
                            onValueChange = { manualReplyText = it },
                            singleLine = false,
                            maxLines = 6,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.weight(1f),
                            decorationBox = { innerTextField ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (manualReplyText.text.isEmpty()) {
                                        Text(
                                            text = "Type a message...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )

                        if (manualReplyText.text.isNotEmpty()) {
                            IconButton(
                                onClick = { manualReplyText = TextFieldValue("") },
                                modifier = Modifier
                                    .padding(bottom = 0.dp)
                                    .size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = "Clear text",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                if (manualReplyText.text.isNotBlank()) {
                                    sendReply(
                                        viewModel = viewModel,
                                        context = context,
                                        clipboardManager = clipboardManager,
                                        conversation = conversation,
                                        replyText = manualReplyText.text.trim(),
                                        onUnconnectedEmail = { reply ->
                                            pendingUnconnectedReplyText = reply
                                            showNotConnectedDialog = true
                                        }
                                    )
                                    manualReplyText = TextFieldValue("")
                                }
                            },
                            enabled = manualReplyText.text.isNotBlank(),
                            modifier = Modifier.size(40.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = if (manualReplyText.text.isNotBlank()) MaterialTheme.colorScheme.primary else Color.Transparent,
                                contentColor = if (manualReplyText.text.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.outlineVariant
                            )
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send message",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Dismiss Dialog
    if (showDismissDialog && conversation != null) {
        AlertDialog(
            onDismissRequest = { showDismissDialog = false },
            title = { Text("Dismiss Conversation?", fontWeight = FontWeight.Bold) },
            text = { Text("This will clear this thread from your priority feed.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.dismissGroup(conversation)
                        showDismissDialog = false
                        onNavigateBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDismissDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showNotConnectedDialog) {
        val accountDisplay = EmailUtils.cleanEmail(conversation?.receiverIdentifier)?.ifBlank { null } ?: "Email"
        AlertDialog(
            onDismissRequest = { showNotConnectedDialog = false },
            title = {
                Text("Account Not Connected", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    text = "Account '$accountDisplay' is not connected. Connect your Google account in Settings for 1-stage AI replies, or copy the reply and open the email app.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showNotConnectedDialog = false
                        onNavigateToSettings?.invoke()
                    }
                ) {
                    Text("Connect Account")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showNotConnectedDialog = false
                        clipboardManager.setText(AnnotatedString(pendingUnconnectedReplyText))
                        val cleanEmail = EmailUtils.cleanEmail(conversation?.senderIdentifier)?.removePrefix("mailto:")?.trim()
                        val titleStr = conversation?.contact?.displayName ?: conversation?.messages?.firstOrNull()?.senderName ?: conversation?.packageName
                        val subject = if (titleStr?.startsWith("Re:", ignoreCase = true) == true) titleStr else "Re: $titleStr"

                        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:${cleanEmail ?: ""}")
                            if (!cleanEmail.isNullOrBlank()) putExtra(Intent.EXTRA_EMAIL, arrayOf(cleanEmail))
                            putExtra(Intent.EXTRA_SUBJECT, subject)
                            putExtra(Intent.EXTRA_TEXT, pendingUnconnectedReplyText)
                            conversation?.packageName?.let { setPackage(it) }
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }

                        val sent = try { context.startActivity(emailIntent); true } catch (_: Exception) { false }
                        if (!sent) {
                            val launchIntent = conversation?.packageName?.let { context.packageManager.getLaunchIntentForPackage(it) }
                            if (launchIntent != null) {
                                launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                context.startActivity(launchIntent)
                                Toast.makeText(context, "Copied reply! Opening app...", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Copied reply to clipboard", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, "Copied reply! Opening draft...", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Copy & Open App")
                }
            }
        )
    }
}

private fun sendReply(
    viewModel: NotificationViewModel,
    context: android.content.Context,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager,
    conversation: Conversation,
    replyText: String,
    onUnconnectedEmail: (String) -> Unit
) {
    val isEmailApp = conversation.packageName.let { pkg ->
        pkg.contains("gm") || pkg.contains("email") || pkg.contains("outlook") || pkg.contains("mail")
    }

    if (isEmailApp) {
        val cleanReceiver = EmailUtils.cleanEmail(conversation.receiverIdentifier)
        val isConnected = viewModel.isEmailAccountConnected(conversation.packageName, cleanReceiver)
        if (isConnected) {
            Toast.makeText(context, "Sending via Gmail...", Toast.LENGTH_SHORT).show()
            viewModel.sendEmailReply(conversation, replyText) { success, errorMsg ->
                if (success) {
                    Toast.makeText(context, "Sent: $replyText", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Failed: ${errorMsg ?: "Check connection"}", Toast.LENGTH_LONG).show()
                }
            }
            return
        } else {
            onUnconnectedEmail(replyText)
            return
        }
    }

    val success = viewModel.replyToNotification(
        key = conversation.latestNotificationKey,
        replyMessage = replyText,
        packageName = conversation.packageName,
        contact = conversation.contact,
        senderIdentifier = EmailUtils.cleanEmail(conversation.senderIdentifier) ?: conversation.senderIdentifier,
        groupKey = conversation.groupKey
    )

    if (success) {
        Toast.makeText(context, "Sent: $replyText", Toast.LENGTH_SHORT).show()
    } else {
        viewModel.recordOutgoingReply(
            packageName = conversation.packageName,
            replyText = replyText,
            notificationKey = conversation.latestNotificationKey,
            contact = conversation.contact,
            senderIdentifier = EmailUtils.cleanEmail(conversation.senderIdentifier) ?: conversation.senderIdentifier,
            receiverIdentifier = EmailUtils.cleanEmail(conversation.receiverIdentifier) ?: conversation.receiverIdentifier,
            groupKey = conversation.groupKey
        )

        val dispatched = if (isEmailApp) {
            val cleanEmail = EmailUtils.cleanEmail(conversation.senderIdentifier)?.removePrefix("mailto:")?.trim()
            val titleStr = conversation.contact?.displayName ?: conversation.messages.firstOrNull()?.senderName ?: conversation.packageName
            val subject = if (titleStr.startsWith("Re:", ignoreCase = true)) titleStr else "Re: $titleStr"

            val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:${cleanEmail ?: ""}")
                if (!cleanEmail.isNullOrBlank()) putExtra(Intent.EXTRA_EMAIL, arrayOf(cleanEmail))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, replyText)
                setPackage(conversation.packageName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            val sent = try { context.startActivity(emailIntent); true } catch (_: Exception) { false }
            if (!sent) {
                try { emailIntent.setPackage(null); context.startActivity(emailIntent); true } catch (_: Exception) { false }
            } else {
                Toast.makeText(context, "Opening email draft...", Toast.LENGTH_SHORT).show()
                true
            }
        } else false

        if (!dispatched) {
            clipboardManager.setText(AnnotatedString(replyText))
            val launchIntent = context.packageManager.getLaunchIntentForPackage(conversation.packageName)
            if (launchIntent != null) {
                Toast.makeText(context, "Copied reply! Opening app...", Toast.LENGTH_SHORT).show()
                context.startActivity(launchIntent)
            } else {
                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
