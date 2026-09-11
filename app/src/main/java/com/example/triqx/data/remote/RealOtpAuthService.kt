package com.example.triqx.data.remote

import android.util.Log
import com.example.triqx.data.api.ApiRoutes
import com.example.triqx.data.local.UserProfile
import com.example.triqx.data.remote.dto.*
import com.example.triqx.utils.Constants
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RealOtpAuthService @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson
) : OtpAuthService {

    companion object {
        private const val TAG = "RealOtpAuthService"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private fun normalizePhoneNumber(phone: String): String {
        return phone.replace(" ", "").replace("-", "").replace("(", "").replace(")", "").trim()
    }

    override suspend fun sendOtp(phoneNumber: String): Result<String> = withContext(Dispatchers.IO) {
        val cleanNumber = normalizePhoneNumber(phoneNumber)
        if (cleanNumber.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Phone number cannot be empty"))
        }

        try {
            val requestDto = SendOtpRequest(mobileNumber = cleanNumber)
            val jsonBody = gson.toJson(requestDto)
            val request = Request.Builder()
                .url(ApiRoutes.Auth.sendOtpUrl())
                .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            Log.d(TAG, "Sending OTP to $cleanNumber at ${request.url}")
            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string().orEmpty()

            val parsedResponse = try {
                gson.fromJson(responseBody, SendOtpResponse::class.java)
            } catch (e: Exception) {
                null
            }

            if (response.isSuccessful && parsedResponse?.success == true) {
                Result.success(parsedResponse.message ?: "OTP sent successfully")
            } else {
                val errorMsg = parsedResponse?.message
                    ?: "Failed to send OTP (${response.code}: ${response.message})"
                Log.w(TAG, "sendOtp failed: $errorMsg (Body: $responseBody)")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "sendOtp network error: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun verifyOtp(phoneNumber: String, otpCode: String): Result<VerifyOtpResult> = withContext(Dispatchers.IO) {
        val cleanNumber = normalizePhoneNumber(phoneNumber)
        val cleanOtp = otpCode.trim()

        if (cleanNumber.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Phone number cannot be empty"))
        }
        if (cleanOtp.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("OTP code cannot be empty"))
        }

        try {
            val requestDto = VerifyOtpRequest(mobileNumber = cleanNumber, otp = cleanOtp)
            val jsonBody = gson.toJson(requestDto)
            val request = Request.Builder()
                .url(ApiRoutes.Auth.verifyOtpUrl())
                .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            Log.d(TAG, "Verifying OTP for $cleanNumber")
            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string().orEmpty()

            val parsedResponse = try {
                gson.fromJson(responseBody, VerifyOtpResponse::class.java)
            } catch (e: Exception) {
                null
            }

            if (response.isSuccessful && parsedResponse?.success == true && parsedResponse.data != null) {
                val data = parsedResponse.data
                val userProfile = data.user?.toUserProfile() ?: UserProfile(
                    phoneNumbers = listOf(cleanNumber),
                    mobileNumber = cleanNumber,
                    isFirstLogin = true
                )
                val accessToken = data.accessToken.orEmpty()
                val refreshToken = data.refreshToken.orEmpty()

                Result.success(
                    VerifyOtpResult(
                        userProfile = userProfile,
                        accessToken = accessToken,
                        refreshToken = refreshToken
                    )
                )
            } else {
                val errorMsg = parsedResponse?.message
                    ?: "OTP verification failed (${response.code}: ${response.message})"
                Log.w(TAG, "verifyOtp failed: $errorMsg (Body: $responseBody)")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "verifyOtp network error: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun resendOtp(phoneNumber: String): Result<String> {
        return sendOtp(phoneNumber)
    }

    override suspend fun updateProfile(accessToken: String?, profile: UserProfile): Result<UserProfile> = withContext(Dispatchers.IO) {
        try {
            val cleanPhone = normalizePhoneNumber(profile.primaryPhone)
            val requestDto = UpdateProfileRequest(
                email = profile.primaryEmail.orEmpty(),
                firstName = profile.firstName.trim(),
                lastName = profile.lastName.trim(),
                mobileNumber = cleanPhone,
                emails = profile.emails,
                aboutMe = profile.aboutMe.trim(),
                professionalDetails = profile.professionalDetails.trim()
            )
            val jsonBody = gson.toJson(requestDto)

            val reqBuilder = Request.Builder()
                .url(ApiRoutes.Auth.updateProfileUrl())
                .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))

            if (!accessToken.isNullOrBlank()) {
                reqBuilder.addHeader("Authorization", "Bearer $accessToken")
            }

            val response = okHttpClient.newCall(reqBuilder.build()).execute()
            val responseBody = response.body?.string().orEmpty()

            val parsedResponse = try {
                gson.fromJson(responseBody, UpdateProfileResponse::class.java)
            } catch (e: Exception) {
                null
            }

            if (response.isSuccessful && parsedResponse?.success == true) {
                val updatedProfile = parsedResponse.data?.user?.toUserProfile()
                    ?: profile.copy(isFirstLogin = false)
                Result.success(updatedProfile)
            } else {
                val errorMsg = parsedResponse?.message
                    ?: "Failed to update profile (${response.code}: ${response.message})"
                Log.w(TAG, "updateProfile failed: $errorMsg (Body: $responseBody)")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "updateProfile network error: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun logout(accessToken: String?, refreshToken: String?): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val requestDto = LogoutRequest(refreshToken = refreshToken.orEmpty())
            val jsonBody = gson.toJson(requestDto)

            val reqBuilder = Request.Builder()
                .url(ApiRoutes.Auth.logoutUrl())
                .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))

            if (!accessToken.isNullOrBlank()) {
                reqBuilder.addHeader("Authorization", "Bearer $accessToken")
            }

            val response = okHttpClient.newCall(reqBuilder.build()).execute()
            val responseBody = response.body?.string().orEmpty()
            Log.d(TAG, "Logout response: ${response.code} $responseBody")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "logout network error: ${e.message}", e)
            // Even if network fails, logout should proceed locally
            Result.success(Unit)
        }
    }

    override suspend fun refreshToken(refreshToken: String): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        try {
            val requestDto = RefreshTokenRequest(refreshToken = refreshToken)
            val jsonBody = gson.toJson(requestDto)

            val request = Request.Builder()
                .url(ApiRoutes.Auth.refreshTokenUrl())
                .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string().orEmpty()

            val parsedResponse = try {
                gson.fromJson(responseBody, RefreshTokenResponse::class.java)
            } catch (e: Exception) {
                null
            }

            if (response.isSuccessful && parsedResponse?.success == true && parsedResponse.data != null) {
                val newAccess = parsedResponse.data.accessToken.orEmpty()
                val newRefresh = parsedResponse.data.refreshToken.orEmpty()
                Result.success(Pair(newAccess, newRefresh))
            } else {
                val errorMsg = parsedResponse?.message ?: "Token refresh failed (${response.code})"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
