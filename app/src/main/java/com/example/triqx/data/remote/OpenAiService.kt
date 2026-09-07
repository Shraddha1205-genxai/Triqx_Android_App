package com.example.triqx.data.remote

import android.util.Log
import com.example.triqx.data.local.ChatMessage
import com.example.triqx.data.local.NotificationEntity
import com.google.gson.Gson
import com.google.gson.JsonParser
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
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenAiService @Inject constructor(
    private val gson: Gson
) {

    companion object {
        private const val TAG = "TriqxOpenAi"
        private const val RESPONSES_URL = "https://api.openai.com/v1/responses"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Returns exactly 3 AI-generated reply suggestions for a list of ChatMessages.
     */
    @JvmName("generate3RepliesForChat")
    suspend fun generate3Replies(
        apiKey: String,
        model: String,
        contactOrTitle: String,
        chatMessages: List<ChatMessage>
    ): List<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            Log.w(
                TAG,
                "[OPENAI] API Key is blank. Using default offline fallback replies."
            )
            return@withContext getDefaultReplies(
                chatMessages.firstOrNull { !it.isFromYou }?.bodyText
            )
        }

        val latestIncomingMessage = chatMessages
            .firstOrNull { !it.isFromYou }
            ?.bodyText
            .orEmpty()

        val formattedHistory = buildString {
            appendLine("Conversation with $contactOrTitle:")
            val history = chatMessages
                .take(10)
                .reversed()

            for (msg in history) {
                val sender = if (msg.isFromYou) {
                    "You"
                } else if (msg.senderName.isNotBlank() && !msg.senderName.equals("You", ignoreCase = true)) {
                    msg.senderName
                } else {
                    contactOrTitle
                }
                appendLine("- $sender: ${msg.bodyText}")
            }

            appendLine()
            appendLine(
                "Latest incoming message to reply to: \"$latestIncomingMessage\""
            )
        }

        executeResponsesApi(
            apiKey = apiKey,
            model = model,
            contactOrTitle = contactOrTitle,
            messageCount = chatMessages.size,
            latestIncomingMessage = latestIncomingMessage,
            formattedHistory = formattedHistory
        )
    }

    /**
     * Returns exactly 3 AI-generated reply suggestions for a list of NotificationEntities.
     */
    suspend fun generate3Replies(
        apiKey: String,
        model: String,
        contactOrTitle: String,
        messages: List<NotificationEntity>
    ): List<String> = withContext(Dispatchers.IO) {

        if (apiKey.isBlank()) {
            Log.w(
                TAG,
                "[OPENAI] API Key is blank. Using default offline fallback replies."
            )

            return@withContext getDefaultReplies(
                messages.firstOrNull { !it.isFromYou() }?.text
            )
        }

        val latestIncomingMessage = messages
            .firstOrNull { !it.isFromYou() }
            ?.text
            .orEmpty()

        val formattedHistory = buildString {
            appendLine("Conversation with $contactOrTitle:")

            val history = messages
                .take(10)
                .reversed()

            for (msg in history) {
                val sender = if (msg.isFromYou()) {
                    "You"
                } else {
                    contactOrTitle
                }

                appendLine("- $sender: ${msg.text.orEmpty()}")
            }

            appendLine()
            appendLine(
                "Latest incoming message to reply to: \"$latestIncomingMessage\""
            )
        }

        executeResponsesApi(
            apiKey = apiKey,
            model = model,
            contactOrTitle = contactOrTitle,
            messageCount = messages.size,
            latestIncomingMessage = latestIncomingMessage,
            formattedHistory = formattedHistory
        )
    }

    private suspend fun executeResponsesApi(
        apiKey: String,
        model: String,
        contactOrTitle: String,
        messageCount: Int,
        latestIncomingMessage: String,
        formattedHistory: String
    ): List<String> = withContext(Dispatchers.IO) {
        val requestModel = model.ifBlank { "gpt-5.6-luna" }

        return@withContext try {
            val requestBody = mapOf(
                "model" to requestModel,

                "instructions" to """
                    You are a smart reply generator for mobile notifications.

                    Generate EXACTLY 3 natural and concise reply options for the user
                    to quickly send.

                    The replies should:
                    - Match the context of the conversation.
                    - Respond specifically to the latest incoming message.
                    - Sound natural and human.
                    - Vary slightly in tone when appropriate.
                    - Never mention that you are an AI.
                    - Never include explanations.
                    - Never include markdown.

                    Return ONLY a valid JSON array containing exactly 3 strings.

                    Example:
                    ["Working on it now!", "Yes, sounds good.", "I'll check and update you."]
                """.trimIndent(),

                "input" to formattedHistory
            )

            val requestJson = gson.toJson(requestBody)

            Log.i(
                TAG,
                "===> [SENT TO OPENAI] Model: $requestModel | " +
                        "Contact: $contactOrTitle | Messages: $messageCount"
            )

            Log.d(
                TAG,
                "===> [SENT TO OPENAI PROMPT]:\n$formattedHistory"
            )

            Log.v(
                TAG,
                "===> [SENT TO OPENAI JSON]: $requestJson"
            )

            val request = Request.Builder()
                .url(RESPONSES_URL)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(
                    requestJson.toRequestBody(jsonMediaType)
                )
                .build()

            client.executeCancellable(request).use { response ->

                val responseBody = response.body?.string().orEmpty()

                Log.i(
                    TAG,
                    "<=== [RECEIVED FROM OPENAI] " +
                            "HTTP Code: ${response.code} (${response.message})"
                )

                if (!response.isSuccessful || responseBody.isBlank()) {
                    Log.e(
                        TAG,
                        "<=== [OPENAI REQUEST FAILED] " +
                                "HTTP ${response.code}: $responseBody"
                    )

                    return@withContext getDefaultReplies(
                        latestIncomingMessage
                    )
                }

                Log.d(
                    TAG,
                    "<=== [RECEIVED FROM OPENAI RAW BODY]: $responseBody"
                )

                /*
                 * Responses API returns:
                 *
                 * {
                 *   "output": [
                 *     {
                 *       "type": "message",
                 *       "content": [
                 *         {
                 *           "type": "output_text",
                 *           "text": "[...]"
                 *         }
                 *       ]
                 *     }
                 *   ]
                 * }
                 */

                val jsonObject = JsonParser
                    .parseString(responseBody)
                    .asJsonObject

                val output = jsonObject
                    .getAsJsonArray("output")

                if (output == null || output.size() == 0) {
                    Log.e(
                        TAG,
                        "[OPENAI] No output returned from Responses API."
                    )

                    return@withContext getDefaultReplies(
                        latestIncomingMessage
                    )
                }

                var contentText: String? = null

                for (outputItemElement in output) {
                    val outputItem = outputItemElement.asJsonObject

                    if (outputItem.get("type")?.asString != "message") {
                        continue
                    }

                    val contentArray = outputItem
                        .getAsJsonArray("content")

                    for (contentItemElement in contentArray) {
                        val contentItem =
                            contentItemElement.asJsonObject

                        if (
                            contentItem.get("type")?.asString ==
                            "output_text"
                        ) {
                            contentText =
                                contentItem.get("text")?.asString

                            break
                        }
                    }

                    if (!contentText.isNullOrBlank()) {
                        break
                    }
                }

                val content = contentText?.trim()

                if (content.isNullOrBlank()) {
                    Log.e(
                        TAG,
                        "[OPENAI] No output_text found in response."
                    )

                    return@withContext getDefaultReplies(
                        latestIncomingMessage
                    )
                }

                Log.d(
                    TAG,
                    "<=== [RECEIVED FROM OPENAI CONTENT]: $content"
                )

                /*
                 * Parse the JSON array generated by the model.
                 */
                val cleanJson = content
                    .removePrefix("```json")
                    .removePrefix("```")
                    .removeSuffix("```")
                    .trim()

                val repliesArray = try {
                    JsonParser.parseString(cleanJson).asJsonArray
                } catch (e: Exception) {
                    Log.e(
                        TAG,
                        "[OPENAI] Failed to parse reply JSON: $cleanJson",
                        e
                    )

                    return@withContext getDefaultReplies(
                        latestIncomingMessage
                    )
                }

                val replies = mutableListOf<String>()

                for (element in repliesArray) {
                    if (element.isJsonPrimitive &&
                        element.asJsonPrimitive.isString
                    ) {
                        val reply = element.asString.trim()

                        if (reply.isNotBlank()) {
                            replies.add(reply)
                        }
                    }
                }

                if (replies.size >= 3) {
                    val finalReplies = replies.take(3)

                    Log.i(
                        TAG,
                        "<=== [PARSED 3 REPLIES FROM OPENAI]: $finalReplies"
                    )

                    return@withContext finalReplies
                }

                Log.w(
                    TAG,
                    "[OPENAI] Model returned fewer than 3 valid replies."
                )
            }

            val fallback = getDefaultReplies(latestIncomingMessage)

            Log.w(
                TAG,
                "---> [FALLBACK DEFAULTS] Returning default replies: $fallback"
            )

            return@withContext fallback

        } catch (e: CancellationException) {
            Log.d(TAG, "[OPENAI] Request cancelled.")
            throw e
        } catch (e: Exception) {
            Log.e(
                TAG,
                "<=== [OPENAI EXCEPTION] " +
                        "Error communicating with OpenAI: ${e.message}",
                e
            )

            return@withContext getDefaultReplies(latestIncomingMessage)
        }
    }

    /**
     * Determines whether this notification/message was generated by the user.
     */
    private fun NotificationEntity.isFromYou(): Boolean {
        return title.equals("You", ignoreCase = true) ||
                text?.startsWith(
                    "Replied you using",
                    ignoreCase = true
                ) == true
    }

    private fun getDefaultReplies(
        latestText: String?
    ): List<String> {

        val lower = latestText
            ?.lowercase()
            .orEmpty()

        return when {
            lower.contains("?") -> {
                listOf(
                    "Yes, sure!",
                    "Not yet, will check.",
                    "Let me get back to you."
                )
            }

            lower.contains("call") -> {
                listOf(
                    "Calling you in 5 mins.",
                    "Can't talk right now.",
                    "I'll call you later."
                )
            }

            lower.contains("where") ||
                    lower.contains("reached") -> {
                listOf(
                    "On my way!",
                    "Almost there.",
                    "Will let you know."
                )
            }

            lower.contains("thanks") ||
                    lower.contains("thank you") -> {
                listOf(
                    "You're welcome!",
                    "No problem!",
                    "Anytime 😊"
                )
            }

            lower.contains("ok") ||
                    lower.contains("okay") -> {
                listOf(
                    "Sounds good!",
                    "Great 👍",
                    "See you!"
                )
            }

            else -> {
                listOf(
                    "Sounds good!",
                    "Got it, thanks!",
                    "I'll check and let you know."
                )
            }
        }
    }

    /**
     * Executes an OkHttp request cancellably with coroutines.
     * When the coroutine is cancelled, the underlying OkHttp call is aborted immediately.
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