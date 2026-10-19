package com.example.triqx.data.remote

import android.util.Log
import com.example.triqx.data.local.UserSessionManager
import com.example.triqx.data.remote.auth.requireAuth
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
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class BackendAiService @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
    private val sessionManager: UserSessionManager
) {

    companion object {
        private const val TAG = "BackendAiService"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    /**
     * Sends conversation context to backend AI endpoint and returns generated reply suggestions.
     */
    suspend fun generateReplies(
        baseUrl: String,
        request: SmartReplyRequest
    ): List<String> = withContext(Dispatchers.IO) {
        executeGenerateReplies(baseUrl, request)
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
        request: SmartReplyRequest
    ): List<String> {
        val endpointUrl = com.example.triqx.data.api.ApiRoutes.Ai.generateRepliesUrl(baseUrl)
        val requestJson = gson.toJson(request)

        // Tokens are attached (proactively refreshed) and, on 401, refreshed + retried once by the
        // OkHttp TokenAuthInterceptor/TokenAuthenticator. Here we only fail fast when there is no session.
        if (!sessionManager.isLoggedIn.value) {
            Log.w(TAG, "[BACKEND AI] No active session. Cannot generate smart replies without authentication.")
            throw IOException(
                if (sessionManager.needsReauth.value) "Session expired. Please log in again to refresh your account."
                else "Authentication required to generate replies."
            )
        }

        // Log a summary only: never the bearer token, message text or base64 images.
        Log.i(
            TAG,
            "[AI GENERATE REPLIES REQUEST] POST $endpointUrl app=${request.appPackage} " +
                "msgs=${request.messages.size} media=${request.messages.count { it.mediaBase64 != null }} " +
                "replyCount=${request.replyCount}"
        )

        val requestBuilder = Request.Builder()
            .url(endpointUrl)
            .addHeader("Content-Type", "application/json")
            .requireAuth()
            .post(requestJson.toRequestBody(JSON_MEDIA_TYPE))

        try {
            okHttpClient.executeCancellable(requestBuilder.build()).use { response ->
                val responseBody = response.body?.string().orEmpty()
                Log.i(TAG, "[AI GENERATE REPLIES RESPONSE] HTTP ${response.code} (${response.message})")

                if (!response.isSuccessful) {
                    // 401 refresh + retry is handled centrally by TokenAuthenticator; a 401 here means it failed.
                    val serverError = try {
                        gson.fromJson(responseBody, SmartReplyResponse::class.java)?.error
                    } catch (_: Exception) {
                        null
                    }
                    val friendlyMessage = when (response.code) {
                        401 -> "Session expired. Please log in again to refresh your account."
                        429 -> "Too many AI requests. Please try again shortly."
                        else -> "Backend returned HTTP ${response.code}" + (serverError?.let { ": $it" } ?: "")
                    }
                    throw IOException(friendlyMessage)
                }

                if (responseBody.isBlank()) {
                    throw IOException("Backend returned empty response body")
                }

                val parsed = gson.fromJson(responseBody, SmartReplyResponse::class.java)
                // Honour the backend's success flag: success=false means the replies are NOT real
                // model output and must never be shown as suggestions or sent.
                if (!parsed.success) {
                    throw IOException("AI reply generation failed: ${parsed.error ?: "unknown error"}")
                }
                val validReplies = parsed.replies
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .take(request.replyCount)

                if (validReplies.isEmpty()) {
                    throw IOException("Backend returned empty replies list")
                }

                Log.i(TAG, "<=== [PARSED ${validReplies.size} REPLIES FROM BACKEND]")
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
