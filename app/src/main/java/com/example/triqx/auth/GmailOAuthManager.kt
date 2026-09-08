package com.example.triqx.auth

import android.accounts.Account
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.triqx.data.local.EmailAccountEntity
import com.example.triqx.data.local.EmailAccountStore
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.TimeUnit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles native Google Play Services authentication for Gmail API using an Android Client ID.
 * Eliminates browser redirects and custom URL schemes.
 */
@Singleton
class GmailOAuthManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val emailAccountStore: EmailAccountStore
) {

    companion object {
        private const val TAG = "GmailOAuthManager"
        private const val GMAIL_SEND_SCOPE_URL = "https://www.googleapis.com/auth/gmail.send"
        private const val GMAIL_READONLY_SCOPE_URL = "https://www.googleapis.com/auth/gmail.readonly"
        private const val OAUTH_SCOPE_STRING = "oauth2:$GMAIL_SEND_SCOPE_URL $GMAIL_READONLY_SCOPE_URL"
    }

    private val googleSignInOptions: GoogleSignInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestProfile()
        .requestScopes(
            Scope(GMAIL_SEND_SCOPE_URL),
            Scope(GMAIL_READONLY_SCOPE_URL)
        )
        .build()

    private fun getSignInClient(): GoogleSignInClient {
        return GoogleSignIn.getClient(context, googleSignInOptions)
    }

    /**
     * Builds the Intent to launch the native Google Play Services Account Picker.
     * Signs out of GoogleSignInClient first so Google Play Services always displays
     * the account chooser bottom sheet instead of silently reusing the cached account.
     */
    suspend fun createAuthorizationIntent(): Intent = withContext(Dispatchers.IO) {
        try {
            Tasks.await(getSignInClient().signOut(), 3, TimeUnit.SECONDS)
        } catch (e: Exception) {
            Log.w(TAG, "Sign out before sign-in intent timed out or failed: ${e.message}")
        }
        getSignInClient().signInIntent
    }

    /**
     * Handles the native Google Sign-In result, fetches the OAuth access token via GoogleAuthUtil,
     * and saves the connected account to EmailAccountStore.
     */
    suspend fun handleAuthorizationResult(resultIntent: Intent): Result<EmailAccountEntity> = withContext(Dispatchers.IO) {
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(resultIntent)
            val googleAccount: GoogleSignInAccount = task.getResult(ApiException::class.java)

            val email = googleAccount.email
            if (email.isNullOrBlank()) {
                return@withContext Result.failure(IllegalStateException("No email returned from Google Sign-In"))
            }

            val displayName = googleAccount.displayName?.ifBlank { null }
                ?: listOfNotNull(googleAccount.givenName?.ifBlank { null }, googleAccount.familyName?.ifBlank { null })
                    .joinToString(" ").ifBlank { null }

            // Fetch Bearer OAuth access token directly from Google Play Services on device
            val account = googleAccount.account ?: Account(email, "com.google")
            val token = GoogleAuthUtil.getToken(context, account, OAUTH_SCOPE_STRING)

            val entity = EmailAccountEntity(
                emailAddress = email,
                displayName = displayName,
                accessToken = token,
                refreshToken = null, // Managed transparently by Google Play Services
                tokenExpiryEpochMs = System.currentTimeMillis() + (3600 * 1000L),
                provider = "GMAIL",
                isConnected = true
            )

            emailAccountStore.saveGmailAccount(entity)
            Log.i(TAG, "Successfully connected Google account via Play Services: $email (name: '$displayName')")
            Result.success(entity)
        } catch (e: ApiException) {
            Log.e(TAG, "Google Sign-In ApiException status code: ${e.statusCode}", e)
            Result.failure(e)
        } catch (e: UserRecoverableAuthException) {
            Log.w(TAG, "User interaction needed for Google Auth: ${e.message}")
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Error completing Google Sign-In: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Returns a valid OAuth access token for the specified account (or primary account if null).
     * Google Play Services automatically handles caching and refreshing the token behind the scenes.
     */
    suspend fun getValidAccessToken(emailAddress: String? = null): String? = withContext(Dispatchers.IO) {
        val stored = emailAccountStore.getAccount(emailAddress) ?: return@withContext null

        try {
            val account = Account(stored.emailAddress, "com.google")
            val token = GoogleAuthUtil.getToken(context, account, OAUTH_SCOPE_STRING)

            if (!token.isNullOrBlank()) {
                emailAccountStore.updateAccessToken(stored.emailAddress, token, System.currentTimeMillis() + (3600 * 1000L))
                return@withContext token
            }
        } catch (e: UserRecoverableAuthException) {
            Log.e(TAG, "Google auth requires user recovery for ${stored.emailAddress}: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing Google token for ${stored.emailAddress}: ${e.message}", e)
        }

        return@withContext stored.accessToken.takeIf { it.isNotBlank() }
    }

    /**
     * Clears cached token if Gmail API returns an authentication error (HTTP 401).
     */
    suspend fun invalidateToken(token: String) = withContext(Dispatchers.IO) {
        try {
            GoogleAuthUtil.clearToken(context, token)
            Log.d(TAG, "Invalidated stale Google access token from cache.")
        } catch (e: Exception) {
            Log.w(TAG, "Could not invalidate token: ${e.message}")
        }
    }

    /**
     * Resolves the current user's display name from EmailAccountStore or GoogleSignInAccount.
     */
    fun getSignedInDisplayName(emailAddress: String? = null): String? {
        val stored = emailAccountStore.getAccount(emailAddress)?.displayName
        if (!stored.isNullOrBlank()) return stored

        val lastAccount = GoogleSignIn.getLastSignedInAccount(context)
        val name = lastAccount?.displayName?.ifBlank { null }
            ?: listOfNotNull(lastAccount?.givenName?.ifBlank { null }, lastAccount?.familyName?.ifBlank { null })
                .joinToString(" ").ifBlank { null }

        if (!name.isNullOrBlank() && !emailAddress.isNullOrBlank()) {
            emailAccountStore.updateDisplayName(emailAddress, name)
        }
        return name
    }

    /**
     * Disconnects a specific email account.
     */
    fun disconnectAccount(emailAddress: String) {
        emailAccountStore.disconnectAccount(emailAddress)
        Log.i(TAG, "Account $emailAddress disconnected.")
    }

    /**
     * Disconnects and revokes access to all Google accounts.
     */
    fun disconnectGmail() {
        try {
            getSignInClient().signOut()
            getSignInClient().revokeAccess()
        } catch (e: Exception) {
            Log.w(TAG, "Error signing out of GoogleSignInClient: ${e.message}")
        }
        emailAccountStore.disconnectGmail()
        Log.i(TAG, "All Gmail accounts disconnected.")
    }
}
