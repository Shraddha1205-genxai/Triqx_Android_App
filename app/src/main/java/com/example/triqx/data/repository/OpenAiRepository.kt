package com.example.triqx.data.repository

import android.content.Context
import android.util.Log
import com.example.triqx.data.local.NotificationEntity
import com.example.triqx.data.remote.OpenAiService
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cache entry holding both the message signature and the generated replies.
 */
data class CachedReply(
    val messageSignature: String,
    val replies: List<String>
)

/**
 * Manages AI smart reply generation and caching.
 * Each conversation group stores its message signature along with its generated replies.
 * Automatically cancels obsolete in-flight generation jobs when newer messages arrive.
 */
@Singleton
class OpenAiRepository @Inject constructor(
    private val openAiService: OpenAiService,
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "TriqxOpenAi"
        private val DEFAULT_REPLIES = listOf(
            "Sounds good!",
            "I'll check and get back to you.",
            "Can we connect later?"
        )
    }

    // --- Storage & Coroutine ---

    private val prefs = context.getSharedPreferences("triqx_openai_prefs", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = Gson()

    // --- Settings ---

    private val _apiKey = MutableStateFlow(prefs.getString("openai_api_key", "") ?: "")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _selectedModel = MutableStateFlow(prefs.getString("openai_model", "gpt-4o-mini") ?: "gpt-4o-mini")
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    // --- Unified Cache (groupKey -> CachedReply(signature, replies)) ---

    private val _cache = MutableStateFlow<Map<String, CachedReply>>(emptyMap())

    // Public reply map exposed to UI (groupKey -> List<String>)
    private val _cachedReplies = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val cachedReplies: StateFlow<Map<String, List<String>>> = _cachedReplies.asStateFlow()

    // --- Loading State (groupKey -> isLoading) ---

    private val _loadingGroups = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val loadingGroups: StateFlow<Map<String, Boolean>> = _loadingGroups.asStateFlow()

    // --- Active In-Flight Jobs (groupKey -> Job) ---
    private val activeJobsByGroup = mutableMapOf<String, Job>()

    init {
        loadRepliesFromDisk()
    }

    // =============================
    // Settings
    // =============================

    fun setApiKey(key: String) {
        val trimmed = key.trim()
        prefs.edit().putString("openai_api_key", trimmed).apply()
        _apiKey.value = trimmed
        Log.i(TAG, "Saved API key (length: ${trimmed.length})")
    }

    fun setModel(model: String) {
        prefs.edit().putString("openai_model", model).apply()
        _selectedModel.value = model
        Log.i(TAG, "Selected model: $model")
    }

    // =============================
    // Reply Generation
    // =============================

    /**
     * Generate replies in the background (non-blocking).
     * Skips if replies already exist for this exact message signature.
     * Cancels any prior in-flight request for the same conversation group.
     */
    fun generateRepliesIfNeeded(
        groupKey: String,
        contactOrTitle: String,
        messages: List<NotificationEntity>
    ) {
        if (messages.isEmpty()) return

        val signature = messageSignature(messages)
        val cached = _cache.value[groupKey]

        // Skip if we already have replies for this exact message
        if (cached != null && cached.messageSignature == signature) {
            return
        }

        // Cancel any existing in-flight job for this group
        activeJobsByGroup[groupKey]?.cancel()

        val job = scope.launch {
            setLoading(groupKey, true)
            try {
                val replies = callOpenAi(contactOrTitle, messages)
                cacheReplies(groupKey, signature, replies)
            } catch (e: CancellationException) {
                Log.d(TAG, "Reply generation cancelled for group '$groupKey' due to newer incoming message")
            } catch (e: Exception) {
                Log.e(TAG, "Error generating replies: ${e.message}")
            } finally {
                // Only clear loading state if this job is still the active one
                if (activeJobsByGroup[groupKey] == coroutineContext[Job]) {
                    setLoading(groupKey, false)
                    activeJobsByGroup.remove(groupKey)
                }
            }
        }

        activeJobsByGroup[groupKey] = job
    }

    /**
     * Generate replies synchronously (blocking).
     * Used by the notification listener service for real-time assistant notifications.
     */
    suspend fun getOrGenerateReplies(
        groupKey: String,
        contactOrTitle: String,
        messages: List<NotificationEntity>
    ): List<String> {
        if (messages.isEmpty()) return emptyList()

        val signature = messageSignature(messages)
        val cached = _cache.value[groupKey]

        // Return cached if available for this exact message
        if (cached != null && cached.messageSignature == signature && cached.replies.isNotEmpty()) {
            return cached.replies
        }

        if (_apiKey.value.isBlank()) return DEFAULT_REPLIES

        return try {
            val replies = callOpenAi(contactOrTitle, messages)
            cacheReplies(groupKey, signature, replies)
            replies
        } catch (e: CancellationException) {
            Log.d(TAG, "getOrGenerateReplies cancelled for group '$groupKey'")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error generating replies: ${e.message}")
            cached?.replies ?: DEFAULT_REPLIES
        }
    }

    /**
     * Force-regenerate replies (ignores cache).
     * Called when user taps the "Regenerate" button.
     * Cancels any prior in-flight request for the same conversation group.
     */
    fun regenerateReplies(
        groupKey: String,
        contactOrTitle: String,
        messages: List<NotificationEntity>
    ) {
        if (messages.isEmpty()) return

        val signature = messageSignature(messages)

        // Cancel any existing in-flight job for this group
        activeJobsByGroup[groupKey]?.cancel()

        val job = scope.launch {
            setLoading(groupKey, true)
            try {
                val replies = callOpenAi(contactOrTitle, messages)
                cacheReplies(groupKey, signature, replies)
            } catch (e: CancellationException) {
                Log.d(TAG, "Regenerate replies cancelled for group '$groupKey'")
            } catch (e: Exception) {
                Log.e(TAG, "Error generating replies: ${e.message}")
            } finally {
                if (activeJobsByGroup[groupKey] == coroutineContext[Job]) {
                    setLoading(groupKey, false)
                    activeJobsByGroup.remove(groupKey)
                }
            }
        }

        activeJobsByGroup[groupKey] = job
    }

    /**
     * Test API connection with a sample message.
     */
    suspend fun testConnection(apiKey: String, model: String): Result<String> {
        return try {
            val testMessages = listOf(
                NotificationEntity(
                    packageName = "com.test",
                    title = "Test User",
                    text = "Hey, are you free for lunch today?",
                    notificationKey = "test_key",
                    timestamp = System.currentTimeMillis()
                )
            )

            val replies = openAiService.generate3Replies(apiKey, model, "Test User", testMessages)

            if (replies.isNotEmpty()) {
                Result.success("Success! Replies: ${replies.joinToString(" | ")}")
            } else {
                Result.failure(Exception("No replies generated"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Test failed: $e")
            Result.failure(e)
        }
    }

    // =============================
    // Helpers
    // =============================

    /** Creates a signature string for the latest message in a conversation. */
    private fun messageSignature(
        messages: List<NotificationEntity>
    ): String {
        val latest = messages.first()

        return "${latest.title?.trim()}|" +
               "${latest.text?.trim()}|" +
               latest.timestamp
    }

    /** Call OpenAI API to generate 3 replies. */
    private suspend fun callOpenAi(contactOrTitle: String, messages: List<NotificationEntity>): List<String> {
        return openAiService.generate3Replies(
            apiKey = _apiKey.value,
            model = _selectedModel.value,
            contactOrTitle = contactOrTitle,
            messages = messages
        )
    }

    /** Save replies to in-memory cache and disk. */
    private fun cacheReplies(groupKey: String, signature: String, replies: List<String>) {
        val updated = _cache.value + (groupKey to CachedReply(signature, replies))
        _cache.value = updated
        _cachedReplies.value = updated.mapValues { it.value.replies }
        saveRepliesToDisk(updated)
    }

    /** Update loading state for a group. */
    private fun setLoading(groupKey: String, loading: Boolean) {
        _loadingGroups.value = _loadingGroups.value + (groupKey to loading)
    }

    // =============================
    // Disk Persistence
    // =============================

    private fun loadRepliesFromDisk() {
        try {
            val json = prefs.getString("cached_replies_json", null) ?: return
            val type = object : TypeToken<Map<String, CachedReply>>() {}.type
            val map: Map<String, CachedReply> = gson.fromJson(json, type)
            if (map.isNotEmpty()) {
                _cache.value = map
                _cachedReplies.value = map.mapValues { it.value.replies }
                Log.i(TAG, "Restored ${map.size} cached reply sets from disk")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading replies from disk: ${e.message}")
        }
    }

    private fun saveRepliesToDisk(map: Map<String, CachedReply>) {
        try {
            prefs.edit().putString("cached_replies_json", gson.toJson(map)).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving replies to disk: ${e.message}")
        }
    }
}
