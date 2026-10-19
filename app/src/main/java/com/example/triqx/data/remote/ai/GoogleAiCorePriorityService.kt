package com.example.triqx.data.remote.ai

import android.content.Context
import android.util.Log
import com.example.triqx.data.local.ChatMessage
import com.example.triqx.data.local.ObligationEntity
import com.example.triqx.data.remote.dto.ExtractObligationsResponse
import com.google.ai.edge.aicore.DownloadCallback
import com.google.ai.edge.aicore.DownloadConfig
import com.google.ai.edge.aicore.GenerativeAIException
import com.google.ai.edge.aicore.GenerativeModel
import com.google.ai.edge.aicore.generationConfig
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device obligation extraction service using Google AI Edge AICore (Gemini Nano).
 *
 * Runs completely locally on Android AICore supported devices with zero network latency
 * and complete privacy.
 */
@Singleton
class GoogleAiCorePriorityService @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val gson: Gson
) : PriorityAiService {

    companion object {
        private const val TAG = "TriqxGoogleAiCore"
    }

    private val downloadCallback = object : DownloadCallback {
        override fun onDownloadStarted(bytesToDownload: Long) {
            Log.d(TAG, "AICore model download started: $bytesToDownload bytes")
        }

        override fun onDownloadProgress(bytesDownloaded: Long) {
            Log.d(TAG, "AICore model downloading: $bytesDownloaded bytes")
        }

        override fun onDownloadCompleted() {
            Log.i(TAG, "AICore model download completed successfully.")
        }

        override fun onDownloadFailed(failureStatus: String, e: GenerativeAIException) {
            Log.w(TAG, "AICore model download failed [$failureStatus]: ${e.message}")
        }
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

        val prompt = buildAiCorePrompt(conversationTitle, packageName, messages)
        val requestLog = buildString {
            appendLine("==================== [GOOGLE AICORE EXTRACTION REQUEST] ====================")
            appendLine("Package: $packageName")
            appendLine("Conversation: '$conversationTitle' (key: '$conversationKey')")
            appendLine("Messages Context: ${messages.size} message(s)")
            appendLine("Prompt:")
            appendLine(prompt)
            append("===========================================================================")
        }
        logMultiline(TAG, requestLog)

        var generativeModel: GenerativeModel? = null
        try {
            val genConfig = generationConfig {
                context = this@GoogleAiCorePriorityService.context
            }

            generativeModel = GenerativeModel(
                generationConfig = genConfig,
                downloadConfig = DownloadConfig(downloadCallback)
            )

            // Warm up and initialize the on-device inference engine via AICore system service
            generativeModel.prepareInferenceEngine()

            // Run generation directly on-device
            val response = generativeModel.generateContent(prompt)
            val outputJson = response.text.orEmpty()
            val parsed = parseObligationsFromJson(outputJson, conversationKey, conversationTitle)

            val responseLog = buildString {
                appendLine("==================== [GOOGLE AICORE EXTRACTION RESPONSE] ===================")
                appendLine("Conversation: '$conversationTitle'")
                appendLine("Raw Output:")
                appendLine(if (outputJson.isNotBlank()) outputJson else "(empty response)")
                appendLine("Parsed Obligations Count: ${parsed.size}")
                parsed.forEachIndexed { index, item ->
                    appendLine("  [${index + 1}] Title: \"${item.title}\" | Urgency: ${item.urgency} | Category: ${item.category} | Amount: ${item.amount ?: "N/A"}")
                }
                append("===========================================================================")
            }
            logMultiline(TAG, responseLog)

            parsed
        } catch (e: Exception) {
            val errorLog = buildString {
                appendLine("==================== [GOOGLE AICORE EXCEPTION] ====================")
                appendLine("Conversation: '$conversationTitle'")
                appendLine("Error: ${e.message}")
                append("===================================================================")
            }
            logMultiline(TAG, errorLog)
            emptyList()
        } finally {
            try {
                generativeModel?.close()
            } catch (_: Exception) {}
        }
    }

    /**
     * Formats the prompt sent to Google AICore (Gemini Nano) session.
     */
    fun buildAiCorePrompt(
        conversationTitle: String,
        packageName: String,
        messages: List<ChatMessage>
    ): String {
        val transcript = messages.take(10).reversed().joinToString("\n") { msg ->
            val sender = if (msg.isFromYou) "User" else msg.senderName.ifBlank { conversationTitle }
            "$sender: ${msg.bodyText}"
        }

        return """
            You are Google AICore on-device obligation detector for Triqx.
            Analyze the following incoming notification conversation and extract any pending obligations,
            bills, payment reminders, deadlines, promises to someone, or critical tasks.
            
            App: $packageName
            Conversation: $conversationTitle
            Transcript:
            $transcript
            
            Return JSON in this exact structure:
            {
              "obligations": [
                {
                  "title": "Short descriptive title, e.g. Electricity bill payment",
                  "category": "Payment | Work | Personal | Security | Health | Other",
                  "identity": "Who or what it relates to, e.g. Tata Power, Rahul, HR",
                  "dueTimestamp": 1771340000000,
                  "urgency": "Critical | High | Medium | Low",
                  "amount": 1450.0,
                  "confidence": 0.95
                }
              ]
            }
            If no obligation, bill, deadline, or promise exists, return: {"obligations": []}
        """.trimIndent()
    }

    private fun parseObligationsFromJson(
        rawJson: String,
        conversationKey: String,
        conversationTitle: String
    ): List<ObligationEntity> {
        val cleanedJson = rawJson.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        if (cleanedJson.isBlank()) return emptyList()

        return try {
            val parsed = gson.fromJson(cleanedJson, ExtractObligationsResponse::class.java)
            val now = System.currentTimeMillis()
            parsed.obligations.map { dto ->
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
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse obligations JSON from AICore: ${e.message}")
            emptyList()
        }
    }
}
