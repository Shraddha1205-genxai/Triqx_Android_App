package com.example.triqx.service.mapper

/**
 * Standardized data model produced by app-specific notification mappers.
 */
data class ParsedNotification(
    val conversationTitle: String, // Group name for groups, or sender name for 1-on-1
    val individualSender: String,  // Specific person who sent the message, or "You"
    val subText: String? = null,   // Email Subject (null for chat apps)
    val bodyText: String,          // Clean message content (without redundant sender prefix)
    val chatTag: String,           // Stable thread identifier (trimmed of whitespace/newlines)
    val isGroup: Boolean,          // Whether the notification belongs to a group/channel
    val isFromYou: Boolean = individualSender.equals("You", ignoreCase = true)
)
