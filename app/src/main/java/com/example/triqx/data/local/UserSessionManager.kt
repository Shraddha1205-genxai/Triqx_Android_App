package com.example.triqx.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Secure persistent store for user session and profile data.
 * Uses EncryptedSharedPreferences (AES256_GCM via Android Keystore).
 */
@Singleton
class UserSessionManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val gson: Gson
) {

    companion object {
        private const val TAG = "UserSessionManager"
        private const val PREFS_FILE = "triqx_secure_session_prefs"

        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_USER_PROFILE_JSON = "user_profile_json"
    }

    private val prefs: SharedPreferences = createSecurePreferences(context)

    private val _isLoggedIn = MutableStateFlow(prefs.getBoolean(KEY_IS_LOGGED_IN, false))
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _userProfile = MutableStateFlow<UserProfile?>(loadProfile())
    val userProfile: StateFlow<UserProfile?> = _userProfile.asStateFlow()

    private fun createSecurePreferences(ctx: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(ctx)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                ctx,
                PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.w(TAG, "EncryptedSharedPreferences init failed, falling back to private prefs: ${e.message}")
            ctx.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
        }
    }

    @Synchronized
    private fun loadProfile(): UserProfile? {
        val json = prefs.getString(KEY_USER_PROFILE_JSON, null) ?: return null
        return try {
            gson.fromJson(json, UserProfile::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing user profile JSON: ${e.message}", e)
            null
        }
    }

    @Synchronized
    fun saveAuthSession(profile: UserProfile, accessToken: String, refreshToken: String) {
        val json = gson.toJson(profile)
        prefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .putString(KEY_AUTH_TOKEN, accessToken)
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putString(KEY_USER_PROFILE_JSON, json)
            .apply()

        _userProfile.value = profile
        _isLoggedIn.value = true
    }

    @Synchronized
    fun saveSession(verifiedPhoneNumber: String, token: String) {
        val current = _userProfile.value
        val initialProfile = if (current != null) {
            val phones = if (current.phoneNumbers.contains(verifiedPhoneNumber)) {
                current.phoneNumbers
            } else {
                listOf(verifiedPhoneNumber) + current.phoneNumbers
            }
            current.copy(phoneNumbers = phones, mobileNumber = verifiedPhoneNumber)
        } else {
            UserProfile(
                phoneNumbers = listOf(verifiedPhoneNumber),
                mobileNumber = verifiedPhoneNumber,
                isFirstLogin = true
            )
        }

        val json = gson.toJson(initialProfile)
        prefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .putString(KEY_AUTH_TOKEN, token)
            .putString(KEY_ACCESS_TOKEN, token)
            .putString(KEY_USER_PROFILE_JSON, json)
            .apply()

        _userProfile.value = initialProfile
        _isLoggedIn.value = true
    }

    @Synchronized
    fun updateTokens(accessToken: String, refreshToken: String) {
        prefs.edit()
            .putString(KEY_AUTH_TOKEN, accessToken)
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .apply()
    }

    @Synchronized
    fun updateProfile(profile: UserProfile) {
        val json = gson.toJson(profile)
        prefs.edit()
            .putString(KEY_USER_PROFILE_JSON, json)
            .apply()

        _userProfile.value = profile
    }

    fun getAccessToken(): String? {
        return prefs.getString(KEY_ACCESS_TOKEN, null) ?: prefs.getString(KEY_AUTH_TOKEN, null)
    }

    fun getRefreshToken(): String? {
        return prefs.getString(KEY_REFRESH_TOKEN, null)
    }

    fun getAuthToken(): String? {
        return getAccessToken()
    }

    @Synchronized
    fun clearSession() {
        prefs.edit()
            .clear()
            .apply()

        _userProfile.value = null
        _isLoggedIn.value = false
    }
}
