package com.example.triqx.service

import android.util.Log
import com.example.triqx.auth.GmailOAuthManager
import com.example.triqx.data.local.EmailAccountStore
import com.example.triqx.data.remote.GmailApiService
import com.example.triqx.utils.EmailUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * High-level dispatcher for sending email replies via connected OAuth accounts.
 */
@Singleton
class EmailReplyDispatcher @Inject constructor(
    private val emailAccountStore: EmailAccountStore,
    private val gmailOAuthManager: GmailOAuthManager,
    private val gmailApiService: GmailApiService
) {

    companion object {
        private const val TAG = "EmailReplyDispatcher"
    }

    /**
     * Checks if direct API reply is supported for the given email app package and optional target account.
     */
    fun canReply(packageName: String, accountEmail: String? = null): Boolean {
        val cleanAccount = EmailUtils.cleanEmail(accountEmail) ?: accountEmail
        return emailAccountStore.hasAccountFor(packageName, cleanAccount)
    }

    /**
     * Dispatches a reply to an email via the appropriate connected email API.
     *
     * @param recipientEmail Clean email address of the recipient (e.g. sender of incoming email)
     * @param subject Original subject or thread title
     * @param replyText Body of the reply
     * @param packageName Originating package (e.g. "com.google.android.gm")
     * @param accountEmail User's email account that originally received the email (e.g. "raj.harsh2001@gmail.com")
     * @return Result indicating success with message ID, or failure
     */
    suspend fun sendReply(
        recipientEmail: String,
        subject: String?,
        replyText: String,
        packageName: String = "com.google.android.gm",
        accountEmail: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanRecipient = EmailUtils.cleanEmail(recipientEmail) ?: recipientEmail.removePrefix("mailto:").trim()
        val cleanAccount = EmailUtils.cleanEmail(accountEmail) ?: accountEmail?.trim()

        if (cleanRecipient.isBlank() || !cleanRecipient.contains("@")) {
            val error = "Cannot send email reply: invalid recipient address '$cleanRecipient'"
            Log.e(TAG, error)
            return@withContext Result.failure(IllegalArgumentException(error))
        }

        // Look up the specific account that received the message, with fallback to primary
        val account = emailAccountStore.getAccount(cleanAccount) ?: emailAccountStore.getGmailAccount()
        if (account == null || !account.isConnected) {
            val error = if (!cleanAccount.isNullOrBlank()) {
                "Gmail account '$cleanAccount' is not connected. Please connect it in Settings."
            } else {
                "Gmail account not connected. Please connect your Google account in Settings."
            }
            Log.e(TAG, error)
            return@withContext Result.failure(IllegalStateException(error))
        }

        val accessToken = gmailOAuthManager.getValidAccessToken(account.emailAddress)
        if (accessToken.isNullOrBlank()) {
            val error = "Failed to obtain valid Gmail OAuth access token for ${account.emailAddress}."
            Log.e(TAG, error)
            return@withContext Result.failure(IllegalStateException(error))
        }

        val senderDisplayName = account.displayName?.ifBlank { null }
            ?: gmailOAuthManager.getSignedInDisplayName(account.emailAddress)

        // Resolve parent thread and Message-ID metadata from Gmail API
        val threadInfo = gmailApiService.resolveThreadInfo(accessToken, cleanRecipient, subject)

        val baseSubject = threadInfo?.originalSubject?.ifBlank { null } ?: subject
        val formattedSubject = when {
            baseSubject.isNullOrBlank() -> "Re: Email"
            baseSubject.startsWith("Re:", ignoreCase = true) -> baseSubject
            else -> "Re: $baseSubject"
        }

        Log.i(TAG, "Sending email reply to $cleanRecipient (from: ${account.emailAddress}, name: '$senderDisplayName', subject: '$formattedSubject', threadId: ${threadInfo?.threadId})")
        return@withContext gmailApiService.sendEmail(
            accessToken = accessToken,
            fromEmail = account.emailAddress,
            fromDisplayName = senderDisplayName,
            toEmail = cleanRecipient,
            subject = formattedSubject,
            bodyText = replyText,
            threadInfo = threadInfo
        )
    }
}
