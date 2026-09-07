package com.example.triqx.service.mapper

import android.app.Notification
import android.os.Bundle
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.example.triqx.data.local.ContactEntity
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Mapper for WhatsApp and WhatsApp Business notifications.
 *
 * Handles:
 * - Group conversation detection
 * - Conversation title extraction
 * - Latest real message detection using message timestamp
 * - Correct "You" / current-user detection
 * - WhatsApp synthetic messages such as "⤷ You got a reply"
 * - Message body cleaning
 * - Chat tag extraction
 */
class WhatsAppNotificationMapper : NotificationMapper {

    override fun canHandle(packageName: String): Boolean {
        return packageName.equals("com.whatsapp", ignoreCase = true) ||
                packageName.equals("com.whatsapp.w4b", ignoreCase = true)
    }

    override fun parse(
        sbn: StatusBarNotification,
        rawJson: String?,
        matchedContact: ContactEntity?
    ): ParsedNotification {

        val notification = sbn.notification
        val extras = notification.extras ?: Bundle.EMPTY

        // ---------------------------------------------------------
        // Basic notification fields
        // ---------------------------------------------------------

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

        val hiddenConversationTitle = extras
            .getString("android.hiddenConversationTitle")
            ?.trim()

        // ---------------------------------------------------------
        // Group detection
        // ---------------------------------------------------------

        val isGroupFlag = extras.getBoolean(
            Notification.EXTRA_IS_GROUP_CONVERSATION,
            false
        )

        val isGroupChannel =
            notification.channelId
                ?.contains("group_chat", ignoreCase = true) == true

        val sbnTag = sbn.tag?.trim()

        val isGroup =
            isGroupFlag ||
                    isGroupChannel ||
                    sbnTag?.endsWith("@g.us", ignoreCase = true) == true ||
                    !conversationTitleExtra.isNullOrBlank()

        // ---------------------------------------------------------
        // Conversation title
        // ---------------------------------------------------------

        val conversationTitle = if (isGroup) {

            conversationTitleExtra
                ?.takeIf { it.isNotBlank() }

                ?: hiddenConversationTitle
                    ?.takeIf { it.isNotBlank() }

                ?: title
                    ?.takeIf { it.isNotBlank() }

                ?: "Group Chat"

        } else {

            matchedContact?.displayName
                ?.takeIf { it.isNotBlank() }

                ?: extractOtherPersonName(notification, rawJson)

                ?: sbn.packageName
        }

        // ---------------------------------------------------------
        // Latest real message sender
        // ---------------------------------------------------------

        val senderResult = extractLatestRealMessageSender(
            notification = notification,
            rawJson = rawJson
        )

        // ---------------------------------------------------------
        // Sender name
        // ---------------------------------------------------------

        val individualSender = if (isGroup) {

            when {

                senderResult?.isFromYou == true -> {
                    "You"
                }

                !senderResult?.name.isNullOrBlank() -> {
                    senderResult!!.name!!
                }

                title != null &&
                        title.contains(":") &&
                        !title.equals(
                            conversationTitle,
                            ignoreCase = true
                        ) -> {

                    title
                        .substringAfter(":")
                        .trim()
                        .takeIf { it.isNotBlank() }
                        ?: conversationTitle
                }

                else -> {
                    conversationTitle
                }
            }

        } else {
            ""
        }

        // IMPORTANT:
        // Never calculate this from individualSender.
        val isFromYou = senderResult?.isFromYou == true

        // ---------------------------------------------------------
        // Message body
        // ---------------------------------------------------------

        val rawBody = text ?: bigText ?: ""

        val cleanBody = cleanMessageBody(
            rawBody = rawBody,
            senderName = individualSender,
            conversationTitle = conversationTitle,
            isGroup = isGroup
        )

        // ---------------------------------------------------------
        // Chat tag
        // ---------------------------------------------------------

        val chatTag =
            sbnTag?.takeIf { it.isNotBlank() }
                ?: extractTagFromKey(sbn.key)
                ?: conversationTitle

        return ParsedNotification(
            conversationTitle = conversationTitle,
            individualSender = individualSender,
            subText = null,
            bodyText = cleanBody,
            chatTag = chatTag.trim(),
            isGroup = isGroup,
            isFromYou = isFromYou
        )
    }

    // =============================================================
    // Sender result
    // =============================================================

    private data class SenderResult(
        val name: String?,
        val isFromYou: Boolean,
        val timestamp: Long
    )

    // =============================================================
    // Latest real message
    // =============================================================

    private fun extractLatestRealMessageSender(
        notification: Notification,
        rawJson: String?
    ): SenderResult? {

        val extras = notification.extras ?: Bundle.EMPTY

        // WhatsApp tells us how the current user is displayed.
        val selfDisplayName = extras
            .getCharSequence("android.selfDisplayName")
            ?.toString()
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        // ---------------------------------------------------------
        // AndroidX MessagingStyle
        // ---------------------------------------------------------

        val messagingStyle = try {
            NotificationCompat.MessagingStyle
                .extractMessagingStyleFromNotification(notification)
        } catch (_: Exception) {
            null
        }

        if (messagingStyle != null) {

            /*
             * Do NOT trust the array order.
             *
             * WhatsApp can append synthetic/system entries.
             * We sort by the actual message timestamp and then
             * choose the latest real message.
             */
            val latestMessage = messagingStyle.messages
                .filterNot { message ->

                    val senderName = message.person
                        ?.name
                        ?.toString()
                        ?.trim()

                    isSyntheticWhatsAppSender(senderName)
                }
                .maxByOrNull { message ->
                    message.timestamp
                }

            if (latestMessage != null) {

                val person = latestMessage.person

                // -------------------------------------------------
                // No sender Person
                // -------------------------------------------------

                if (person == null) {
                    return SenderResult(
                        name = "You",
                        isFromYou = true,
                        timestamp = latestMessage.timestamp
                    )
                }

                val senderName = person.name
                    ?.toString()
                    ?.trim()

                if (!senderName.isNullOrBlank()) {

                    val isFromYou = isCurrentUser(
                        senderName = senderName,
                        personUri = person.uri?.toString(),
                        personKey = person.key,
                        selfDisplayName = selfDisplayName
                    )

                    return SenderResult(
                        name = if (isFromYou) "You" else senderName,
                        isFromYou = isFromYou,
                        timestamp = latestMessage.timestamp
                    )
                }
            }
        }

        // ---------------------------------------------------------
        // Raw JSON fallback
        // ---------------------------------------------------------

        return extractLatestRealMessageSenderFromJson(
            rawJson = rawJson,
            selfDisplayName = selfDisplayName
        )
    }

    // =============================================================
    // Extract the other person's name (for 1:1 chat titles)
    // =============================================================

    /**
     * Scans message senders to find someone who is NOT the current user.
     * The other person will have a non-null URI (contact lookup),
     * while "You" always has uri = null and key = null.
     */
    private fun extractOtherPersonName(
        notification: Notification,
        rawJson: String?
    ): String? {

        // --- Try AndroidX MessagingStyle first ---

        val messagingStyle = try {
            NotificationCompat.MessagingStyle
                .extractMessagingStyleFromNotification(notification)
        } catch (_: Exception) {
            null
        }

        if (messagingStyle != null) {
            val otherPerson = messagingStyle.messages
                .mapNotNull { it.person }
                .firstOrNull { person ->
                    !person.uri?.toString().isNullOrBlank() ||
                        !person.key.isNullOrBlank()
                }

            val name = otherPerson?.name
                ?.toString()
                ?.trim()
                ?.takeIf { it.isNotBlank() }

            if (name != null) return name
        }

        // --- Raw JSON fallback ---

        if (!rawJson.isNullOrBlank()) {
            try {
                val extras = JsonParser
                    .parseString(rawJson)
                    .asJsonObject
                    .getAsJsonObject("extras")
                    ?: return null

                val messages = extras
                    .getAsJsonArray("android.messages")
                    ?: return null

                for (msg in messages) {
                    val obj = msg.asJsonObject
                    val senderPerson = obj.getAsJsonObject("sender_person")
                        ?: continue

                    val uri = senderPerson
                        .get("uri")
                        ?.takeIf { !it.isJsonNull }
                        ?.asString
                        ?.trim()

                    val key = senderPerson
                        .get("key")
                        ?.takeIf { !it.isJsonNull }
                        ?.asString
                        ?.trim()

                    if (!uri.isNullOrBlank() || !key.isNullOrBlank()) {
                        val name = senderPerson
                            .get("name")
                            ?.takeIf { !it.isJsonNull }
                            ?.asString
                            ?.trim()
                            ?.takeIf { it.isNotBlank() }

                        if (name != null) return name
                    }
                }
            } catch (_: Exception) {
                // fall through
            }
        }

        return null
    }

    // =============================================================
    // Determine current user
    // =============================================================

    private fun isCurrentUser(
        senderName: String,
        personUri: String?,
        personKey: String?,
        selfDisplayName: String?
    ): Boolean {

        // WhatsApp explicitly uses "You" for self messages
        // with no stable identity (null uri and key).
        if (senderName.equals("You", ignoreCase = true) &&
            personUri.isNullOrBlank() &&
            personKey.isNullOrBlank()
        ) {
            return true
        }

        // Compare against WhatsApp's own self display name,
        // but only when there is no stable identity.
        if (
            !selfDisplayName.isNullOrBlank() &&
            senderName.equals(
                selfDisplayName,
                ignoreCase = true
            ) &&
            personUri.isNullOrBlank() &&
            personKey.isNullOrBlank()
        ) {
            return true
        }

        /*
         * In your observed WhatsApp JSON:
         *
         * You:
         *   uri = null
         *   key = null
         *
         * Incoming:
         *   uri = content://...
         *
         * Keep this only as a secondary signal.
         */
        val hasStableIdentity =
            !personUri.isNullOrBlank() ||
                    !personKey.isNullOrBlank()

        if (
            !hasStableIdentity &&
            senderName.equals(
                selfDisplayName ?: "You",
                ignoreCase = true
            )
        ) {
            return true
        }

        return false
    }

    // =============================================================
    // Raw JSON fallback
    // =============================================================

    private fun extractLatestRealMessageSenderFromJson(
        rawJson: String?,
        selfDisplayName: String?
    ): SenderResult? {

        if (rawJson.isNullOrBlank()) {
            return null
        }

        return try {

            val root = JsonParser
                .parseString(rawJson)
                .asJsonObject

            val extras = root
                .getAsJsonObject("extras")
                ?: return null

            val messages = extras
                .getAsJsonArray("android.messages")
                ?: return null

            /*
             * Convert to objects, remove synthetic entries,
             * then sort by actual message time.
             */
            val latestMessage = messages
                .map { it.asJsonObject }
                .filterNot { message ->
                    isSyntheticWhatsAppMessage(message)
                }
                .maxByOrNull { message ->
                    messageTime(message)
                }
                ?: return null

            val senderName =
                extractSenderName(latestMessage)

            val timestamp =
                messageTime(latestMessage)

            // -----------------------------------------------------
            // No sender -> current user
            // -----------------------------------------------------

            if (senderName.isNullOrBlank()) {
                return SenderResult(
                    name = "You",
                    isFromYou = true,
                    timestamp = timestamp
                )
            }

            // -----------------------------------------------------
            // Extract sender identity
            // -----------------------------------------------------

            val senderPerson =
                latestMessage.getAsJsonObject(
                    "sender_person"
                )

            val personUri =
                senderPerson
                    ?.get("uri")
                    ?.takeIf { !it.isJsonNull }
                    ?.asString
                    ?.trim()

            val personKey =
                senderPerson
                    ?.get("key")
                    ?.takeIf { !it.isJsonNull }
                    ?.asString
                    ?.trim()

            val isFromYou = isCurrentUser(
                senderName = senderName,
                personUri = personUri,
                personKey = personKey,
                selfDisplayName = selfDisplayName
            )

            SenderResult(
                name = if (isFromYou) "You" else senderName,
                isFromYou = isFromYou,
                timestamp = timestamp
            )

        } catch (_: Exception) {
            null
        }
    }

    // =============================================================
    // Extract sender name
    // =============================================================

    private fun extractSenderName(
        message: JsonObject
    ): String? {

        // Preferred:
        // sender_person.name
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
                ?.takeIf { it.isNotBlank() }

        if (personName != null) {
            return personName
        }

        // Fallback:
        // sender
        return message
            .get("sender")
            ?.takeIf { !it.isJsonNull }
            ?.asString
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    // =============================================================
    // Message timestamp
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
    // Synthetic WhatsApp messages
    // =============================================================

    private fun isSyntheticWhatsAppMessage(
        message: JsonObject
    ): Boolean {

        val sender =
            message
                .get("sender")
                ?.takeIf { !it.isJsonNull }
                ?.asString
                ?.trim()

        val senderPersonName =
            message
                .getAsJsonObject("sender_person")
                ?.get("name")
                ?.takeIf { !it.isJsonNull }
                ?.asString
                ?.trim()

        val text =
            message
                .get("text")
                ?.takeIf { !it.isJsonNull }
                ?.asString
                ?.trim()

        return isSyntheticWhatsAppSender(sender) ||
                isSyntheticWhatsAppSender(senderPersonName) ||
                text.equals(
                    "You got a reply",
                    ignoreCase = true
                )
    }

    private fun isSyntheticWhatsAppSender(
        sender: String?
    ): Boolean {

        if (sender.isNullOrBlank()) {
            return false
        }

        val normalized = sender.trim()

        return normalized.startsWith("⤷") ||
                normalized.equals(
                    "You got a reply",
                    ignoreCase = true
                )
    }

    // =============================================================
    // Message body
    // =============================================================

    private fun cleanMessageBody(
        rawBody: String,
        senderName: String,
        conversationTitle: String,
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

        /*
         * Example:
         *
         * Ashwini GenXAI: Hello
         *
         * becomes:
         *
         * Hello
         */

        val prefix = "$senderName:"

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

    // =============================================================
    // Chat tag
    // =============================================================

    private fun extractTagFromKey(
        key: String
    ): String? {

        val parts = key.split('|')

        return if (
            parts.size >= 4 &&
            parts[3].isNotBlank() &&
            parts[3] != "null"
        ) {
            parts[3].trim()
        } else {
            null
        }
    }
}