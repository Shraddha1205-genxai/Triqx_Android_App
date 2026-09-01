package com.example.triqx.service

import android.app.Notification
import android.app.Person
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.triqx.data.local.AppDao
import com.example.triqx.data.local.ChatMessage
import com.example.triqx.data.local.ContactDao
import com.example.triqx.data.local.ContactEntity
import com.example.triqx.data.local.ConversationDao
import com.example.triqx.data.local.ConversationEntity
import com.example.triqx.data.local.NotificationDao
import com.example.triqx.data.local.NotificationEntity
import com.example.triqx.data.local.ReplyActionStore
import com.example.triqx.data.repository.OpenAiRepository
import com.google.gson.GsonBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Service responsible for intercepting system notifications.
 * Automatically persists reply actions to ReplyActionStore (L1 RAM + L2 Disk),
 * stores conversation data in Room, and generates AI smart replies.
 */
@AndroidEntryPoint
class TriqxNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "TriqxReply"

        var instance: TriqxNotificationListenerService? = null
            private set
    }

    @Inject lateinit var notificationDao: NotificationDao
    @Inject lateinit var conversationDao: ConversationDao
    @Inject lateinit var appDao: AppDao
    @Inject lateinit var contactDao: ContactDao
    @Inject lateinit var openAiRepository: OpenAiRepository
    @Inject lateinit var replyActionStore: ReplyActionStore

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = GsonBuilder()
        .setPrettyPrinting()
        .serializeNulls()
        .create()

    // =============================
    // Lifecycle Methods
    // =============================

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i(TAG, "TriqxNotificationListenerService created.")
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
        Log.i(TAG, "TriqxNotificationListenerService destroyed.")
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        Log.i(TAG, "Notification Listener Connected! Indexing active notifications...")

        serviceScope.launch {
            try {
                val priorityContacts = contactDao.getAllContacts().first()
                activeNotifications?.forEach { sbn ->
                    val isImportant = appDao.isAppImportant(sbn.packageName).first()
                    val title = sbn.notification.extras.getString(Notification.EXTRA_TITLE)
                    val isContact = priorityContacts.any { c ->
                        title?.contains(c.displayName, ignoreCase = true) == true ||
                        (c.officialName?.let { title?.contains(it, ignoreCase = true) } == true)
                    }

                    if (isImportant || isContact) {
                        extractReplyAction(sbn.notification)?.let { action ->
                            val tag = sbn.tag ?: extractTagFromKey(sbn.key) ?: title?.trim() ?: "default"
                            val conversationKey = computeConversationKey(sbn.packageName, tag)
                            replyActionStore.put(conversationKey, action)
                            Log.d(TAG, "Indexed reply action for $conversationKey")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error indexing active notifications: ${e.message}", e)
            }
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "Notification Listener Disconnected")
    }

    // =============================
    // Universal Reply Execution (1-Stage)
    // =============================

    /**
     * Determines if a given conversation has an available reply action.
     */
    fun canReply(conversationKey: String, notificationKey: String? = null): Boolean {
        if (replyActionStore.canReply(conversationKey)) return true
        if (notificationKey != null && activeNotifications?.any { it.key == notificationKey && hasReplyAction(it.notification) } == true) return true
        return findActionInActiveByConversationKey(conversationKey) != null
    }

    /**
     * Sends a reply using the unified ReplyActionStore.
     */
    fun sendReply(
        conversationKey: String,
        message: String,
        notificationKey: String? = null
    ): Boolean {
        Log.i(TAG, "===> Attempting to send reply: \"$message\" for key='$conversationKey'")

        // 1. Retrieve action from unified store (checks RAM first, then Disk)
        val action = replyActionStore.get(conversationKey)
            ?: (notificationKey?.let { findActionInActive(it) })
            ?: findActionInActiveByConversationKey(conversationKey)

        if (action == null) {
            Log.e(TAG, "No reply action found for conversationKey='$conversationKey'")
            return false
        }

        val remoteInputs = action.remoteInputs
        if (remoteInputs.isNullOrEmpty()) {
            Log.e(TAG, "Reply action found ('${action.title}') but remoteInputs is null or empty")
            return false
        }

        // 2. Inject message text into RemoteInput slots
        val intent = Intent()
        val bundle = Bundle()
        for (remoteInput in remoteInputs) {
            bundle.putCharSequence(remoteInput.resultKey, message)
        }
        RemoteInput.addResultsToIntent(remoteInputs, intent, bundle)

        // 3. Dispatch the PendingIntent
        return try {
            action.actionIntent.send(this, 0, intent)
            Log.i(TAG, "<=== SUCCESS: Reply PendingIntent dispatched successfully ($conversationKey)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "<=== ERROR: Exception sending reply PendingIntent: ${e.message}", e)
            false
        }
    }

    private fun hasReplyAction(notification: Notification): Boolean {
        return extractReplyAction(notification) != null
    }

    private fun findActionInActive(notificationKey: String): Notification.Action? {
        val sbn = activeNotifications?.find { it.key == notificationKey } ?: return null
        return extractReplyAction(sbn.notification)
    }

    private fun findActionInActiveByConversationKey(conversationKey: String): Notification.Action? {
        val active = activeNotifications ?: return null
        for (sbn in active) {
            val tag = extractTagFromKey(sbn.key) ?: sbn.notification.extras.getString(Notification.EXTRA_TITLE)?.trim() ?: "default"
            if (computeConversationKey(sbn.packageName, tag) == conversationKey) {
                val action = extractReplyAction(sbn.notification)
                if (action != null) return action
            }
        }
        return null
    }

    fun extractReplyAction(notification: Notification): Notification.Action? {
        // 1. Direct notification actions
        notification.actions?.forEach { action ->
            if (action.remoteInputs != null && action.remoteInputs.isNotEmpty()) {
                return action
            }
        }

        // 2. WearableExtender actions (WhatsApp, Telegram, Signal, etc.)
        try {
            val wearable = Notification.WearableExtender(notification)
            wearable.actions?.forEach { action ->
                if (action.remoteInputs != null && action.remoteInputs.isNotEmpty()) {
                    return action
                }
            }
        } catch (_: Exception) {}

        // 3. Raw Android Wearable Extension Bundle
        try {
            val wearableBundle = notification.extras.getBundle("android.wearable.EXTENSIONS")
            if (wearableBundle != null) {
                @Suppress("DEPRECATION")
                val actionsList = wearableBundle.getParcelableArrayList<Notification.Action>("actions")
                actionsList?.forEach { action ->
                    if (action.remoteInputs != null && action.remoteInputs.isNotEmpty()) {
                        return action
                    }
                }
            }
        } catch (_: Exception) {}

        return null
    }

    fun dismissNotification(notificationKey: String) {
        try {
            cancelNotification(notificationKey)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun dismissNotifications(notificationKeys: List<String>) {
        try {
            notificationKeys.forEach { cancelNotification(it) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun dismissAllActiveNotifications() {
        try {
            cancelAllNotifications()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // =============================
    // Notification Posted Handler
    // =============================

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val notification = sbn.notification
        val packageName = sbn.packageName
        val extras = notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE)
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()

        // --- 1. SENDER EXTRACTION (Email & Lookup URI) ---
        val emailSet = mutableSetOf<String>()
        val uriSet = mutableSetOf<String>()

        @Suppress("DEPRECATION")
        extras.getParcelable<Person>(Notification.EXTRA_MESSAGING_PERSON)?.let { p ->
            p.uri?.let {
                if (it.startsWith("mailto:")) emailSet.add(it.substringAfter("mailto:").trim())
                if (it.startsWith("content://com.android.contacts/")) uriSet.add(it)
            }
        }

        @Suppress("DEPRECATION")
        extras.getParcelableArray(Notification.EXTRA_MESSAGES)?.forEach { msg ->
            (msg as? Bundle)?.let { b ->
                b.getParcelable<Person>(Notification.EXTRA_MESSAGING_PERSON)?.let { p ->
                    p.uri?.let {
                        if (it.startsWith("mailto:")) emailSet.add(it.substringAfter("mailto:").trim())
                        if (it.startsWith("content://com.android.contacts/")) uriSet.add(it)
                    }
                }
            }
        }

        @Suppress("DEPRECATION")
        extras.getParcelableArrayList<Person>(Notification.EXTRA_PEOPLE_LIST)?.forEach { p ->
            p.uri?.let {
                if (it.startsWith("mailto:")) emailSet.add(it.substringAfter("mailto:").trim())
                if (it.startsWith("content://com.android.contacts/")) uriSet.add(it)
            }
        }

        @Suppress("DEPRECATION")
        extras.getStringArray(Notification.EXTRA_PEOPLE)?.forEach { p ->
            if (p.startsWith("mailto:")) emailSet.add(p.substringAfter("mailto:").trim())
            if (p.startsWith("content://com.android.contacts/")) uriSet.add(p)
        }

        @Suppress("DEPRECATION")
        extras.getStringArrayList("android.people")?.forEach { p ->
            if (p.startsWith("mailto:")) emailSet.add(p.substringAfter("mailto:").trim())
            if (p.startsWith("content://com.android.contacts/")) uriSet.add(p)
        }

        val senderEmail = if (emailSet.isNotEmpty()) emailSet.firstOrNull { it.isNotBlank() } else null
        val contactLookupUri = if (uriSet.isNotEmpty()) uriSet.first() else null

        if (title.isNullOrEmpty() && text.isNullOrEmpty() && bigText.isNullOrEmpty()) return

        val timestamp = System.currentTimeMillis()

        // --- 2. COMPREHENSIVE JSON SNAPSHOT ---
        val rawJson = try {
            val root = mutableMapOf<String, Any?>()
            root["packageName"] = packageName
            root["postTime"] = sbn.postTime
            root["id"] = sbn.id
            root["tag"] = sbn.tag
            root["groupKey"] = sbn.groupKey
            root["key"] = sbn.key
            root["isClearable"] = sbn.isClearable
            root["isOngoing"] = sbn.isOngoing
            root["channelId"] = notification.channelId
            root["category"] = notification.category
            @Suppress("DEPRECATION")
            root["priority"] = notification.priority
            root["visibility"] = notification.visibility
            root["when"] = notification.`when`
            root["subText"] = subText

            val flagList = mutableListOf<String>()
            if (notification.flags and Notification.FLAG_ONGOING_EVENT != 0) flagList.add("ONGOING")
            if (notification.flags and Notification.FLAG_NO_CLEAR != 0) flagList.add("NO_CLEAR")
            if (notification.flags and Notification.FLAG_FOREGROUND_SERVICE != 0) flagList.add("FOREGROUND")
            root["flags"] = flagList

            root["actions"] = notification.actions?.map { action ->
                mapOf("title" to action.title?.toString(), "remoteInputs" to (action.remoteInputs?.size ?: 0))
            }

            val ranking = Ranking()
            if (currentRanking.getRanking(sbn.key, ranking)) {
                root["importance"] = ranking.importance
                root["isAmbient"] = ranking.isAmbient
                root["matchesInterruptionFilter"] = ranking.matchesInterruptionFilter()
                root["canBubble"] = ranking.canBubble()
            }

            root["extras"] = deepExtract(extras)
            gson.toJson(root)
        } catch (e: Exception) {
            "Error: ${e.message}"
        }

        serviceScope.launch {
            val isImportantApp = appDao.isAppImportant(packageName).first()

            // Check if notification traces back to a priority contact
            val priorityContacts = contactDao.getAllContacts().first()
            val matchedContact = priorityContacts.find { contact ->
                val nameMatch = (title?.contains(contact.displayName, ignoreCase = true) == true) ||
                                (contact.officialName?.let { title?.contains(it, ignoreCase = true) } == true) ||
                                (text?.contains(contact.displayName, ignoreCase = true) == true) ||
                                (contact.officialName?.let { text?.contains(it, ignoreCase = true) } == true)

                val phoneMatch = contact.phoneNumbers.any { phone ->
                    phone.isNotBlank() && ((title?.contains(phone) == true) || (text?.contains(phone) == true))
                }

                val emailMatch = contact.emails.any { email ->
                    email.isNotBlank() && (
                        (title?.contains(email, ignoreCase = true) == true) ||
                        (text?.contains(email, ignoreCase = true) == true) ||
                        (senderEmail?.contains(email, ignoreCase = true) == true)
                    )
                }

                val uriMatch = contactLookupUri != null && contact.lookupKey != null &&
                               contactLookupUri.contains(contact.lookupKey)

                nameMatch || phoneMatch || emailMatch || uriMatch
            }
            val isFromPriorityContact = (matchedContact != null)

            val isEmail = isEmailApp(packageName)
            val effectiveText = if (isEmail && !bigText.isNullOrBlank()) {
                if (!text.isNullOrBlank() && bigText.startsWith(text.trim())) {
                    val body = bigText.substringAfter(text.trim()).trim().removePrefix("\n").trim()
                    if (body.isNotBlank()) "$text: $body" else bigText
                } else bigText
            } else {
                text ?: bigText
            }

            // --- 1. ALWAYS RECORD ALL NOTIFICATIONS IN NotificationDao (FOR DEBUG TAB) ---
            val existing = notificationDao.getLatestMatching(packageName, title, effectiveText)
            if (existing != null && (timestamp - existing.timestamp < 60 * 60 * 1000L || existing.notificationKey == sbn.key)) {
                notificationDao.updateTimestampAndJson(existing.id, timestamp, rawJson)
            } else {
                notificationDao.insertNotification(
                    NotificationEntity(
                        packageName = packageName,
                        title = title,
                        text = effectiveText,
                        senderEmail = senderEmail,
                        contactLookupUri = contactLookupUri,
                        rawJson = rawJson,
                        notificationKey = sbn.key,
                        timestamp = timestamp
                    )
                )
            }

            // --- 2. GUARD: CONVERSATIONS, SMART REPLIES & REPLY ACTIONS ONLY FOR ALLOWED APPS OR VIP CONTACTS ---
            if (!isImportantApp && !isFromPriorityContact) {
                return@launch
            }

            // --- 3. UNIVERSAL CONVERSATION KEY COMPUTATION ---
            val chatTag = if (isEmail) {
                val email = senderEmail?.removePrefix("mailto:")?.trim()
                if (!email.isNullOrBlank()) "email_$email" else "sender_${title?.trim() ?: "default"}"
            } else {
                extractTagFromJson(rawJson) ?: extractTagFromKey(sbn.key) ?: title?.trim() ?: "default"
            }

            val conversationKey = computeConversationKey(packageName, chatTag)

            // --- 4. PERSIST ACTION TO UNIFIED STORE ---
            val extractedReplyAction = extractReplyAction(notification)
            if (extractedReplyAction != null) {
                replyActionStore.put(conversationKey, extractedReplyAction)
            }

            // --- 5. RESOLVE GROUP vs 1-ON-1 TITLES AND UPDATE ROOM ---
            val isFromYou = title.equals("You", ignoreCase = true) || effectiveText?.startsWith("Replied you using", ignoreCase = true) == true
            val isGroup = chatTag.endsWith("@g.us") || extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)
            val conversationTitleExtra = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()?.trim()

            val conversationTitle = if (isGroup) {
                // For Group: Conversation Title is the Group Name
                conversationTitleExtra?.ifBlank { null }
                    ?: (if (!chatTag.endsWith("@s.whatsapp.net") && !title.isNullOrBlank()) title.trim() else "Group Chat")
            } else {
                // For 1-on-1: Conversation Title is the Sender's Name
                matchedContact?.displayName?.ifBlank { null }
                    ?: title?.trim()?.ifBlank { null }
                    ?: packageName
            }

            val currentConversation = conversationDao.getConversationByKey(conversationKey).firstOrNull()

            val individualSenderName = if (isFromYou) {
                "You"
            } else if (isGroup) {
                // In a group, extract individual sender name
                val messagingPerson = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    @Suppress("DEPRECATION")
                    extras.getParcelable<Person>(Notification.EXTRA_MESSAGING_PERSON)?.name?.toString()
                } else null

                messagingPerson?.ifBlank { null }
                    ?: if (effectiveText != null && effectiveText.contains(":") && !effectiveText.startsWith("http")) {
                        effectiveText.substringBefore(":").trim()
                    } else {
                        title ?: "Member"
                    }
            } else {
                matchedContact?.displayName?.ifBlank { null } ?: title ?: "Unknown"
            }

            val newChatMessage = ChatMessage(
                senderName = individualSenderName,
                subText = if (isGroup) conversationTitle else subText,
                bodyText = effectiveText ?: "",
                timestamp = timestamp,
                isFromYou = isFromYou
            )

            val updatedMessages = ((currentConversation?.messages ?: emptyList()) + newChatMessage)
                .sortedByDescending { it.timestamp }
                .distinctBy { "${it.senderName}_${it.bodyText}_${it.timestamp / 1000}" }

            conversationDao.insertOrUpdate(
                ConversationEntity(
                    conversationKey = conversationKey,
                    packageName = packageName,
                    contactId = matchedContact?.id,
                    title = conversationTitle,
                    specificIdentifier = senderEmail ?: matchedContact?.primaryPhone,
                    messages = updatedMessages,
                    latestTimestamp = timestamp,
                    latestNotificationKey = sbn.key
                )
            )

            val chatTitle = conversationTitle

            // --- 6. TRIGGER ASSISTANT NOTIFICATION ---
            if ((isImportantApp || isFromPriorityContact) && !isFromYou && !effectiveText.isNullOrBlank()) {
                val recentList = notificationDao.getRecentNotificationsList(25)
                val threadMessages = recentList.filter { notif ->
                    if (notif.packageName != packageName) return@filter false
                    val notifTag = if (isEmail) {
                        val notifEmail = notif.senderEmail?.removePrefix("mailto:")?.trim()
                        if (!notifEmail.isNullOrBlank()) "email_$notifEmail" else "sender_${notif.title?.trim() ?: "default"}"
                    } else {
                        extractTagFromJson(notif.rawJson) ?: extractTagFromKey(notif.notificationKey) ?: notif.title?.trim() ?: "default"
                    }
                    computeConversationKey(notif.packageName, notifTag) == conversationKey || notif.title.equals("You", ignoreCase = true)
                }.sortedByDescending { it.timestamp }

                val messagesForAi = if (threadMessages.isNotEmpty()) threadMessages else listOf(
                    NotificationEntity(
                        packageName = packageName,
                        title = title,
                        text = effectiveText,
                        senderEmail = senderEmail,
                        contactLookupUri = contactLookupUri,
                        rawJson = rawJson,
                        notificationKey = sbn.key,
                        timestamp = timestamp
                    )
                )

                val smartReplies = openAiRepository.getOrGenerateReplies(conversationKey, chatTitle, messagesForAi)

                TriqxAssistantNotificationManager.postOrUpdateAssistantNotification(
                    context = applicationContext,
                    groupKey = conversationKey,
                    contactOrTitle = chatTitle,
                    packageName = packageName,
                    notificationKey = sbn.key,
                    contactId = matchedContact?.id,
                    specificIdentifier = senderEmail ?: matchedContact?.primaryPhone,
                    messages = messagesForAi,
                    smartReplies = smartReplies
                )
            }
        }
    }

    // =============================
    // Helpers
    // =============================

    fun computeConversationKey(packageName: String, chatTag: String): String {
        return "${packageName}_$chatTag"
    }

    private fun isEmailApp(packageName: String): Boolean {
        return packageName.contains("gm") || packageName.contains("email") ||
               packageName.contains("outlook") || packageName.contains("mail")
    }

    private fun deepExtract(bundle: Bundle?): Map<String, Any?> {
        if (bundle == null) return emptyMap()
        val map = mutableMapOf<String, Any?>()
        for (key in bundle.keySet()) {
            @Suppress("DEPRECATION")
            val value = bundle.get(key)
            map[key] = processValue(value)
        }
        return map
    }

    private fun processValue(value: Any?): Any? {
        return when (value) {
            is Bundle -> deepExtract(value)
            is Person -> mapOf("name" to value.name?.toString(), "uri" to value.uri, "key" to value.key)
            is Array<*> -> value.map { processValue(it) }
            is List<*> -> value.map { processValue(it) }
            is CharSequence -> value.toString()
            is Int, is Long, is Boolean, is Double, is Float -> value
            else -> value?.toString()
        }
    }

    private fun extractTagFromJson(rawJson: String?): String? {
        if (rawJson.isNullOrBlank()) return null
        return try {
            val obj = com.google.gson.JsonParser.parseString(rawJson).asJsonObject
            if (obj.has("tag") && !obj.get("tag").isJsonNull) {
                val tag = obj.get("tag").asString
                if (!tag.isNullOrBlank() && tag != "null") tag else null
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun extractTagFromKey(notificationKey: String): String? {
        val keyParts = notificationKey.split('|')
        if (keyParts.size >= 4) {
            val tag = keyParts[3]
            if (tag.isNotBlank() && tag != "null") return tag
        }
        return null
    }
}
