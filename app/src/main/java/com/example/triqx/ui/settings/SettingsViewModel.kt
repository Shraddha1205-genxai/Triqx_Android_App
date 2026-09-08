package com.example.triqx.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.triqx.data.repository.OpenAiRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val openAiRepository: OpenAiRepository,
    val emailAccountStore: com.example.triqx.data.local.EmailAccountStore,
    private val gmailOAuthManager: com.example.triqx.auth.GmailOAuthManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val prefs = context.getSharedPreferences("triqx_settings_prefs", Context.MODE_PRIVATE)

    val apiKey: StateFlow<String> = openAiRepository.apiKey
    val selectedModel: StateFlow<String> = openAiRepository.selectedModel
    val gmailAccount: StateFlow<com.example.triqx.data.local.EmailAccountEntity?> = emailAccountStore.gmailAccount
    val connectedAccounts: StateFlow<List<com.example.triqx.data.local.EmailAccountEntity>> = emailAccountStore.connectedAccounts

    private val _authStatus = MutableStateFlow<String?>(null)
    val authStatus: StateFlow<String?> = _authStatus.asStateFlow()

    private val _showDebugMenu = MutableStateFlow(prefs.getBoolean("show_debug_menu", false))
    val showDebugMenu: StateFlow<Boolean> = _showDebugMenu.asStateFlow()

    private val _notificationReplyStyle = MutableStateFlow(prefs.getString("notification_reply_style", "body_numbered") ?: "body_numbered")
    val notificationReplyStyle: StateFlow<String> = _notificationReplyStyle.asStateFlow()

    private val _isTesting = MutableStateFlow(false)
    val isTesting: StateFlow<Boolean> = _isTesting.asStateFlow()

    private val _testResult = MutableStateFlow<String?>(null)
    val testResult: StateFlow<String?> = _testResult.asStateFlow()

    fun setShowDebugMenu(enabled: Boolean) {
        prefs.edit().putBoolean("show_debug_menu", enabled).apply()
        _showDebugMenu.value = enabled
    }

    fun setNotificationReplyStyle(style: String) {
        prefs.edit().putString("notification_reply_style", style).apply()
        _notificationReplyStyle.value = style
    }

    fun saveApiKey(key: String) {
        openAiRepository.setApiKey(key)
        _testResult.value = "API Key saved successfully."
    }

    fun saveModel(model: String) {
        openAiRepository.setModel(model)
    }

    fun testConnection(key: String, model: String) {
        viewModelScope.launch {
            _isTesting.value = true
            _testResult.value = null
            val result = openAiRepository.testConnection(key, model)
            result.onSuccess { msg ->
                _testResult.value = msg
            }.onFailure { err ->
                _testResult.value = "Error: ${err.message ?: "Failed to connect to OpenAI"}"
            }
            _isTesting.value = false
        }
    }

    fun prepareGoogleAuthIntent(onReady: (android.content.Intent) -> Unit, onError: (() -> Unit)? = null) {
        viewModelScope.launch {
            try {
                val intent = gmailOAuthManager.createAuthorizationIntent()
                onReady(intent)
            } catch (e: Exception) {
                _authStatus.value = "Error: ${e.message ?: "Failed to initialize Google Sign-In"}"
                onError?.invoke()
            }
        }
    }

    suspend fun getGoogleAuthIntent(): android.content.Intent {
        return gmailOAuthManager.createAuthorizationIntent()
    }

    fun handleGoogleAuthResult(resultIntent: android.content.Intent) {
        viewModelScope.launch {
            _authStatus.value = "Connecting to Google..."
            val result = gmailOAuthManager.handleAuthorizationResult(resultIntent)
            result.onSuccess { account ->
                val namePart = if (!account.displayName.isNullOrBlank()) "${account.displayName} (${account.emailAddress})" else account.emailAddress
                _authStatus.value = "Connected: $namePart"
            }.onFailure { ex ->
                _authStatus.value = "Error: ${ex.message ?: "Authentication failed"}"
            }
        }
    }

    fun updateDisplayName(name: String) {
        emailAccountStore.updateDisplayName(name)
    }

    fun updateDisplayName(emailAddress: String, name: String) {
        emailAccountStore.updateDisplayName(emailAddress, name)
    }

    fun disconnectAccount(emailAddress: String) {
        gmailOAuthManager.disconnectAccount(emailAddress)
        _authStatus.value = null
    }

    fun disconnectGmail() {
        gmailOAuthManager.disconnectGmail()
        _authStatus.value = null
    }

    fun clearAuthStatus() {
        _authStatus.value = null
    }
}
