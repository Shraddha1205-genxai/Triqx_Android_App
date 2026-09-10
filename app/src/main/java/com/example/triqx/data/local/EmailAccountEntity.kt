package com.example.triqx.data.local

import com.example.triqx.service.email.EmailProvider

/**
 * Represents a connected email account (e.g. Gmail, Microsoft Outlook).
 */
data class EmailAccountEntity(
    val emailAddress: String,
    val displayName: String? = null,
    val accessToken: String,
    val refreshToken: String?,
    val tokenExpiryEpochMs: Long,
    val provider: String = "GMAIL",
    val isConnected: Boolean = true
) {
    val emailProvider: EmailProvider
        get() = EmailProvider.fromString(provider)
}
