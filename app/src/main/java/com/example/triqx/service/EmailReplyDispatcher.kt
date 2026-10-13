package com.example.triqx.service

import android.util.Log
import com.example.triqx.data.local.EmailAccountStore
import com.example.triqx.service.email.EmailProvider
import com.example.triqx.service.email.EmailService
import com.example.triqx.service.email.GmailEmailService
import com.example.triqx.service.email.OutlookEmailService
import com.example.triqx.utils.EmailUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * High-level dispatcher for sending email replies polymorphically via connected OAuth email services.
 */
@Singleton
class EmailReplyDispatcher @Inject constructor(
    private val emailAccountStore: EmailAccountStore,
    private val gmailEmailService: GmailEmailService,
    private val outlookEmailService: OutlookEmailService
) {

    companion object {
        private const val TAG = "EmailReplyDispatcher"
    }

    private val services: List<EmailService> by lazy {
        listOf(gmailEmailService, outlookEmailService)
    }

    fun getServiceForPackage(packageName: String): EmailService? {
        return services.firstOrNull { it.canHandle(packageName) }
    }

    fun getServiceForProvider(provider: EmailProvider): EmailService? {
        return services.firstOrNull { it.provider == provider }
    }

    /**
     * Checks if direct API reply is supported for the given email app package and optional target account.
     */
    fun canReply(packageName: String, accountEmail: String? = null): Boolean {
        val cleanAccount = EmailUtils.cleanEmail(accountEmail) ?: accountEmail
        return emailAccountStore.hasAccountFor(packageName, cleanAccount)
    }

    /**
     * Dispatches a reply to an email via the appropriate connected email API (Gmail, Microsoft Graph).
     *
     * @param recipientEmail Clean email address of the recipient (e.g. sender of incoming email)
     * @param subject Original subject or thread title
     * @param replyText Body of the reply
     * @param packageName Originating package (e.g. "com.google.android.gm" or "com.microsoft.office.outlook")
     * @param accountEmail User's email account that originally received the email
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

        val service = getServiceForPackage(packageName)
            ?: return@withContext Result.failure(IllegalArgumentException("Unsupported email application package: $packageName"))

        // Look up the specific account that received the message, with fallback to primary of that provider
        val account = emailAccountStore.getAccount(cleanAccount, service.provider)
            ?: emailAccountStore.getAccount(null, service.provider)

        if (account == null || !account.isConnected) {
            val providerName = service.provider.displayName
            val error = if (!cleanAccount.isNullOrBlank()) {
                "$providerName account '$cleanAccount' is not connected. Please connect it in Settings."
            } else {
                "$providerName account is not connected. Please connect your account in Settings."
            }
            Log.e(TAG, error)
            return@withContext Result.failure(IllegalStateException(error))
        }

        Log.i(TAG, "Dispatching reply via ${service.provider.name} for $cleanRecipient from ${account.emailAddress}")
        service.sendReply(
            account = account,
            recipientEmail = cleanRecipient,
            subject = subject,
            replyText = replyText
        )
    }
}
