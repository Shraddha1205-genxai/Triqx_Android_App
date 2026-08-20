package com.example.triqx.ui.notifications

import android.content.Context
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.triqx.data.local.AppDao
import com.example.triqx.data.local.ContactDao
import com.example.triqx.data.local.ContactEntity
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

data class ClubbedNotificationGroup(
    val groupKey: String,
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
    private val contactDao: ContactDao,
    private val appDao: AppDao,
    private val openAiRepository: OpenAiRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    // All captured raw notifications (untouched for Debug view)
    val allNotifications = notificationDao.getAllNotifications()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notifications = allNotifications

    // AI generated replies & loading state per groupKey
    val cachedReplies: StateFlow<Map<String, List<String>>> = openAiRepository.cachedReplies
    val loadingGroups: StateFlow<Map<String, Boolean>> = openAiRepository.loadingGroups

    // Flat filtered notifications list: (Important App || Priority Contact)
    val filteredNotifications: StateFlow<List<NotificationEntity>> = combine(
        notificationDao.getAllNotifications(),
        contactDao.getAllContacts(),
        appDao.getAllImportantApps()
    ) { notificationsList, contactsList, appsList ->
        val importantPackageSet = appsList.map { it.packageName }.toSet()

        notificationsList.filter { notification ->
            val isFromImportantApp = importantPackageSet.contains(notification.packageName)
            val isFromPriorityContact = contactsList.any { contact ->
                isNotificationFromContact(notification, contact)
            }
            isFromImportantApp || isFromPriorityContact
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Grouped notifications stream isolated by (Contact / App + Chat Tag/Number)
    // OR FILTER: (Important App || Priority Contact)
    val groupedPriorityNotifications: StateFlow<List<ClubbedNotificationGroup>> = combine(
        notificationDao.getAllNotifications(),
        contactDao.getAllContacts(),
        appDao.getAllImportantApps()
    ) { notificationsList, contactsList, appsList ->
        val importantPackageSet = appsList.map { it.packageName }.toSet()

        val rawGroups = mutableMapOf<String, MutableList<NotificationEntity>>()
        val groupContactMap = mutableMapOf<String, ContactEntity?>()
        val groupPackageMap = mutableMapOf<String, String>()
        val groupIdentifierMap = mutableMapOf<String, String?>()

        val incomingNotifications = notificationsList.filter { !it.title.equals("You", ignoreCase = true) }
        val outgoingReplies = notificationsList.filter { it.title.equals("You", ignoreCase = true) }

        // --- PASS 1: Group incoming messages by (Contact / App + Chat Tag) ---
        for (notification in incomingNotifications) {
            val isFromImportantApp = importantPackageSet.contains(notification.packageName)
            val matchedContact = contactsList.find { isNotificationFromContact(notification, it) }
            val isFromPriorityContact = (matchedContact != null)

            // Must satisfy: (Important App || Priority Contact)
            if (!isFromImportantApp && !isFromPriorityContact) continue

            val chatTag = extractChatTag(notification) ?: notification.title?.trim() ?: "default"

            val groupKey = if (matchedContact != null) {
                "contact_${matchedContact.id}_pkg_${notification.packageName}_tag_$chatTag"
            } else {
                "app_${notification.packageName}_tag_$chatTag"
            }

            if (!rawGroups.containsKey(groupKey)) {
                rawGroups[groupKey] = mutableListOf()
                groupContactMap[groupKey] = matchedContact
                groupPackageMap[groupKey] = notification.packageName
                groupIdentifierMap[groupKey] = matchedContact?.displayName ?: notification.title
            }
            rawGroups[groupKey]?.add(notification)
        }

        // --- PASS 2: Attach outgoing 'You' replies to the appropriate conversation thread ---
        for (notification in outgoingReplies) {
            val contactIdFromRaw = try {
                val obj = JsonParser.parseString(notification.rawJson ?: "{}").asJsonObject
                if (obj.has("contactId") && !obj.get("contactId").isJsonNull) obj.get("contactId").asInt else null
            } catch (e: Exception) { null }

            val matchedContact = if (contactIdFromRaw != null) {
                contactsList.find { it.id == contactIdFromRaw }
            } else {
                contactsList.find { isNotificationFromContact(notification, it) }
            }

            val isFromImportantApp = importantPackageSet.contains(notification.packageName)
            val isFromPriorityContact = (matchedContact != null)
            if (!isFromImportantApp && !isFromPriorityContact) continue

            val chatTag = extractChatTag(notification)
            val exactKey = if (matchedContact != null && chatTag != null) {
                "contact_${matchedContact.id}_pkg_${notification.packageName}_tag_$chatTag"
            } else if (chatTag != null) {
                "app_${notification.packageName}_tag_$chatTag"
            } else null

            // Join exact tag group, or active thread for this contact / package
            val targetGroupKey = exactKey?.takeIf { rawGroups.containsKey(it) }
                ?: rawGroups.keys.firstOrNull { key ->
                    (matchedContact != null && groupContactMap[key]?.id == matchedContact.id && groupPackageMap[key] == notification.packageName) ||
                    (matchedContact == null && groupPackageMap[key] == notification.packageName)
                }

            if (targetGroupKey != null) {
                rawGroups[targetGroupKey]?.add(notification)
            }
        }

        // --- PASS 3: Deduplicate and sort ---
        val resultGroups = rawGroups.mapNotNull { (groupKey, notifList) ->
            if (notifList.isEmpty()) return@mapNotNull null
            val contact = groupContactMap[groupKey]

            // Sort newest first
            val sortedList = notifList.sortedByDescending { it.timestamp }

            // Deduplicate repeated identical messages (same sender + text)
            val deduplicatedList = mutableListOf<NotificationEntity>()
            for (notif in sortedList) {
                val isDuplicate = deduplicatedList.any { existing ->
                    val sameSender = existing.title?.trim()?.equals(notif.title?.trim(), ignoreCase = true) == true
                    val sameText = existing.text?.trim()?.equals(notif.text?.trim(), ignoreCase = true) == true
                    val closeInTime = kotlin.math.abs(existing.timestamp - notif.timestamp) < 2 * 60 * 60 * 1000L
                    (sameSender && sameText && closeInTime) || (sameSender && sameText && notif.title.equals("You", ignoreCase = true))
                }
                if (!isDuplicate) {
                    deduplicatedList.add(notif)
                }
            }

            if (deduplicatedList.isEmpty()) return@mapNotNull null

            // Suppress phantom cards that only contain 'You' outgoing replies without incoming messages
            val hasIncomingMessage = deduplicatedList.any { !it.title.equals("You", ignoreCase = true) }
            if (!hasIncomingMessage) return@mapNotNull null

            val latest = deduplicatedList.first()
            val contactOrTitle = contact?.displayName?.ifBlank { null }
                ?: groupIdentifierMap[groupKey]
                ?: (latest.title ?: latest.packageName)
            val pkg = groupPackageMap[groupKey] ?: latest.packageName

            val canReplyAny = deduplicatedList.any { canReply(it.notificationKey, pkg) } || canReply(latest.notificationKey, pkg)
            val replyableKey = deduplicatedList.firstOrNull { canReply(it.notificationKey, pkg) }?.notificationKey ?: latest.notificationKey

            val isLatestFromYou = latest.title.equals("You", ignoreCase = true) ||
                                  latest.text?.startsWith("Replied you using", ignoreCase = true) == true

            // Trigger Smart Replies if latest is genuine incoming message
            val shouldGenerateAiReplies = !isLatestFromYou && !latest.text.isNullOrBlank()

            if (shouldGenerateAiReplies) {
                openAiRepository.generateRepliesIfNeeded(groupKey, contactOrTitle, deduplicatedList)
            }

            ClubbedNotificationGroup(
                groupKey = groupKey,
                contact = contact,
                specificIdentifier = groupIdentifierMap[groupKey],
                packageName = pkg,
                notifications = deduplicatedList,
                latestTimestamp = latest.timestamp,
                canReply = canReplyAny,
                latestNotificationKey = replyableKey
            )
        }.sortedByDescending { it.latestTimestamp }

        resultGroups
    }
    .flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun extractDigits(str: String?): String = str?.filter { it.isDigit() } ?: ""

    private fun extractChatTag(notification: NotificationEntity): String? {
        if (!notification.rawJson.isNullOrBlank()) {
            try {
                val obj = JsonParser.parseString(notification.rawJson).asJsonObject
                if (obj.has("tag") && !obj.get("tag").isJsonNull) {
                    val tag = obj.get("tag").asString
                    if (!tag.isNullOrBlank() && tag != "null") return tag
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
        val keyParts = notification.notificationKey.split('|')
        if (keyParts.size >= 4) {
            val tag = keyParts[3]
            if (tag.isNotBlank() && tag != "null") {
                return tag
            }
        }
        return null
    }
    private fun isNotificationFromContact(notification: NotificationEntity, contact: ContactEntity): Boolean {
        val notifDigits = extractDigits(notification.title) + "_" + 
                          extractDigits(notification.text) + "_" + 
                          extractDigits(notification.notificationKey) + "_" + 
                          extractDigits(notification.rawJson)

        val phoneMatch = contact.phoneNumbers.any { phone ->
            val phoneDigits = extractDigits(phone).takeLast(10)
            phoneDigits.length >= 7 && notifDigits.contains(phoneDigits)
        }

        val emailMatch = contact.emails.any { email ->
            email.isNotBlank() && (
                (notification.title?.contains(email, ignoreCase = true) == true) || 
                (notification.text?.contains(email, ignoreCase = true) == true) ||
                (notification.senderEmail?.contains(email, ignoreCase = true) == true)
            )
        }

        val uriMatch = notification.contactLookupUri != null && contact.lookupKey != null &&
                       notification.contactLookupUri.contains(contact.lookupKey)
        val nameMatch = (notification.title?.contains(contact.displayName, ignoreCase = true) == true) ||
                        (contact.officialName?.let { notification.title?.contains(it, ignoreCase = true) } == true) ||
                        (notification.text?.contains(contact.displayName, ignoreCase = true) == true) ||
                        (contact.officialName?.let { notification.text?.contains(it, ignoreCase = true) } == true)

        return nameMatch || phoneMatch || emailMatch || uriMatch
    }

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
            val keys = group.notifications.map { it.notificationKey }
            val ids = group.notifications.map { it.id }
            TriqxNotificationListenerService.instance?.dismissNotifications(keys)
            notificationDao.deleteNotificationsByIds(ids)
        }
    }

    fun clearAllPriorityNotifications() {
        viewModelScope.launch(Dispatchers.IO) {
            val currentGroups = groupedPriorityNotifications.value
            val allKeys = currentGroups.flatMap { it.notifications.map { n -> n.notificationKey } }
            val allIds = currentGroups.flatMap { it.notifications.map { n -> n.id } }

            TriqxNotificationListenerService.instance?.dismissNotifications(allKeys)
            notificationDao.deleteNotificationsByIds(allIds)
        }
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            TriqxNotificationListenerService.instance?.dismissAllActiveNotifications()
            notificationDao.clearAll()
        }
    }

    fun isNotificationServiceEnabled(): Boolean {
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(context.packageName)
    }

    fun canReply(key: String, packageName: String? = null): Boolean {
        return TriqxNotificationListenerService.instance?.canReply(key, packageName) == true
    }

    fun replyToNotification(
        key: String, 
        replyMessage: String = "Replied you using Triqx App", 
        packageName: String? = null,
        contact: ContactEntity? = null,
        specificIdentifier: String? = null
    ): Boolean {
        val success = TriqxNotificationListenerService.instance?.sendReply(key, replyMessage, packageName) == true
        if (success) {
            recordOutgoingReply(packageName ?: "com.triqx", replyMessage, key, contact, specificIdentifier)
        }
        return success
    }

    fun recordOutgoingReply(
        packageName: String, 
        replyText: String, 
        notificationKey: String,
        contact: ContactEntity? = null,
        specificIdentifier: String? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = notificationDao.getLatestMatching(packageName, "You", replyText)
            if (existing != null && (System.currentTimeMillis() - existing.timestamp < 15_000L)) {
                // Prevent duplicate insertion within 15 seconds
                return@launch
            }
            notificationDao.insertNotification(
                NotificationEntity(
                    packageName = packageName,
                    title = "You",
                    text = replyText,
                    senderEmail = specificIdentifier ?: contact?.primaryPhone ?: contact?.primaryEmail,
                    contactLookupUri = contact?.lookupKey?.let { "content://com.android.contacts/lookup/$it" },
                    rawJson = "{\"type\": \"outgoing_reply\", \"text\": \"$replyText\", \"contactId\": ${contact?.id}, \"specificIdentifier\": \"${specificIdentifier ?: ""}\"}",
                    notificationKey = notificationKey,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    fun regenerateRepliesForGroup(group: ClubbedNotificationGroup) {
        val contactOrTitle = group.contact?.displayName ?: (group.notifications.firstOrNull()?.title ?: group.packageName)
        openAiRepository.regenerateReplies(
            groupKey = group.groupKey,
            contactOrTitle = contactOrTitle,
            messages = group.notifications
        )
    }
}
