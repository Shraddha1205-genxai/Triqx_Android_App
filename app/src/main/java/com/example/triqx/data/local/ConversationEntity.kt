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
    val senderIdentifier: String? = null,        // Email address or phone number of sender
    val receiverIdentifier: String? = null,      // User's receiving account email or identifier (e.g. raj.harsh2001@gmail.com)
    val messages: List<ChatMessage> = emptyList(), // Full conversation history
    val latestTimestamp: Long,                   // Timestamp of the latest message for sorting
    val latestNotificationKey: String? = null,   // Android notification slot key for live dismiss/reply
    val customPrompt: String? = null,            // Conversation-specific AI prompt override
    val replyCount: Int? = null                  // Conversation-specific number of replies override
)
