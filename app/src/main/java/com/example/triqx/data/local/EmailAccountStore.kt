package com.example.triqx.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.example.triqx.service.email.EmailProvider
import com.example.triqx.utils.EmailUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Secure persistent store for connected email accounts and OAuth tokens.
 * Uses EncryptedSharedPreferences (AES256_GCM via Android Keystore).
 * Supports multiple connected accounts simultaneously.
 */
@Singleton
class EmailAccountStore @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "EmailAccountStore"
        private const val PREFS_FILE = "triqx_secure_email_prefs"

        private const val KEY_ACCOUNTS_JSON = "connected_email_accounts_json"

        // Legacy keys kept for migration
        private const val KEY_GMAIL_EMAIL = "gmail_email"
        private const val KEY_GMAIL_DISPLAY_NAME = "gmail_display_name"
        private const val KEY_GMAIL_ACCESS_TOKEN = "gmail_access_token"
        private const val KEY_GMAIL_REFRESH_TOKEN = "gmail_refresh_token"
        private const val KEY_GMAIL_EXPIRY = "gmail_token_expiry"
        private const val KEY_GMAIL_CONNECTED = "gmail_is_connected"
    }

    private val prefs: SharedPreferences = createSecurePreferences(context)
    private val gson = Gson()

    private val _connectedAccounts = MutableStateFlow<List<EmailAccountEntity>>(emptyList())
    val connectedAccounts: StateFlow<List<EmailAccountEntity>> = _connectedAccounts.asStateFlow()

    // Backward-compatibility: first connected account
    private val _gmailAccount = MutableStateFlow<EmailAccountEntity?>(null)
    val gmailAccount: StateFlow<EmailAccountEntity?> = _gmailAccount.asStateFlow()

    init {
        loadAccounts()
    }

    private fun createSecurePreferences(ctx: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(ctx)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                ctx,
                PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.w(TAG, "EncryptedSharedPreferences init failed, falling back to private prefs: ${e.message}")
            ctx.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
        }
    }

    @Synchronized
    private fun loadAccounts() {
        val json = prefs.getString(KEY_ACCOUNTS_JSON, null)
        val loadedList = mutableListOf<EmailAccountEntity>()

        if (!json.isNullOrBlank()) {
            try {
                val type = object : TypeToken<List<EmailAccountEntity>>() {}.type
                val parsed: List<EmailAccountEntity>? = gson.fromJson(json, type)
                if (parsed != null) {
                    loadedList.addAll(parsed)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing connected accounts JSON: ${e.message}", e)
            }
        }

        // Migrate legacy single account if needed
        val isConnected = prefs.getBoolean(KEY_GMAIL_CONNECTED, false)
        val legacyEmail = prefs.getString(KEY_GMAIL_EMAIL, null)
        val legacyToken = prefs.getString(KEY_GMAIL_ACCESS_TOKEN, null)

        if (isConnected && !legacyEmail.isNullOrBlank() && !legacyToken.isNullOrBlank()) {
            if (loadedList.none { it.emailAddress.equals(legacyEmail, ignoreCase = true) }) {
                var displayName = prefs.getString(KEY_GMAIL_DISPLAY_NAME, null)?.ifBlank { null }
                if (displayName == null) {
                    val lastAccount = GoogleSignIn.getLastSignedInAccount(context)
                    displayName = lastAccount?.displayName?.ifBlank { null }
                        ?: listOfNotNull(lastAccount?.givenName?.ifBlank { null }, lastAccount?.familyName?.ifBlank { null })
                            .joinToString(" ").ifBlank { null }
                }

                val migrated = EmailAccountEntity(
                    emailAddress = legacyEmail,
                    displayName = displayName,
                    accessToken = legacyToken,
                    refreshToken = prefs.getString(KEY_GMAIL_REFRESH_TOKEN, null),
                    tokenExpiryEpochMs = prefs.getLong(KEY_GMAIL_EXPIRY, 0L),
                    provider = "GMAIL",
                    isConnected = true
                )
                loadedList.add(migrated)
                persistAccounts(loadedList)
                Log.i(TAG, "Migrated legacy account $legacyEmail into multi-account list.")
            }
        }

        _connectedAccounts.value = loadedList
        _gmailAccount.value = loadedList.firstOrNull()
        Log.i(TAG, "Loaded ${loadedList.size} connected email accounts: ${loadedList.map { it.emailAddress }}")
    }

    private fun persistAccounts(accounts: List<EmailAccountEntity>) {
        val json = gson.toJson(accounts)
        prefs.edit().putString(KEY_ACCOUNTS_JSON, json).apply()
        _connectedAccounts.value = accounts
        _gmailAccount.value = accounts.firstOrNull()
    }

    @Synchronized
    fun saveAccount(account: EmailAccountEntity) {
        val current = _connectedAccounts.value.toMutableList()
        val index = current.indexOfFirst { it.emailAddress.equals(account.emailAddress, ignoreCase = true) }
        if (index >= 0) {
            current[index] = account
        } else {
            current.add(account)
        }
        persistAccounts(current)
        Log.i(TAG, "Saved account ${account.emailAddress} (total: ${current.size})")
    }

    fun saveGmailAccount(account: EmailAccountEntity) = saveAccount(account)

    @Synchronized
    fun updateAccessToken(emailAddress: String, newAccessToken: String, expiryEpochMs: Long) {
        val current = _connectedAccounts.value.toMutableList()
        val index = current.indexOfFirst { it.emailAddress.equals(emailAddress, ignoreCase = true) }
        if (index >= 0) {
            current[index] = current[index].copy(
                accessToken = newAccessToken,
                tokenExpiryEpochMs = expiryEpochMs
            )
            persistAccounts(current)
            Log.d(TAG, "Updated token for $emailAddress. Valid until $expiryEpochMs")
        }
    }

    fun updateAccessToken(newAccessToken: String, expiryEpochMs: Long) {
        val primary = _gmailAccount.value ?: return
        updateAccessToken(primary.emailAddress, newAccessToken, expiryEpochMs)
    }

    @Synchronized
    fun updateDisplayName(emailAddress: String, newDisplayName: String) {
        val current = _connectedAccounts.value.toMutableList()
        val index = current.indexOfFirst { it.emailAddress.equals(emailAddress, ignoreCase = true) }
        val trimmed = newDisplayName.trim().ifBlank { null }
        if (index >= 0) {
            current[index] = current[index].copy(displayName = trimmed)
            persistAccounts(current)
            Log.i(TAG, "Updated display name for $emailAddress: '$trimmed'")
        }
    }

    fun updateDisplayName(newDisplayName: String) {
        val primary = _gmailAccount.value ?: return
        updateDisplayName(primary.emailAddress, newDisplayName)
    }

    @Synchronized
    fun disconnectAccount(emailAddress: String) {
        val current = _connectedAccounts.value.toMutableList()
        current.removeAll { it.emailAddress.equals(emailAddress, ignoreCase = true) }
        persistAccounts(current)

        if (current.isEmpty()) {
            prefs.edit()
                .remove(KEY_GMAIL_EMAIL)
                .remove(KEY_GMAIL_DISPLAY_NAME)
                .remove(KEY_GMAIL_ACCESS_TOKEN)
                .remove(KEY_GMAIL_REFRESH_TOKEN)
                .remove(KEY_GMAIL_EXPIRY)
                .putBoolean(KEY_GMAIL_CONNECTED, false)
                .apply()
        }
        Log.i(TAG, "Disconnected account $emailAddress. Remaining: ${current.size}")
    }

    fun disconnectGmail() {
        persistAccounts(emptyList())
        prefs.edit()
            .remove(KEY_ACCOUNTS_JSON)
            .remove(KEY_GMAIL_EMAIL)
            .remove(KEY_GMAIL_DISPLAY_NAME)
            .remove(KEY_GMAIL_ACCESS_TOKEN)
            .remove(KEY_GMAIL_REFRESH_TOKEN)
            .remove(KEY_GMAIL_EXPIRY)
            .putBoolean(KEY_GMAIL_CONNECTED, false)
            .apply()
        Log.i(TAG, "Disconnected all Gmail accounts.")
    }

    fun getAccountsForProvider(provider: EmailProvider): List<EmailAccountEntity> {
        return _connectedAccounts.value.filter { it.emailProvider == provider }
    }

    fun getAccount(emailAddress: String? = null, provider: EmailProvider? = null): EmailAccountEntity? {
        val accounts = _connectedAccounts.value
        if (accounts.isEmpty()) return null
        if (emailAddress.isNullOrBlank()) {
            return if (provider != null) {
                accounts.firstOrNull { it.emailProvider == provider }
            } else {
                accounts.firstOrNull()
            }
        }
        val clean = EmailUtils.cleanEmail(emailAddress) ?: emailAddress.trim()
        return accounts.firstOrNull { account ->
            account.emailAddress.equals(clean, ignoreCase = true) &&
                (provider == null || account.emailProvider == provider)
        } ?: accounts.firstOrNull { it.emailAddress.equals(clean, ignoreCase = true) }
          ?: if (provider != null) accounts.firstOrNull { it.emailProvider == provider } else accounts.firstOrNull()
    }

    fun getGmailAccount(): EmailAccountEntity? = getAccount(null, EmailProvider.GMAIL)

    fun getOutlookAccount(): EmailAccountEntity? = getAccount(null, EmailProvider.OUTLOOK)

    fun isGmailConnected(): Boolean = _connectedAccounts.value.any { it.emailProvider == EmailProvider.GMAIL }

    fun isOutlookConnected(): Boolean = _connectedAccounts.value.any { it.emailProvider == EmailProvider.OUTLOOK }

    @Synchronized
    fun disconnectAccountsForProvider(provider: EmailProvider) {
        val current = _connectedAccounts.value.toMutableList()
        current.removeAll { it.emailProvider == provider }
        persistAccounts(current)
        Log.i(TAG, "Disconnected all accounts for provider ${provider.name}. Remaining: ${current.size}")
    }

    fun hasAccountFor(packageName: String, targetEmail: String? = null): Boolean {
        val provider = EmailProvider.fromPackageName(packageName)
        val accounts = _connectedAccounts.value
        if (accounts.isEmpty()) return false

        val clean = targetEmail?.let { EmailUtils.cleanEmail(it) ?: it.trim() }

        return if (provider != null) {
            if (clean.isNullOrBlank()) {
                accounts.any { it.emailProvider == provider }
            } else {
                accounts.any { it.emailProvider == provider && it.emailAddress.equals(clean, ignoreCase = true) }
            }
        } else {
            if (clean.isNullOrBlank()) false else accounts.any { it.emailAddress.equals(clean, ignoreCase = true) }
        }
    }

    /**
     * Completely wipes all connected email accounts and stored tokens from the device.
     */
    @Synchronized
    fun clearAll() {
        prefs.edit().clear().apply()
        _connectedAccounts.value = emptyList()
        _gmailAccount.value = null
        Log.i(TAG, "Cleared all connected email accounts and credentials from device.")
    }
}
