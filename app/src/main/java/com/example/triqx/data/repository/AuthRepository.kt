package com.example.triqx.data.repository

import android.app.NotificationManager
import android.content.Context
import android.util.Log
import com.example.triqx.data.local.AppDatabase
import com.example.triqx.data.local.EmailAccountStore
import com.example.triqx.data.local.ReplyActionStore
import com.example.triqx.data.local.UserProfile
import com.example.triqx.data.local.UserSessionManager
import com.example.triqx.data.remote.OtpAuthService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val otpAuthService: OtpAuthService,
    private val userSessionManager: UserSessionManager,
    private val appDatabase: AppDatabase,
    private val emailAccountStore: EmailAccountStore,
    private val replyActionStore: ReplyActionStore,
    private val openAiRepository: OpenAiRepository,
    @param:ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "AuthRepository"
    }

    val isLoggedIn: StateFlow<Boolean> = userSessionManager.isLoggedIn
    val userProfile: StateFlow<UserProfile?> = userSessionManager.userProfile

    suspend fun sendOtp(phoneNumber: String): Result<String> {
        return otpAuthService.sendOtp(phoneNumber)
    }

    suspend fun verifyOtp(phoneNumber: String, otpCode: String): Result<Boolean> {
        val result = otpAuthService.verifyOtp(phoneNumber, otpCode)
        return result.map { verifyData ->
            userSessionManager.saveAuthSession(
                profile = verifyData.userProfile,
                accessToken = verifyData.accessToken,
                refreshToken = verifyData.refreshToken
            )
            true
        }
    }

    suspend fun resendOtp(phoneNumber: String): Result<String> {
        return otpAuthService.resendOtp(phoneNumber)
    }

    suspend fun completeProfile(
        firstName: String,
        lastName: String,
        phoneNumbers: List<String>,
        emails: List<String>,
        aboutMe: String,
        professionalDetails: String
    ): Result<UserProfile> {
        val current = userSessionManager.userProfile.value
        val verifiedPhone = current?.primaryPhone.orEmpty()
        val allPhones = if (verifiedPhone.isNotBlank() && !phoneNumbers.contains(verifiedPhone)) {
            listOf(verifiedPhone) + phoneNumbers
        } else {
            phoneNumbers.ifEmpty { if (verifiedPhone.isNotBlank()) listOf(verifiedPhone) else emptyList() }
        }

        val updatedProfile = (current ?: UserProfile()).copy(
            firstName = firstName.trim(),
            lastName = lastName.trim(),
            phoneNumbers = allPhones,
            emails = emails.map { it.trim() }.filter { it.isNotBlank() },
            aboutMe = aboutMe.trim(),
            professionalDetails = professionalDetails.trim(),
            isFirstLogin = false,
            mobileNumber = verifiedPhone.ifBlank { current?.mobileNumber }
        )

        // Save locally first so UI immediately reflects updates
        userSessionManager.updateProfile(updatedProfile)

        // Sync with backend API
        val token = userSessionManager.getValidAccessToken(otpAuthService)
        val remoteResult = otpAuthService.updateProfile(token, updatedProfile)
        remoteResult.onSuccess { syncedProfile ->
            userSessionManager.updateProfile(syncedProfile)
        }
        return remoteResult
    }

    suspend fun updateProfile(profile: UserProfile): Result<UserProfile> {
        userSessionManager.updateProfile(profile)
        val token = userSessionManager.getValidAccessToken(otpAuthService)
        val remoteResult = otpAuthService.updateProfile(token, profile)
        remoteResult.onSuccess { syncedProfile ->
            userSessionManager.updateProfile(syncedProfile)
        }
        return remoteResult
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        val token = userSessionManager.getAccessToken()
        val refreshToken = userSessionManager.getRefreshToken()

        // 1. Notify backend API of logout (best-effort)
        try {
            otpAuthService.logout(token, refreshToken)
            Log.i(TAG, "Backend logout notification completed.")
        } catch (e: Exception) {
            Log.w(TAG, "Backend logout network call failed: ${e.message}")
        }

        // 2. Wipe all Room database tables (priority_contacts, important_apps, notifications, conversations)
        try {
            appDatabase.clearAllTables()
            Log.i(TAG, "All Room database tables cleared successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear Room database: ${e.message}", e)
        }

        // 3. Wipe User Session & Profile credentials
        userSessionManager.clearSession()
        Log.i(TAG, "User session cleared.")

        // 4. Wipe connected email accounts and OAuth credentials
        emailAccountStore.clearAll()

        // 5. Wipe notification quick reply actions
        replyActionStore.clear()

        // 6. Wipe AI smart reply caches, custom prompts, and reply settings
        openAiRepository.clearAllData()

        // 7. Wipe general settings preferences (e.g. debug menu flag, notification reply style)
        try {
            context.getSharedPreferences("triqx_settings_prefs", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply()
            Log.i(TAG, "Settings preferences cleared successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear settings prefs: ${e.message}", e)
        }

        // 8. Cancel all active status bar notifications posted by Triqx
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.cancelAll()
            Log.i(TAG, "Active status bar notifications cancelled.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel active notifications: ${e.message}", e)
        }
    }
}
