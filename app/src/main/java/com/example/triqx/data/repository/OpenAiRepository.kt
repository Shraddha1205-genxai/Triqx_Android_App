package com.example.triqx.data.repository

import android.content.Context
import android.util.Log
import com.example.triqx.data.local.AppDao
import com.example.triqx.data.local.ChatMessage
import com.example.triqx.data.local.ConversationDao
import com.example.triqx.data.local.NotificationEntity
import com.example.triqx.data.remote.BackendAiService
import com.example.triqx.data.remote.dto.ReplyMessageDto
import com.example.triqx.data.remote.dto.SmartReplyRequest
import com.example.triqx.utils.Constants
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
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
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
 * Manages AI smart reply generation via the Triqx backend service and local caching.
 * Each conversation group stores its message signature along with its generated replies.
 * Automatically cancels obsolete in-flight generation jobs when newer messages arrive.
 */
@Singleton
class OpenAiRepository @Inject constructor(
    private val backendAiService: BackendAiService,
    private val appDao: AppDao,
    private val conversationDao: ConversationDao,
    @param:ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "TriqxSmartReply"
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

    // --- Backend AI Settings ---

    private val _backendBaseUrl = MutableStateFlow(
        prefs.getString("backend_ai_base_url", Constants.DEFAULT_BACKEND_BASE_URL)
            ?: Constants.DEFAULT_BACKEND_BASE_URL
    )
    val backendBaseUrl: StateFlow<String> = _backendBaseUrl.asStateFlow()

    private val _replyStyle = MutableStateFlow(
        prefs.getString("ai_reply_style", "concise") ?: "concise"
    )
    val replyStyle: StateFlow<String> = _replyStyle.asStateFlow()

    private val _additionalPrompt = MutableStateFlow(
        prefs.getString("ai_additional_prompt", "") ?: ""
    )
    val additionalPrompt: StateFlow<String> = _additionalPrompt.asStateFlow()

    private val _defaultReplyCount = MutableStateFlow(
        prefs.getInt("ai_default_reply_count", 3)
    )
    val defaultReplyCount: StateFlow<Int> = _defaultReplyCount.asStateFlow()

    // Backward-compatibility flows for existing UI references
    private val _apiKey = MutableStateFlow(prefs.getString("openai_api_key", "") ?: "")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _selectedModel = MutableStateFlow(prefs.getString("openai_model", "backend-default") ?: "backend-default")
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
    private val activeJobsByGroup = ConcurrentHashMap<String, Job>()

    init {
        loadRepliesFromDisk()
    }

    // =============================
    // Settings Management
    // =============================

    fun setBackendBaseUrl(url: String) {
        val trimmed = url.trim().removeSuffix("/")
        prefs.edit().putString("backend_ai_base_url", trimmed).apply()
        _backendBaseUrl.value = trimmed
        Log.i(TAG, "Saved Backend Base URL: $trimmed")
    }

    fun setReplyStyle(style: String) {
        val trimmed = style.trim()
        prefs.edit().putString("ai_reply_style", trimmed).apply()
        _replyStyle.value = trimmed
        Log.i(TAG, "Saved AI Reply Style: $trimmed")
    }

    fun setAdditionalPrompt(prompt: String) {
        val trimmed = prompt.trim()
        prefs.edit().putString("ai_additional_prompt", trimmed).apply()
        _additionalPrompt.value = trimmed
        Log.i(TAG, "Saved Additional Prompt: $trimmed")
    }

    fun setDefaultReplyCount(count: Int) {
        val clamped = count.coerceIn(1, 5)
        prefs.edit().putInt("ai_default_reply_count", clamped).apply()
        _defaultReplyCount.value = clamped
        Log.i(TAG, "Saved Default Reply Count: $clamped")
    }

    fun setApiKey(key: String) {
        val trimmed = key.trim()
        prefs.edit().putString("openai_api_key", trimmed).apply()
        _apiKey.value = trimmed
    }

    fun setModel(model: String) {
        prefs.edit().putString("openai_model", model).apply()
        _selectedModel.value = model
    }

    /**
     * Completely wipes all AI reply caches, custom prompts, and settings on sign out.
     */
    fun clearAllData() {
        prefs.edit().clear().apply()
        _cache.value = emptyMap()
        _cachedReplies.value = emptyMap()
        _loadingGroups.value = emptyMap()
        activeJobsByGroup.values.forEach { it.cancel() }
        activeJobsByGroup.clear()
        _replyStyle.value = "concise"
        _additionalPrompt.value = ""
        _defaultReplyCount.value = 3
        _apiKey.value = ""
        _selectedModel.value = "backend-default"
        _backendBaseUrl.value = Constants.DEFAULT_BACKEND_BASE_URL
        Log.i(TAG, "Cleared all AI smart reply caches and preferences from device.")
    }

    // =============================
    // Reply Generation
    // =============================

    /**
     * Generate replies in the background (non-blocking) for NotificationEntity list.
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
                val replies = callBackendForNotifications(groupKey, contactOrTitle, messages)
                cacheReplies(groupKey, signature, replies)
            } catch (e: CancellationException) {
                Log.d(TAG, "Reply generation cancelled for group '$groupKey' due to newer incoming message")
            } catch (e: Exception) {
                Log.e(TAG, "Error generating replies from backend: ${e.message}")
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
     * ChatMessage overload for generateRepliesIfNeeded.
     */
    @JvmName("generateRepliesIfNeededForChat")
    fun generateRepliesIfNeeded(
        groupKey: String,
        contactOrTitle: String,
        messages: List<ChatMessage>
    ) {
        if (messages.isEmpty()) return

        val signature = chatMessageSignature(messages)
        val cached = _cache.value[groupKey]

        if (cached != null && cached.messageSignature == signature && cached.replies.isNotEmpty()) {
            return
        }

        // Avoid duplicate concurrent request if already in-flight for this group
        if (_loadingGroups.value[groupKey] == true) {
            Log.d(TAG, "Reply generation already in-flight for group '$groupKey', skipping duplicate request")
            return
        }

        activeJobsByGroup[groupKey]?.cancel()

        val job = scope.launch {
            setLoading(groupKey, true)
            try {
                val replies = callBackendForChat(groupKey, contactOrTitle, messages)
                cacheReplies(groupKey, signature, replies)
            } catch (e: CancellationException) {
                Log.d(TAG, "Reply generation cancelled for group '$groupKey'")
            } catch (e: Exception) {
                Log.e(TAG, "Error generating replies from backend: ${e.message}")
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
     * Generate replies synchronously (suspending) for ChatMessage list.
     * Used by the notification listener service for real-time assistant notifications.
     */
    @JvmName("getOrGenerateRepliesForChat")
    suspend fun getOrGenerateReplies(
        groupKey: String,
        contactOrTitle: String,
        messages: List<ChatMessage>
    ): List<String> {
        if (messages.isEmpty()) return emptyList()

        val signature = chatMessageSignature(messages)
        val cached = _cache.value[groupKey]

        if (cached != null && cached.messageSignature == signature && cached.replies.isNotEmpty()) {
            return cached.replies
        }

        return try {
            setLoading(groupKey, true)
            val replies = callBackendForChat(groupKey, contactOrTitle, messages)
            cacheReplies(groupKey, signature, replies)
            replies
        } catch (e: CancellationException) {
            Log.d(TAG, "getOrGenerateReplies cancelled for group '$groupKey'")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error generating replies from backend: ${e.message}")
            cached?.replies ?: getDefaultReplies(messages.firstOrNull { !it.isFromYou }?.bodyText)
        } finally {
            setLoading(groupKey, false)
        }
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

        if (cached != null && cached.messageSignature == signature && cached.replies.isNotEmpty()) {
            return cached.replies
        }

        return try {
            val replies = callBackendForNotifications(groupKey, contactOrTitle, messages)
            cacheReplies(groupKey, signature, replies)
            replies
        } catch (e: CancellationException) {
            Log.d(TAG, "getOrGenerateReplies cancelled for group '$groupKey'")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error generating replies from backend: ${e.message}")
            cached?.replies ?: getDefaultReplies(messages.firstOrNull { !it.isFromYou() }?.text)
        }
    }

    /**
     * Force-regenerate replies (ignores cache) for NotificationEntities.
     */
    fun regenerateReplies(
        groupKey: String,
        contactOrTitle: String,
        messages: List<NotificationEntity>
    ) {
        if (messages.isEmpty()) return

        val signature = messageSignature(messages)
        activeJobsByGroup[groupKey]?.cancel()

        val job = scope.launch {
            setLoading(groupKey, true)
            try {
                val replies = callBackendForNotifications(groupKey, contactOrTitle, messages)
                cacheReplies(groupKey, signature, replies)
            } catch (e: CancellationException) {
                Log.d(TAG, "Regenerate replies cancelled for group '$groupKey'")
            } catch (e: Exception) {
                Log.e(TAG, "Error generating replies from backend: ${e.message}")
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
     * ChatMessage overload for regenerateReplies.
     */
    @JvmName("regenerateRepliesForChat")
    fun regenerateReplies(
        groupKey: String,
        contactOrTitle: String,
        messages: List<ChatMessage>
    ) {
        if (messages.isEmpty()) return

        val signature = chatMessageSignature(messages)
        activeJobsByGroup[groupKey]?.cancel()

        val job = scope.launch {
            setLoading(groupKey, true)
            try {
                val replies = callBackendForChat(groupKey, contactOrTitle, messages)
                cacheReplies(groupKey, signature, replies)
            } catch (e: CancellationException) {
                Log.d(TAG, "Regenerate replies cancelled for group '$groupKey'")
            } catch (e: Exception) {
                Log.e(TAG, "Error generating replies from backend: ${e.message}")
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
     * Invalidate cached replies for a specific conversation so next generation uses updated prompt.
     */
    fun invalidateCache(groupKey: String) {
        _cache.value = _cache.value - groupKey
    }

    /**
     * Test backend AI connection with sample message.
     */
    suspend fun testConnection(
        baseUrl: String = _backendBaseUrl.value,
        replyStyle: String = _replyStyle.value,
        additionalPrompt: String? = _additionalPrompt.value.ifBlank { null }
    ): Result<String> {
        return backendAiService.testConnection(
            baseUrl = baseUrl,
            replyStyle = replyStyle,
            additionalPrompt = additionalPrompt
        )
    }

    /**
     * Overload for backward compatibility with settings screen tests.
     */
    suspend fun testConnection(apiKey: String, model: String): Result<String> {
        return testConnection()
    }

    // =============================
    // Network Call Helpers
    // =============================

    private suspend fun callBackendForNotifications(
        groupKey: String,
        contactOrTitle: String,
        messages: List<NotificationEntity>
    ): List<String> {
        val appPackage = messages.firstOrNull()?.packageName
            ?: extractPackageFromGroupKey(groupKey)

        val dtoList = messages
            .take(10)
            .reversed()
            .map { msg ->
                val sender = if (msg.isFromYou()) {
                    "You"
                } else {
                    msg.title ?: contactOrTitle
                }
                ReplyMessageDto(
                    sender = sender,
                    text = msg.text.orEmpty(),
                    timestamp = msg.timestamp,
                    isFromUser = msg.isFromYou()
                )
            }

        val convEntity = conversationDao.findConversationByKey(groupKey)
        val appSpecificPrompt = appDao.getPromptForApp(appPackage)
        val appReplyStyle = appDao.getReplyStyleForApp(appPackage)

        val resolvedPrompt = resolveMergedPrompt(
            globalPrompt = _additionalPrompt.value,
            appPrompt = appSpecificPrompt,
            conversationPrompt = convEntity?.customPrompt
        )

        val resolvedReplyCount = convEntity?.replyCount ?: _defaultReplyCount.value
        val resolvedReplyStyle = appReplyStyle?.ifBlank { null } ?: _replyStyle.value

        val request = SmartReplyRequest(
            appPackage = appPackage,
            conversationTitle = contactOrTitle,
            replyStyle = resolvedReplyStyle,
            additionalPrompt = resolvedPrompt,
            replyCount = resolvedReplyCount,
            messages = dtoList
        )

        return try {
            backendAiService.generateReplies(_backendBaseUrl.value, request)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Backend call failed, using offline fallback: ${e.message}")
            getDefaultReplies(messages.firstOrNull { !it.isFromYou() }?.text, resolvedReplyCount)
        }
    }

    private suspend fun callBackendForChat(
        groupKey: String,
        contactOrTitle: String,
        messages: List<ChatMessage>
    ): List<String> {
        val appPackage = extractPackageFromGroupKey(groupKey)

        val dtoList = messages
            .take(10)
            .reversed()
            .map { msg ->
                val sender = if (msg.isFromYou) {
                    "You"
                } else if (msg.senderName.isNotBlank() && !msg.senderName.equals("You", ignoreCase = true)) {
                    msg.senderName
                } else {
                    contactOrTitle
                }
                ReplyMessageDto(
                    sender = sender,
                    text = msg.bodyText,
                    timestamp = msg.timestamp,
                    isFromUser = msg.isFromYou
                )
            }

        val convEntity = conversationDao.findConversationByKey(groupKey)
        val appSpecificPrompt = appDao.getPromptForApp(appPackage)
        val appReplyStyle = appDao.getReplyStyleForApp(appPackage)

        val resolvedPrompt = resolveMergedPrompt(
            globalPrompt = _additionalPrompt.value,
            appPrompt = appSpecificPrompt,
            conversationPrompt = convEntity?.customPrompt
        )

        val resolvedReplyCount = convEntity?.replyCount ?: _defaultReplyCount.value
        val resolvedReplyStyle = appReplyStyle?.ifBlank { null } ?: _replyStyle.value

        val request = SmartReplyRequest(
            appPackage = appPackage,
            conversationTitle = contactOrTitle,
            replyStyle = resolvedReplyStyle,
            additionalPrompt = resolvedPrompt,
            replyCount = resolvedReplyCount,
            messages = dtoList
        )

        return try {
            backendAiService.generateReplies(_backendBaseUrl.value, request)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Backend call failed, using offline fallback: ${e.message}")
            getDefaultReplies(messages.firstOrNull { !it.isFromYou }?.bodyText, resolvedReplyCount)
        }
    }

    /**
     * Merges all active prompt layers:
     * 1. Global Settings Prompt
     * 2. App-specific Prompt
     * 3. Conversation-specific Prompt
     *
     * Combines non-blank prompts into a unified multi-layer prompt, ensuring that
     * global directives, app instructions, and conversation instructions all apply together.
     */
    private fun resolveMergedPrompt(
        globalPrompt: String?,
        appPrompt: String?,
        conversationPrompt: String?
    ): String? {
        val parts = listOfNotNull(
            globalPrompt?.trim()?.ifBlank { null },
            appPrompt?.trim()?.ifBlank { null },
            conversationPrompt?.trim()?.ifBlank { null }
        ).distinct()

        return if (parts.isEmpty()) null else parts.joinToString("\n\n")
    }

    private fun extractPackageFromGroupKey(groupKey: String): String {
        return when {
            groupKey.contains(":") -> groupKey.substringBefore(":")
            groupKey.contains("_") -> groupKey.substringBefore("_")
            else -> groupKey
        }
    }

    private fun NotificationEntity.isFromYou(): Boolean {
        return title.equals("You", ignoreCase = true) ||
                text?.startsWith("Replied you using", ignoreCase = true) == true
    }

    private fun getDefaultReplies(latestText: String?, count: Int = 3): List<String> {
        val lower = latestText?.lowercase().orEmpty()
        val allReplies = when {
            lower.contains("?") -> listOf(
                "Yes, sure!",
                "Not yet, will check.",
                "Let me get back to you.",
                "I'll confirm in a bit.",
                "Sounds good to me!"
            )
            lower.contains("call") -> listOf(
                "Calling you in 5 mins.",
                "Can't talk right now.",
                "I'll call you later.",
                "Please drop a message.",
                "Let's connect soon."
            )
            lower.contains("where") || lower.contains("reached") -> listOf(
                "On my way!",
                "Almost there.",
                "Will let you know.",
                "Stuck in traffic.",
                "Just arriving!"
            )
            lower.contains("thanks") || lower.contains("thank you") -> listOf(
                "You're welcome!",
                "No problem!",
                "Anytime 😊",
                "Glad I could help!",
                "Most welcome!"
            )
            lower.contains("ok") || lower.contains("okay") -> listOf(
                "Sounds good!",
                "Great 👍",
                "See you!",
                "Perfect.",
                "All set!"
            )
            else -> listOf(
                "Sounds good!",
                "I'll check and get back to you.",
                "Can we connect later?",
                "Got it, thanks!",
                "Understood."
            )
        }
        return allReplies.take(count.coerceAtLeast(1))
    }

    // =============================
    // Signature & Cache Helpers
    // =============================

    /** Creates a signature string for the latest message in a conversation (NotificationEntity). */
    private fun messageSignature(messages: List<NotificationEntity>): String {
        val latest = messages.first()
        return "${latest.title?.trim()}|${latest.text?.trim()}|${latest.timestamp}"
    }

    /** Creates a signature string for the latest ChatMessage in a conversation. */
    private fun chatMessageSignature(messages: List<ChatMessage>): String {
        val latest = messages.first()
        return "${latest.senderName.trim()}|${latest.bodyText.trim()}|${latest.timestamp}"
    }

    /** Save replies to in-memory cache and disk. */
    private fun cacheReplies(groupKey: String, signature: String, replies: List<String>) {
        val updated = _cache.value + (groupKey to CachedReply(signature, replies))
        _cache.value = updated
        _cachedReplies.value = updated.mapValues { it.value.replies }
        saveRepliesToDisk(updated)
    }

    /** Clear cached replies for a specific conversation. */
    fun clearRepliesForGroup(groupKey: String) {
        activeJobsByGroup[groupKey]?.cancel()
        activeJobsByGroup.remove(groupKey)
        val updated = _cache.value - groupKey
        _cache.value = updated
        _cachedReplies.value = updated.mapValues { it.value.replies }
        _loadingGroups.value = _loadingGroups.value - groupKey
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
