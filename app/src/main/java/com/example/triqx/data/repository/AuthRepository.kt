package com.example.triqx.data.repository

import com.example.triqx.data.local.UserProfile
import com.example.triqx.data.local.UserSessionManager
import com.example.triqx.data.remote.OtpAuthService
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val otpAuthService: OtpAuthService,
    private val userSessionManager: UserSessionManager
) {

    val isLoggedIn: StateFlow<Boolean> = userSessionManager.isLoggedIn
    val userProfile: StateFlow<UserProfile?> = userSessionManager.userProfile

    suspend fun sendOtp(phoneNumber: String): Result<String> {
        return otpAuthService.sendOtp(phoneNumber)
    }

    suspend fun verifyOtp(phoneNumber: String, otpCode: String): Result<Boolean> {
        val result = otpAuthService.verifyOtp(phoneNumber, otpCode)
        return result.map { token ->
            userSessionManager.saveSession(phoneNumber, token)
            true
        }
    }

    suspend fun resendOtp(phoneNumber: String): Result<String> {
        return otpAuthService.resendOtp(phoneNumber)
    }

    fun completeProfile(
        firstName: String,
        lastName: String,
        phoneNumbers: List<String>,
        emails: List<String>,
        aboutMe: String,
        professionalDetails: String
    ) {
        val current = userSessionManager.userProfile.value
        val verifiedPhone = current?.primaryPhone.orEmpty()
        val allPhones = if (verifiedPhone.isNotBlank() && !phoneNumbers.contains(verifiedPhone)) {
            listOf(verifiedPhone) + phoneNumbers
        } else {
            phoneNumbers.ifEmpty { if (verifiedPhone.isNotBlank()) listOf(verifiedPhone) else emptyList() }
        }

        val updatedProfile = UserProfile(
            firstName = firstName.trim(),
            lastName = lastName.trim(),
            phoneNumbers = allPhones,
            emails = emails.map { it.trim() }.filter { it.isNotBlank() },
            aboutMe = aboutMe.trim(),
            professionalDetails = professionalDetails.trim(),
            isFirstLogin = false
        )
        userSessionManager.updateProfile(updatedProfile)
    }

    fun updateProfile(profile: UserProfile) {
        userSessionManager.updateProfile(profile)
    }

    fun logout() {
        userSessionManager.clearSession()
    }
}
