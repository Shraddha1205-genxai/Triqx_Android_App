package com.example.triqx.data.remote

import android.util.Log
import com.example.triqx.utils.Constants
import com.example.triqx.utils.EmailUtils
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for sending replies and managing mail via the Microsoft Graph REST API.
 */
@Singleton
class OutlookGraphApiService @Inject constructor(
    private val okHttpClient: OkHttpClient
) {

    companion object {
        private const val TAG = "OutlookGraphApiService"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    /**
     * User profile retrieved from Microsoft Graph /me.
     */
    data class MicrosoftUserProfile(
        val email: String,
        val displayName: String?
    )

    /**
     * Fetches the authenticated user's email and display name from Graph API.
     */
    suspend fun fetchUserProfile(accessToken: String): Result<MicrosoftUserProfile> = withContext(Dispatchers.IO) {
        try {
            val url = "${Constants.MICROSOFT_GRAPH_BASE_URL}/me"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Accept", "application/json")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (!response.isSuccessful || body == null) {
                    val error = "Graph API /me failed (${response.code}): $body"
                    Log.e(TAG, error)
                    return@withContext Result.failure(RuntimeException(error))
                }

                val json = JsonParser.parseString(body).asJsonObject
                val mail = json.get("mail")?.takeIf { !it.isJsonNull }?.asString?.ifBlank { null }
                val upn = json.get("userPrincipalName")?.takeIf { !it.isJsonNull }?.asString?.ifBlank { null }
                val email = EmailUtils.cleanEmail(mail) ?: EmailUtils.cleanEmail(upn)

                if (email.isNullOrBlank()) {
                    return@withContext Result.failure(IllegalStateException("No email address found in Microsoft profile"))
                }

                val displayName = json.get("displayName")?.takeIf { !it.isJsonNull }?.asString?.ifBlank { null }
                Result.success(MicrosoftUserProfile(email = email, displayName = displayName))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching Microsoft profile: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Resolves the parent message ID for in-thread replies.
     */
    suspend fun resolveParentMessageId(
        accessToken: String,
        senderEmail: String,
        subject: String?
    ): String? = withContext(Dispatchers.IO) {
        val cleanSender = EmailUtils.cleanEmail(senderEmail) ?: senderEmail.trim()
        if (cleanSender.isBlank()) return@withContext null

        try {
            // Filter by sender email address, order by receivedDateTime desc
            val encodedFilter = URLEncoder.encode("from/emailAddress/address eq '$cleanSender'", StandardCharsets.UTF_8.name())
            val url = "${Constants.MICROSOFT_GRAPH_BASE_URL}/me/messages?\$filter=$encodedFilter&\$top=5&\$select=id,conversationId,subject"

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Accept", "application/json")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return@withContext null
                if (!response.isSuccessful) {
                    Log.w(TAG, "Failed to resolve messages for $cleanSender (${response.code}): $body")
                    return@withContext null
                }

                val json = JsonParser.parseString(body).asJsonObject
                val valueArray = json.getAsJsonArray("value") ?: return@withContext null
                if (valueArray.size() == 0) return@withContext null

                val cleanTargetSubj = subject?.replace(Regex("(?i)^(\\s*re:\\s*)+"), "")?.trim()

                if (!cleanTargetSubj.isNullOrBlank() && cleanTargetSubj != "Email") {
                    for (i in 0 until valueArray.size()) {
                        val item = valueArray.get(i).asJsonObject
                        val itemSubj = item.get("subject")?.takeIf { !it.isJsonNull }?.asString
                            ?.replace(Regex("(?i)^(\\s*re:\\s*)+"), "")?.trim()
                        if (itemSubj != null && (itemSubj.contains(cleanTargetSubj, ignoreCase = true) || cleanTargetSubj.contains(itemSubj, ignoreCase = true))) {
                            val id = item.get("id")?.asString
                            Log.i(TAG, "Found matching parent message by subject: $id")
                            return@withContext id
                        }
                    }
                }

                // Fallback: return the most recent message from this sender
                val latestId = valueArray.get(0).asJsonObject.get("id")?.asString
                Log.i(TAG, "Using latest message from sender as fallback parent: $latestId")
                latestId
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error resolving parent message ID: ${e.message}")
            null
        }
    }

    /**
     * Sends an email reply via Microsoft Graph API.
     * Tries in-thread reply (/me/messages/{id}/reply) first; falls back to /me/sendMail if no parent ID.
     */
    suspend fun sendReply(
        accessToken: String,
        recipientEmail: String,
        subject: String?,
        replyText: String,
        parentMessageId: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanRecipient = EmailUtils.cleanEmail(recipientEmail) ?: recipientEmail.trim()

        if (!parentMessageId.isNullOrBlank()) {
            val replyResult = sendInThreadReply(accessToken, parentMessageId, cleanRecipient, replyText)
            if (replyResult.isSuccess) {
                return@withContext replyResult
            }
            Log.w(TAG, "In-thread reply failed, falling back to /me/sendMail...")
        }

        sendStandaloneEmail(accessToken, cleanRecipient, subject, replyText)
    }

    /**
     * Posts an in-thread reply using Microsoft Graph's /messages/{id}/reply endpoint.
     */
    private fun sendInThreadReply(
        accessToken: String,
        messageId: String,
        recipientEmail: String,
        replyText: String
    ): Result<String> {
        return try {
            val url = "${Constants.MICROSOFT_GRAPH_BASE_URL}/me/messages/$messageId/reply"

            val recipientObj = JsonObject().apply {
                val emailObj = JsonObject().apply {
                    addProperty("address", recipientEmail)
                }
                add("emailAddress", emailObj)
            }
            val toRecipients = JsonArray().apply { add(recipientObj) }

            val messageObj = JsonObject().apply {
                add("toRecipients", toRecipients)
            }

            val payload = JsonObject().apply {
                add("message", messageObj)
                addProperty("comment", replyText)
            }

            val requestBody = payload.toString().toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Accept", "application/json")
                .post(requestBody)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string()
                // HTTP 202 Accepted indicates successful queued dispatch in Microsoft Graph
                if (response.isSuccessful || response.code == 202) {
                    Log.i(TAG, "Reply sent successfully via Graph API /reply to $recipientEmail (parent: $messageId)")
                    Result.success(messageId)
                } else {
                    val error = "Graph API /reply failed (${response.code}): $body"
                    Log.w(TAG, error)
                    Result.failure(RuntimeException(error))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception calling Graph API /reply: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Sends a direct email using Microsoft Graph's /me/sendMail endpoint.
     */
    private fun sendStandaloneEmail(
        accessToken: String,
        recipientEmail: String,
        subject: String?,
        bodyText: String
    ): Result<String> {
        return try {
            val url = "${Constants.MICROSOFT_GRAPH_BASE_URL}/me/sendMail"

            val cleanSubject = when {
                subject.isNullOrBlank() -> "Re: Email"
                subject.startsWith("Re:", ignoreCase = true) -> subject
                else -> "Re: $subject"
            }

            val recipientObj = JsonObject().apply {
                val emailObj = JsonObject().apply {
                    addProperty("address", recipientEmail)
                }
                add("emailAddress", emailObj)
            }
            val toRecipients = JsonArray().apply { add(recipientObj) }

            val bodyObj = JsonObject().apply {
                addProperty("contentType", "Text")
                addProperty("content", bodyText)
            }

            val messageObj = JsonObject().apply {
                addProperty("subject", cleanSubject)
                add("body", bodyObj)
                add("toRecipients", toRecipients)
            }

            val payload = JsonObject().apply {
                add("message", messageObj)
                addProperty("saveToSentItems", "true")
            }

            val requestBody = payload.toString().toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Accept", "application/json")
                .post(requestBody)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string()
                // HTTP 202 Accepted indicates successful sendMail dispatch
                if (response.isSuccessful || response.code == 202) {
                    Log.i(TAG, "Email sent successfully via Graph API /sendMail to $recipientEmail")
                    Result.success("sent")
                } else {
                    val error = "Graph API /sendMail failed (${response.code}): $body"
                    Log.e(TAG, error)
                    Result.failure(RuntimeException(error))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception calling Graph API /sendMail: ${e.message}", e)
            Result.failure(e)
        }
    }
}
