package com.example.triqx.service.mapper

import android.app.Notification
import android.os.Bundle
import androidx.core.app.NotificationCompat
import com.google.gson.JsonParser

data class MediaResult(
    val uri: String?,
    val type: String?
)

object MediaExtractor {

    /**
     * Extracts image or sticker media URI and MIME type from a system notification
     * (supports AndroidX MessagingStyle, raw JSON android.messages, and BigPictureStyle).
     */
    fun extractMedia(notification: Notification, rawJson: String?): MediaResult? {
        val extras = notification.extras ?: Bundle.EMPTY

        // 1. Check AndroidX MessagingStyle messages for dataUri / dataMimeType
        try {
            val messagingStyle = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
            if (messagingStyle != null) {
                val latestWithMedia = messagingStyle.messages
                    .filter { it.dataUri != null }
                    .maxByOrNull { it.timestamp }

                if (latestWithMedia?.dataUri != null) {
                    return MediaResult(
                        uri = latestWithMedia.dataUri.toString(),
                        type = latestWithMedia.dataMimeType ?: "image/jpeg"
                    )
                }
            }
        } catch (_: Exception) {}

        // 2. Check Raw JSON extras "android.messages" array (WhatsApp, Telegram, etc.)
        if (!rawJson.isNullOrBlank()) {
            try {
                val root = JsonParser.parseString(rawJson).asJsonObject
                val jsonExtras = root.getAsJsonObject("extras")
                val messages = jsonExtras?.getAsJsonArray("android.messages")
                if (messages != null && messages.size() > 0) {
                    val latest = messages.map { it.asJsonObject }.maxByOrNull {
                        it.get("time")?.takeIf { v -> !v.isJsonNull }?.asLong ?: 0L
                    }
                    if (latest != null && latest.has("uri") && !latest.get("uri").isJsonNull) {
                        val uriStr = latest.get("uri").asString.trim()
                        val typeStr = if (latest.has("type") && !latest.get("type").isJsonNull) {
                            latest.get("type").asString.trim()
                        } else "image/jpeg"

                        if (uriStr.isNotBlank()) {
                            return MediaResult(uri = uriStr, type = typeStr)
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // 3. Fallback: Check BigPictureStyle / Picture extras
        try {
            if (extras.containsKey("android.picture")) {
                return MediaResult(uri = null, type = "image/jpeg")
            }
        } catch (_: Exception) {}

        return null
    }
}
