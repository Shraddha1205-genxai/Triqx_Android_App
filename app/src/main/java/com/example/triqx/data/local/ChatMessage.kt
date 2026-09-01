package com.example.triqx.data.local

/**
 * Represents a single message inside a conversation thread.
 */
data class ChatMessage(
    val senderName: String,               // Sender's name (e.g. "Harsh Raj", "Mom") or "You"
    val subText: String? = null,          // Optional subtitle (e.g. Email Subject, Channel/Account)
    val bodyText: String,                 // Main message body content
    val timestamp: Long,                  // Time received/sent in milliseconds
    val isFromYou: Boolean = senderName.equals("You", ignoreCase = true)
)
