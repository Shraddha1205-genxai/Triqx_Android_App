package com.example.triqx.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores aggregated conversation thread data per unique conversation target.
 */
@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey
    val conversationKey: String,                 // e.g. "contact_3_pkg_com.whatsapp_tag_12345"
    val packageName: String,                     // e.g. "com.whatsapp"
    val contactId: Int? = null,                  // Linked VIP Contact ID (null for non-VIPs/groups)
    val title: String,                           // Sender/Group display name
    val specificIdentifier: String? = null,        // Email address or phone number
    val messages: List<ChatMessage> = emptyList(), // Full conversation history
    val latestTimestamp: Long,                   // Timestamp of the latest message for sorting
    val latestNotificationKey: String? = null    // Android notification slot key for live dismiss/reply
)
