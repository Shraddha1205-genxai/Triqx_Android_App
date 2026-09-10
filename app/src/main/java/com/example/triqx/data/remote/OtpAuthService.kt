package com.example.triqx.data.remote

import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Interface defining OTP authentication service contract.
 * Ready to be swapped with a real REST API or Firebase service.
 */
interface OtpAuthService {
    suspend fun sendOtp(phoneNumber: String): Result<String>
    suspend fun verifyOtp(phoneNumber: String, otpCode: String): Result<String>
    suspend fun resendOtp(phoneNumber: String): Result<String>
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

    override suspend fun verifyOtp(phoneNumber: String, otpCode: String): Result<String> {
        delay(600) // Simulate network latency
        val cleanCode = otpCode.trim()
        return if (cleanCode == TEST_OTP_5_DIGIT || cleanCode == TEST_OTP_6_DIGIT) {
            Result.success("mock_jwt_token_${System.currentTimeMillis()}")
        } else {
            Result.failure(IllegalArgumentException("Invalid verification code. Please enter $TEST_OTP_6_DIGIT"))
        }
    }

    override suspend fun resendOtp(phoneNumber: String): Result<String> {
        delay(600)
        return Result.success("resend_verification_id_${System.currentTimeMillis()}")
    }
}
