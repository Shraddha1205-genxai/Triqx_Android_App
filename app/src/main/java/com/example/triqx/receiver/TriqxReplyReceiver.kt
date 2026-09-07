package com.example.triqx.receiver

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.app.RemoteInput
import com.example.triqx.data.local.ChatMessage
import com.example.triqx.data.local.ContactDao
import com.example.triqx.data.local.ConversationDao
import com.example.triqx.data.local.ConversationEntity
import com.example.triqx.data.local.NotificationDao
import com.example.triqx.data.local.NotificationEntity
import com.example.triqx.service.TriqxAssistantNotificationManager
import com.example.triqx.service.TriqxNotificationListenerService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Handles smart reply actions from Triqx assistant notifications.
 * Dispatches replies via unified ReplyActionStore (RemoteInput) or email intent/clipboard fallback.
 */
@AndroidEntryPoint
class TriqxReplyReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "TriqxReplyReceiver"

        // Actions
        const val ACTION_SMART_REPLY = "com.example.triqx.action.SMART_REPLY"
        const val ACTION_CUSTOM_REPLY = "com.example.triqx.action.CUSTOM_REPLY"

        // Intent extras
        const val EXTRA_GROUP_KEY = "extra_group_key"
        const val EXTRA_REPLY_TEXT = "extra_reply_text"
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_NOTIFICATION_KEY = "extra_notification_key"
        const val EXTRA_CONTACT_ID = "extra_contact_id"
        const val EXTRA_SPECIFIC_IDENTIFIER = "extra_specific_identifier"
        const val EXTRA_CONTACT_OR_TITLE = "extra_contact_or_title"

        // RemoteInput key for custom/edited replies
        const val KEY_TEXT_REPLY = "key_custom_reply"
    }

    @Inject lateinit var notificationDao: NotificationDao
    @Inject lateinit var conversationDao: ConversationDao
    @Inject lateinit var contactDao: ContactDao

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val groupKey = intent.getStringExtra(EXTRA_GROUP_KEY) ?: return
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: "com.triqx"
        val notificationKey = intent.getStringExtra(EXTRA_NOTIFICATION_KEY) ?: ""
        val contactId = intent.getIntExtra(EXTRA_CONTACT_ID, -1).takeIf { it != -1 }
        val specificIdentifier = intent.getStringExtra(EXTRA_SPECIFIC_IDENTIFIER)
        val contactOrTitle = intent.getStringExtra(EXTRA_CONTACT_OR_TITLE) ?: ""

        val replyText = when (intent.action) {
            ACTION_CUSTOM_REPLY -> {
                RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence(KEY_TEXT_REPLY)?.toString()
                    ?: intent.getStringExtra(EXTRA_REPLY_TEXT)
            }
            ACTION_SMART_REPLY -> intent.getStringExtra(EXTRA_REPLY_TEXT)
            else -> null
        }

        if (replyText.isNullOrBlank()) return

        Log.i(TAG, "Processing assistant reply: \"$replyText\" for key=$groupKey, pkg=$packageName")

        val pendingResult = goAsync()

        scope.launch {
            try {
                // 1. Dismiss assistant notification immediately
                TriqxAssistantNotificationManager.cancelNotification(context, groupKey)

                // 2. Dispatch reply via unified ReplyActionStore (1-Stage)
                val sent = TriqxNotificationListenerService.instance?.sendReply(
                    conversationKey = groupKey,
                    message = replyText,
                    notificationKey = notificationKey
                ) == true

                if (sent) {
                    Log.i(TAG, "Reply sent successfully via RemoteInput to $packageName ($groupKey)")
                } else {
                    handleFallback(context, packageName, replyText, contactId, specificIdentifier, contactOrTitle)
                }

                // 3. Record outgoing reply in Room database
                saveOutgoingReply(groupKey, packageName, replyText, notificationKey, contactId, specificIdentifier, contactOrTitle)

            } catch (e: Exception) {
                Log.e(TAG, "Error sending reply: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    // =============================
    // Fallback Methods
    // =============================

    private suspend fun handleFallback(
        context: Context,
        packageName: String,
        replyText: String,
        contactId: Int?,
        specificIdentifier: String?,
        contactOrTitle: String
    ) {
        Log.w(TAG, "RemoteInput failed for $packageName. Executing fallback...")

        if (isEmailApp(packageName)) {
            sendViaEmailIntent(context, packageName, replyText, contactId, specificIdentifier, contactOrTitle)
        } else {
            copyToClipboardAndLaunch(context, packageName, replyText)
        }
    }

    private suspend fun sendViaEmailIntent(
        context: Context,
        packageName: String,
        replyText: String,
        contactId: Int?,
        specificIdentifier: String?,
        contactOrTitle: String
    ) {
        val contact = contactId?.let { contactDao.getContactById(it).first() }
        val cleanEmail = (specificIdentifier ?: contact?.primaryEmail)?.removePrefix("mailto:")?.trim()

        val subject = when {
            contactOrTitle.isBlank() -> "Re:"
            contactOrTitle.startsWith("Re:", ignoreCase = true) -> contactOrTitle
            else -> "Re: $contactOrTitle"
        }

        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:${cleanEmail ?: ""}")
            if (!cleanEmail.isNullOrBlank()) putExtra(Intent.EXTRA_EMAIL, arrayOf(cleanEmail))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, replyText)
            setPackage(packageName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        val dispatched = tryStartActivity(context, emailIntent)
            || tryStartActivity(context, emailIntent.apply { setPackage(null) })

        if (!dispatched) {
            copyToClipboardAndLaunch(context, packageName, replyText)
        }
    }

    private fun tryStartActivity(context: Context, intent: Intent): Boolean {
        return try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun copyToClipboardAndLaunch(context: Context, packageName: String, replyText: String) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("AI Reply", replyText))

            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            launchIntent?.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            launchIntent?.let { context.startActivity(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Clipboard fallback failed: ${e.message}")
        }
    }

    // =============================
    // Database
    // =============================

    private suspend fun saveOutgoingReply(
        conversationKey: String,
        packageName: String,
        replyText: String,
        notificationKey: String,
        contactId: Int?,
        specificIdentifier: String?,
        contactOrTitle: String
    ) {
        val contact = contactId?.let { contactDao.getContactById(it).first() }
        val timestamp = System.currentTimeMillis()

        // 1. Notification Entity
        val rawJson = """{"type":"outgoing_reply","text":"$replyText","contactId":${contact?.id},"specificIdentifier":"${specificIdentifier ?: ""}"}"""
        notificationDao.insertNotification(
            NotificationEntity(
                packageName = packageName,
                title = "You",
                text = replyText,
                senderEmail = specificIdentifier ?: contact?.primaryPhone ?: contact?.primaryEmail,
                contactLookupUri = contact?.lookupKey?.let { "content://com.android.contacts/lookup/$it" },
                rawJson = rawJson,
                notificationKey = notificationKey,
                timestamp = timestamp
            )
        )

        // 2. Conversation Entity
        val current = conversationDao.getConversationByKey(conversationKey).firstOrNull()
        val chatMessage = ChatMessage(
            senderName = "You",
            bodyText = replyText,
            timestamp = timestamp,
            isFromYou = true
        )
        val updatedMessages = ((current?.messages ?: emptyList()) + chatMessage)
            .sortedByDescending { it.timestamp }

        val cleanTitle = current?.title ?: contactOrTitle.takeIf { !it.equals("You", ignoreCase = true) } ?: packageName

        val entityToSave = ConversationEntity(
            conversationKey = conversationKey,
            packageName = packageName,
            contactId = contactId ?: current?.contactId,
            title = cleanTitle,
            specificIdentifier = specificIdentifier ?: current?.specificIdentifier,
            messages = updatedMessages,
            latestTimestamp = timestamp,
            latestNotificationKey = notificationKey
        )
        conversationDao.insertOrUpdate(entityToSave)

        Log.i(TAG, "===> [CONVERSATION TABLE OUTGOING] key='$conversationKey', title='$cleanTitle', replyText='$replyText', totalMsgs=${updatedMessages.size}")
    }

    private fun isEmailApp(packageName: String): Boolean {
        return packageName.contains("gm") || packageName.contains("email") ||
               packageName.contains("outlook") || packageName.contains("mail")
    }
}
