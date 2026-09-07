package com.example.triqx.service.mapper

import android.app.Notification
import android.app.Person
import android.os.Build
import android.os.Bundle
import android.service.notification.StatusBarNotification
import com.example.triqx.data.local.ContactEntity

/**
 * Default fallback mapper for standard Android messaging notifications
 * (Google Messages, Signal, Discord, etc.).
 */
class DefaultNotificationMapper : NotificationMapper {

    override fun canHandle(packageName: String): Boolean = true // Always matches as fallback

    override fun parse(
        sbn: StatusBarNotification,
        rawJson: String?,
        matchedContact: ContactEntity?
    ): ParsedNotification {
        val extras = sbn.notification.extras ?: Bundle.EMPTY
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim()
        val conversationTitleExtra = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()?.trim()

        val isGroup = extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false) || !conversationTitleExtra.isNullOrBlank()
        val isFromYouInitial = title?.equals("You", ignoreCase = true) == true ||
                               title?.endsWith(": You", ignoreCase = true) == true ||
                               text?.startsWith("Replied you using", ignoreCase = true) == true

        // 1. Resolve Conversation Title
        val conversationTitle = if (isGroup && !conversationTitleExtra.isNullOrBlank()) {
            conversationTitleExtra
        } else {
            matchedContact?.displayName?.ifBlank { null }
                ?: title?.ifBlank { null }
                ?: sbn.packageName
        }

        // 2. Resolve Individual Sender Name
        val individualSender = if (isFromYouInitial) {
            "You"
        } else if (isGroup) {
            extractPersonName(extras)
                ?: (if (title != null && title.contains(":")) title.substringAfter(":").trim() else title)
                ?: "Member"
        } else {
            conversationTitle
        }

        val isFromYou = isFromYouInitial || individualSender.equals("You", ignoreCase = true)

        // 3. Resolve Chat Tag
        val chatTag = sbn.tag?.trim()?.ifBlank { null }
            ?: extractTagFromKey(sbn.key)
            ?: conversationTitle

        return ParsedNotification(
            conversationTitle = conversationTitle,
            individualSender = if (isFromYou) "You" else individualSender,
            subText = null, // Strictly null for chat apps (subText reserved for Email Subject)
            bodyText = text ?: bigText ?: "",
            chatTag = chatTag.trim(),
            isGroup = isGroup,
            isFromYou = isFromYou
        )
    }

    private fun extractPersonName(extras: Bundle): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            @Suppress("DEPRECATION")
            extras.getParcelable<Person>(Notification.EXTRA_MESSAGING_PERSON)?.name?.toString()?.trim()
        } else null
    }

    private fun extractTagFromKey(key: String): String? {
        val parts = key.split('|')
        return if (parts.size >= 4 && parts[3].isNotBlank() && parts[3] != "null") {
            parts[3].trim()
        } else null
    }
}
