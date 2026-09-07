package com.example.triqx.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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
        specificIdentifier: String?,
        messages: List<NotificationEntity>,
        smartReplies: List<String>
    ) {
        createNotificationChannel(context)
        if (messages.isEmpty()) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val tag = getNotificationTag(groupKey)
        val latest = messages.first()

        // Build conversation summary (last 4 messages, oldest first)
        val conversationSummary = messages.take(4).reversed().joinToString("\n") { notif ->
            val sender = if (notif.title.equals("You", ignoreCase = true)) "You" else contactOrTitle
            "$sender: ${notif.text ?: ""}"
        }

        // Read user's selected notification style
        val prefs = context.getSharedPreferences("triqx_settings_prefs", Context.MODE_PRIVATE)
        val style = prefs.getString("notification_reply_style", "body_numbered") ?: "body_numbered"

        // Build the BigTextStyle body
        val bigTextStyle = NotificationCompat.BigTextStyle()
        if (style == "body_numbered") {
            // Show replies as numbered bullets in the body
            val replyList = smartReplies.take(3).mapIndexed { i, r ->
                "${numberEmoji(i)} \"$r\""
            }.joinToString("\n")

            val fullBody = if (replyList.isNotBlank()) {
                "$conversationSummary\n\n✨ AI Suggested Replies:\n$replyList"
            } else {
                conversationSummary
            }
            bigTextStyle.bigText(fullBody)
        } else {
            // Clean body (replies shown as native chips instead)
            bigTextStyle.bigText(conversationSummary)
        }
        bigTextStyle.setBigContentTitle(contactOrTitle)
        bigTextStyle.setSummaryText("Triqx AI Assistant")

        // Content tap -> open app
        val contentIntent = PendingIntent.getActivity(
            context,
            groupKey.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(contactOrTitle)
            .setContentText(latest.text ?: "New message")
            .setStyle(bigTextStyle)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setWhen(latest.timestamp)
            .setShowWhen(true)

        // Add action buttons based on style
        if (style == "body_numbered") {
            addNumberedActions(context, builder, groupKey, packageName, notificationKey, contactId, specificIdentifier, contactOrTitle, smartReplies)
        } else {
            addChipActions(context, builder, groupKey, packageName, notificationKey, contactId, specificIdentifier, contactOrTitle, smartReplies)
        }

        manager.notify(tag, NOTIFICATION_ID, builder.build())
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

    /** Approach 1: Numbered send buttons + edit action. */
    private fun addNumberedActions(
        context: Context,
        builder: NotificationCompat.Builder,
        groupKey: String,
        packageName: String,
        notificationKey: String,
        contactId: Int?,
        specificIdentifier: String?,
        contactOrTitle: String,
        smartReplies: List<String>
    ) {
        // Add "Send #1", "Send #2", "Send #3" buttons
        smartReplies.take(3).forEachIndexed { index, replyText ->
            val intent = createReplyIntent(context, TriqxReplyReceiver.ACTION_SMART_REPLY,
                groupKey, packageName, notificationKey, contactId, specificIdentifier, contactOrTitle, replyText)

            val pendingIntent = PendingIntent.getBroadcast(
                context, (groupKey.hashCode() * 10) + index, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

            builder.addAction(NotificationCompat.Action.Builder(0, "${numberEmoji(index)} Send #${index + 1}", pendingIntent).build())
        }

        // Add "Edit" button with RemoteInput
        val editIntent = createReplyIntent(context, TriqxReplyReceiver.ACTION_CUSTOM_REPLY,
            groupKey, packageName, notificationKey, contactId, specificIdentifier, contactOrTitle)

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

    /** Approach 3: Native smart reply chips + quick send buttons. */
    private fun addChipActions(
        context: Context,
        builder: NotificationCompat.Builder,
        groupKey: String,
        packageName: String,
        notificationKey: String,
        contactId: Int?,
        specificIdentifier: String?,
        contactOrTitle: String,
        smartReplies: List<String>
    ) {
        // Smart reply chip with choices dropdown
        val chipIntent = createReplyIntent(context, TriqxReplyReceiver.ACTION_CUSTOM_REPLY,
            groupKey, packageName, notificationKey, contactId, specificIdentifier, contactOrTitle)

        val chipPending = PendingIntent.getBroadcast(
            context, (groupKey.hashCode() * 10) + 9, chipIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val remoteInput = RemoteInput.Builder(TriqxReplyReceiver.KEY_TEXT_REPLY)
            .setLabel("Reply with AI...")
            .setChoices(smartReplies.take(3).toTypedArray())
            .build()

        builder.addAction(
            NotificationCompat.Action.Builder(0, "💬 Smart Reply", chipPending)
                .addRemoteInput(remoteInput)
                .build()
        )

        // Quick send buttons (first 2 replies)
        smartReplies.take(2).forEachIndexed { index, replyText ->
            val intent = createReplyIntent(context, TriqxReplyReceiver.ACTION_SMART_REPLY,
                groupKey, packageName, notificationKey, contactId, specificIdentifier, contactOrTitle, replyText)

            val pendingIntent = PendingIntent.getBroadcast(
                context, (groupKey.hashCode() * 10) + index, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

            builder.addAction(NotificationCompat.Action.Builder(0, "✨ $replyText", pendingIntent).build())
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
        specificIdentifier: String?,
        contactOrTitle: String,
        replyText: String? = null
    ): Intent {
        return Intent(context, TriqxReplyReceiver::class.java).apply {
            this.action = action
            putExtra(TriqxReplyReceiver.EXTRA_GROUP_KEY, groupKey)
            putExtra(TriqxReplyReceiver.EXTRA_PACKAGE_NAME, packageName)
            putExtra(TriqxReplyReceiver.EXTRA_NOTIFICATION_KEY, notificationKey)
            putExtra(TriqxReplyReceiver.EXTRA_CONTACT_ID, contactId ?: -1)
            putExtra(TriqxReplyReceiver.EXTRA_SPECIFIC_IDENTIFIER, specificIdentifier)
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
