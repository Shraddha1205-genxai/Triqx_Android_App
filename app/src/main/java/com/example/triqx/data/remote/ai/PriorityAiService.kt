package com.example.triqx.data.remote.ai

import com.example.triqx.data.local.ChatMessage
import com.example.triqx.data.local.ObligationEntity

/**
 * Common interface for AI-powered obligation and priority extraction.
 * Enables seamless switching between on-device models (e.g. Google AICore / Gemini Nano)
 * and cloud backend services.
 */
interface PriorityAiService {

    /**
     * Analyzes recent conversation messages and extracts pending obligations,
     * deadlines, bills, promises, or critical action items.
     * Returns an empty list if no obligations are present.
     */
    suspend fun extractObligations(
        conversationKey: String,
        conversationTitle: String,
        packageName: String,
        messages: List<ChatMessage>
    ): List<ObligationEntity>
}
