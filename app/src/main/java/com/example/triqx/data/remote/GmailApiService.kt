package com.example.triqx.data.remote

import android.util.Base64
import android.util.Log
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
 * Thread metadata holder for constructing true RFC 5322 nested replies.
 */
data class GmailThreadInfo(
    val threadId: String,
    val messageId: String?,
    val references: String?,
    val originalSubject: String?
)

/**
 * Service for sending emails and resolving thread metadata via Google's official Gmail REST API.
 */
@Singleton
class GmailApiService @Inject constructor(
    private val okHttpClient: OkHttpClient
) {

    companion object {
        private const val TAG = "GmailApiService"
        private const val GMAIL_API_BASE = "https://gmail.googleapis.com/gmail/v1/users/me"
        private const val GMAIL_SEND_URL = "$GMAIL_API_BASE/messages/send"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    /**
     * Resolves the original email's threadId, Message-ID, and Subject from the Gmail API.
     */
    suspend fun resolveThreadInfo(
        accessToken: String,
        senderEmail: String,
        subject: String?
    ): GmailThreadInfo? = withContext(Dispatchers.IO) {
        val emailInBrackets = Regex("<([^>]+)>").find(senderEmail)?.groupValues?.get(1)
        val cleanSender = (emailInBrackets ?: senderEmail).removePrefix("mailto:").trim()
        if (cleanSender.isBlank()) {
            Log.w(TAG, "resolveThreadInfo: cleanSender is blank")
            return@withContext null
        }

        try {
            Log.i(TAG, "resolveThreadInfo: resolving thread for sender='$cleanSender', subject='$subject'")

            // 1. Build queries with server-side 'q' parameter (supported by gmail.readonly)
            val cleanSubject = subject?.replace(Regex("(?i)^(\\s*re:\\s*)+"), "")?.trim()?.take(40)
            var latestMessageId: String? = null

            if (!cleanSubject.isNullOrBlank() && cleanSubject != "Email") {
                val sanitizedSubject = cleanSubject.replace("\"", "").replace("\\", "").trim()
                val queryWithSubject = "from:$cleanSender \"$sanitizedSubject\""
                Log.d(TAG, "resolveThreadInfo: querying with subject: $queryWithSubject")
                latestMessageId = queryLatestMessageId(accessToken, queryWithSubject)
            }

            // Fallback: search by sender alone if subject query returned no messages
            if (latestMessageId == null) {
                val querySenderOnly = "from:$cleanSender"
                Log.d(TAG, "resolveThreadInfo: querying with sender only: $querySenderOnly")
                latestMessageId = queryLatestMessageId(accessToken, querySenderOnly)
            }

            if (latestMessageId == null) {
                Log.w(TAG, "resolveThreadInfo: no message found for sender '$cleanSender'")
                return@withContext null
            }

            Log.i(TAG, "resolveThreadInfo: found parent message ID=$latestMessageId. Fetching metadata...")

            // 2. Fetch metadata (Message-ID, Subject, References) for that message
            val metaUrl = "$GMAIL_API_BASE/messages/$latestMessageId?format=metadata&metadataHeaders=Message-ID&metadataHeaders=Subject&metadataHeaders=References"
            val metaRequest = Request.Builder()
                .url(metaUrl)
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Accept", "application/json")
                .get()
                .build()

            okHttpClient.newCall(metaRequest).execute().use { response ->
                val body = response.body?.string() ?: return@withContext null
                if (!response.isSuccessful) {
                    Log.w(TAG, "Failed to fetch message metadata (${response.code}): $body")
                    return@withContext null
                }

                val json = JsonParser.parseString(body).asJsonObject
                val threadId = json.get("threadId")?.asString ?: return@withContext null

                var messageId: String? = null
                var originalSubject: String? = null
                var references: String? = null

                val headers = json.getAsJsonObject("payload")?.getAsJsonArray("headers")
                if (headers != null) {
                    for (i in 0 until headers.size()) {
                        val header = headers.get(i).asJsonObject
                        val name = header.get("name")?.asString ?: ""
                        val value = header.get("value")?.asString ?: ""
                        when {
                            name.equals("Message-ID", ignoreCase = true) -> messageId = value
                            name.equals("Subject", ignoreCase = true) -> originalSubject = value
                            name.equals("References", ignoreCase = true) -> references = value
                        }
                    }
                }

                Log.i(TAG, "Resolved thread metadata: threadId='$threadId', messageId='$messageId', subject='$originalSubject'")
                GmailThreadInfo(
                    threadId = threadId,
                    messageId = messageId,
                    references = references,
                    originalSubject = originalSubject
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception resolving thread info: ${e.message}", e)
            null
        }
    }

    private fun queryLatestMessageId(accessToken: String, query: String): String? {
        return try {
            val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
            val listUrl = "$GMAIL_API_BASE/messages?q=$encodedQuery&maxResults=3"

            val listRequest = Request.Builder()
                .url(listUrl)
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Accept", "application/json")
                .get()
                .build()

            okHttpClient.newCall(listRequest).execute().use { response ->
                val body = response.body?.string() ?: return null
                if (!response.isSuccessful) {
                    Log.w(TAG, "Failed to query messages (${response.code}) for '$query': $body")
                    return null
                }

                val json = JsonParser.parseString(body).asJsonObject
                val messages = json.getAsJsonArray("messages")
                if (messages != null && messages.size() > 0) {
                    messages.get(0).asJsonObject.get("id")?.asString
                } else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error executing query '$query': ${e.message}")
            null
        }
    }

    /**
     * Sends an email via Gmail REST API using the user's OAuth access token.
     * When threadInfo is provided, explicitly attaches threadId and In-Reply-To/References headers.
     */
    suspend fun sendEmail(
        accessToken: String,
        fromEmail: String,
        fromDisplayName: String? = null,
        toEmail: String,
        subject: String,
        bodyText: String,
        threadInfo: GmailThreadInfo? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // 1. Build RFC 2822 MIME message with In-Reply-To & References
            val rawMime = buildRfc2822Message(fromEmail, fromDisplayName, toEmail, subject, bodyText, threadInfo)

            // 2. Encode to URL-safe Base64 without padding (RFC 4648 §5)
            val encodedRaw = Base64.encodeToString(
                rawMime.toByteArray(StandardCharsets.UTF_8),
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )

            // 3. Create JSON payload {"raw": "...", "threadId": "..."}
            val payload = JsonObject().apply {
                addProperty("raw", encodedRaw)
                if (!threadInfo?.threadId.isNullOrBlank()) {
                    addProperty("threadId", threadInfo.threadId)
                }
            }

            val requestBody = payload.toString().toRequestBody(JSON_MEDIA_TYPE)

            val request = Request.Builder()
                .url(GMAIL_SEND_URL)
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Accept", "application/json")
                .post(requestBody)
                .build()

            Log.i(TAG, "Dispatching email to $toEmail (subject: '$subject', threadId: ${threadInfo?.threadId})...")

            okHttpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()

                if (!response.isSuccessful || responseBody == null) {
                    val errorMsg = "Gmail API send failed HTTP ${response.code}: $responseBody"
                    Log.e(TAG, errorMsg)
                    return@withContext Result.failure(RuntimeException(errorMsg))
                }

                val jsonResponse = JsonParser.parseString(responseBody).asJsonObject
                val messageId = jsonResponse.get("id")?.asString ?: "sent"
                Log.i(TAG, "Email successfully sent via Gmail API! Message ID: $messageId, Thread: ${jsonResponse.get("threadId")?.asString}")
                Result.success(messageId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception calling Gmail API: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Builds standard RFC 2822 MIME plain-text email string with optional reply headers and sender display name.
     */
    private fun buildRfc2822Message(
        fromEmail: String,
        fromDisplayName: String? = null,
        toEmail: String,
        subject: String,
        body: String,
        threadInfo: GmailThreadInfo? = null
    ): String {
        val cleanFromEmail = fromEmail.removePrefix("mailto:").trim()
        val cleanName = fromDisplayName?.replace("\"", "")?.replace("\r", "")?.replace("\n", "")?.trim()
        val fromHeader = if (!cleanName.isNullOrBlank()) {
            val isAscii = cleanName.all { it.code in 32..126 }
            if (isAscii) {
                "\"$cleanName\" <$cleanFromEmail>"
            } else {
                val encoded = Base64.encodeToString(cleanName.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
                "=?UTF-8?B?$encoded?= <$cleanFromEmail>"
            }
        } else {
            cleanFromEmail
        }

        return buildString {
            append("From: $fromHeader\r\n")
            append("To: $toEmail\r\n")
            append("Subject: $subject\r\n")
            if (!threadInfo?.messageId.isNullOrBlank()) {
                val formattedMsgId = if (threadInfo.messageId.startsWith("<") && threadInfo.messageId.endsWith(">")) {
                    threadInfo.messageId
                } else {
                    "<${threadInfo.messageId}>"
                }
                append("In-Reply-To: $formattedMsgId\r\n")
                val refs = if (!threadInfo.references.isNullOrBlank()) {
                    if (threadInfo.references.contains(formattedMsgId)) threadInfo.references else "${threadInfo.references} $formattedMsgId"
                } else {
                    formattedMsgId
                }
                append("References: $refs\r\n")
            }
            append("MIME-Version: 1.0\r\n")
            append("Content-Type: text/plain; charset=UTF-8\r\n")
            append("Content-Transfer-Encoding: 7bit\r\n")
            append("\r\n")
            append(body)
        }
    }
}
