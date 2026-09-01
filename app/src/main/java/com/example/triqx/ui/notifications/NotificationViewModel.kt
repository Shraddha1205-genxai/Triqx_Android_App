package com.example.triqx.ui.notifications

import android.content.Context
import android.provider.Settings
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
import com.google.gson.JsonParser
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A conversation group card shown on the Home screen.
 * Groups notifications by universal conversationKey (packageName + chatTag).
 */
data class ClubbedNotificationGroup(
    val groupKey: String,
    val title: String,                           // Clean computed title (Group Name or Sender Name, never "You")
    val contact: ContactEntity?,
    val specificIdentifier: String?,
    val packageName: String,
    val notifications: List<NotificationEntity>, // Sorted newest first, deduplicated
    val latestTimestamp: Long,
    val canReply: Boolean,
    val latestNotificationKey: String
)

@HiltViewModel
class NotificationViewModel @Inject constructor(
    private val notificationDao: NotificationDao,
    private val conversationDao: ConversationDao,
    private val contactDao: ContactDao,
    private val appDao: AppDao,
    private val openAiRepository: OpenAiRepository,
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
    // Grouped Priority Notifications (Conversation Cards)
    // =============================

    val groupedPriorityNotifications: StateFlow<List<ClubbedNotificationGroup>> = combine(
        notificationDao.getAllNotifications(),
        contactDao.getAllContacts(),
        appDao.getAllImportantApps()
    ) { notifications, contacts, apps ->
        buildConversationGroups(notifications, contacts, apps)
    }
    .flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Build conversation cards from raw notifications.
     * 3-pass pipeline: Group incoming -> Attach outgoing -> Deduplicate & sort
     */
    private fun buildConversationGroups(
        notifications: List<NotificationEntity>,
        contacts: List<ContactEntity>,
        apps: List<com.example.triqx.data.local.AppEntity>
    ): List<ClubbedNotificationGroup> {

        val importantPackages = apps.map { it.packageName }.toSet()

        val incoming = notifications.filter { !it.title.equals("You", ignoreCase = true) }
        val outgoing = notifications.filter { it.title.equals("You", ignoreCase = true) }

        val rawGroups = mutableMapOf<String, MutableList<NotificationEntity>>()
        val groupContactMap = mutableMapOf<String, ContactEntity?>()
        val groupPackageMap = mutableMapOf<String, String>()
        val groupIdentifierMap = mutableMapOf<String, String?>()

        // --- PASS 1: Group incoming messages ---
        for (notif in incoming) {
            val isImportantApp = importantPackages.contains(notif.packageName)
            val matchedContact = contacts.find { isNotificationFromContact(notif, it) }

            if (!isImportantApp && matchedContact == null) continue

            val chatTag = extractChatTag(notif) ?: notif.title?.trim() ?: "default"
            val groupKey = buildGroupKey(notif.packageName, chatTag)

            if (!rawGroups.containsKey(groupKey)) {
                rawGroups[groupKey] = mutableListOf()
                groupContactMap[groupKey] = matchedContact
                groupPackageMap[groupKey] = notif.packageName
                groupIdentifierMap[groupKey] = notif.senderEmail
                    ?: matchedContact?.primaryEmail
                    ?: matchedContact?.primaryPhone
                    ?: matchedContact?.displayName
                    ?: notif.title
            }
            rawGroups[groupKey]?.add(notif)
        }

        // --- PASS 2: Attach outgoing "You" replies to their conversation ---
        for (notif in outgoing) {
            val contactId = extractContactIdFromJson(notif.rawJson)
            val matchedContact = if (contactId != null) {
                contacts.find { it.id == contactId }
            } else {
                contacts.find { isNotificationFromContact(notif, it) }
            }

            val isImportantApp = importantPackages.contains(notif.packageName)
            if (!isImportantApp && matchedContact == null) continue

            val chatTag = extractChatTag(notif)
            val exactKey = if (chatTag != null) buildGroupKey(notif.packageName, chatTag) else null

            val targetKey = exactKey?.takeIf { rawGroups.containsKey(it) }
                ?: rawGroups.keys.firstOrNull { key ->
                    val sameContact = matchedContact != null && groupContactMap[key]?.id == matchedContact.id
                    val samePackage = groupPackageMap[key] == notif.packageName
                    (sameContact && samePackage) || (matchedContact == null && samePackage)
                }

            if (targetKey != null) {
                rawGroups[targetKey]?.add(notif)
            }
        }

        // --- PASS 3: Deduplicate, sort, and build final groups ---
        return rawGroups.mapNotNull { (groupKey, notifList) ->
            buildSingleGroup(
                groupKey,
                notifList,
                groupContactMap[groupKey],
                groupPackageMap[groupKey] ?: notifList.first().packageName,
                groupIdentifierMap[groupKey]
            )
        }.sortedByDescending { it.latestTimestamp }
    }

    /** Build a single ClubbedNotificationGroup from a list of notifications. */
    private fun buildSingleGroup(
        groupKey: String,
        notifList: List<NotificationEntity>,
        contact: ContactEntity?,
        pkg: String,
        identifier: String?
    ): ClubbedNotificationGroup? {
        if (notifList.isEmpty()) return null

        val sorted = notifList.sortedByDescending { it.timestamp }

        // Remove duplicate messages (same sender + same text within 2 hours)
        val deduped = mutableListOf<NotificationEntity>()
        for (notif in sorted) {
            val isDupe = deduped.any { existing ->
                val sameSender = existing.title?.trim().equals(notif.title?.trim(), ignoreCase = true)
                val sameText = existing.text?.trim().equals(notif.text?.trim(), ignoreCase = true)
                val closeInTime = kotlin.math.abs(existing.timestamp - notif.timestamp) < 2 * 60 * 60 * 1000L
                val isYouDupe = sameSender && sameText && notif.title.equals("You", ignoreCase = true)
                (sameSender && sameText && closeInTime) || isYouDupe
            }
            if (!isDupe) deduped.add(notif)
        }

        if (deduped.isEmpty()) return null

        // Skip phantom cards that only have outgoing replies (no incoming messages)
        if (deduped.none { !it.title.equals("You", ignoreCase = true) }) return null

        val latest = deduped.first()
        val latestIncoming = deduped.firstOrNull { !it.title.equals("You", ignoreCase = true) } ?: latest

        // Resolve conversation title directly here during grouping (never "You")
        val conversationTitle = contact?.displayName?.ifBlank { null }
            ?: latestIncoming.title?.takeIf { !it.equals("You", ignoreCase = true) }
            ?: identifier
            ?: pkg

        val canReplyAny = canReply(groupKey, latest.notificationKey)
        val replyableKey = deduped.firstOrNull { canReply(groupKey, it.notificationKey) }
            ?.notificationKey ?: latest.notificationKey

        // Trigger AI reply generation if latest message is incoming
        val isLatestFromYou = latest.title.equals("You", ignoreCase = true)
            || latest.text?.startsWith("Replied you using", ignoreCase = true) == true
        if (!isLatestFromYou && !latest.text.isNullOrBlank()) {
            openAiRepository.generateRepliesIfNeeded(groupKey, conversationTitle, deduped)
        }

        return ClubbedNotificationGroup(
            groupKey = groupKey,
            title = conversationTitle,
            contact = contact,
            specificIdentifier = identifier,
            packageName = pkg,
            notifications = deduped,
            latestTimestamp = latest.timestamp,
            canReply = canReplyAny,
            latestNotificationKey = replyableKey
        )
    }

    // =============================
    // Chat Tag & Contact Matching
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

    fun canReply(groupKey: String, notificationKey: String? = null): Boolean {
        return TriqxNotificationListenerService.instance?.canReply(groupKey, notificationKey) == true
    }

    fun replyToNotification(
        key: String,
        replyMessage: String = "Replied you using Triqx App",
        packageName: String? = null,
        contact: ContactEntity? = null,
        specificIdentifier: String? = null,
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
            recordOutgoingReply(packageName ?: "com.triqx", replyMessage, key, contact, specificIdentifier, targetKey)
        }
        return success
    }

    fun recordOutgoingReply(
        packageName: String,
        replyText: String,
        notificationKey: String,
        contact: ContactEntity? = null,
        specificIdentifier: String? = null,
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
                    senderEmail = specificIdentifier ?: contact?.primaryPhone ?: contact?.primaryEmail,
                    contactLookupUri = contact?.lookupKey?.let { "content://com.android.contacts/lookup/$it" },
                    rawJson = """{"type":"outgoing_reply","text":"$replyText","contactId":${contact?.id},"specificIdentifier":"${specificIdentifier ?: ""}"}""",
                    notificationKey = notificationKey,
                    timestamp = timestamp
                )
            )

            if (groupKey != null) {
                val current = conversationDao.getConversationByKey(groupKey).firstOrNull()
                val chatMessage = ChatMessage(
                    senderName = "You",
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
                        specificIdentifier = specificIdentifier ?: current?.specificIdentifier,
                        messages = updatedMessages,
                        latestTimestamp = timestamp,
                        latestNotificationKey = notificationKey
                    )
                )
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

    fun dismissGroup(group: ClubbedNotificationGroup) {
        viewModelScope.launch(Dispatchers.IO) {
            TriqxNotificationListenerService.instance?.dismissNotifications(group.notifications.map { it.notificationKey })
            notificationDao.deleteNotificationsByIds(group.notifications.map { it.id })
            conversationDao.deleteByKey(group.groupKey)
        }
    }

    fun clearAllPriorityNotifications() {
        viewModelScope.launch(Dispatchers.IO) {
            val currentGroups = groupedPriorityNotifications.value
            val allKeys = currentGroups.flatMap { it.notifications.map { n -> n.notificationKey } }
            val allIds = currentGroups.flatMap { it.notifications.map { n -> n.id } }
            TriqxNotificationListenerService.instance?.dismissNotifications(allKeys)
            notificationDao.deleteNotificationsByIds(allIds)
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

    fun regenerateRepliesForGroup(group: ClubbedNotificationGroup) {
        openAiRepository.regenerateReplies(group.groupKey, group.title, group.notifications)
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
