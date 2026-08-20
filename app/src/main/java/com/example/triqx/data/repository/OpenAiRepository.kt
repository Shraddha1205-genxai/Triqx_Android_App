package com.example.triqx.data.repository

import android.content.Context
import android.util.Log
import com.example.triqx.data.local.NotificationEntity
import com.example.triqx.data.remote.OpenAiService
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenAiRepository @Inject constructor(
    private val openAiService: OpenAiService,
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "TriqxOpenAi"
    }

    private val prefs = context.getSharedPreferences("triqx_openai_prefs", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = Gson()

    private val _apiKey = MutableStateFlow(prefs.getString("openai_api_key", "") ?: "")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _selectedModel = MutableStateFlow(prefs.getString("openai_model", "gpt-4o-mini") ?: "gpt-4o-mini")
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    // Map of groupKey -> list of 3 replies
    private val _cachedReplies = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val cachedReplies: StateFlow<Map<String, List<String>>> = _cachedReplies.asStateFlow()

    // Map of groupKey -> boolean loading state
    private val _loadingGroups = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val loadingGroups: StateFlow<Map<String, Boolean>> = _loadingGroups.asStateFlow()

    // Track latest message ID per group to avoid redundant API calls
    private val latestMessageIdByGroup = mutableMapOf<String, Int>()

    init {
        loadCachedRepliesFromDisk()
    }

    private fun loadCachedRepliesFromDisk() {
        try {
            val json = prefs.getString("cached_replies_json", null)
            if (!json.isNullOrBlank()) {
                val type = object : TypeToken<Map<String, List<String>>>() {}.type
                val map: Map<String, List<String>> = gson.fromJson(json, type)
                if (map.isNotEmpty()) {
                    _cachedReplies.value = map
                    Log.i(TAG, "Restored ${map.size} cached conversation reply sets from persistent disk storage")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring cached replies from disk: ${e.message}")
        }
    }

    private fun saveRepliesToDisk(map: Map<String, List<String>>) {
        try {
            val json = gson.toJson(map)
            prefs.edit().putString("cached_replies_json", json).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error persisting cached replies to disk: ${e.message}")
        }
    }

    fun setApiKey(key: String) {
        prefs.edit().putString("openai_api_key", key.trim()).apply()
        _apiKey.value = key.trim()
        Log.i(TAG, "[SETTINGS] Saved OpenAI API key (Length: ${key.trim().length})")
    }

    fun setModel(model: String) {
        prefs.edit().putString("openai_model", model).apply()
        _selectedModel.value = model
        Log.i(TAG, "[SETTINGS] Selected OpenAI model: $model")
    }

    fun generateRepliesIfNeeded(
        groupKey: String,
        contactOrTitle: String,
        messages: List<NotificationEntity>
    ) {
        if (messages.isEmpty()) return
        val latestId = messages.first().id

        // If already generated for this exact latest message, don't spam API
        if (latestMessageIdByGroup[groupKey] == latestId && _cachedReplies.value.containsKey(groupKey)) {
            Log.d(TAG, "[CACHE HIT] Skipping duplicate request for group '$groupKey' on message ID $latestId")
            return
        }

        latestMessageIdByGroup[groupKey] = latestId

        scope.launch {
            _loadingGroups.value = _loadingGroups.value + (groupKey to true)
            Log.i(TAG, "[TRIGGER] Generating 3 AI replies for group '$groupKey' ($contactOrTitle)...")
            try {
                val replies = openAiService.generate3Replies(
                    apiKey = _apiKey.value,
                    model = _selectedModel.value,
                    contactOrTitle = contactOrTitle,
                    messages = messages
                )
                val updated = _cachedReplies.value + (groupKey to replies)
                _cachedReplies.value = updated
                saveRepliesToDisk(updated)
                Log.i(TAG, "[SUCCESS] Cached and persisted ${replies.size} replies for group '$groupKey': $replies")
            } finally {
                _loadingGroups.value = _loadingGroups.value + (groupKey to false)
            }
        }
    }

    fun regenerateReplies(
        groupKey: String,
        contactOrTitle: String,
        messages: List<NotificationEntity>
    ) {
        if (messages.isEmpty()) return
        val latestId = messages.first().id
        latestMessageIdByGroup[groupKey] = latestId

        scope.launch {
            _loadingGroups.value = _loadingGroups.value + (groupKey to true)
            Log.i(TAG, "[REGENERATE] Forcing fresh reply generation for group '$groupKey' ($contactOrTitle)...")
            try {
                val replies = openAiService.generate3Replies(
                    apiKey = _apiKey.value,
                    model = _selectedModel.value,
                    contactOrTitle = contactOrTitle,
                    messages = messages
                )
                val updated = _cachedReplies.value + (groupKey to replies)
                _cachedReplies.value = updated
                saveRepliesToDisk(updated)
                Log.i(TAG, "[REGENERATE SUCCESS] Cached and persisted new ${replies.size} replies for group '$groupKey': $replies")
            } finally {
                _loadingGroups.value = _loadingGroups.value + (groupKey to false)
            }
        }
    }

    suspend fun testConnection(apiKey: String, model: String): Result<String> {
        Log.i(TAG, "[TEST CONNECTION] Testing OpenAI connection with model '$model'...")
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
            val replies = openAiService.generate3Replies(
                apiKey = apiKey,
                model = model,
                contactOrTitle = "Test User",
                messages = testMessages
            )
            if (replies.isNotEmpty()) {
                Log.i(TAG, "[TEST SUCCESS] Replies received: $replies")
                Result.success("Success! Generated sample replies: ${replies.joinToString(" | ")}")
            } else {
                Log.w(TAG, "[TEST FAILED] No replies generated")
                Result.failure(Exception("No replies generated"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "[TEST EXCEPTION] $e")
            Result.failure(e)
        }
    }
}
