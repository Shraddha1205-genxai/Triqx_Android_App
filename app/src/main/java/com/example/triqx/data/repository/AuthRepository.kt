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
    /** True when the session lapsed and the user must verify again (their local data was kept). */
    val needsReauth: StateFlow<Boolean> = userSessionManager.needsReauth

    suspend fun sendOtp(phoneNumber: String): Result<String> {
        return otpAuthService.sendOtp(phoneNumber)
    }

    suspend fun verifyOtp(phoneNumber: String, otpCode: String): Result<Boolean> {
        val result = otpAuthService.verifyOtp(phoneNumber, otpCode)
        // Soft-logout recovery: the profile (and all local data) were kept when the session lapsed. If the user
        // now verifies the SAME number, everything is still theirs. A different number means a different
        // person is using this device, so their predecessor's data must not leak into the new account.
        val keptProfile = userSessionManager.userProfile.value
        val verifiedProfile = result.getOrNull()?.userProfile
        if (keptProfile != null && verifiedProfile != null &&
            !samePhone(keptProfile.primaryPhone, verifiedProfile.primaryPhone)
        ) {
            Log.i(TAG, "Different number verified after a lapsed session; wiping previous user's local data.")
            withContext(Dispatchers.IO) { wipeLocalData() }
        }
        return result.map { verifyData ->
            userSessionManager.saveAuthSession(
                profile = verifyData.userProfile,
                accessToken = verifyData.accessToken,
                refreshToken = verifyData.refreshToken
            )
            true
        }
    }

    private fun samePhone(a: String, b: String): Boolean {
        val da = a.filter { it.isDigit() }
        val db = b.filter { it.isDigit() }
        if (da.isEmpty() || db.isEmpty()) return false
        // Compare the national part so "+91 98765…" and "98765…" count as the same number.
        return da.takeLast(10) == db.takeLast(10)
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

        wipeLocalData()

        // 3. Wipe User Session & Profile credentials
        userSessionManager.clearSession()
        Log.i(TAG, "User session cleared.")
    }

    /** Everything stored on the device for the current user except the session itself. */
    private suspend fun wipeLocalData() {
        // 2. Wipe all Room database tables (priority_contacts, important_apps, notifications, conversations)
        try {
            appDatabase.clearAllTables()
            Log.i(TAG, "All Room database tables cleared successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear Room database: ${e.message}", e)
        }

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
