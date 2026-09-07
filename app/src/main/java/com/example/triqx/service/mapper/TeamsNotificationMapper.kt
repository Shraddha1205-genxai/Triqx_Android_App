package com.example.triqx.service.mapper

import android.app.Notification
import android.os.Bundle
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.example.triqx.data.local.ContactEntity
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Mapper for Microsoft Teams notifications.
 *
 * Handles:
 * - Channel / group messages
 * - 1-on-1 chats
 * - Conversation title extraction
 * - Sender extraction from MessagingStyle when available
 * - "You" / current-user detection
 * - Latest message selection by timestamp
 * - Reply notifications
 * - Stable chat tag extraction
 */
class TeamsNotificationMapper : NotificationMapper {

    override fun canHandle(packageName: String): Boolean {
        return packageName.contains("teams", ignoreCase = true) ||
                packageName.equals(
                    "com.microsoft.teams",
                    ignoreCase = true
                ) ||
                packageName.equals(
                    "com.microsoft.skype.teams",
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

        val subText = extras
            .getCharSequence(Notification.EXTRA_SUB_TEXT)
            ?.toString()
            ?.trim()

        val conversationTitleExtra = extras
            .getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
            ?.toString()
            ?.trim()

        val sbnTag = sbn.tag?.trim()

        // ---------------------------------------------------------
        // Group / channel detection
        // ---------------------------------------------------------

        val isGroupFlag = extras.getBoolean(
            Notification.EXTRA_IS_GROUP_CONVERSATION,
            false
        )

        val isChannelOrGroup =
            isGroupFlag ||
                    !conversationTitleExtra.isNullOrBlank() ||
                    sbnTag?.contains(
                        "thread",
                        ignoreCase = true
                    ) == true

        // ---------------------------------------------------------
        // Current-user identity
        // ---------------------------------------------------------

        val selfDisplayName =
            extractSelfDisplayName(extras)

        // ---------------------------------------------------------
        // Latest real message
        // ---------------------------------------------------------

        val latestMessage =
            extractLatestMessage(
                notification = notification,
                rawJson = rawJson,
                selfDisplayName = selfDisplayName
            )

        // ---------------------------------------------------------
        // Conversation title
        // ---------------------------------------------------------

        val conversationTitle =
            resolveConversationTitle(
                isGroupOrChannel = isChannelOrGroup,
                conversationTitleExtra = conversationTitleExtra,
                matchedContact = matchedContact,
                title = title
            )

        // ---------------------------------------------------------
        // Sender
        // ---------------------------------------------------------

        val isFromYou =
            latestMessage?.isFromYou
                ?: detectIsFromYouFallback(
                    title = title,
                    text = text
                )

        val individualSender =
            when {

                isFromYou -> {
                    "You"
                }

                !latestMessage?.sender.isNullOrBlank() -> {
                    latestMessage!!.sender!!
                }

                isChannelOrGroup &&
                        hasSenderPrefix(
                            latestMessage?.body
                                ?: text
                                ?: bigText
                        ) -> {

                    extractSenderFromPrefixedText(
                        latestMessage?.body
                            ?: text
                            ?: bigText
                            ?: ""
                    )
                }

                !title.isNullOrBlank() -> {
                    title
                }

                !matchedContact?.displayName.isNullOrBlank() -> {
                    matchedContact!!.displayName
                }

                else -> {
                    "Member"
                }
            }

        // ---------------------------------------------------------
        // Body
        // ---------------------------------------------------------

        val rawBody =
            latestMessage?.body
                ?.takeIf { it.isNotBlank() }
                ?: text
                ?: bigText
                ?: ""

        val cleanBody =
            cleanMessageBody(
                rawBody = rawBody,
                senderName = individualSender,
                isGroupOrChannel = isChannelOrGroup
            )

        // ---------------------------------------------------------
        // Chat tag
        // ---------------------------------------------------------

        val chatTag =
            sbnTag
                ?.takeIf { it.isNotBlank() }
                ?: conversationTitleExtra
                ?: title
                ?: sbn.packageName

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
            isGroup = isChannelOrGroup,
            isFromYou = isFromYou
        )
    }

    // =============================================================
    // Message model
    // =============================================================

    private data class TeamsMessage(
        val sender: String?,
        val body: String,
        val timestamp: Long,
        val isFromYou: Boolean
    )

    // =============================================================
    // Self display name
    // =============================================================

    private fun extractSelfDisplayName(
        extras: Bundle
    ): String? {

        return extras
            .getCharSequence("android.selfDisplayName")
            ?.toString()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    // =============================================================
    // Latest message
    // =============================================================

    private fun extractLatestMessage(
        notification: Notification,
        rawJson: String?,
        selfDisplayName: String?
    ): TeamsMessage? {

        // ---------------------------------------------------------
        // Preferred: AndroidX MessagingStyle
        // ---------------------------------------------------------

        val messagingStyle = try {
            NotificationCompat.MessagingStyle
                .extractMessagingStyleFromNotification(
                    notification
                )
        } catch (_: Exception) {
            null
        }

        if (messagingStyle != null) {

            /*
             * Select by actual message timestamp rather than
             * assuming Android's message list is already sorted.
             */
            val latest =
                messagingStyle.messages
                    .maxByOrNull { it.timestamp }

            if (latest != null) {

                val person = latest.person

                val senderName =
                    person
                        ?.name
                        ?.toString()
                        ?.trim()

                val messagingUserName =
                    messagingStyle.user
                        ?.name
                        ?.toString()
                        ?.trim()

                val isFromYou =
                    when {

                        senderName.isNullOrBlank() -> {
                            true
                        }

                        senderName.equals(
                            "You",
                            ignoreCase = true
                        ) -> {
                            true
                        }

                        !selfDisplayName.isNullOrBlank() &&
                                senderName.equals(
                                    selfDisplayName,
                                    ignoreCase = true
                                ) -> {
                            true
                        }

                        !messagingUserName.isNullOrBlank() &&
                                senderName.equals(
                                    messagingUserName,
                                    ignoreCase = true
                                ) -> {
                            true
                        }

                        else -> {
                            false
                        }
                    }

                return TeamsMessage(
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

        return extractLatestMessageFromJson(
            rawJson = rawJson,
            selfDisplayName = selfDisplayName
        )
    }

    // =============================================================
    // JSON fallback
    // =============================================================

    private fun extractLatestMessageFromJson(
        rawJson: String?,
        selfDisplayName: String?
    ): TeamsMessage? {

        if (rawJson.isNullOrBlank()) {
            return null
        }

        return try {

            val root =
                JsonParser
                    .parseString(rawJson)
                    .asJsonObject

            val extras =
                root
                    .getAsJsonObject("extras")
                    ?: return null

            val messages =
                extras
                    .getAsJsonArray("android.messages")
                    ?: return null

            val latest =
                messages
                    .map { it.asJsonObject }
                    .maxByOrNull { message ->
                        messageTime(message)
                    }
                    ?: return null

            val sender =
                extractSenderName(latest)

            val body =
                latest
                    .get("text")
                    ?.takeIf { !it.isJsonNull }
                    ?.asString
                    ?.trim()
                    ?: ""

            val timestamp =
                messageTime(latest)

            val isFromYou =
                if (sender.isNullOrBlank()) {
                    true
                } else {
                    sender.equals(
                        "You",
                        ignoreCase = true
                    ) ||
                            (
                                    !selfDisplayName.isNullOrBlank() &&
                                            sender.equals(
                                                selfDisplayName,
                                                ignoreCase = true
                                            )
                                    )
                }

            TeamsMessage(
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
    // JSON sender extraction
    // =============================================================

    private fun extractSenderName(
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
    // Timestamp
    // =============================================================

    private fun messageTime(
        message: JsonObject
    ): Long {

        return message
            .get("time")
            ?.takeIf { !it.isJsonNull }
            ?.asLong
            ?: 0L
    }

    // =============================================================
    // Fallback "You" detection
    // =============================================================

    private fun detectIsFromYouFallback(
        title: String?,
        text: String?
    ): Boolean {

        if (
            title.equals(
                "You",
                ignoreCase = true
            )
        ) {
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
    // Sender prefix
    // =============================================================

    private fun hasSenderPrefix(
        text: String?
    ): Boolean {

        if (text.isNullOrBlank()) {
            return false
        }

        if (text.startsWith("http", ignoreCase = true)) {
            return false
        }

        val colonIndex =
            text.indexOf(":")

        if (colonIndex <= 0) {
            return false
        }

        val prefix =
            text.substring(
                0,
                colonIndex
            ).trim()

        if (prefix.isBlank()) {
            return false
        }

        // Avoid treating "5:30 PM" as sender/message.
        if (prefix.all { it.isDigit() }) {
            return false
        }

        return prefix.length <= 100
    }

    private fun extractSenderFromPrefixedText(
        text: String
    ): String {

        val colonIndex =
            text.indexOf(":")

        if (colonIndex <= 0) {
            return "Member"
        }

        return text
            .substring(
                0,
                colonIndex
            )
            .trim()
            .ifBlank {
                "Member"
            }
    }

    // =============================================================
    // Conversation title
    // =============================================================

    private fun resolveConversationTitle(
        isGroupOrChannel: Boolean,
        conversationTitleExtra: String?,
        matchedContact: ContactEntity?,
        title: String?
    ): String {

        if (
            isGroupOrChannel &&
            !conversationTitleExtra.isNullOrBlank()
        ) {
            return conversationTitleExtra
        }

        return matchedContact?.displayName
            ?.takeIf { it.isNotBlank() }
            ?: title
                ?.takeIf { it.isNotBlank() }
            ?: "Teams Chat"
    }

    // =============================================================
    // Body cleanup
    // =============================================================

    private fun cleanMessageBody(
        rawBody: String,
        senderName: String,
        isGroupOrChannel: Boolean
    ): String {

        if (rawBody.isBlank()) {
            return ""
        }

        if (!isGroupOrChannel) {
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