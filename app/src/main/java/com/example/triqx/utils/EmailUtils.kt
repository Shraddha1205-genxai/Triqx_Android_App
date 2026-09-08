package com.example.triqx.utils

/**
 * Utility for parsing, extracting, and sanitizing email addresses from notification
 * metadata (channelId, subText, extras, Person objects).
 */
object EmailUtils {

    val EMAIL_REGEX = Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")

    /**
     * Normalizes an email address string (removes 'mailto:', extracts from '<email>', etc.).
     */
    fun cleanEmail(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        var text = raw.trim().removePrefix("mailto:").trim()

        if (text.contains("<") && text.contains(">")) {
            text = text.substringAfter("<").substringBefore(">").trim()
        }

        // Backward compatibility for any old records previously stored with channel prefixes
        if (text.contains("nc_") || text.contains("_mail_")) {
            text = text.substringAfterLast("_")
        }
        if (text.contains("account_")) {
            text = text.substringAfter("account_")
        }

        val match = EMAIL_REGEX.find(text)
        return match?.value?.trim()?.lowercase()
            ?: (if (text.contains("@") && !text.contains(" ")) text.trim().lowercase() else null)
    }
}
