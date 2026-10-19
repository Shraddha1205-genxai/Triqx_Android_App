package com.example.triqx.data.remote.dto

import com.example.triqx.data.local.ObligationCategory
import com.example.triqx.data.local.ObligationUrgency
import com.google.gson.annotations.SerializedName

/**
 * Request payload sent to backend /ai/extract-obligations endpoint.
 */
data class ExtractObligationsRequest(
    @SerializedName("appPackage")
    val appPackage: String,
    @SerializedName("conversationTitle")
    val conversationTitle: String,
    @SerializedName("messages")
    val messages: List<ReplyMessageDto>
)

/**
 * Single obligation item returned by AI extraction.
 */
data class ObligationDto(
    @SerializedName("title")
    val title: String,
    @SerializedName("category")
    val category: String = "Personal",
    @SerializedName("identity")
    val identity: String = "",
    @SerializedName("dueTimestamp")
    val dueTimestamp: Long? = null,
    @SerializedName("urgency")
    val urgency: String = "Medium",
    @SerializedName("amount")
    val amount: Double? = null,
    @SerializedName("confidence")
    val confidence: Float = 1.0f
) {
    val categoryEnum: ObligationCategory
        get() = ObligationCategory.fromString(category)

    val urgencyEnum: ObligationUrgency
        get() = ObligationUrgency.fromString(urgency)
}

/**
 * Response payload returned from /ai/extract-obligations.
 */
data class ExtractObligationsResponse(
    @SerializedName("obligations")
    val obligations: List<ObligationDto> = emptyList()
)
