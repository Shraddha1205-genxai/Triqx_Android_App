package com.example.triqx.service

import android.app.Notification
import android.app.Person
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Base64
import android.util.Log
import com.example.triqx.data.local.AppDao
import com.example.triqx.data.local.ContactDao
import com.example.triqx.data.local.NotificationDao
import com.example.triqx.data.local.NotificationEntity
import com.google.gson.GsonBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class TriqxNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "TriqxReply"
        var instance: TriqxNotificationListenerService? = null
            private set
    }

    @Inject
    lateinit var notificationDao: NotificationDao

    @Inject
    lateinit var appDao: AppDao

    @Inject
    lateinit var contactDao: ContactDao

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = GsonBuilder()
        .setPrettyPrinting()
        .serializeNulls()
        .create()

    private val actionPrefs by lazy {
        getSharedPreferences("triqx_reply_action_prefs", Context.MODE_PRIVATE)
    }

    // In-memory cache for reply actions (Last 150 notifications)
    private val replyCache = object : LinkedHashMap<String, Notification.Action>(150, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Notification.Action>?): Boolean {
            return size > 150
        }
    }

    // Package-level reply action cache for quick fallbacks
    private val packageReplyCache = mutableMapOf<String, Notification.Action>()

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i(TAG, "TriqxNotificationListenerService created. Loading persistent actions from disk...")
        loadAllActionsFromDisk()
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
        Log.i(TAG, "TriqxNotificationListenerService destroyed")
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        Log.i(TAG, "Notification Listener Connected! Indexing active notifications...")
        try {
            activeNotifications?.forEach { sbn ->
                extractReplyAction(sbn.notification)?.let { action ->
                    replyCache[sbn.key] = action
                    packageReplyCache[sbn.packageName] = action
                    saveActionToDisk("key_${sbn.key}", action)
                    saveActionToDisk("pkg_${sbn.packageName}", action)
                    Log.d(TAG, "Indexed & persisted reply action for ${sbn.packageName} (${sbn.key})")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error indexing active notifications: ${e.message}", e)
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "Notification Listener Disconnected")
    }

    private fun saveActionToDisk(key: String, action: Notification.Action) {
        try {
            val parcel = Parcel.obtain()
            action.writeToParcel(parcel, 0)
            val bytes = parcel.marshall()
            parcel.recycle()
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            actionPrefs.edit().putString(key, base64).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving action to disk for key '$key': ${e.message}")
        }
    }

    private fun loadActionFromDisk(key: String): Notification.Action? {
        try {
            val base64 = actionPrefs.getString(key, null) ?: return null
            val bytes = Base64.decode(base64, Base64.NO_WRAP)
            val parcel = Parcel.obtain()
            parcel.unmarshall(bytes, 0, bytes.size)
            parcel.setDataPosition(0)
            val action = Notification.Action.CREATOR.createFromParcel(parcel)
            parcel.recycle()
            return action
        } catch (e: Exception) {
            Log.e(TAG, "Error loading action from disk for key '$key': ${e.message}")
            return null
        }
    }

    private fun loadAllActionsFromDisk() {
        try {
            actionPrefs.all.forEach { (key, value) ->
                if (value is String) {
                    try {
                        val bytes = Base64.decode(value, Base64.NO_WRAP)
                        val parcel = Parcel.obtain()
                        parcel.unmarshall(bytes, 0, bytes.size)
                        parcel.setDataPosition(0)
                        val action = Notification.Action.CREATOR.createFromParcel(parcel)
                        parcel.recycle()

                        if (key.startsWith("key_")) {
                            replyCache[key.removePrefix("key_")] = action
                        } else if (key.startsWith("pkg_")) {
                            packageReplyCache[key.removePrefix("pkg_")] = action
                        }
                    } catch (e: Exception) {
                        // Skip corrupted entry
                    }
                }
            }
            Log.i(TAG, "Loaded ${replyCache.size} notification actions and ${packageReplyCache.size} package actions from persistent disk cache")
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring persistent actions: ${e.message}")
        }
    }

    fun canReply(notificationKey: String, packageName: String? = null): Boolean {
        if (replyCache.containsKey(notificationKey)) return true
        if (packageName != null && packageReplyCache.containsKey(packageName)) return true
        if (loadActionFromDisk("key_$notificationKey") != null) return true
        if (packageName != null && loadActionFromDisk("pkg_$packageName") != null) return true
        if (activeNotifications?.any { it.key == notificationKey && hasReplyAction(it.notification) } == true) return true
        if (packageName != null && activeNotifications?.any { it.packageName == packageName && hasReplyAction(it.notification) } == true) return true
        return false
    }

    private fun hasReplyAction(notification: Notification): Boolean {
        return extractReplyAction(notification) != null
    }

    fun extractReplyAction(notification: Notification): Notification.Action? {
        // 1. Direct notification actions
        notification.actions?.forEach { action ->
            if (action.remoteInputs != null && action.remoteInputs.isNotEmpty()) {
                return action
            }
        }

        // 2. WearableExtender actions (WhatsApp, Telegram, Signal, Gmail, etc.)
        try {
            val wearable = Notification.WearableExtender(notification)
            wearable.actions?.forEach { action ->
                if (action.remoteInputs != null && action.remoteInputs.isNotEmpty()) {
                    return action
                }
            }
        } catch (e: Exception) {
            // Ignore
        }

        // 3. Android Wearable Extension Bundle
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
        } catch (e: Exception) {
            // Ignore
        }

        return null
    }

    fun sendReply(notificationKey: String, message: String, packageName: String? = null): Boolean {
        Log.i(TAG, "===> Attempting to send reply: \"$message\" to key='$notificationKey', pkg='$packageName'")

        val action = replyCache[notificationKey]
            ?: loadActionFromDisk("key_$notificationKey")
            ?: findActionInActive(notificationKey)
            ?: (if (packageName != null) packageReplyCache[packageName] ?: loadActionFromDisk("pkg_$packageName") ?: findActionInActiveByPackage(packageName) else null)

        if (action == null) {
            Log.e(TAG, "No reply action found for key='$notificationKey', pkg='$packageName'. Active notifications: ${activeNotifications?.size ?: 0}")
            return false
        }

        val remoteInputs = action.remoteInputs
        if (remoteInputs.isNullOrEmpty()) {
            Log.e(TAG, "Reply action found ('${action.title}') but remoteInputs is null or empty")
            return false
        }

        val intent = Intent()
        val bundle = Bundle()
        for (remoteInput in remoteInputs) {
            bundle.putCharSequence(remoteInput.resultKey, message)
            Log.d(TAG, "Filling RemoteInput '${remoteInput.resultKey}' with \"$message\"")
        }
        RemoteInput.addResultsToIntent(remoteInputs, intent, bundle)

        return try {
            action.actionIntent.send(this, 0, intent)
            Log.i(TAG, "<=== SUCCESS: Reply PendingIntent dispatched successfully to target app ($packageName)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "<=== ERROR: Exception sending reply PendingIntent: ${e.message}", e)
            false
        }
    }

    private fun findActionInActive(notificationKey: String): Notification.Action? {
        val sbn = activeNotifications?.find { it.key == notificationKey } ?: return null
        return extractReplyAction(sbn.notification)
    }

    private fun findActionInActiveByPackage(packageName: String): Notification.Action? {
        val sbn = activeNotifications?.find { it.packageName == packageName && hasReplyAction(it.notification) } ?: return null
        return extractReplyAction(sbn.notification)
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

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val notification = sbn.notification
        val packageName = sbn.packageName
        
        // Extract and cache reply action across direct and wearable extensions
        extractReplyAction(notification)?.let { action ->
            replyCache[sbn.key] = action
            packageReplyCache[packageName] = action
            saveActionToDisk("key_${sbn.key}", action)
            saveActionToDisk("pkg_$packageName", action)
            Log.d(TAG, "Captured and persisted reply action for $packageName (${sbn.key})")
        }

        val extras = notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE)
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()

        // --- 1. SENDER EXTRACTION (Email & Lookup URI) ---
        val emailSet = mutableSetOf<String>()
        val uriSet = mutableSetOf<String>()
        
        // Primary Person
        @Suppress("DEPRECATION")
        extras.getParcelable<Person>(Notification.EXTRA_MESSAGING_PERSON)?.let { p ->
            p.uri?.let { 
                if (it.startsWith("mailto:")) emailSet.add(it.substringAfter("mailto:"))
                if (it.startsWith("content://com.android.contacts/")) uriSet.add(it)
            }
        }

        // Messages Thread
        @Suppress("DEPRECATION")
        extras.getParcelableArray(Notification.EXTRA_MESSAGES)?.forEach { msg ->
            (msg as? Bundle)?.let { b ->
                b.getParcelable<Person>(Notification.EXTRA_MESSAGING_PERSON)?.let { p ->
                    p.uri?.let { 
                        if (it.startsWith("mailto:")) emailSet.add(it.substringAfter("mailto:"))
                        if (it.startsWith("content://com.android.contacts/")) uriSet.add(it)
                    }
                }
            }
        }

        // People List
        @Suppress("DEPRECATION")
        extras.getParcelableArrayList<Person>(Notification.EXTRA_PEOPLE_LIST)?.forEach { p ->
            p.uri?.let { 
                if (it.startsWith("mailto:")) emailSet.add(it.substringAfter("mailto:"))
                if (it.startsWith("content://com.android.contacts/")) uriSet.add(it)
            }
        }
        
        val senderEmail = if (emailSet.isNotEmpty()) emailSet.joinToString(", ") else null
        val contactLookupUri = if (uriSet.isNotEmpty()) uriSet.first() else null

        if (title.isNullOrEmpty() && text.isNullOrEmpty()) return

        val timestamp = System.currentTimeMillis()

        // --- 2. COMPREHENSIVE JSON GENERATION ---
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
            
            notification.contentIntent?.let { pendingIntent ->
                val intentMap = mutableMapOf<String, Any?>()
                intentMap["creatorPackage"] = pendingIntent.creatorPackage
                intentMap["creatorUid"] = pendingIntent.creatorUid
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    intentMap["isActivity"] = pendingIntent.isActivity
                    intentMap["isBroadcast"] = pendingIntent.isBroadcast
                    intentMap["isService"] = pendingIntent.isService
                }
                root["contentIntent"] = intentMap
            }
            
            gson.toJson(root)
        } catch (e: Exception) {
            "Error: ${e.message}"
        }

        serviceScope.launch {
            // Check if app is marked as important
            val isImportantApp = appDao.isAppImportant(packageName).first()
            
            // Check if notification is from a priority contact
            val priorityContacts = contactDao.getAllContacts().first()
            val isFromPriorityContact = priorityContacts.any { contact ->
                val nameMatch = (title?.contains(contact.displayName, ignoreCase = true) == true) ||
                                (contact.officialName?.let { title?.contains(it, ignoreCase = true) } == true) ||
                                (text?.contains(contact.displayName, ignoreCase = true) == true) ||
                                (contact.officialName?.let { text?.contains(it, ignoreCase = true) } == true)
                
                val phoneMatch = contact.phoneNumbers.any { phone ->
                    phone.isNotBlank() && (
                        (title?.contains(phone) == true) || 
                        (text?.contains(phone) == true)
                    )
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

            // Deduplication: Avoid recording identical repeated notifications within 1 hour or same key
            val existing = notificationDao.getLatestMatching(packageName, title, text)
            if (existing != null && (timestamp - existing.timestamp < 60 * 60 * 1000L || existing.notificationKey == sbn.key)) {
                notificationDao.updateTimestampAndJson(existing.id, timestamp, rawJson)
                return@launch
            }

            notificationDao.insertNotification(
                NotificationEntity(
                    packageName = packageName,
                    title = title,
                    text = text,
                    senderEmail = senderEmail,
                    contactLookupUri = contactLookupUri,
                    rawJson = rawJson,
                    notificationKey = sbn.key,
                    timestamp = timestamp
                )
            )
        }
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
}
