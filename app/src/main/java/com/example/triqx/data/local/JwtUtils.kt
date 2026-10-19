package com.example.triqx.data.local

import com.google.gson.JsonParser
import java.util.Base64

/**
 * Returns the JWT `exp` claim (epoch seconds), [Long.MAX_VALUE] when the token is well-formed but
 * has no `exp`, or null when the token cannot be parsed at all.
 */
fun jwtExpiryEpochSeconds(token: String?): Long? {
    if (token.isNullOrBlank()) return null
    return try {
        val parts = token.split(".")
        if (parts.size < 2) return null
        val bytes = try {
            Base64.getUrlDecoder().decode(parts[1])
        } catch (_: Exception) {
            try {
                Base64.getDecoder().decode(parts[1])
            } catch (_: Exception) {
                null
            }
        } ?: return null
        val obj = JsonParser.parseString(String(bytes, Charsets.UTF_8)).asJsonObject
        if (obj.has("exp")) obj.get("exp").asLong else Long.MAX_VALUE
    } catch (_: Exception) {
        null
    }
}

/** True when [token] is blank, malformed, or expires within [bufferSeconds]. */
fun isJwtExpired(token: String?, bufferSeconds: Long = 60, nowMillis: Long = System.currentTimeMillis()): Boolean {
    val exp = jwtExpiryEpochSeconds(token) ?: return true
    if (exp == Long.MAX_VALUE) return false
    return nowMillis / 1000 >= exp - bufferSeconds
}
