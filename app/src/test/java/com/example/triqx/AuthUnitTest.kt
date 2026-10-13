package com.example.triqx

import com.example.triqx.data.local.UserProfile
import com.example.triqx.data.remote.MockOtpAuthService
import com.example.triqx.ui.auth.LoginStep
import com.example.triqx.ui.auth.LoginUiState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AuthUnitTest {

    @Test
    fun userProfile_computedProperties_areCorrect() {
        val profile = UserProfile(
            firstName = "John",
            lastName = "Doe",
            phoneNumbers = listOf("+91 9876543210", "+91 9123456780"),
            emails = listOf("john.doe@example.com"),
            aboutMe = "Lead Developer",
            professionalDetails = "Android Architect",
            isFirstLogin = true
        )

        assertEquals("John Doe", profile.fullName)
        assertEquals("JD", profile.initials)
        assertEquals("+91 9876543210", profile.primaryPhone)
        assertEquals("john.doe@example.com", profile.primaryEmail)
        assertTrue(profile.isFirstLogin)

        val singleNameProfile = UserProfile(firstName = "Triqx")
        assertEquals("Triqx", singleNameProfile.fullName)
        assertEquals("T", singleNameProfile.initials)
    }

    @Test
    fun mockOtpAuthService_validatesExpectedCodes() = runBlocking {
        val service = MockOtpAuthService()

        // Test 6-digit OTP
        val result6 = service.verifyOtp("+91 9876543210", "123456")
        assertTrue(result6.isSuccess)
        assertNotNull(result6.getOrNull())

        // Test 5-digit OTP
        val result5 = service.verifyOtp("+91 9876543210", "12345")
        assertTrue(result5.isSuccess)

        // Test Invalid OTP
        val resultInvalid = service.verifyOtp("+91 9876543210", "000000")
        assertTrue(resultInvalid.isFailure)
    }

    @Test
    fun mockOtpAuthService_sendOtp_rejectsBlankPhone() = runBlocking {
        val service = MockOtpAuthService()
        val failureResult = service.sendOtp("")
        assertTrue(failureResult.isFailure)

        val successResult = service.sendOtp("+91 9876543210")
        assertTrue(successResult.isSuccess)
    }

    @Test
    fun loginUiState_phoneValidation_worksAsExpected() {
        val validState = LoginUiState(phoneNumber = "9876543210")
        assertTrue(validState.isPhoneValid)

        val shortState = LoginUiState(phoneNumber = "12345")
        assertFalse(shortState.isPhoneValid)

        val lettersState = LoginUiState(phoneNumber = "98765abcd0")
        assertFalse(lettersState.isPhoneValid)

        val readyState = LoginUiState(
            step = LoginStep.OTP_VERIFICATION,
            otpCode = "123456",
            countdownSeconds = 0,
            isLoading = false
        )
        assertTrue(readyState.canResendOtp)
        assertTrue(readyState.canSubmitOtp)
    }
}
