package com.example.triqx.service.mapper

import android.app.Notification
import android.os.Bundle
import android.service.notification.StatusBarNotification
import com.example.triqx.data.local.ContactEntity

/**
 * Mapper for Slack notifications.
 * Handles:
 * - Slack Channels (#general, #dev) vs Direct Messages
 * - Channel name extraction from EXTRA_CONVERSATION_TITLE
 * - Workspace name extraction from EXTRA_SUB_TEXT
 * - Sender name extraction from EXTRA_TITLE
 */
class SlackNotificationMapper : NotificationMapper {

    override fun canHandle(packageName: String): Boolean {
        return packageName.contains("slack", ignoreCase = true) ||
                packageName.equals("com.Slack", ignoreCase = true)
    }

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

        val isGroupFlag = extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)
        val isChannel = isGroupFlag || !conversationTitleExtra.isNullOrBlank() || title?.startsWith("#") == true
        val isFromYou = title.equals("You", ignoreCase = true) || text?.startsWith("Replied you using", ignoreCase = true) == true

        // 1. Resolve Conversation Title (Channel #name or Sender Name)
        val conversationTitle = if (isChannel && !conversationTitleExtra.isNullOrBlank()) {
            conversationTitleExtra
        } else {
            matchedContact?.displayName?.ifBlank { null }
                ?: title?.ifBlank { null }
                ?: "Slack DM"
        }

        // 2. Resolve Sender Name
        val individualSender = if (isFromYou) {
            "You"
        } else {
            title ?: "Member"
        }

        // 3. Resolve Subtext (Workspace Name e.g. "Acme Corp")
        val workspaceSubtext = subText?.ifBlank { null } ?: conversationTitleExtra

        // 4. Resolve Chat Tag
        val chatTag = sbn.tag?.trim()?.ifBlank { null }
            ?: conversationTitleExtra
            ?: (title ?: sbn.packageName)

        return ParsedNotification(
            conversationTitle = conversationTitle,
            individualSender = if (isFromYou) "You" else individualSender,
            subText = null, // Strictly null for chat apps (subText reserved for Email Subject)
            bodyText = text ?: bigText ?: "",
            chatTag = chatTag.trim(),
            isGroup = isChannel,
            isFromYou = isFromYou || individualSender.equals("You", ignoreCase = true)
        )
    }
}
