package com.example.triqx.service.mapper

import android.app.Notification
import android.os.Bundle
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.example.triqx.data.local.ContactEntity
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Mapper for Telegram notifications.
 *
 * Handles:
 * - Group chats vs 1-on-1 chats
 * - "Sender: Message" separation for group notifications
 * - Current-user ("You") detection
 * - Latest message selection by timestamp
 * - Stable chat tag extraction
 */
class TelegramNotificationMapper : NotificationMapper {

    override fun canHandle(packageName: String): Boolean {
        return packageName.contains("telegram", ignoreCase = true) ||
                packageName.equals(
                    "org.telegram.messenger",
                    ignoreCase = true
                ) ||
                packageName.equals(
                    "org.telegram.messenger.web",
                    ignoreCase = true
                ) ||
                packageName.equals(
                    "org.thunderdog.challegram",
                    ignoreCase = true
                )
    }

    override fun parse(
        sbn: StatusBarNotification,
        rawJson: String?,
        matchedContact: ContactEntity?
    ): ParsedNotification {

        val notification = sbn.notification
        val extras = notification.extras ?: Bundle.EMPTY

        val title = extras
            .getCharSequence(Notification.EXTRA_TITLE)
            ?.toString()
            ?.trim()

        val text = extras
            .getCharSequence(Notification.EXTRA_TEXT)
            ?.toString()
            ?.trim()

        val bigText = extras
            .getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?.toString()
            ?.trim()

        val conversationTitleExtra = extras
            .getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
            ?.toString()
            ?.trim()

        val sbnTag = sbn.tag?.trim()

        // ---------------------------------------------------------
        // Determine whether this is a group
        // ---------------------------------------------------------

        val isGroupFlag = extras.getBoolean(
            Notification.EXTRA_IS_GROUP_CONVERSATION,
            false
        )

        val isGroup =
            isGroupFlag ||
                    !conversationTitleExtra.isNullOrBlank() ||
                    sbnTag?.startsWith("chat_", ignoreCase = true) == true ||
                    isLikelySenderPrefixedMessage(
                        text ?: bigText
                    )

        // ---------------------------------------------------------
        // Conversation title
        // ---------------------------------------------------------

        val conversationTitle = if (isGroup) {

            conversationTitleExtra
                ?.takeIf { it.isNotBlank() }

                ?: extractGroupTitleFromTitle(title)

                ?: title
                    ?.takeIf { it.isNotBlank() }

                ?: "Telegram Group"

        } else {

            matchedContact?.displayName
                ?.takeIf { it.isNotBlank() }

                ?: title
                    ?.takeIf { it.isNotBlank() }

                ?: "Telegram Chat"
        }

        // ---------------------------------------------------------
        // Extract latest message
        // ---------------------------------------------------------

        val latestMessage =
            extractLatestMessage(
                notification = notification,
                rawJson = rawJson
            )

        // ---------------------------------------------------------
        // Sender
        // ---------------------------------------------------------

        val rawBody =
            latestMessage?.body
                ?: text
                ?: bigText
                ?: ""

        val senderFromMessage =
            latestMessage?.sender

        val isFromYou =
            latestMessage?.isFromYou
                ?: detectIsFromYou(
                    title = title,
                    text = rawBody
                )

        val individualSender = when {

            isFromYou -> {
                "You"
            }

            !senderFromMessage.isNullOrBlank() -> {
                senderFromMessage
            }

            isGroup && hasSenderPrefix(rawBody) -> {
                rawBody
                    .substringBefore(":")
                    .trim()
            }

            else -> {
                matchedContact?.displayName
                    ?: title
                    ?: "Member"
            }
        }

        // ---------------------------------------------------------
        // Body
        // ---------------------------------------------------------

        val cleanBody =
            cleanMessageBody(
                rawBody = rawBody,
                senderName = individualSender,
                isGroup = isGroup
            )

        // ---------------------------------------------------------
        // Chat tag
        // ---------------------------------------------------------

        val chatTag =
            sbnTag
                ?.takeIf { it.isNotBlank() }
                ?: conversationTitle

        return ParsedNotification(
            conversationTitle = conversationTitle,
            individualSender = if (isFromYou) {
                "You"
            } else {
                individualSender
            },
            subText = null,
            bodyText = cleanBody,
            chatTag = chatTag.trim(),
            isGroup = isGroup,
            isFromYou = isFromYou
        )
    }

    // =============================================================
    // Message model
    // =============================================================

    private data class TelegramMessage(
        val sender: String?,
        val body: String,
        val timestamp: Long,
        val isFromYou: Boolean
    )

    // =============================================================
    // Latest message
    // =============================================================

    private fun extractLatestMessage(
        notification: Notification,
        rawJson: String?
    ): TelegramMessage? {

        // ---------------------------------------------------------
        // Try AndroidX MessagingStyle first
        // ---------------------------------------------------------

        val messagingStyle = try {
            NotificationCompat.MessagingStyle
                .extractMessagingStyleFromNotification(notification)
        } catch (_: Exception) {
            null
        }

        if (messagingStyle != null) {

            val messages = messagingStyle.messages

            val latest = messages
                .maxByOrNull { it.timestamp }

            if (latest != null) {

                val person = latest.person

                val senderName = person
                    ?.name
                    ?.toString()
                    ?.trim()

                val isFromYou =
                    senderName.equals(
                        "You",
                        ignoreCase = true
                    ) ||
                            senderName.equals(
                                messagingStyle.user?.name
                                    ?.toString()
                                    ?.trim(),
                                ignoreCase = true
                            ) ||
                            person == null

                return TelegramMessage(
                    sender = if (isFromYou) {
                        "You"
                    } else {
                        senderName
                    },
                    body = latest.text
                        ?.toString()
                        ?.trim()
                        ?: "",
                    timestamp = latest.timestamp,
                    isFromYou = isFromYou
                )
            }
        }

        // ---------------------------------------------------------
        // Raw JSON fallback
        // ---------------------------------------------------------

        return extractLatestMessageFromJson(rawJson)
    }

    // =============================================================
    // JSON fallback
    // =============================================================

    private fun extractLatestMessageFromJson(
        rawJson: String?
    ): TelegramMessage? {

        if (rawJson.isNullOrBlank()) {
            return null
        }

        return try {

            val root =
                JsonParser
                    .parseString(rawJson)
                    .asJsonObject

            val extras =
                root.getAsJsonObject("extras")
                    ?: return null

            val messages =
                extras.getAsJsonArray("android.messages")
                    ?: return null

            val latest = messages
                .map { it.asJsonObject }
                .maxByOrNull {
                    it.get("time")
                        ?.takeIf { value -> !value.isJsonNull }
                        ?.asLong
                        ?: 0L
                }
                ?: return null

            val sender =
                extractJsonSender(latest)

            val body =
                latest.get("text")
                    ?.takeIf { !it.isJsonNull }
                    ?.asString
                    ?.trim()
                    ?: ""

            val timestamp =
                latest.get("time")
                    ?.takeIf { !it.isJsonNull }
                    ?.asLong
                    ?: 0L

            val isFromYou =
                sender.equals(
                    "You",
                    ignoreCase = true
                ) || sender.isNullOrBlank()

            TelegramMessage(
                sender = if (isFromYou) {
                    "You"
                } else {
                    sender
                },
                body = body,
                timestamp = timestamp,
                isFromYou = isFromYou
            )

        } catch (_: Exception) {
            null
        }
    }

    // =============================================================
    // Sender
    // =============================================================

    private fun extractJsonSender(
        message: JsonObject
    ): String? {

        val senderPerson =
            message.getAsJsonObject(
                "sender_person"
            )

        val personName =
            senderPerson
                ?.get("name")
                ?.takeIf { !it.isJsonNull }
                ?.asString
                ?.trim()

        if (!personName.isNullOrBlank()) {
            return personName
        }

        return message
            .get("sender")
            ?.takeIf { !it.isJsonNull }
            ?.asString
            ?.trim()
    }

    // =============================================================
    // Current-user detection
    // =============================================================

    private fun detectIsFromYou(
        title: String?,
        text: String?
    ): Boolean {

        if (title.equals("You", ignoreCase = true)) {
            return true
        }

        if (
            title?.endsWith(
                ": You",
                ignoreCase = true
            ) == true
        ) {
            return true
        }

        if (
            text?.startsWith(
                "Replied you using",
                ignoreCase = true
            ) == true
        ) {
            return true
        }

        return false
    }

    // =============================================================
    // Group detection
    // =============================================================

    private fun isLikelySenderPrefixedMessage(
        text: String?
    ): Boolean {

        if (text.isNullOrBlank()) {
            return false
        }

        if (text.startsWith("http", ignoreCase = true)) {
            return false
        }

        val colonIndex = text.indexOf(":")

        if (colonIndex <= 0) {
            return false
        }

        /*
         * Avoid treating normal time values such as:
         *
         * 5:30 PM
         *
         * as sender prefixes.
         */
        val prefix = text
            .substring(0, colonIndex)
            .trim()

        if (prefix.all { it.isDigit() }) {
            return false
        }

        return prefix.length <= 100
    }

    private fun hasSenderPrefix(
        body: String
    ): Boolean {

        if (body.isBlank()) {
            return false
        }

        val colonIndex = body.indexOf(":")

        if (colonIndex <= 0) {
            return false
        }

        val prefix =
            body.substring(
                0,
                colonIndex
            ).trim()

        return prefix.isNotBlank()
    }

    // =============================================================
    // Conversation title
    // =============================================================

    private fun extractGroupTitleFromTitle(
        title: String?
    ): String? {

        if (title.isNullOrBlank()) {
            return null
        }

        /*
         * Example:
         *
         * Telegram Group: Sender
         *
         * Keep only the group part.
         */
        if (title.contains(":")) {

            val prefix =
                title.substringBefore(":")
                    .trim()

            if (prefix.isNotBlank() &&
                !prefix.equals(
                    "You",
                    ignoreCase = true
                )
            ) {
                return prefix
            }
        }

        return title.trim()
    }

    // =============================================================
    // Clean body
    // =============================================================

    private fun cleanMessageBody(
        rawBody: String,
        senderName: String,
        isGroup: Boolean
    ): String {

        if (rawBody.isBlank()) {
            return ""
        }

        if (!isGroup) {
            return rawBody
        }

        if (senderName.isBlank()) {
            return rawBody
        }

        val prefix =
            "$senderName:"

        return if (
            rawBody.startsWith(
                prefix,
                ignoreCase = true
            )
        ) {
            rawBody
                .substring(prefix.length)
                .trim()
        } else {
            rawBody
        }
    }
}