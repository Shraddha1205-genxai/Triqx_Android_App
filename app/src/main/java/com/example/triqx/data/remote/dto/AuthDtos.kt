package com.example.triqx.data.remote.dto

import com.example.triqx.data.local.UserProfile
import com.google.gson.annotations.SerializedName

// 1. Send OTP
data class SendOtpRequest(
    @SerializedName("mobileNumber") val mobileNumber: String
)

data class SendOtpResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: Any? = null
)

// 2. Verify OTP
data class VerifyOtpRequest(
    @SerializedName("mobileNumber") val mobileNumber: String,
    @SerializedName("otp") val otp: String
)

data class UserDto(
    @SerializedName("id") val id: Int? = null,
    @SerializedName("mobileNumber") val mobileNumber: String? = null,
    @SerializedName("firstName") val firstName: String? = null,
    @SerializedName("lastName") val lastName: String? = null,
    @SerializedName("emails") val emails: List<String>? = null,
    @SerializedName("aboutMe") val aboutMe: String? = null,
    @SerializedName("professionalDetails") val professionalDetails: String? = null,
    @SerializedName("isFirstLogin") val isFirstLogin: Boolean? = null
) {
    fun toUserProfile(): UserProfile {
        val phones = mutableListOf<String>()
        if (!mobileNumber.isNullOrBlank()) {
            phones.add(mobileNumber)
        }
        return UserProfile(
            id = id,
            firstName = firstName.orEmpty(),
            lastName = lastName.orEmpty(),
            phoneNumbers = phones,
            emails = emails ?: emptyList(),
            aboutMe = aboutMe.orEmpty(),
            professionalDetails = professionalDetails.orEmpty(),
            isFirstLogin = isFirstLogin ?: false,
            mobileNumber = mobileNumber
        )
    }
}

data class VerifyOtpData(
    @SerializedName("user") val user: UserDto? = null,
    @SerializedName("accessToken") val accessToken: String? = null,
    @SerializedName("refreshToken") val refreshToken: String? = null
)

data class VerifyOtpResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: VerifyOtpData? = null
)

// 3. Refresh Token
data class RefreshTokenRequest(
    @SerializedName("refreshToken") val refreshToken: String,
    @SerializedName("token") val token: String? = refreshToken
)

data class RefreshTokenData(
    @SerializedName("accessToken") val accessToken: String? = null,
    @SerializedName("token") val token: String? = null,
    @SerializedName("refreshToken") val refreshToken: String? = null
)

data class RefreshTokenResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("data") val data: RefreshTokenData? = null,
    @SerializedName("message") val message: String? = null
)

// 4. Logout
data class LogoutRequest(
    @SerializedName("refreshToken") val refreshToken: String
)

data class LogoutResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String? = null
)

// 5. Update Profile
data class UpdateProfileRequest(
    @SerializedName("email") val email: String? = null,
    @SerializedName("firstName") val firstName: String,
    @SerializedName("lastName") val lastName: String = "",
    @SerializedName("mobileNumber") val mobileNumber: String,
    @SerializedName("emails") val emails: List<String> = emptyList(),
    @SerializedName("aboutMe") val aboutMe: String = "",
    @SerializedName("professionalDetails") val professionalDetails: String = ""
)

data class UpdateProfileData(
    @SerializedName("user") val user: UserDto? = null
)

data class UpdateProfileResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: UpdateProfileData? = null
)
