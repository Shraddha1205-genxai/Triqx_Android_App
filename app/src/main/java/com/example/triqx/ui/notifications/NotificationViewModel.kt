package com.example.triqx.ui.notifications

import android.content.Context
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.triqx.data.local.AppDao
import com.example.triqx.data.local.ChatMessage
import com.example.triqx.data.local.ContactDao
import com.example.triqx.data.local.ContactEntity
import com.example.triqx.data.local.ConversationDao
import com.example.triqx.data.local.ConversationEntity
import com.example.triqx.data.local.NotificationDao
import com.example.triqx.data.local.NotificationEntity
import com.example.triqx.data.repository.OpenAiRepository
import com.example.triqx.service.TriqxNotificationListenerService
import com.example.triqx.utils.EmailUtils
import com.google.gson.JsonParser
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A conversation card shown on the Home screen.
 * Backed by ConversationEntity from the conversations table.
 */
data class Conversation(
    val groupKey: String,                        // conversationKey
    val title: String,                           // Clean display title (Group Name or Sender Name)
    val contact: ContactEntity?,                 // Resolved from contactId
    val senderIdentifier: String?,               // Email address or phone number of sender
    val receiverIdentifier: String? = null,      // User's receiving account email or identifier
    val packageName: String,                     // Source app package
    val messages: List<ChatMessage>,             // Conversation messages (newest first)
    val latestTimestamp: Long,                   // Timestamp of the latest message
    val canReply: Boolean,                       // Whether RemoteInput reply is available
    val latestNotificationKey: String            // Android notification key for reply/dismiss
) {
    // Backward-compatibility getters
    val specificIdentifier: String? get() = senderIdentifier
    val accountEmail: String? get() = receiverIdentifier
}

@HiltViewModel
class NotificationViewModel @Inject constructor(
    private val notificationDao: NotificationDao,
    private val conversationDao: ConversationDao,
    private val contactDao: ContactDao,
    private val appDao: AppDao,
    private val openAiRepository: OpenAiRepository,
    val emailAccountStore: com.example.triqx.data.local.EmailAccountStore,
    val emailReplyDispatcher: com.example.triqx.service.EmailReplyDispatcher,
    @ApplicationContext private val context: Context
) : ViewModel() {

    // =============================
    // Raw Data Flows
    // =============================

    // All captured notifications (used in Debug view)
    val allNotifications = notificationDao.getAllNotifications()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notifications = allNotifications

    // Stored conversation entities directly from Room
    val allConversations: StateFlow<List<ConversationEntity>> = conversationDao.getAllConversations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // AI reply cache & loading state
    val cachedReplies: StateFlow<Map<String, List<String>>> = openAiRepository.cachedReplies
    val loadingGroups: StateFlow<Map<String, Boolean>> = openAiRepository.loadingGroups

    // =============================
    // Filtered Notifications (Flat List)
    // =============================

    val filteredNotifications: StateFlow<List<NotificationEntity>> = combine(
        notificationDao.getAllNotifications(),
        contactDao.getAllContacts(),
        appDao.getAllImportantApps()
    ) { notifications, contacts, apps ->
        val importantPackages = apps.map { it.packageName }.toSet()

        notifications.filter { notif ->
            importantPackages.contains(notif.packageName) ||
                contacts.any { isNotificationFromContact(notif, it) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // =============================
    // Grouped Priority Conversations (Conversation Cards from conversations table)
    // =============================

    val groupedPriorityNotifications: StateFlow<List<Conversation>> = combine(
        conversationDao.getAllConversations(),
        contactDao.getAllContacts()
    ) { conversations, contacts ->
        buildConversationCards(conversations, contacts)
    }
    .flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            conversationDao.getAllConversations().collect { list ->
                Log.d("TriqxConversations", "=== [CONVERSATION TABLE DUMP: ${list.size} active threads] ===")
                list.forEachIndexed { i, c ->
                    val latestMsg = c.messages.firstOrNull()
                    Log.d("TriqxConversations", "  #$i key='${c.conversationKey}', title='${c.title}', pkg='${c.packageName}', totalMsgs=${c.messages.size}, latest=[${latestMsg?.senderName}]: '${latestMsg?.bodyText}'")
                }
            }
        }

        viewModelScope.launch(Dispatchers.Default) {
            groupedPriorityNotifications.collect { conversations ->
                conversations.forEach { conv ->
                    val latestMsg = conv.messages.firstOrNull()
                    if (latestMsg != null && !latestMsg.isFromYou && latestMsg.bodyText.isNotBlank()) {
                        openAiRepository.generateRepliesIfNeeded(conv.groupKey, conv.title, conv.messages)
                    }
                }
            }
        }
    }

    /**
     * Build conversation cards directly from the conversations table.
     * ConversationEntity already contains pre-grouped, deduplicated ChatMessage lists.
     */
    private fun buildConversationCards(
        conversations: List<ConversationEntity>,
        contacts: List<ContactEntity>
    ): List<Conversation> {
        val contactsMap = contacts.associateBy { it.id }
        return conversations.mapNotNull { entity ->
            // Skip conversations with no messages
            if (entity.messages.isEmpty()) return@mapNotNull null

            // Resolve contact from contactId in O(1) time
            val contact = entity.contactId?.let { id -> contactsMap[id] }

            val canReplyAny = canReply(entity.conversationKey, entity.latestNotificationKey)

            Conversation(
                groupKey = entity.conversationKey,
                title = entity.title,
                contact = contact,
                senderIdentifier = EmailUtils.cleanEmail(entity.senderIdentifier) ?: entity.senderIdentifier,
                receiverIdentifier = EmailUtils.cleanEmail(entity.receiverIdentifier) ?: entity.receiverIdentifier,
                packageName = entity.packageName,
                messages = entity.messages,
                latestTimestamp = entity.latestTimestamp,
                canReply = canReplyAny,
                latestNotificationKey = entity.latestNotificationKey ?: ""
            )
        }.sortedByDescending { it.latestTimestamp }
    }

    // =============================
    // Chat Tag & Contact Matching (kept for filteredNotifications / Debug tab)
    // =============================

    private fun extractChatTag(notification: NotificationEntity): String? {
        if (isEmailApp(notification.packageName)) {
            val email = notification.senderEmail?.removePrefix("mailto:")?.trim()
            if (!email.isNullOrBlank()) return "email_$email"
            if (!notification.title.isNullOrBlank()) return "sender_${notification.title.trim()}"
        }

        if (!notification.rawJson.isNullOrBlank()) {
            try {
                val obj = JsonParser.parseString(notification.rawJson).asJsonObject
                if (obj.has("tag") && !obj.get("tag").isJsonNull) {
                    val tag = obj.get("tag").asString
                    if (!tag.isNullOrBlank() && tag != "null") return tag
                }
            } catch (_: Exception) {}
        }

        val keyParts = notification.notificationKey.split('|')
        if (keyParts.size >= 4) {
            val tag = keyParts[3]
            if (tag.isNotBlank() && tag != "null") return tag
        }

        return null
    }

    private fun isNotificationFromContact(notification: NotificationEntity, contact: ContactEntity): Boolean {
        if (notification.contactLookupUri != null && contact.lookupKey != null &&
            notification.contactLookupUri.contains(contact.lookupKey)) {
            return true
        }

        val title = notification.title?.trim() ?: ""
        val senderEmail = notification.senderEmail?.removePrefix("mailto:")?.trim()

        if (contact.emails.any { email ->
            email.isNotBlank() && (
                senderEmail?.equals(email.trim(), ignoreCase = true) == true ||
                title.equals(email.trim(), ignoreCase = true)
            )
        }) return true

        val titleDigits = title.filter { it.isDigit() }
        val senderDigits = senderEmail?.filter { it.isDigit() } ?: ""
        if (contact.phoneNumbers.any { phone ->
            val digits = phone.filter { it.isDigit() }.takeLast(10)
            digits.length >= 7 && (titleDigits.contains(digits) || senderDigits.contains(digits))
        }) return true

        if (title.isNotBlank()) {
            val displayName = contact.displayName.trim()
            val officialName = contact.officialName?.trim() ?: ""

            if (title.equals(displayName, ignoreCase = true)) return true
            if (officialName.isNotBlank() && title.equals(officialName, ignoreCase = true)) return true

            if (displayName.length >= 3) {
                if (title.startsWith("$displayName:", ignoreCase = true)) return true
                if (title.startsWith("$displayName (", ignoreCase = true)) return true
            }
            if (officialName.length >= 3) {
                if (title.startsWith("$officialName:", ignoreCase = true)) return true
                if (title.startsWith("$officialName (", ignoreCase = true)) return true
            }
        }

        return false
    }

    // =============================
    // Reply Actions (1-Stage)
    // =============================

    fun isEmailAccountConnected(packageName: String, accountEmail: String? = null): Boolean {
        return emailReplyDispatcher.canReply(packageName, accountEmail)
    }

    fun canReply(groupKey: String, notificationKey: String? = null): Boolean {
        if (isEmailApp(groupKey) && emailAccountStore.isGmailConnected()) return true
        return TriqxNotificationListenerService.instance?.canReply(groupKey, notificationKey) == true
    }

    fun sendEmailReply(
        conversation: Conversation,
        replyText: String,
        onResult: ((Boolean, String?) -> Unit)? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val emailFromKey = if (conversation.groupKey.contains("_email_")) conversation.groupKey.substringAfterLast("_email_").substringAfterLast("_").trim() else null
            val cleanEmail = EmailUtils.cleanEmail(conversation.senderIdentifier)
                ?: EmailUtils.cleanEmail(conversation.contact?.primaryEmail)
                ?: EmailUtils.cleanEmail(emailFromKey)

            if (cleanEmail.isNullOrBlank()) {
                withContext(Dispatchers.Main) {
                    onResult?.invoke(false, "No recipient email address found")
                }
                return@launch
            }

            val subject = conversation.messages.firstOrNull { !it.subText.isNullOrBlank() }?.subText ?: conversation.title
            val cleanReceiver = EmailUtils.cleanEmail(conversation.receiverIdentifier)

            val result = emailReplyDispatcher.sendReply(
                recipientEmail = cleanEmail,
                subject = subject,
                replyText = replyText,
                packageName = conversation.packageName,
                accountEmail = cleanReceiver
            )

            if (result.isSuccess) {
                recordOutgoingReply(
                    packageName = conversation.packageName,
                    replyText = replyText,
                    notificationKey = conversation.latestNotificationKey,
                    contact = conversation.contact,
                    senderIdentifier = cleanEmail,
                    receiverIdentifier = conversation.receiverIdentifier,
                    groupKey = conversation.groupKey
                )
                withContext(Dispatchers.Main) {
                    onResult?.invoke(true, null)
                }
            } else {
                withContext(Dispatchers.Main) {
                    onResult?.invoke(false, result.exceptionOrNull()?.message)
                }
            }
        }
    }

    fun replyToNotification(
        key: String,
        replyMessage: String = "Replied you using Triqx App",
        packageName: String? = null,
        contact: ContactEntity? = null,
        senderIdentifier: String? = null,
        chatTag: String? = null,
        groupKey: String? = null
    ): Boolean {
        val targetKey = groupKey ?: if (chatTag != null && packageName != null) buildGroupKey(packageName, chatTag) else key

        val success = TriqxNotificationListenerService.instance?.sendReply(
            conversationKey = targetKey,
            message = replyMessage,
            notificationKey = key
        ) == true

        if (success) {
            recordOutgoingReply(packageName ?: "com.triqx", replyMessage, key, contact, senderIdentifier, groupKey = targetKey)
        }
        return success
    }

    fun recordOutgoingReply(
        packageName: String,
        replyText: String,
        notificationKey: String,
        contact: ContactEntity? = null,
        senderIdentifier: String? = null,
        receiverIdentifier: String? = null,
        groupKey: String? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = notificationDao.getLatestMatching(packageName, "You", replyText)
            if (existing != null && (System.currentTimeMillis() - existing.timestamp < 15_000L)) return@launch

            val timestamp = System.currentTimeMillis()

            notificationDao.insertNotification(
                NotificationEntity(
                    packageName = packageName,
                    title = "You",
                    text = replyText,
                    senderEmail = senderIdentifier ?: contact?.primaryPhone ?: contact?.primaryEmail,
                    contactLookupUri = contact?.lookupKey?.let { "content://com.android.contacts/lookup/$it" },
                    rawJson = """{"type":"outgoing_reply","text":"$replyText","contactId":${contact?.id},"senderIdentifier":"${senderIdentifier ?: ""}","receiverIdentifier":"${receiverIdentifier ?: ""}"}""",
                    notificationKey = notificationKey,
                    timestamp = timestamp
                )
            )

            if (groupKey != null) {
                val current = conversationDao.getConversationByKey(groupKey).firstOrNull()
                val parentSubject = current?.messages?.firstOrNull { !it.subText.isNullOrBlank() }?.subText
                val outgoingSubText = if (isEmailApp(packageName) && !parentSubject.isNullOrBlank()) {
                    if (parentSubject.startsWith("Re:", ignoreCase = true)) parentSubject else "Re: $parentSubject"
                } else null

                val chatMessage = ChatMessage(
                    senderName = "You",
                    subText = outgoingSubText,
                    bodyText = replyText,
                    timestamp = timestamp,
                    isFromYou = true
                )
                val updatedMessages = ((current?.messages ?: emptyList()) + chatMessage)
                    .sortedByDescending { it.timestamp }

                // Preserve existing conversation title (never overwrite with "You")
                val cleanTitle = current?.title ?: (contact?.displayName ?: packageName)

                conversationDao.insertOrUpdate(
                    ConversationEntity(
                        conversationKey = groupKey,
                        packageName = packageName,
                        contactId = contact?.id ?: current?.contactId,
                        title = cleanTitle,
                        senderIdentifier = EmailUtils.cleanEmail(senderIdentifier) ?: current?.senderIdentifier,
                        receiverIdentifier = EmailUtils.cleanEmail(receiverIdentifier) ?: current?.receiverIdentifier,
                        messages = updatedMessages,
                        latestTimestamp = timestamp,
                        latestNotificationKey = notificationKey
                    )
                )

                Log.i("TriqxReply", "===> [CONVERSATION TABLE OUTGOING] key='$groupKey', title='$cleanTitle', replyText='$replyText', totalMsgs=${updatedMessages.size}")
            }
        }
    }

    // =============================
    // Dismiss & Clear
    // =============================

    suspend fun getPriorityContactId(uri: String): Int? {
        return contactDao.getContactByLookupUri(uri).firstOrNull()?.id
    }

    fun dismissNotification(notification: NotificationEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            TriqxNotificationListenerService.instance?.dismissNotification(notification.notificationKey)
            notificationDao.deleteNotificationById(notification.id)
        }
    }

    fun dismissGroup(group: Conversation) {
        viewModelScope.launch(Dispatchers.IO) {
            // Dismiss the Android notification
            if (group.latestNotificationKey.isNotBlank()) {
                TriqxNotificationListenerService.instance?.dismissNotification(group.latestNotificationKey)
            }
            // Delete conversation from conversations table
            conversationDao.deleteByKey(group.groupKey)
        }
    }

    fun clearAllPriorityNotifications() {
        viewModelScope.launch(Dispatchers.IO) {
            val currentGroups = groupedPriorityNotifications.value
            val allNotificationKeys = currentGroups.map { it.latestNotificationKey }.filter { it.isNotBlank() }
            TriqxNotificationListenerService.instance?.dismissNotifications(allNotificationKeys)
            conversationDao.clearAll()
        }
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            TriqxNotificationListenerService.instance?.dismissAllActiveNotifications()
            notificationDao.clearAll()
            conversationDao.clearAll()
        }
    }

    // =============================
    // AI Reply Regeneration
    // =============================

    fun regenerateRepliesForGroup(group: Conversation) {
        openAiRepository.regenerateReplies(group.groupKey, group.title, group.messages)
    }

    // =============================
    // System Check
    // =============================

    fun isNotificationServiceEnabled(): Boolean {
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(context.packageName)
    }

    // =============================
    // Utilities
    // =============================

    /** Single Universal Key: "${packageName}_$chatTag" */
    private fun buildGroupKey(packageName: String, chatTag: String): String {
        return "${packageName}_$chatTag"
    }

    private fun extractContactIdFromJson(rawJson: String?): Int? {
        return try {
            val obj = JsonParser.parseString(rawJson ?: "{}").asJsonObject
            if (obj.has("contactId") && !obj.get("contactId").isJsonNull) obj.get("contactId").asInt else null
        } catch (_: Exception) { null }
    }

    private fun isEmailApp(packageName: String): Boolean {
        return packageName.contains("gm") || packageName.contains("email")
            || packageName.contains("outlook") || packageName.contains("mail")
    }
}
