package com.example.triqx.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Request payload sent to backend for AI smart reply generation.
 */
data class SmartReplyRequest(
    @SerializedName("appPackage") val appPackage: String,
    @SerializedName("conversationTitle") val conversationTitle: String,
    @SerializedName("replyStyle") val replyStyle: String = "concise",
    @SerializedName("additionalPrompt") val additionalPrompt: String? = null,
    @SerializedName("replyCount") val replyCount: Int = 3,
    @SerializedName("messages") val messages: List<ReplyMessageDto>
)

/**
 * Individual message item in the conversation history.
 */
data class ReplyMessageDto(
    @SerializedName("sender") val sender: String,
    @SerializedName("text") val text: String,
    @SerializedName("timestamp") val timestamp: Long,
    @SerializedName("isFromUser") val isFromUser: Boolean
)

/**
 * Response payload received from backend AI service.
 */
data class SmartReplyResponse(
    @SerializedName("success") val success: Boolean = true,
    @SerializedName("replies") val replies: List<String> = emptyList(),
    @SerializedName("error") val error: String? = null
)
