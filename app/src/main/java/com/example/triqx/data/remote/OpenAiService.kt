package com.example.triqx.data.remote

import android.util.Log
import com.example.triqx.data.local.NotificationEntity
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenAiService @Inject constructor(
    private val gson: Gson
) {
    companion object {
        private const val TAG = "TriqxOpenAi"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun generate3Replies(
        apiKey: String,
        model: String,
        contactOrTitle: String,
        messages: List<NotificationEntity>
    ): List<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            Log.w(TAG, "[OPENAI] API Key is blank. Using default offline fallback replies.")
            return@withContext getDefaultReplies(messages.firstOrNull()?.text)
        }

        try {
            val formattedHistory = buildString {
                appendLine("Conversation with $contactOrTitle:")
                // Take up to last 10 messages in chronological order
                val history = messages.take(10).reversed()
                for (msg in history) {
                    val sender = if (msg.title.equals("You", ignoreCase = true) || msg.text?.startsWith("Replied you using", ignoreCase = true) == true) {
                        "You"
                    } else {
                        contactOrTitle
                    }
                    appendLine("- $sender: ${msg.text ?: ""}")
                }
                val latest = messages.firstOrNull()?.text ?: ""
                appendLine("Latest incoming message to reply to: \"$latest\"")
            }

            val requestModel = model.ifBlank { "gpt-4o-mini" }
            val requestBodyMap = mapOf(
                "model" to requestModel,
                "messages" to listOf(
                    mapOf(
                        "role" to "system",
                        "content" to "You are a smart reply generator for mobile notifications. Generate EXACTLY 3 natural, concise reply options (each between 1 to 8 words) for the user to quickly send. Output ONLY a valid JSON array of 3 strings, e.g. [\"Working on it now!\", \"Yes, sounds good.\", \"I'll check and update you.\"]. Do NOT use markdown fences or explanations."
                    ),
                    mapOf(
                        "role" to "user",
                        "content" to formattedHistory
                    )
                )
            )

            val requestJson = gson.toJson(requestBodyMap)

            Log.i(TAG, "===> [SENT TO OPENAI] Model: $requestModel | Contact: $contactOrTitle | Messages: ${messages.size}")
            Log.d(TAG, "===> [SENT TO OPENAI PROMPT]:\n$formattedHistory")
            Log.v(TAG, "===> [SENT TO OPENAI JSON]: $requestJson")

            val request = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestJson.toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            Log.i(TAG, "<=== [RECEIVED FROM OPENAI] HTTP Code: ${response.code} (${response.message})")

            if (response.isSuccessful && !responseBody.isNullOrBlank()) {
                Log.d(TAG, "<=== [RECEIVED FROM OPENAI RAW BODY]: $responseBody")

                val jsonObject = JsonParser.parseString(responseBody).asJsonObject
                val choices = jsonObject.getAsJsonArray("choices")
                if (choices != null && choices.size() > 0) {
                    val content = choices[0].asJsonObject
                        .getAsJsonObject("message")
                        .get("content").asString.trim()

                    Log.d(TAG, "<=== [RECEIVED FROM OPENAI CONTENT]: $content")

                    // Parse JSON array string
                    val cleanJson = if (content.startsWith("```json")) {
                        content.removePrefix("```json").removeSuffix("```").trim()
                    } else if (content.startsWith("```")) {
                        content.removePrefix("```").removeSuffix("```").trim()
                    } else {
                        content
                    }

                    val repliesArray = JsonParser.parseString(cleanJson).asJsonArray
                    val list = mutableListOf<String>()
                    for (element in repliesArray) {
                        val str = element.asString.trim()
                        if (str.isNotBlank()) {
                            list.add(str)
                        }
                    }
                    if (list.isNotEmpty()) {
                        val finalReplies = list.take(3)
                        Log.i(TAG, "<=== [PARSED 3 REPLIES FROM OPENAI]: $finalReplies")
                        return@withContext finalReplies
                    }
                }
            } else {
                Log.e(TAG, "<=== [OPENAI REQUEST FAILED] HTTP ${response.code}: $responseBody")
            }
        } catch (e: Exception) {
            Log.e(TAG, "<=== [OPENAI EXCEPTION] Error communicating with OpenAI: ${e.message}", e)
            e.printStackTrace()
        }

        val fallback = getDefaultReplies(messages.firstOrNull()?.text)
        Log.w(TAG, "---> [FALLBACK DEFAULTS] Returning default replies: $fallback")
        fallback
    }

    private fun getDefaultReplies(latestText: String?): List<String> {
        val lower = latestText?.lowercase() ?: ""
        return when {
            lower.contains("?") -> listOf("Yes, sure!", "Not yet, will check", "Let me get back to you")
            lower.contains("call") -> listOf("Calling you in 5 mins", "Can't talk right now", "I'll call you later")
            lower.contains("where") || lower.contains("reached") -> listOf("On my way!", "Almost there", "Will let you know")
            lower.contains("thanks") || lower.contains("thank you") -> listOf("You're welcome!", "No problem!", "Anytime 😊")
            lower.contains("ok") || lower.contains("okay") -> listOf("Sounds good!", "Great 👍", "See you!")
            else -> listOf("Sounds good!", "Got it, thanks!", "I'll check and let you know")
        }
    }
}
