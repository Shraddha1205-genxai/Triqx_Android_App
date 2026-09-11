package com.example.triqx.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.example.triqx.MainActivity
import com.example.triqx.R
import com.example.triqx.data.local.NotificationEntity
import com.example.triqx.receiver.TriqxReplyReceiver

/**
 * Posts AI assistant notifications with smart reply buttons.
 * Supports two styles (configurable in Settings):
 *   - "body_numbered": Full reply text in notification body with numbered send buttons
 *   - "chips_native": Clean body with native smart reply chips
 */
object TriqxAssistantNotificationManager {

    const val CHANNEL_ID = "triqx_ai_assistant"
    const val CHANNEL_NAME = "Triqx AI Assistant"
    const val CHANNEL_DESC = "Real-time AI suggested replies for priority conversations"
    const val NOTIFICATION_ID = 1001
    const val ACTION_OPEN_CONVERSATION = "com.example.triqx.ACTION_OPEN_CONVERSATION"
    const val EXTRA_CONVERSATION_KEY = "extra_conversation_key"

    // =============================
    // Channel Setup
    // =============================

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                description = CHANNEL_DESC
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    // =============================
    // Post / Update Notification
    // =============================

    fun getNotificationTag(groupKey: String): String = "triqx_thread_${groupKey.hashCode()}"

    fun postOrUpdateAssistantNotification(
        context: Context,
        groupKey: String,
        contactOrTitle: String,
        packageName: String,
        notificationKey: String,
        contactId: Int?,
        senderIdentifier: String?,
        receiverIdentifier: String? = null,
        latestMessageText: String,
        timestamp: Long = System.currentTimeMillis(),
        smartReplies: List<String>
    ) {
        createNotificationChannel(context)
        if (latestMessageText.isBlank()) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val tag = getNotificationTag(groupKey)

        // Show max 2 lines of user message so AI replies have full priority
        val truncatedMessage = truncateToTwoLines(latestMessageText)

        // Show replies as numbered bullets in the body
        val replyList = smartReplies.take(3).mapIndexed { i, r ->
            "${numberEmoji(i)} \"$r\""
        }.joinToString("\n")

        val fullBody = if (replyList.isNotBlank()) {
            "$truncatedMessage\n\n✨ AI Suggested Replies:\n$replyList"
        } else {
            truncatedMessage
        }

        val bigTextStyle = NotificationCompat.BigTextStyle()
            .bigText(fullBody)
            .setBigContentTitle(contactOrTitle)
            .setSummaryText("Triqx AI Assistant")

        // Content tap -> open conversation directly in Triqx
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            action = ACTION_OPEN_CONVERSATION
            putExtra(EXTRA_CONVERSATION_KEY, groupKey)
            data = Uri.parse("triqx://chat/${Uri.encode(groupKey)}")
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            groupKey.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val singleLinePreview = latestMessageText.replace('\n', ' ').trim().let {
            if (it.length > 70) it.take(70).trimEnd() + "…" else it
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(contactOrTitle)
            .setContentText(singleLinePreview)
            .setStyle(bigTextStyle)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setWhen(timestamp)
            .setShowWhen(true)

        // Always add numbered send buttons + edit action
        addNumberedActions(context, builder, groupKey, packageName, notificationKey, contactId, senderIdentifier, receiverIdentifier, contactOrTitle, smartReplies)

        manager.notify(tag, NOTIFICATION_ID, builder.build())
    }

    /** Backward-compatibility overload accepting NotificationEntity list */
    fun postOrUpdateAssistantNotification(
        context: Context,
        groupKey: String,
        contactOrTitle: String,
        packageName: String,
        notificationKey: String,
        contactId: Int?,
        senderIdentifier: String?,
        receiverIdentifier: String? = null,
        messages: List<NotificationEntity>,
        smartReplies: List<String>
    ) {
        val latestText = messages.firstOrNull()?.text.orEmpty()
        val latestTime = messages.firstOrNull()?.timestamp ?: System.currentTimeMillis()
        postOrUpdateAssistantNotification(
            context = context,
            groupKey = groupKey,
            contactOrTitle = contactOrTitle,
            packageName = packageName,
            notificationKey = notificationKey,
            contactId = contactId,
            senderIdentifier = senderIdentifier,
            receiverIdentifier = receiverIdentifier,
            latestMessageText = latestText,
            timestamp = latestTime,
            smartReplies = smartReplies
        )
    }

    // =============================
    // Cancel
    // =============================

    fun cancelNotification(context: Context, groupKey: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(getNotificationTag(groupKey), NOTIFICATION_ID)
    }

    // =============================
    // Action Builders (Private)
    // =============================

    /** Numbered send buttons + edit action. */
    private fun addNumberedActions(
        context: Context,
        builder: NotificationCompat.Builder,
        groupKey: String,
        packageName: String,
        notificationKey: String,
        contactId: Int?,
        senderIdentifier: String?,
        receiverIdentifier: String?,
        contactOrTitle: String,
        smartReplies: List<String>
    ) {
        // Add "Send #1", "Send #2", "Send #3" buttons
        smartReplies.take(3).forEachIndexed { index, replyText ->
            val intent = createReplyIntent(context, TriqxReplyReceiver.ACTION_SMART_REPLY,
                groupKey, packageName, notificationKey, contactId, senderIdentifier, receiverIdentifier, contactOrTitle, replyText)

            val pendingIntent = PendingIntent.getBroadcast(
                context, (groupKey.hashCode() * 10) + index, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

            builder.addAction(NotificationCompat.Action.Builder(0, "${numberEmoji(index)} Send #${index + 1}", pendingIntent).build())
        }

        // Add "Edit" button with RemoteInput
        val editIntent = createReplyIntent(context, TriqxReplyReceiver.ACTION_CUSTOM_REPLY,
            groupKey, packageName, notificationKey, contactId, senderIdentifier, receiverIdentifier, contactOrTitle)

        val editPending = PendingIntent.getBroadcast(
            context, (groupKey.hashCode() * 10) + 9, editIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val remoteInput = RemoteInput.Builder(TriqxReplyReceiver.KEY_TEXT_REPLY)
            .setLabel("Edit reply...")
            .build()

        builder.addAction(
            NotificationCompat.Action.Builder(0, "✏️ Edit", editPending)
                .addRemoteInput(remoteInput)
                .build()
        )
    }

    /**
     * Truncates message to at most 2 lines, keeping user message concise so AI replies have full priority.
     */
    private fun truncateToTwoLines(text: String, maxCharsPerLine: Int = 60): String {
        val clean = text.trim()
        val rawLines = clean.lines().map { it.trim() }.filter { it.isNotEmpty() }

        return if (rawLines.size > 1) {
            val line1 = if (rawLines[0].length > maxCharsPerLine) rawLines[0].take(maxCharsPerLine).trimEnd() + "…" else rawLines[0]
            val line2 = if (rawLines.size > 2 || rawLines[1].length > maxCharsPerLine) {
                rawLines[1].take(maxCharsPerLine).trimEnd() + "…"
            } else {
                rawLines[1]
            }
            "$line1\n$line2"
        } else {
            if (clean.length <= maxCharsPerLine) {
                clean
            } else if (clean.length <= maxCharsPerLine * 2) {
                val splitIndex = clean.lastIndexOf(' ', maxCharsPerLine).takeIf { it > 20 } ?: maxCharsPerLine
                "${clean.substring(0, splitIndex).trimEnd()}\n${clean.substring(splitIndex).trimStart()}"
            } else {
                val splitIndex = clean.lastIndexOf(' ', maxCharsPerLine).takeIf { it > 20 } ?: maxCharsPerLine
                val line1 = clean.substring(0, splitIndex).trimEnd()
                val remainder = clean.substring(splitIndex).trimStart()
                val line2 = if (remainder.length > maxCharsPerLine) remainder.take(maxCharsPerLine).trimEnd() + "…" else remainder
                "$line1\n$line2"
            }
        }
    }

    // =============================
    // Helpers
    // =============================

    /** Build a reply broadcast intent with all required extras. */
    private fun createReplyIntent(
        context: Context,
        action: String,
        groupKey: String,
        packageName: String,
        notificationKey: String,
        contactId: Int?,
        senderIdentifier: String?,
        receiverIdentifier: String?,
        contactOrTitle: String,
        replyText: String? = null
    ): Intent {
        return Intent(context, TriqxReplyReceiver::class.java).apply {
            this.action = action
            putExtra(TriqxReplyReceiver.EXTRA_GROUP_KEY, groupKey)
            putExtra(TriqxReplyReceiver.EXTRA_PACKAGE_NAME, packageName)
            putExtra(TriqxReplyReceiver.EXTRA_NOTIFICATION_KEY, notificationKey)
            putExtra(TriqxReplyReceiver.EXTRA_CONTACT_ID, contactId ?: -1)
            putExtra(TriqxReplyReceiver.EXTRA_SENDER_IDENTIFIER, senderIdentifier)
            putExtra(TriqxReplyReceiver.EXTRA_RECEIVER_IDENTIFIER, receiverIdentifier)
            putExtra(TriqxReplyReceiver.EXTRA_SPECIFIC_IDENTIFIER, senderIdentifier) // backward compat
            putExtra(TriqxReplyReceiver.EXTRA_CONTACT_OR_TITLE, contactOrTitle)
            if (replyText != null) putExtra(TriqxReplyReceiver.EXTRA_REPLY_TEXT, replyText)
        }
    }

    /** Convert index to number emoji. */
    private fun numberEmoji(index: Int): String = when (index) {
        0 -> "1️⃣"
        1 -> "2️⃣"
        else -> "3️⃣"
    }
}
