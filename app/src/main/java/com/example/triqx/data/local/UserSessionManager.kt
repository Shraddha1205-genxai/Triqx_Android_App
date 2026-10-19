package com.example.triqx.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.triqx.data.remote.InvalidRefreshTokenException
import com.example.triqx.data.remote.OtpAuthService
import com.example.triqx.service.TriqxAssistantNotificationManager
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Tokens exist but the secure store can't be read right now. The session is kept; retry later. */
class SecureStorageUnavailableException(message: String) : java.io.IOException(message)

/**
 * Owns the user's session:
 *  - tokens live ONLY in [SecureTokenStore] (Keystore AES-GCM),
 *  - profile + login flags live in plain SharedPreferences (so a reboot never logs anyone out),
 *  - refresh is single-flight and survives caller cancellation,
 *  - when the server definitively rejects the refresh token we "soft logout" ([markNeedsReauth]):
 *    tokens are dropped and the user is sent to the login screen, but their profile, priority
 *    contacts/apps, conversations and settings stay on the device. Signing in again restores them.
 *    Only an explicit sign-out ([clearSession]) wipes data.
 */
@Singleton
class UserSessionManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val gson: Gson,
    private val tokenStore: SecureTokenStore
) {

    companion object {
        private const val TAG = "UserSessionManager"
        private const val STANDARD_PREFS_FILE = "triqx_session_prefs"
        private const val ENCRYPTED_PREFS_FILE = "triqx_secure_session_prefs"

        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_NEEDS_REAUTH = "needs_reauth"
        private const val KEY_TOKENS_MIGRATED = "tokens_migrated_v2"
        private const val KEY_LAST_REFRESH_AT = "last_refresh_at"
        private const val KEY_USER_PROFILE_JSON = "user_profile_json"

        // Legacy token keys (pre-SecureTokenStore). Read once for migration, then deleted.
        private const val LEGACY_AUTH_TOKEN = "auth_token"
        private const val LEGACY_ACCESS_TOKEN = "access_token"
        private const val LEGACY_REFRESH_TOKEN = "refresh_token"
    }

    private val standardPrefs: SharedPreferences =
        context.getSharedPreferences(STANDARD_PREFS_FILE, Context.MODE_PRIVATE)

    init {
        migrateLegacyProfile()
        migrateLegacyTokens()
    }

    private val _isLoggedIn = MutableStateFlow(standardPrefs.getBoolean(KEY_IS_LOGGED_IN, false))
    /** True only while there is a usable session. False during soft logout and after sign-out. */
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _needsReauth = MutableStateFlow(standardPrefs.getBoolean(KEY_NEEDS_REAUTH, false))
    /** True when the session expired/was revoked and the user must verify their number again. */
    val needsReauth: StateFlow<Boolean> = _needsReauth.asStateFlow()

    private val _userProfile = MutableStateFlow<UserProfile?>(loadProfile())
    val userProfile: StateFlow<UserProfile?> = _userProfile.asStateFlow()

    init {
        // Logged in on paper, but the tokens are gone for good (key lost / data corrupt): soft logout now
        // instead of failing on the first API call. A merely *unavailable* store is left alone.
        if (_isLoggedIn.value && tokenStore.read() is TokenRead.Absent) {
            markNeedsReauth("logged-in flag set but no tokens stored")
        }
    }

    // ---------------------------------------------------------------- legacy migration

    private fun getLegacyEncryptedPrefs(): SharedPreferences? = try {
        val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context,
            ENCRYPTED_PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Log.w(TAG, "Legacy EncryptedSharedPreferences unavailable: ${e.message}")
        null
    }

    /** Very old layout: login state/profile only in the encrypted file. */
    private fun migrateLegacyProfile() {
        if (standardPrefs.contains(KEY_IS_LOGGED_IN)) return
        val encrypted = getLegacyEncryptedPrefs() ?: return
        if (!encrypted.getBoolean(KEY_IS_LOGGED_IN, false)) return
        val editor = standardPrefs.edit().putBoolean(KEY_IS_LOGGED_IN, true)
        encrypted.getString(KEY_USER_PROFILE_JSON, null)?.takeIf { it.isNotBlank() }
            ?.let { editor.putString(KEY_USER_PROFILE_JSON, it) }
        editor.commit()
    }

    /**
     * Tokens used to be duplicated in plain prefs AND EncryptedSharedPreferences. Move them into the single
     * [SecureTokenStore]. If the copies disagree we keep the pair whose access token expires last (the
     * most recently issued), then delete both legacy copies.
     */
    private fun migrateLegacyTokens() {
        if (standardPrefs.getBoolean(KEY_TOKENS_MIGRATED, false)) return

        val candidates = mutableListOf<LegacyTokens>()
        candidates += LegacyTokens(
            access = standardPrefs.getString(LEGACY_ACCESS_TOKEN, null) ?: standardPrefs.getString(LEGACY_AUTH_TOKEN, null),
            refresh = standardPrefs.getString(LEGACY_REFRESH_TOKEN, null),
            source = "plain"
        )
        val encrypted = getLegacyEncryptedPrefs()
        if (encrypted != null) {
            try {
                candidates += LegacyTokens(
                    access = encrypted.getString(LEGACY_ACCESS_TOKEN, null) ?: encrypted.getString(LEGACY_AUTH_TOKEN, null),
                    refresh = encrypted.getString(LEGACY_REFRESH_TOKEN, null),
                    source = "encrypted"
                )
            } catch (e: Exception) {
                Log.w(TAG, "Could not read legacy encrypted tokens: ${e.message}")
            }
        }

        val best = pickNewestLegacy(candidates) { jwtExpiryEpochSeconds(it) }
        if (best != null) {
            Log.i(TAG, "Migrating legacy tokens from '${best.source}' store into SecureTokenStore")
            val persisted = tokenStore.write(best.access!!, best.refresh)
            if (!persisted) {
                // Keystore not ready yet. Tokens are usable from memory this run; retry the migration next start.
                return
            }
        }

        standardPrefs.edit()
            .remove(LEGACY_ACCESS_TOKEN).remove(LEGACY_AUTH_TOKEN).remove(LEGACY_REFRESH_TOKEN)
            .putBoolean(KEY_TOKENS_MIGRATED, true)
            .commit()
        try {
            encrypted?.edit()?.remove(LEGACY_ACCESS_TOKEN)?.remove(LEGACY_AUTH_TOKEN)?.remove(LEGACY_REFRESH_TOKEN)?.commit()
        } catch (_: Exception) {
        }
    }

    // ---------------------------------------------------------------- profile

    @Synchronized
    private fun loadProfile(): UserProfile? {
        val json = standardPrefs.getString(KEY_USER_PROFILE_JSON, null) ?: return null
        return try {
            gson.fromJson(json, UserProfile::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing user profile JSON: ${e.message}", e)
            null
        }
    }

    @Synchronized
    fun updateProfile(profile: UserProfile) {
        standardPrefs.edit().putString(KEY_USER_PROFILE_JSON, gson.toJson(profile)).apply()
        _userProfile.value = profile
    }

    // ---------------------------------------------------------------- session lifecycle

    @Synchronized
    fun saveAuthSession(profile: UserProfile, accessToken: String, refreshToken: String) {
        tokenStore.write(accessToken, refreshToken)
        standardPrefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .putBoolean(KEY_NEEDS_REAUTH, false)
            .putBoolean(KEY_TOKENS_MIGRATED, true)
            .putLong(KEY_LAST_REFRESH_AT, System.currentTimeMillis())
            .putString(KEY_USER_PROFILE_JSON, gson.toJson(profile))
            .commit()
        _userProfile.value = profile
        _needsReauth.value = false
        _isLoggedIn.value = true
    }

    /**
     * Soft logout: the server definitively rejected our refresh token (or it is gone). Drops the tokens and
     * routes the user to the login screen, but KEEPS the profile and all local app data so signing in again
     * restores everything. Idempotent.
     */
    @Synchronized
    fun markNeedsReauth(reason: String) {
        Log.w(TAG, "[SESSION] Needs re-authentication: $reason")
        tokenStore.clear()
        standardPrefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, false)
            .putBoolean(KEY_NEEDS_REAUTH, true)
            .commit()
        _needsReauth.value = true
        _isLoggedIn.value = false
        try {
            TriqxAssistantNotificationManager.cancelAllAssistantNotifications(context)
        } catch (e: Exception) {
            Log.w(TAG, "Error clearing assistant notifications: ${e.message}")
        }
    }

    /** Explicit sign-out: wipes tokens, profile and flags. (Callers wipe the other local data.) */
    @Synchronized
    fun clearSession() {
        tokenStore.clear()
        standardPrefs.edit().clear().commit()
        try {
            getLegacyEncryptedPrefs()?.edit()?.clear()?.commit()
        } catch (_: Exception) {
        }

        _userProfile.value = null
        _needsReauth.value = false
        _isLoggedIn.value = false

        try {
            TriqxAssistantNotificationManager.cancelAllAssistantNotifications(context)
        } catch (e: Exception) {
            Log.w(TAG, "Error clearing assistant notifications on session clear: ${e.message}")
        }
    }

    // ---------------------------------------------------------------- tokens

    fun getAccessToken(): String? = (tokenStore.read() as? TokenRead.Present)?.accessToken

    fun getRefreshToken(): String? = (tokenStore.read() as? TokenRead.Present)?.refreshToken

    fun getAuthToken(): String? = getAccessToken()

    /** When the refresh token was last rotated (login or refresh); 0 if unknown. Used by the keep-alive worker. */
    fun lastRefreshAtMillis(): Long = standardPrefs.getLong(KEY_LAST_REFRESH_AT, 0L)

    fun isTokenExpired(token: String? = getAccessToken(), bufferSeconds: Long = 60): Boolean =
        isJwtExpired(token, bufferSeconds)

    /**
     * Refresh runs in an app-scoped coroutine, NOT in the caller's. Callers (smart-reply jobs, ViewModels,
     * OkHttp threads) are cancelled all the time; the server rotates the refresh token the instant it answers,
     * so a cancelled caller used to lose the new pair and strand the device with a revoked token (the main
     * cause of random same-day logouts). Concurrent callers share ONE in-flight refresh.
     */
    private val refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val flightLock = Any()
    private var inFlight: Deferred<Result<String>>? = null

    suspend fun refreshAccessToken(
        otpAuthService: OtpAuthService,
        failedToken: String? = null
    ): Result<String> {
        val flight = synchronized(flightLock) {
            val existing = inFlight
            if (existing != null && existing.isActive) {
                existing
            } else {
                refreshScope.async { doRefresh(otpAuthService, failedToken) }.also { inFlight = it }
            }
        }
        return flight.await()
    }

    private suspend fun doRefresh(otpAuthService: OtpAuthService, failedToken: String?): Result<String> {
        try {
            var retriedWithNewerToken = false
            while (true) {
                when (val stored = tokenStore.read()) {
                    is TokenRead.Unavailable ->
                        return Result.failure(SecureStorageUnavailableException(stored.reason))

                    is TokenRead.Absent -> {
                        if (_isLoggedIn.value) markNeedsReauth("no tokens stored")
                        return Result.failure(IllegalStateException("No active session"))
                    }

                    is TokenRead.Present -> {
                        val access = stored.accessToken
                        // Someone already refreshed while we waited: reuse unless it's the token the server just rejected.
                        if (!isJwtExpired(access) && access != failedToken) {
                            return Result.success(access)
                        }
                        val refresh = stored.refreshToken
                        if (refresh.isNullOrBlank()) {
                            markNeedsReauth("no refresh token stored")
                            return Result.failure(IllegalStateException("No refresh token"))
                        }

                        Log.i(TAG, "[TOKEN REFRESH] Refreshing session...")
                        val result = otpAuthService.refreshToken(refresh)
                        if (result.isSuccess) {
                            val (newAccess, newRefresh) = result.getOrThrow()
                            tokenStore.write(newAccess, newRefresh)
                            standardPrefs.edit().putLong(KEY_LAST_REFRESH_AT, System.currentTimeMillis()).apply()
                            Log.i(TAG, "[TOKEN REFRESH] Session refreshed.")
                            return Result.success(newAccess)
                        }

                        val ex = result.exceptionOrNull()
                        if (ex is InvalidRefreshTokenException) {
                            // Second look before giving up: if the stored refresh token changed while our request
                            // was in flight, another path rotated it. Try the newer one once.
                            val again = tokenStore.read()
                            if (!retriedWithNewerToken && again is TokenRead.Present &&
                                !again.refreshToken.isNullOrBlank() && again.refreshToken != refresh
                            ) {
                                retriedWithNewerToken = true
                                continue
                            }
                            markNeedsReauth("refresh token rejected by server: ${ex.message}")
                        } else {
                            Log.w(TAG, "[TOKEN REFRESH] Transient failure, session kept: ${ex?.message}")
                        }
                        return Result.failure(ex ?: Exception("Unknown token refresh error"))
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "[TOKEN REFRESH] Unexpected failure, session kept: ${e.message}")
            return Result.failure(e)
        }
    }

    /** Returns a usable access token, refreshing first if needed. Null when there is no usable session. */
    suspend fun getValidAccessToken(
        otpAuthService: OtpAuthService,
        forceRefresh: Boolean = false,
        failedToken: String? = null
    ): String? {
        if (!forceRefresh) {
            val current = getAccessToken()
            if (!current.isNullOrBlank() && !isJwtExpired(current)) return current
        }
        return refreshAccessToken(otpAuthService, failedToken).getOrNull()
    }
}
