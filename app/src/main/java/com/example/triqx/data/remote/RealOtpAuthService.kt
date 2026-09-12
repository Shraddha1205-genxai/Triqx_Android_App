package com.example.triqx.data.remote

import android.util.Log
import com.example.triqx.data.api.ApiRoutes
import com.example.triqx.data.local.UserProfile
import com.example.triqx.data.local.UserSessionManager
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
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class RealOtpAuthService @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
    private val userSessionManagerProvider: Provider<UserSessionManager>
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
        executeUpdateProfile(accessToken, profile, isRetry = false)
    }

    private suspend fun executeUpdateProfile(
        accessToken: String?,
        profile: UserProfile,
        isRetry: Boolean
    ): Result<UserProfile> {
        return try {
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
            val url = ApiRoutes.Auth.updateProfileUrl()

            Log.i(TAG, "==================== [UPDATE PROFILE REQUEST] ====================")
            Log.i(TAG, "URL: POST $url")
            Log.i(TAG, "Headers: Authorization: Bearer ${accessToken?.take(15)}...")
            Log.i(TAG, "Payload: $jsonBody")
            Log.i(TAG, "==================================================================")

            val reqBuilder = Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))

            if (!accessToken.isNullOrBlank()) {
                reqBuilder.addHeader("Authorization", "Bearer $accessToken")
            }

            val response = okHttpClient.newCall(reqBuilder.build()).execute()
            val responseBody = response.body?.string().orEmpty()

            Log.i(TAG, "==================== [UPDATE PROFILE RESPONSE] ===================")
            Log.i(TAG, "HTTP Status: ${response.code} (${response.message})")
            Log.i(TAG, "Response Body: ${if (responseBody.isNotBlank()) responseBody else "(empty)"}")
            Log.i(TAG, "==================================================================")

            if (!response.isSuccessful) {
                // If token expired (HTTP 401) and we haven't retried yet, refresh token and retry
                if ((response.code == 401 || responseBody.contains("token", ignoreCase = true)) && !isRetry) {
                    val sessionManager = userSessionManagerProvider.get()
                    Log.i(TAG, "[UPDATE PROFILE] Auth token rejected (HTTP ${response.code}). Attempting token refresh...")
                    val refreshResult = sessionManager.refreshAccessToken(this, failedToken = accessToken)
                    if (refreshResult.isSuccess) {
                        val refreshedToken = refreshResult.getOrThrow()
                        Log.i(TAG, "[UPDATE PROFILE] Token refreshed successfully. Retrying profile update...")
                        return executeUpdateProfile(refreshedToken, profile, isRetry = true)
                    } else {
                        Log.w(TAG, "[UPDATE PROFILE] Token refresh failed: ${refreshResult.exceptionOrNull()?.message}")
                    }
                }
            }

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
        val cleanRefresh = refreshToken.trim()
        if (cleanRefresh.isBlank()) {
            Log.w(TAG, "[AUTH REFRESH TOKEN] Provided refresh token is blank.")
            return@withContext Result.failure(IllegalArgumentException("Refresh token cannot be blank"))
        }

        try {
            val requestDto = RefreshTokenRequest(refreshToken = cleanRefresh)
            val jsonBody = gson.toJson(requestDto)
            val url = ApiRoutes.Auth.refreshTokenUrl()

            val logReq = buildString {
                appendLine("==================== [AUTH REFRESH TOKEN REQUEST] ====================")
                appendLine("URL: POST $url")
                appendLine("Headers: Content-Type: application/json")
                appendLine("Payload: $jsonBody")
                append("=======================================================================")
            }
            Log.i(TAG, logReq)
            Log.i("BackendAiService", logReq)

            val request = Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string().orEmpty()

            val logRes = buildString {
                appendLine("==================== [AUTH REFRESH TOKEN RESPONSE] ===================")
                appendLine("HTTP Status: ${response.code} (${response.message})")
                appendLine("Response Body: ${if (responseBody.isNotBlank()) responseBody else "(empty)"}")
                append("=======================================================================")
            }
            Log.i(TAG, logRes)
            Log.i("BackendAiService", logRes)

            val parsedResponse = try {
                gson.fromJson(responseBody, RefreshTokenResponse::class.java)
            } catch (e: Exception) {
                null
            }

            if (response.isSuccessful && parsedResponse?.success == true && parsedResponse.data != null) {
                val newAccess = parsedResponse.data.accessToken ?: parsedResponse.data.token ?: ""
                val newRefresh = parsedResponse.data.refreshToken?.takeIf { it.isNotBlank() } ?: cleanRefresh
                if (newAccess.isBlank()) {
                    Result.failure(Exception("Backend did not return a valid new access token"))
                } else {
                    Result.success(Pair(newAccess, newRefresh))
                }
            } else {
                val errorMsg = parsedResponse?.message ?: "Token refresh failed (${response.code}: ${response.message})"
                if (response.code == 401 || errorMsg.contains("Invalid refresh token", ignoreCase = true) || errorMsg.contains("revoked", ignoreCase = true)) {
                    Result.failure(InvalidRefreshTokenException(errorMsg))
                } else {
                    Result.failure(Exception(errorMsg))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "[AUTH REFRESH TOKEN EXCEPTION]: ${e.message}", e)
            Result.failure(e)
        }
    }
}
