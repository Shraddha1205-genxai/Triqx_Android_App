package com.example.triqx.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.triqx.data.local.EmailAccountEntity
import com.example.triqx.data.local.EmailAccountStore
import com.example.triqx.data.remote.OutlookGraphApiService
import com.example.triqx.service.email.EmailProvider
import com.example.triqx.utils.Constants
import com.google.gson.JsonParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Handles OAuth 2.0 PKCE authentication and token refresh with the Microsoft Identity Platform.
 * Uses net.openid.appauth with Chrome Custom Tabs and prompt=select_account to support multiple accounts.
 */
@Singleton
class OutlookOAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val emailAccountStore: EmailAccountStore,
    private val outlookGraphApiService: OutlookGraphApiService,
    private val okHttpClient: OkHttpClient
) {

    companion object {
        private const val TAG = "OutlookOAuthManager"
    }

    private val serviceConfig = AuthorizationServiceConfiguration(
        Uri.parse(Constants.MICROSOFT_AUTH_ENDPOINT),
        Uri.parse(Constants.MICROSOFT_TOKEN_ENDPOINT)
    )

    private val authService: AuthorizationService by lazy {
        AuthorizationService(context)
    }

    /**
     * Builds the Intent to launch the Microsoft OAuth 2.0 authorization flow in a Chrome Custom Tab.
     * Uses prompt=select_account so the Microsoft account picker always displays.
     */
    suspend fun createAuthorizationIntent(): Intent = withContext(Dispatchers.Default) {
        val clientId = Constants.MICROSOFT_CLIENT_ID
        if (clientId.isBlank() || clientId == "YOUR_MICROSOFT_CLIENT_ID") {
            Log.w(TAG, "MICROSOFT_CLIENT_ID is not configured in Constants.kt!")
        }

        val authRequest = AuthorizationRequest.Builder(
            serviceConfig,
            clientId,
            ResponseTypeValues.CODE,
            Uri.parse(Constants.MICROSOFT_REDIRECT_URI)
        )
            .setScopes(Constants.MICROSOFT_SCOPES)
            .setPrompt("select_account")
            .build()

        authService.getAuthorizationRequestIntent(authRequest)
    }

    /**
     * Handles the redirect result from the custom tab, exchanges the auth code for access & refresh tokens,
     * fetches the user's Microsoft profile, and saves the connected account.
     */
    suspend fun handleAuthorizationResult(resultIntent: Intent): Result<EmailAccountEntity> = withContext(Dispatchers.IO) {
        val exception = AuthorizationException.fromIntent(resultIntent)
        if (exception != null) {
            Log.e(TAG, "Microsoft auth failed: ${exception.message}", exception)
            return@withContext Result.failure(exception)
        }

        val response = AuthorizationResponse.fromIntent(resultIntent)
            ?: return@withContext Result.failure(IllegalStateException("No authorization response returned from Microsoft login"))

        // Exchange authorization code for tokens
        val tokenResult = exchangeToken(response)
        tokenResult.fold(
            onSuccess = { (accessToken, refreshToken, expiryEpochMs) ->
                // Fetch profile to identify user email and display name
                val profileResult = outlookGraphApiService.fetchUserProfile(accessToken)
                profileResult.fold(
                    onSuccess = { profile ->
                        val entity = EmailAccountEntity(
                            emailAddress = profile.email,
                            displayName = profile.displayName,
                            accessToken = accessToken,
                            refreshToken = refreshToken,
                            tokenExpiryEpochMs = expiryEpochMs,
                            provider = EmailProvider.OUTLOOK.id,
                            isConnected = true
                        )
                        emailAccountStore.saveAccount(entity)
                        Log.i(TAG, "Successfully connected Microsoft account: ${profile.email} (${profile.displayName})")
                        Result.success(entity)
                    },
                    onFailure = { ex ->
                        Log.e(TAG, "Failed to fetch Microsoft profile: ${ex.message}", ex)
                        Result.failure(ex)
                    }
                )
            },
            onFailure = { ex ->
                Log.e(TAG, "Failed to exchange Microsoft auth code: ${ex.message}", ex)
                Result.failure(ex)
            }
        )
    }

    private suspend fun exchangeToken(response: AuthorizationResponse): Result<Triple<String, String?, Long>> =
        suspendCancellableCoroutine { continuation ->
            authService.performTokenRequest(response.createTokenExchangeRequest()) { tokenResponse, ex ->
                if (ex != null || tokenResponse == null) {
                    continuation.resume(Result.failure(ex ?: IllegalStateException("Token response is null")))
                    return@performTokenRequest
                }

                val accessToken = tokenResponse.accessToken
                if (accessToken.isNullOrBlank()) {
                    continuation.resume(Result.failure(IllegalStateException("No access token in response")))
                    return@performTokenRequest
                }

                val refreshToken = tokenResponse.refreshToken
                val expiryEpochMs = tokenResponse.accessTokenExpirationTime
                    ?: (System.currentTimeMillis() + (3600 * 1000L))

                continuation.resume(Result.success(Triple(accessToken, refreshToken, expiryEpochMs)))
            }
        }

    /**
     * Returns a valid access token for the given account.
     * If the token is within 1 minute of expiring, it is refreshed automatically using the refresh_token.
     */
    suspend fun getValidAccessToken(emailAddress: String): String? = withContext(Dispatchers.IO) {
        val account = emailAccountStore.getAccount(emailAddress, EmailProvider.OUTLOOK)
            ?: return@withContext null

        val now = System.currentTimeMillis()
        if (account.accessToken.isNotBlank() && account.tokenExpiryEpochMs > (now + 60_000L)) {
            return@withContext account.accessToken
        }

        val refreshToken = account.refreshToken
        if (refreshToken.isNullOrBlank()) {
            Log.w(TAG, "No refresh token available for Microsoft account ${account.emailAddress}")
            return@withContext account.accessToken.takeIf { it.isNotBlank() }
        }

        // Refresh token via Microsoft Identity token endpoint
        try {
            val formBody = FormBody.Builder()
                .add("client_id", Constants.MICROSOFT_CLIENT_ID)
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .add("scope", Constants.MICROSOFT_SCOPES.joinToString(" "))
                .add("redirect_uri", Constants.MICROSOFT_REDIRECT_URI)
                .build()

            val request = Request.Builder()
                .url(Constants.MICROSOFT_TOKEN_ENDPOINT)
                .post(formBody)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (!response.isSuccessful || body == null) {
                    Log.e(TAG, "Failed to refresh Microsoft token (${response.code}): $body")
                    return@withContext account.accessToken.takeIf { it.isNotBlank() }
                }

                val json = JsonParser.parseString(body).asJsonObject
                val newAccessToken = json.get("access_token")?.asString
                val newRefreshToken = json.get("refresh_token")?.asString ?: refreshToken
                val expiresInSec = json.get("expires_in")?.asLong ?: 3600L
                val newExpiry = System.currentTimeMillis() + (expiresInSec * 1000L)

                if (!newAccessToken.isNullOrBlank()) {
                    emailAccountStore.updateAccessToken(account.emailAddress, newAccessToken, newExpiry)
                    val updated = account.copy(
                        accessToken = newAccessToken,
                        refreshToken = newRefreshToken,
                        tokenExpiryEpochMs = newExpiry
                    )
                    emailAccountStore.saveAccount(updated)
                    Log.i(TAG, "Successfully refreshed Microsoft access token for ${account.emailAddress}")
                    return@withContext newAccessToken
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception refreshing Microsoft token for ${account.emailAddress}: ${e.message}", e)
        }

        return@withContext account.accessToken.takeIf { it.isNotBlank() }
    }

    /**
     * Disconnects a specific Microsoft account.
     */
    fun disconnectAccount(emailAddress: String) {
        emailAccountStore.disconnectAccount(emailAddress)
        Log.i(TAG, "Microsoft account $emailAddress disconnected.")
    }

    /**
     * Disconnects all Microsoft accounts.
     */
    fun disconnectAll() {
        emailAccountStore.disconnectAccountsForProvider(EmailProvider.OUTLOOK)
        Log.i(TAG, "All Microsoft accounts disconnected.")
    }
}
