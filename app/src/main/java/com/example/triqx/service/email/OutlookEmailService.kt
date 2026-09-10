package com.example.triqx.service.email

import android.content.Intent
import com.example.triqx.auth.OutlookOAuthManager
import com.example.triqx.data.local.EmailAccountEntity
import com.example.triqx.data.remote.OutlookGraphApiService
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [EmailService] for Microsoft Outlook and Office 365.
 */
@Singleton
class OutlookEmailService @Inject constructor(
    private val outlookOAuthManager: OutlookOAuthManager,
    private val outlookGraphApiService: OutlookGraphApiService
) : EmailService {

    override val provider: EmailProvider = EmailProvider.OUTLOOK

    override suspend fun createAuthorizationIntent(): Intent {
        return outlookOAuthManager.createAuthorizationIntent()
    }

    override suspend fun handleAuthorizationResult(resultIntent: Intent): Result<EmailAccountEntity> {
        return outlookOAuthManager.handleAuthorizationResult(resultIntent)
    }

    override suspend fun getValidAccessToken(account: EmailAccountEntity): String? {
        return outlookOAuthManager.getValidAccessToken(account.emailAddress)
    }

    override suspend fun sendReply(
        account: EmailAccountEntity,
        recipientEmail: String,
        subject: String?,
        replyText: String
    ): Result<String> {
        val accessToken = getValidAccessToken(account)
            ?: return Result.failure(IllegalStateException("No valid Microsoft access token for ${account.emailAddress}"))

        // Try to find matching parent message in the user's Outlook inbox for true in-thread reply
        val parentMessageId = outlookGraphApiService.resolveParentMessageId(accessToken, recipientEmail, subject)

        return outlookGraphApiService.sendReply(
            accessToken = accessToken,
            recipientEmail = recipientEmail,
            subject = subject,
            replyText = replyText,
            parentMessageId = parentMessageId
        )
    }

    override suspend fun disconnectAccount(emailAddress: String) {
        outlookOAuthManager.disconnectAccount(emailAddress)
    }

    override suspend fun disconnectAll() {
        outlookOAuthManager.disconnectAll()
    }
}
