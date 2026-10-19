package com.example.triqx.data.remote.ai

import android.util.Log
import com.example.triqx.data.local.ChatMessage
import com.example.triqx.data.local.ObligationDao
import com.example.triqx.data.local.ObligationEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class PriorityAiProvider {
    BACKEND_CLOUD,
    GOOGLE_AICORE
}

/**
 * Coordinates AI-powered obligation extraction across available providers (Backend Cloud vs Google AICore).
 * Handles deduplication and persistence to ObligationDao.
 */
@Singleton
class PriorityAiManager @Inject constructor(
    private val backendPriorityAiService: BackendPriorityAiService,
    private val googleAiCorePriorityService: GoogleAiCorePriorityService,
    private val obligationDao: ObligationDao
) {

    companion object {
        private const val TAG = "PriorityAiManager"
    }

    // Default to BACKEND_CLOUD for high-accuracy, zero-footprint processing
    private val _activeProvider = MutableStateFlow(PriorityAiProvider.BACKEND_CLOUD)
    val activeProvider: StateFlow<PriorityAiProvider> = _activeProvider.asStateFlow()

    fun setProvider(provider: PriorityAiProvider) {
        _activeProvider.value = provider
        Log.i(TAG, "Switched Priority AI Provider to: $provider")
    }

    private fun getActiveService(): PriorityAiService {
        return when (_activeProvider.value) {
            PriorityAiProvider.BACKEND_CLOUD -> backendPriorityAiService
            PriorityAiProvider.GOOGLE_AICORE -> googleAiCorePriorityService
        }
    }

    /**
     * Extracts obligations from recent conversation messages and saves new items to Room.
     * Prevents duplicate insertions if a matching obligation already exists.
     */
    suspend fun extractAndSaveObligations(
        conversationKey: String,
        conversationTitle: String,
        packageName: String,
        messages: List<ChatMessage>
    ): List<ObligationEntity> {
        val service = getActiveService()
        Log.i(TAG, "===> [PRIORITY EXTRACTION STARTED] key='$conversationKey', title='$conversationTitle', pkg='$packageName', msgs=${messages.size}, provider=${_activeProvider.value}")
        val extracted = try {
            service.extractObligations(
                conversationKey = conversationKey,
                conversationTitle = conversationTitle,
                packageName = packageName,
                messages = messages
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting obligations: ${e.message}", e)
            emptyList()
        }

        if (extracted.isEmpty()) {
            Log.i(TAG, "===> [PRIORITY EXTRACTION FINISHED] No obligations identified for key '$conversationKey'")
            return emptyList()
        }

        // Fetch currently active obligations for this conversation to prevent duplicate cards
        val existingActive = obligationDao.getActiveByConversationKey(conversationKey)

        val newlySaved = mutableListOf<ObligationEntity>()
        for (item in extracted) {
            val isDuplicate = existingActive.any { existing ->
                existing.title.equals(item.title, ignoreCase = true) ||
                (existing.amount != null && existing.amount == item.amount && existing.category == item.category)
            }

            if (!isDuplicate) {
                val insertedId = obligationDao.insert(item)
                val savedItem = item.copy(id = insertedId)
                newlySaved.add(savedItem)
                Log.i(TAG, "===> [PRIORITY OBLIGATION SAVED]: '${item.title}' (ID: $insertedId, Urgency: ${item.urgency.name})")
            } else {
                Log.d(TAG, "Skipping duplicate obligation '${item.title}' for key '$conversationKey'")
            }
        }

        Log.i(TAG, "===> [PRIORITY EXTRACTION FINISHED] key='$conversationKey', totalExtracted=${extracted.size}, newlySaved=${newlySaved.size}")
        return newlySaved
    }
}
