package com.example.triqx.data.local

import android.content.SharedPreferences
import android.util.Log

/** Encrypts/decrypts small secrets. The production implementation is Keystore-backed. */
interface TokenCipher {
    @Throws(TokenCipherException::class)
    fun encrypt(plain: String): String

    @Throws(TokenCipherException::class)
    fun decrypt(blob: String): String
}

/**
 * @param permanent true when the data can never be decrypted again (key lost / data corrupt),
 *                  false for transient failures (Keystore busy or not yet available).
 */
class TokenCipherException(message: String, val permanent: Boolean, cause: Throwable? = null) :
    Exception(message, cause)

sealed interface TokenRead {
    data class Present(val accessToken: String, val refreshToken: String?) : TokenRead

    /** Nothing stored (or stored data was permanently unreadable and has been discarded). */
    object Absent : TokenRead

    /** Tokens exist but cannot be read right now. The session is NOT lost; try again later. */
    data class Unavailable(val reason: String) : TokenRead
}

/**
 * The ONE place auth tokens live on the device.
 *
 * Previously tokens were written to two stores (plain SharedPreferences AND EncryptedSharedPreferences)
 * and read from "whichever is available first". When the Keystore was temporarily unavailable the two
 * copies diverged, and a stale refresh token (already revoked by server-side rotation) could be used,
 * which logged the user out. It also left tokens in plaintext.
 *
 * Properties:
 *  - Single source of truth; AES-256-GCM encrypted at rest.
 *  - Writes use commit() so a rotated token is on disk before the process can be killed.
 *  - The latest value is cached in memory, so a Keystore hiccup after the first successful read/write
 *    never drops a live session, and a failed disk write is retried on the next access.
 *  - Distinguishes "no tokens" from "can't read them right now" (see [TokenRead]).
 */
class SecureTokenStore(
    private val prefs: SharedPreferences,
    private val cipher: TokenCipher
) {
    companion object {
        private const val TAG = "SecureTokenStore"
        private const val KEY_ACCESS = "tok_access_v2"
        private const val KEY_REFRESH = "tok_refresh_v2"
    }

    private val lock = Any()

    @Volatile
    private var cached: TokenRead.Present? = null

    /** True when the in-memory value is newer than what is on disk (a persist failed). */
    private var dirty = false

    fun read(): TokenRead = synchronized(lock) {
        val hit = cached
        if (hit != null) {
            if (dirty) persistLocked()
            return hit
        }

        val encAccess = prefs.getString(KEY_ACCESS, null) ?: return TokenRead.Absent
        try {
            val access = cipher.decrypt(encAccess)
            val refresh = prefs.getString(KEY_REFRESH, null)?.let { cipher.decrypt(it) }
            val present = TokenRead.Present(access, refresh)
            cached = present
            present
        } catch (e: TokenCipherException) {
            if (e.permanent) {
                Log.w(TAG, "Stored tokens are permanently unreadable; discarding: ${e.message}")
                prefs.edit().remove(KEY_ACCESS).remove(KEY_REFRESH).commit()
                TokenRead.Absent
            } else {
                Log.w(TAG, "Token store temporarily unavailable: ${e.message}")
                TokenRead.Unavailable(e.message ?: "secure storage unavailable")
            }
        }
    }

    /**
     * Stores a new token pair. The in-memory copy is always updated; the return value says whether
     * it reached disk. A refresh token of null/blank clears the stored refresh token.
     */
    fun write(accessToken: String, refreshToken: String?): Boolean = synchronized(lock) {
        cached = TokenRead.Present(accessToken, refreshToken?.takeIf { it.isNotBlank() })
        persistLocked()
    }

    fun clear() {
        synchronized(lock) {
            cached = null
            dirty = false
            prefs.edit().remove(KEY_ACCESS).remove(KEY_REFRESH).commit()
        }
    }

    private fun persistLocked(): Boolean {
        val value = cached ?: return true
        return try {
            val editor = prefs.edit()
            editor.putString(KEY_ACCESS, cipher.encrypt(value.accessToken))
            if (value.refreshToken != null) {
                editor.putString(KEY_REFRESH, cipher.encrypt(value.refreshToken))
            } else {
                editor.remove(KEY_REFRESH)
            }
            val ok = editor.commit()
            dirty = !ok
            ok
        } catch (e: TokenCipherException) {
            Log.w(TAG, "Could not persist tokens (kept in memory, will retry): ${e.message}")
            dirty = true
            false
        }
    }
}

/** A token pair found in a legacy store, tagged with where it came from (for logging). */
data class LegacyTokens(val access: String?, val refresh: String?, val source: String)

/**
 * Picks the most recent legacy pair: the one whose access token expires last. Pairs without both
 * tokens are ignored. [expiryOf] returns the JWT `exp` (epoch seconds) or null.
 */
internal fun pickNewestLegacy(candidates: List<LegacyTokens>, expiryOf: (String) -> Long?): LegacyTokens? =
    candidates
        .filter { !it.access.isNullOrBlank() && !it.refresh.isNullOrBlank() }
        .maxByOrNull { expiryOf(it.access!!) ?: 0L }
