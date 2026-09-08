package com.example.triqx.service.mapper

import android.app.Notification
import android.app.Person
import android.os.Bundle
import android.service.notification.StatusBarNotification
import com.example.triqx.data.local.ContactEntity
import com.example.triqx.utils.EmailUtils
import com.google.gson.JsonParser

/**
 * Mapper for Email notifications (Gmail, Microsoft Outlook, Yahoo Mail, etc.).
 * Handles:
 * - Sender name extraction (VIP Contact name, email name)
 * - Email address resolution for stable "email_user@domain.com" chatTag
 * - Subject and Body separation
 */
class EmailNotificationMapper : NotificationMapper {

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
        val rawTitle = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim()

        val isFromYou = rawTitle.equals("You", ignoreCase = true) || text?.startsWith("Replied you using", ignoreCase = true) == true

        // 1. Extract user's receiving account email directly from subText (no channelId)
        val receiverIdentifier = extractReceiverIdentifier(extras, rawJson)

        // 2. Extract clean sender email address (excluding the receiver's account)
        val senderEmail = extractSenderEmail(extras, rawJson, rawTitle, matchedContact, text, bigText, receiverIdentifier)

        // 3. Resolve clean display title (strip "<email>" if present in raw title)
        val cleanTitle = if (!rawTitle.isNullOrBlank()) {
            if (rawTitle.contains("<") && rawTitle.contains(">")) {
                val namePart = rawTitle.substringBefore("<").trim()
                if (namePart.isNotBlank()) namePart else rawTitle
            } else {
                rawTitle
            }
        } else null

        val conversationTitle = if (isFromYou) {
            matchedContact?.displayName ?: "Email"
        } else {
            matchedContact?.displayName?.ifBlank { null }
                ?: cleanTitle?.ifBlank { null }
                ?: (senderEmail ?: "Email")
        }

        // 4. Resolve Subject Line for subText
        // In Gmail & email apps, EXTRA_TEXT is the actual Subject line.
        // In BigTextStyle, bigText starts with the subject line followed by body.
        val emailSubject = when {
            !text.isNullOrBlank() -> text
            !bigText.isNullOrBlank() -> bigText.substringBefore("\n").trim()
            !cleanTitle.isNullOrBlank() && !cleanTitle.contains("@") -> cleanTitle
            !subText.isNullOrBlank() && !subText.contains("@") -> subText
            else -> null
        }

        // 5. Resolve Clean Message Body (strip subject prefix from bigText)
        val effectiveText = if (!bigText.isNullOrBlank()) {
            val subjectPrefix = emailSubject ?: text?.trim()
            if (!subjectPrefix.isNullOrBlank() && bigText.startsWith(subjectPrefix)) {
                val body = bigText.substringAfter(subjectPrefix).trim().removePrefix("\n").trim()
                if (body.isNotBlank()) body else bigText
            } else bigText
        } else {
            text ?: bigText ?: ""
        }

        // 6. Build stable chatTag partitioned by receiver account to keep multi-account threads separate
        val cleanReceiver = EmailUtils.cleanEmail(receiverIdentifier)
        val cleanSender = EmailUtils.cleanEmail(senderEmail)

        val chatTag = when {
            !cleanSender.isNullOrBlank() && !cleanReceiver.isNullOrBlank() -> "email_${cleanReceiver}_$cleanSender"
            !cleanSender.isNullOrBlank() -> "email_$cleanSender"
            !cleanReceiver.isNullOrBlank() -> "sender_${cleanReceiver}_${conversationTitle.trim()}"
            else -> "sender_${conversationTitle.trim()}"
        }

        return ParsedNotification(
            conversationTitle = conversationTitle,
            individualSender = if (isFromYou) "You" else conversationTitle,
            subText = emailSubject,
            bodyText = effectiveText,
            chatTag = chatTag.trim(),
            isGroup = false,
            senderIdentifier = cleanSender,
            receiverIdentifier = cleanReceiver
        )
    }

    private fun extractSenderEmail(
        extras: Bundle,
        rawJson: String?,
        title: String?,
        matchedContact: ContactEntity?,
        text: String?,
        bigText: String?,
        receiverIdentifier: String?
    ): String? {
        val cleanReceiver = EmailUtils.cleanEmail(receiverIdentifier)

        // 1. Direct contact email match
        val contactEmail = matchedContact?.primaryEmail ?: matchedContact?.emails?.firstOrNull { it.isNotBlank() }
        if (!contactEmail.isNullOrBlank()) {
            val candidate = EmailUtils.cleanEmail(contactEmail)
            if (candidate != null && (cleanReceiver == null || !candidate.equals(cleanReceiver, ignoreCase = true))) {
                return candidate
            }
        }

        // 2. Extract from modern Android Person extras (EXTRA_MESSAGING_PERSON, EXTRA_PEOPLE_LIST)
        @Suppress("DEPRECATION")
        extras.getParcelable<Person>(Notification.EXTRA_MESSAGING_PERSON)?.let { p ->
            val fromPerson = extractEmailFromPerson(p)
            if (fromPerson != null && (cleanReceiver == null || !fromPerson.equals(cleanReceiver, ignoreCase = true))) {
                return fromPerson
            }
        }

        @Suppress("DEPRECATION")
        extras.getParcelableArrayList<Person>(Notification.EXTRA_PEOPLE_LIST)?.forEach { p ->
            val fromPerson = extractEmailFromPerson(p)
            if (fromPerson != null && (cleanReceiver == null || !fromPerson.equals(cleanReceiver, ignoreCase = true))) {
                return fromPerson
            }
        }

        @Suppress("DEPRECATION")
        extras.getStringArrayList("android.people")?.forEach { p ->
            val candidate = EmailUtils.cleanEmail(p)
            if (candidate != null && (cleanReceiver == null || !candidate.equals(cleanReceiver, ignoreCase = true))) {
                return candidate
            }
        }

        @Suppress("DEPRECATION")
        extras.getStringArray(Notification.EXTRA_PEOPLE)?.forEach { p ->
            val candidate = EmailUtils.cleanEmail(p)
            if (candidate != null && (cleanReceiver == null || !candidate.equals(cleanReceiver, ignoreCase = true))) {
                return candidate
            }
        }

        // 3. Extract from people list in rawJson
        if (!rawJson.isNullOrBlank()) {
            try {
                val root = JsonParser.parseString(rawJson).asJsonObject
                val extrasObj = root.getAsJsonObject("extras")
                if (extrasObj != null && extrasObj.has("android.people.list")) {
                    val people = extrasObj.getAsJsonArray("android.people.list")
                    for (i in 0 until people.size()) {
                        val person = people.get(i).asJsonObject
                        val uri = if (person.has("uri") && !person.get("uri").isJsonNull) person.get("uri").asString else null
                        val key = if (person.has("key") && !person.get("key").isJsonNull) person.get("key").asString else null
                        val candidate = EmailUtils.cleanEmail(uri) ?: EmailUtils.cleanEmail(key)
                        if (candidate != null && (cleanReceiver == null || !candidate.equals(cleanReceiver, ignoreCase = true))) {
                            return candidate
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // 4. Extract from title (e.g. "Harsh Raj <harsh@genxai.com>" or "harsh@genxai.com")
        if (!title.isNullOrBlank()) {
            val candidate = EmailUtils.cleanEmail(title)
            if (candidate != null && (cleanReceiver == null || !candidate.equals(cleanReceiver, ignoreCase = true))) {
                return candidate
            }
        }

        // 5. Search message text for sender email if different from receiver
        val candidateText = "${text ?: ""} ${bigText ?: ""}"
        if (candidateText.isNotBlank()) {
            EmailUtils.EMAIL_REGEX.findAll(candidateText).forEach { match ->
                val found = EmailUtils.cleanEmail(match.value)
                if (found != null && (cleanReceiver == null || !found.equals(cleanReceiver, ignoreCase = true))) {
                    return found
                }
            }
        }

        return null
    }

    private fun extractEmailFromPerson(person: Person): String? {
        val uri = person.uri?.toString()
        val key = person.key
        val name = person.name?.toString()
        return EmailUtils.cleanEmail(uri)
            ?: EmailUtils.cleanEmail(key)
            ?: EmailUtils.cleanEmail(name)
    }

    private fun extractReceiverIdentifier(
        extras: Bundle,
        rawJson: String?
    ): String? {
        // In Gmail & Outlook, EXTRA_SUB_TEXT directly holds the user's receiving account email (e.g. "gamesvc3@gmail.com")
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim()
        if (!subText.isNullOrBlank() && subText.contains("@")) {
            return subText.lowercase()
        }

        // Fallback: Parse subText from rawJson
        if (!rawJson.isNullOrBlank()) {
            try {
                val root = JsonParser.parseString(rawJson).asJsonObject
                val jsonSubText = root.get("subText")?.asString?.trim()
                if (!jsonSubText.isNullOrBlank() && jsonSubText.contains("@")) {
                    return jsonSubText.lowercase()
                }
            } catch (_: Exception) {}
        }

        return null
    }
}
