package com.example.triqx.data.remote

import com.example.triqx.data.local.UserProfile
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result data holder for OTP verification.
 */
data class VerifyOtpResult(
    val userProfile: UserProfile,
    val accessToken: String,
    val refreshToken: String
)

/**
 * Interface defining OTP authentication and user profile service contract.
 */
interface OtpAuthService {
    suspend fun sendOtp(phoneNumber: String): Result<String>
    suspend fun verifyOtp(phoneNumber: String, otpCode: String): Result<VerifyOtpResult>
    suspend fun resendOtp(phoneNumber: String): Result<String>
    suspend fun updateProfile(accessToken: String?, profile: UserProfile): Result<UserProfile>
    suspend fun logout(accessToken: String?, refreshToken: String?): Result<Unit>
    suspend fun refreshToken(refreshToken: String): Result<Pair<String, String>>
}

/**
 * Mock implementation of [OtpAuthService] for development and testing.
 * Accepts "12345" or "123456" as valid test OTP codes.
 */
@Singleton
class MockOtpAuthService @Inject constructor() : OtpAuthService {

    companion object {
        const val TEST_OTP_6_DIGIT = "123456"
        const val TEST_OTP_5_DIGIT = "12345"
    }

    override suspend fun sendOtp(phoneNumber: String): Result<String> {
        delay(600) // Simulate network latency
        return if (phoneNumber.isNotBlank()) {
            Result.success("mock_verification_id_for_$phoneNumber")
        } else {
            Result.failure(IllegalArgumentException("Phone number cannot be empty"))
        }
    }

    override suspend fun verifyOtp(phoneNumber: String, otpCode: String): Result<VerifyOtpResult> {
        delay(600) // Simulate network latency
        val cleanCode = otpCode.trim()
        return if (cleanCode == TEST_OTP_5_DIGIT || cleanCode == TEST_OTP_6_DIGIT) {
            val user = UserProfile(
                phoneNumbers = listOf(phoneNumber),
                mobileNumber = phoneNumber,
                isFirstLogin = true
            )
            Result.success(
                VerifyOtpResult(
                    userProfile = user,
                    accessToken = "mock_jwt_token_${System.currentTimeMillis()}",
                    refreshToken = "mock_refresh_token_${System.currentTimeMillis()}"
                )
            )
        } else {
            Result.failure(IllegalArgumentException("Invalid verification code. Please enter $TEST_OTP_6_DIGIT"))
        }
    }

    override suspend fun resendOtp(phoneNumber: String): Result<String> {
        delay(600)
        return Result.success("resend_verification_id_${System.currentTimeMillis()}")
    }

    override suspend fun updateProfile(accessToken: String?, profile: UserProfile): Result<UserProfile> {
        delay(300)
        return Result.success(profile.copy(isFirstLogin = false))
    }

    override suspend fun logout(accessToken: String?, refreshToken: String?): Result<Unit> {
        delay(200)
        return Result.success(Unit)
    }

    override suspend fun refreshToken(refreshToken: String): Result<Pair<String, String>> {
        delay(200)
        return Result.success(Pair("mock_new_access_${System.currentTimeMillis()}", "mock_new_refresh_${System.currentTimeMillis()}"))
    }
}
