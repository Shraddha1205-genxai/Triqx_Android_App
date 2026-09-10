package com.example.triqx.service.email

import android.content.Intent
import com.example.triqx.auth.GmailOAuthManager
import com.example.triqx.data.local.EmailAccountEntity
import com.example.triqx.data.remote.GmailApiService
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [EmailService] for Google / Gmail.
 */
@Singleton
class GmailEmailService @Inject constructor(
    private val gmailOAuthManager: GmailOAuthManager,
    private val gmailApiService: GmailApiService
) : EmailService {

    override val provider: EmailProvider = EmailProvider.GMAIL

    override suspend fun createAuthorizationIntent(): Intent {
        return gmailOAuthManager.createAuthorizationIntent()
    }

    override suspend fun handleAuthorizationResult(resultIntent: Intent): Result<EmailAccountEntity> {
        return gmailOAuthManager.handleAuthorizationResult(resultIntent)
    }

    override suspend fun getValidAccessToken(account: EmailAccountEntity): String? {
        return gmailOAuthManager.getValidAccessToken(account.emailAddress)
    }

    override suspend fun sendReply(
        account: EmailAccountEntity,
        recipientEmail: String,
        subject: String?,
        replyText: String
    ): Result<String> {
        val accessToken = getValidAccessToken(account)
            ?: return Result.failure(IllegalStateException("No valid Gmail access token for ${account.emailAddress}"))

        val senderDisplayName = account.displayName?.ifBlank { null }
            ?: gmailOAuthManager.getSignedInDisplayName(account.emailAddress)

        // Resolve thread info from Gmail API
        val threadInfo = gmailApiService.resolveThreadInfo(accessToken, recipientEmail, subject)

        val baseSubject = threadInfo?.originalSubject?.ifBlank { null } ?: subject
        val formattedSubject = when {
            baseSubject.isNullOrBlank() -> "Re: Email"
            baseSubject.startsWith("Re:", ignoreCase = true) -> baseSubject
            else -> "Re: $baseSubject"
        }

        return gmailApiService.sendEmail(
            accessToken = accessToken,
            fromEmail = account.emailAddress,
            fromDisplayName = senderDisplayName,
            toEmail = recipientEmail,
            subject = formattedSubject,
            bodyText = replyText,
            threadInfo = threadInfo
        )
    }

    override suspend fun disconnectAccount(emailAddress: String) {
        gmailOAuthManager.disconnectAccount(emailAddress)
    }

    override suspend fun disconnectAll() {
        gmailOAuthManager.disconnectGmail()
    }
}
