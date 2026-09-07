package com.example.triqx.service.mapper

import android.app.Notification
import android.os.Bundle
import android.service.notification.StatusBarNotification
import com.example.triqx.data.local.ContactEntity
import com.google.gson.JsonParser

/**
 * Mapper for Email notifications (Gmail, Microsoft Outlook, Yahoo Mail, etc.).
 * Handles:
 * - Sender name extraction (VIP Contact name, email name)
 * - Email address resolution for stable "email_user@domain.com" chatTag
 * - Subject and Body separation
 */
class EmailNotificationMapper : NotificationMapper {

    private val emailRegex = Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")

    override fun canHandle(packageName: String): Boolean {
        return packageName.contains("gm", ignoreCase = true) ||
               packageName.contains("email", ignoreCase = true) ||
               packageName.contains("outlook", ignoreCase = true) ||
               packageName.contains("mail", ignoreCase = true)
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

        val isFromYou = title.equals("You", ignoreCase = true) || text?.startsWith("Replied you using", ignoreCase = true) == true

        // 1. Extract clean recipient/sender email address
        val senderEmail = extractSenderEmail(extras, rawJson, title, matchedContact)

        // 2. Resolve Conversation Title (Sender/Company Name)
        val conversationTitle = if (isFromYou) {
            matchedContact?.displayName ?: "Email"
        } else {
            matchedContact?.displayName?.ifBlank { null }
                ?: title?.ifBlank { null }
                ?: (senderEmail ?: "Email")
        }

        // 3. Resolve Subject Line for subText
        val emailSubject = when {
            !title.isNullOrBlank() && !title.contains("@") -> title
            !subText.isNullOrBlank() && !subText.contains("@") -> subText
            else -> "Email"
        }

        // 4. Resolve Clean Message Body
        val effectiveText = if (!bigText.isNullOrBlank()) {
            if (!text.isNullOrBlank() && bigText.startsWith(text.trim())) {
                val body = bigText.substringAfter(text.trim()).trim().removePrefix("\n").trim()
                if (body.isNotBlank()) "$text: $body" else bigText
            } else bigText
        } else {
            text ?: bigText ?: ""
        }

        // 5. Build stable chatTag ("email_john@example.com" or "sender_Name")
        val chatTag = if (!senderEmail.isNullOrBlank()) {
            "email_$senderEmail"
        } else {
            "sender_${conversationTitle.trim()}"
        }

        return ParsedNotification(
            conversationTitle = conversationTitle,
            individualSender = if (isFromYou) "You" else conversationTitle,
            subText = emailSubject,
            bodyText = effectiveText,
            chatTag = chatTag.trim(),
            isGroup = false
        )
    }

    private fun extractSenderEmail(
        extras: Bundle,
        rawJson: String?,
        title: String?,
        matchedContact: ContactEntity?
    ): String? {
        // Direct contact email match
        val primary = matchedContact?.primaryEmail
        if (!primary.isNullOrBlank()) {
            return primary.removePrefix("mailto:").trim()
        }

        // Extract from people list in rawJson or extras
        if (!rawJson.isNullOrBlank()) {
            try {
                val root = JsonParser.parseString(rawJson).asJsonObject
                val extrasObj = root.getAsJsonObject("extras")
                if (extrasObj != null && extrasObj.has("android.people.list")) {
                    val people = extrasObj.getAsJsonArray("android.people.list")
                    for (i in 0 until people.size()) {
                        val person = people.get(i).asJsonObject
                        if (person.has("uri") && !person.get("uri").isJsonNull) {
                            val uri = person.get("uri").asString
                            if (uri.startsWith("mailto:")) return uri.removePrefix("mailto:").trim()
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // Regex search in title
        if (title != null) {
            val match = emailRegex.find(title)
            if (match != null) return match.value.trim()
        }

        return null
    }
}
