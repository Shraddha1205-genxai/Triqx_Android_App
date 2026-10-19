package com.example.triqx.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.example.triqx.MainActivity
import com.example.triqx.R
import com.example.triqx.data.local.NotificationEntity
import com.example.triqx.receiver.TriqxReplyReceiver

/**
 * Posts AI assistant notifications with smart reply actions.
 *
 * Layout (Android shows at most 3 action buttons, so the old "Send #1/#2/#3 + Edit" layout
 * silently hid the Edit button):
 *   [1️⃣ Send #1]  [2️⃣ Send #2]  [💬 Reply]
 * "Reply" carries a RemoteInput whose choices are ALL suggestions, so every reply (up to 5)
 * is available as a tappable chip, plus free-text editing.
 *
 * When AI generation failed (empty [smartReplies]) the notification says so and offers only
 * the free-text Reply action — it never shows placeholder text as a one-tap send.
 */
object TriqxAssistantNotificationManager {

    private const val TAG = "TriqxAssistantNotif"

    const val CHANNEL_ID = "triqx_ai_assistant"
    const val CHANNEL_NAME = "Triqx AI Assistant"
    const val CHANNEL_DESC = "Real-time AI suggested replies for priority conversations"
    const val NOTIFICATION_ID = 1001
    const val ACTION_OPEN_CONVERSATION = "com.example.triqx.ACTION_OPEN_CONVERSATION"
    const val EXTRA_CONVERSATION_KEY = "extra_conversation_key"

    private const val TAG_PREFIX = "triqx_thread_"
    private const val MAX_SEND_BUTTONS = 2   // + 1 Reply action = Android's 3-action limit
    private const val MAX_LISTED_REPLIES = 5

    // =============================
    // Channel Setup
    // =============================

    fun createNotificationChannel(context: Context) {
        // minSdk is 31, so channels are always available.
        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
            description = CHANNEL_DESC
            enableLights(true)
            enableVibration(true)
            setShowBadge(true)
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    // =============================
    // Post / Update Notification
    // =============================

    fun getNotificationTag(groupKey: String): String = "$TAG_PREFIX${groupKey.hashCode()}"

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
        if (latestMessageText.isBlank()) return

        // Android 13+: POST_NOTIFICATIONS may be denied/revoked, or the user may have muted the app.
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            Log.w(TAG, "Notifications disabled for Triqx; skipping assistant notification for $groupKey")
            return
        }

        createNotificationChannel(context)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val tag = getNotificationTag(groupKey)
        val replies = smartReplies.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(MAX_LISTED_REPLIES)

        // Show max 2 lines of user message so AI replies have full priority
        val truncatedMessage = truncateToTwoLines(latestMessageText)

        // The suggestions themselves are NOT repeated in the body: they are already offered by the Send
        // buttons and as chips in the Edit action, so a text list here was a pure duplicate.
        val fullBody = if (replies.isNotEmpty()) {
            truncatedMessage
        } else {
            "$truncatedMessage\n\n⚠️ AI replies unavailable right now. Tap Reply to respond."
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

        addActions(context, builder, groupKey, packageName, notificationKey, contactId, senderIdentifier, receiverIdentifier, contactOrTitle, replies)

        try {
            manager.notify(tag, NOTIFICATION_ID, builder.build())
        } catch (e: SecurityException) {
            Log.w(TAG, "POST_NOTIFICATIONS not granted; cannot post assistant notification: ${e.message}")
        }
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

    /** Cancels only Triqx assistant notifications (previously cancelAll() wiped every Triqx notification). */
    fun cancelAllAssistantNotifications(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            manager.activeNotifications
                .filter { it.tag?.startsWith(TAG_PREFIX) == true }
                .forEach { manager.cancel(it.tag, it.id) }
        } catch (e: Exception) {
            Log.w(TAG, "Selective cancel failed, falling back to cancelAll: ${e.message}")
            manager.cancelAll()
        }
    }

    // =============================
    // Action Builders (Private)
    // =============================

    private fun addActions(
        context: Context,
        builder: NotificationCompat.Builder,
        groupKey: String,
        packageName: String,
        notificationKey: String,
        contactId: Int?,
        senderIdentifier: String?,
        receiverIdentifier: String?,
        contactOrTitle: String,
        replies: List<String>
    ) {
        // One-tap send buttons. IMMUTABLE: the reply text is fixed in the intent, so nothing
        // (including other apps) may modify it. Previously these were FLAG_MUTABLE.
        replies.take(MAX_SEND_BUTTONS).forEachIndexed { index, replyText ->
            val intent = createReplyIntent(
                context, TriqxReplyReceiver.ACTION_SMART_REPLY, "send$index",
                groupKey, packageName, notificationKey, contactId, senderIdentifier, receiverIdentifier, contactOrTitle, replyText
            )
            val pendingIntent = PendingIntent.getBroadcast(
                context, requestCode(groupKey, index), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                NotificationCompat.Action.Builder(0, "${numberEmoji(index)} Send #${index + 1}", pendingIntent)
                    .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
                    .setShowsUserInterface(false)
                    .build()
            )
        }

        // Reply action: free text + every suggestion as a chip. MUTABLE is required so the
        // system can fill in the RemoteInput result.
        val replyIntent = createReplyIntent(
            context, TriqxReplyReceiver.ACTION_CUSTOM_REPLY, "reply",
            groupKey, packageName, notificationKey, contactId, senderIdentifier, receiverIdentifier, contactOrTitle
        )
        val replyPending = PendingIntent.getBroadcast(
            context, requestCode(groupKey, 9), replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        val remoteInput = RemoteInput.Builder(TriqxReplyReceiver.KEY_TEXT_REPLY)
            .setLabel(if (replies.isEmpty()) "Type a reply…" else "Edit or pick a reply…")
            .apply { if (replies.isNotEmpty()) setChoices(replies.toTypedArray<CharSequence>()) }
            .setAllowFreeFormInput(true)
            .build()
        builder.addAction(
            NotificationCompat.Action.Builder(0, if (replies.isEmpty()) "💬 Reply" else "✏️ Edit / More", replyPending)
                .addRemoteInput(remoteInput)
                .setAllowGeneratedReplies(false) // our own suggestions are already supplied as choices
                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
                .setShowsUserInterface(false)
                .build()
        )
    }

    /** Distinct per conversation+slot; collisions are also prevented by the unique intent data URI. */
    private fun requestCode(groupKey: String, slot: Int): Int = (groupKey.hashCode() * 31) + slot

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

    /**
     * Build a reply broadcast intent with all required extras.
     *
     * The data URI makes every (conversation, slot) PendingIntent unique. Previously intents differed
     * only in extras + a hashCode-based request code; two conversations whose codes collided shared one
     * PendingIntent, and FLAG_UPDATE_CURRENT overwrote the extras — so tapping "Send" could deliver the
     * reply to the WRONG conversation.
     */
    private fun createReplyIntent(
        context: Context,
        action: String,
        slot: String,
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
            data = Uri.parse("triqx://reply/${Uri.encode(groupKey)}/$slot")
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
        2 -> "3️⃣"
        3 -> "4️⃣"
        else -> "5️⃣"
    }
}
