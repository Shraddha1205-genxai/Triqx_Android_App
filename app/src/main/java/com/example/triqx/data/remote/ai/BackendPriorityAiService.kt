package com.example.triqx.data.remote.ai

import android.content.Context
import android.util.Log
import com.example.triqx.data.local.ChatMessage
import com.example.triqx.data.local.ObligationEntity
import com.example.triqx.data.local.UserSessionManager
import com.example.triqx.data.remote.auth.requireAuth
import com.example.triqx.data.remote.dto.ExtractObligationsRequest
import com.example.triqx.data.remote.dto.ExtractObligationsResponse
import com.example.triqx.data.remote.dto.ReplyMessageDto
import com.example.triqx.data.repository.OpenAiRepository
import com.example.triqx.utils.Constants
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Cloud-based obligation extraction service calling backend /ai/extract-obligations.
 * Prepared for future backend deployment.
 */
@Singleton
class BackendPriorityAiService @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
    private val sessionManager: UserSessionManager,
    private val openAiRepository: OpenAiRepository,
    @param:ApplicationContext private val context: Context
) : PriorityAiService {

    companion object {
        private const val TAG = "BackendPriorityAi"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val prettyGson by lazy {
        com.google.gson.GsonBuilder().setPrettyPrinting().create()
    }

    private fun logMultiline(tag: String, message: String) {
        message.lines().forEach { line ->
            Log.i(tag, line)
        }
    }

    override suspend fun extractObligations(
        conversationKey: String,
        conversationTitle: String,
        packageName: String,
        messages: List<ChatMessage>
    ): List<ObligationEntity> = withContext(Dispatchers.IO) {
        if (messages.isEmpty()) return@withContext emptyList()

        val baseUrl = openAiRepository.backendBaseUrl.value
        val endpointUrl = "$baseUrl/ai/extract-obligations"

        val dtoList = messages.take(10).reversed().map { msg ->
            ReplyMessageDto(
                sender = if (msg.isFromYou) "You" else msg.senderName.ifBlank { conversationTitle },
                text = msg.bodyText,
                timestamp = msg.timestamp,
                isFromUser = msg.isFromYou
            )
        }

        val requestPayload = ExtractObligationsRequest(
            appPackage = packageName,
            conversationTitle = conversationTitle,
            messages = dtoList
        )

        if (!sessionManager.isLoggedIn.value) {
            // /ai/extract-obligations requires authentication (it spends OpenAI tokens). The bearer token is
            // attached and refreshed centrally by TokenAuthInterceptor/TokenAuthenticator.
            Log.d(TAG, "No active session; skipping obligation extraction.")
            return@withContext emptyList()
        }

        val requestJson = gson.toJson(requestPayload)
        val prettyJson = try {
            prettyGson.toJson(requestPayload)
        } catch (_: Exception) {
            requestJson
        }

        val logRequest = buildString {
            appendLine("==================== [AI EXTRACT OBLIGATIONS REQUEST] ====================")
            appendLine("URL: POST $endpointUrl")
            appendLine("Headers:")
            appendLine("  Content-Type: application/json")
            appendLine("  Authorization: Bearer (redacted)")
            appendLine("Payload:")
            appendLine("msgs=${requestPayload.messages.size} (content not logged)")
            append("=======================================================================")
        }
        logMultiline(TAG, logRequest)

        val requestBuilder = Request.Builder()
            .url(endpointUrl)
            .addHeader("Content-Type", "application/json")
            .requireAuth()
            .post(requestJson.toRequestBody(JSON_MEDIA_TYPE))
        val request = requestBuilder.build()

        try {
            okHttpClient.executeCancellable(request).use { response ->
                val body = response.body?.string().orEmpty()

                val logResponse = buildString {
                    appendLine("==================== [AI EXTRACT OBLIGATIONS RESPONSE] ===================")
                    appendLine("HTTP Status: ${response.code} (${response.message})")
                    appendLine("Response Body:")
                    appendLine(if (response.isSuccessful) "(${body.length} bytes)" else body.take(300))
                    append("=======================================================================")
                }
                logMultiline(TAG, logResponse)

                if (!response.isSuccessful) {
                    Log.w(TAG, "Backend priority endpoint returned ${response.code}: $body")
                    return@withContext emptyList()
                }

                val parsed = gson.fromJson(body, ExtractObligationsResponse::class.java)
                val now = System.currentTimeMillis()
                val items = parsed.obligations.map { dto ->
                    ObligationEntity(
                        title = dto.title,
                        category = dto.categoryEnum,
                        identity = dto.identity.ifBlank { conversationTitle },
                        createdAt = now,
                        dueTimestamp = dto.dueTimestamp,
                        urgency = dto.urgencyEnum,
                        amount = dto.amount,
                        status = "Pending",
                        conversationKey = conversationKey,
                        confidence = dto.confidence
                    )
                }
                Log.i(TAG, "Successfully extracted ${items.size} obligation(s) from response.")
                items
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Backend priority extraction call failed: ${e.message}", e)
            emptyList()
        }
    }

    private suspend fun OkHttpClient.executeCancellable(request: Request): Response =
        suspendCancellableCoroutine { continuation ->
            val call = newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
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
