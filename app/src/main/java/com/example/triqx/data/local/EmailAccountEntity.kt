package com.example.triqx.data.local

/**
 * Represents a connected email account (e.g. Gmail).
 */
data class EmailAccountEntity(
    val emailAddress: String,
    val displayName: String? = null,
    val accessToken: String,
    val refreshToken: String?,
    val tokenExpiryEpochMs: Long,
    val provider: String = "GMAIL",
    val isConnected: Boolean = true
)
