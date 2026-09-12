package com.example.triqx.data.remote

import android.util.Log
import com.example.triqx.data.local.UserSessionManager
import com.example.triqx.data.remote.dto.ReplyMessageDto
import com.example.triqx.data.remote.dto.SmartReplyRequest
import com.example.triqx.data.remote.dto.SmartReplyResponse
import com.example.triqx.utils.Constants
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class BackendAiService @Inject constructor(
    private val gson: Gson,
    private val sessionManager: UserSessionManager,
    private val otpAuthServiceProvider: Provider<OtpAuthService>
) {

    companion object {
        private const val TAG = "BackendAiService"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Sends conversation context to backend AI endpoint and returns generated reply suggestions.
     */
    suspend fun generateReplies(
        baseUrl: String,
        request: SmartReplyRequest
    ): List<String> = withContext(Dispatchers.IO) {
        executeGenerateReplies(baseUrl, request, isRetry = false)
    }

    private val prettyGson by lazy {
        GsonBuilder().setPrettyPrinting().create()
    }

    private fun logMultiline(tag: String, message: String) {
        message.lines().forEach { line ->
            Log.i(tag, line)
        }
    }

    private suspend fun executeGenerateReplies(
        baseUrl: String,
        request: SmartReplyRequest,
        isRetry: Boolean
    ): List<String> {
        val endpointUrl = com.example.triqx.data.api.ApiRoutes.Ai.generateRepliesUrl(baseUrl)
        val requestJson = gson.toJson(request)
        val prettyJson = try {
            prettyGson.toJson(request)
        } catch (_: Exception) {
            requestJson
        }

        val isExpired = sessionManager.isTokenExpired()
        if (isExpired && !sessionManager.getAccessToken().isNullOrBlank()) {
            val hasRefresh = !sessionManager.getRefreshToken().isNullOrBlank()
            Log.i(TAG, "[BACKEND AI] Access token is expired. Attempting proactive refresh (has refreshToken: $hasRefresh)...")
        }
        val authToken = sessionManager.getValidAccessToken(otpAuthServiceProvider.get())
        if (authToken.isNullOrBlank()) {
            if (sessionManager.isLoggedIn.value) {
                Log.w(TAG, "[BACKEND AI] User is logged in but has no valid access token. Session expired or revoked.")
                throw IOException("Session expired. Please log in again to refresh your account.")
            } else {
                Log.w(TAG, "[BACKEND AI] User is not logged in. Cannot generate smart replies without authentication.")
                throw IOException("Authentication required to generate replies.")
            }
        }
        val authHeader = "Bearer $authToken"

        val logRequest = buildString {
            appendLine("==================== [AI GENERATE REPLIES REQUEST] ====================")
            appendLine("URL: POST $endpointUrl")
            appendLine("Headers:")
            appendLine("  Content-Type: application/json")
            appendLine("  Authorization: $authHeader")
            appendLine("Payload:")
            appendLine(prettyJson)
            append("=======================================================================")
        }
        logMultiline(TAG, logRequest)

        val requestBuilder = Request.Builder()
            .url(endpointUrl)
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", authHeader)
            .post(requestJson.toRequestBody(JSON_MEDIA_TYPE))

        try {
            client.executeCancellable(requestBuilder.build()).use { response ->
                val responseBody = response.body?.string().orEmpty()
                val logResponse = buildString {
                    appendLine("==================== [AI GENERATE REPLIES RESPONSE] ===================")
                    appendLine("HTTP Status: ${response.code} (${response.message})")
                    appendLine("Response Body:")
                    appendLine(if (responseBody.isNotBlank()) responseBody else "(empty body)")
                    append("=======================================================================")
                }
                logMultiline(TAG, logResponse)

                if (!response.isSuccessful) {
                    // If token expired (HTTP 401) and we haven't retried yet, attempt automatic silent token refresh
                    if ((response.code == 401 || responseBody.contains("expired", ignoreCase = true) || responseBody.contains("token", ignoreCase = true)) && !isRetry) {
                        val hasRefresh = !sessionManager.getRefreshToken().isNullOrBlank()
                        Log.i(TAG, "[BACKEND AI] Auth token rejected (HTTP ${response.code}). Attempting token refresh (refreshToken present: $hasRefresh)...")
                        val refreshResult = sessionManager.refreshAccessToken(
                            otpAuthService = otpAuthServiceProvider.get(),
                            failedToken = authToken
                        )
                        if (refreshResult.isSuccess) {
                            Log.i(TAG, "[BACKEND AI] Token refreshed successfully. Retrying request with fresh token...")
                            return executeGenerateReplies(baseUrl, request, isRetry = true)
                        } else {
                            val failureReason = refreshResult.exceptionOrNull()?.message ?: "Unknown error"
                            Log.w(TAG, "[BACKEND AI] Token refresh failed: $failureReason")
                        }
                    }

                    val friendlyMessage = when {
                        response.code == 401 || responseBody.contains("token", ignoreCase = true) ->
                            "Session expired. Please log in again to refresh your account."
                        responseBody.isNotBlank() ->
                            "Backend returned HTTP ${response.code}: $responseBody"
                        else ->
                            "Backend returned HTTP ${response.code}"
                    }
                    throw IOException(friendlyMessage)
                }

                if (responseBody.isBlank()) {
                    throw IOException("Backend returned empty response body")
                }

                val parsed = gson.fromJson(responseBody, SmartReplyResponse::class.java)
                val validReplies = parsed.replies
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .take(request.replyCount)

                if (validReplies.isEmpty()) {
                    throw IOException("Backend returned empty replies list")
                }

                Log.i(TAG, "<=== [PARSED ${validReplies.size} REPLIES FROM BACKEND]: $validReplies")
                return validReplies
            }
        } catch (e: CancellationException) {
            Log.d(TAG, "[BACKEND AI] Request cancelled.")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "==================== [AI GENERATE REPLIES EXCEPTION] ===================")
            Log.e(TAG, "URL: $endpointUrl")
            Log.e(TAG, "Error: ${e.message}", e)
            Log.e(TAG, "=======================================================================")
            throw e
        }
    }

    /**
     * Test connection to backend AI service with a sample message.
     */
    suspend fun testConnection(baseUrl: String, replyStyle: String, additionalPrompt: String?): Result<String> = withContext(Dispatchers.IO) {
        return@withContext try {
            val testRequest = SmartReplyRequest(
                appPackage = "com.test.triqx",
                conversationTitle = "Test Contact",
                replyStyle = replyStyle,
                additionalPrompt = additionalPrompt,
                messages = listOf(
                    ReplyMessageDto(
                        sender = "Test Contact",
                        text = "Hey, are you free for a quick call today?",
                        timestamp = System.currentTimeMillis(),
                        isFromUser = false
                    )
                )
            )
            val replies = generateReplies(baseUrl, testRequest)
            Result.success("Connected successfully! Generated: ${replies.joinToString(" | ")}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Executes an OkHttp request cancellably with coroutines.
     */
    private suspend fun OkHttpClient.executeCancellable(request: Request): Response =
        suspendCancellableCoroutine { continuation ->
            val call = newCall(request)
            continuation.invokeOnCancellation {
                call.cancel()
            }
            call.enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response)
                }

                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isCancelled) return
                    continuation.resumeWithException(e)
                }
            })
        }
}
