package com.example.triqx.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.triqx.data.local.EmailAccountEntity
import com.example.triqx.data.local.EmailAccountStore
import com.example.triqx.data.repository.OpenAiRepository
import com.example.triqx.service.email.EmailProvider
import com.example.triqx.service.email.EmailService
import com.example.triqx.service.email.GmailEmailService
import com.example.triqx.service.email.OutlookEmailService
import com.example.triqx.data.local.UserProfile
import com.example.triqx.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val openAiRepository: OpenAiRepository,
    val emailAccountStore: EmailAccountStore,
    private val gmailEmailService: GmailEmailService,
    private val outlookEmailService: OutlookEmailService,
    private val authRepository: AuthRepository,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    val userProfile: StateFlow<UserProfile?> = authRepository.userProfile

    private val prefs = context.getSharedPreferences("triqx_settings_prefs", Context.MODE_PRIVATE)

    val apiKey: StateFlow<String> = openAiRepository.apiKey
    val selectedModel: StateFlow<String> = openAiRepository.selectedModel
    val backendBaseUrl: StateFlow<String> = openAiRepository.backendBaseUrl
    val replyStyle: StateFlow<String> = openAiRepository.replyStyle
    val additionalPrompt: StateFlow<String> = openAiRepository.additionalPrompt
    val defaultReplyCount: StateFlow<Int> = openAiRepository.defaultReplyCount
    val gmailAccount: StateFlow<EmailAccountEntity?> = emailAccountStore.gmailAccount
    val connectedAccounts: StateFlow<List<EmailAccountEntity>> = emailAccountStore.connectedAccounts

    val gmailAccounts: StateFlow<List<EmailAccountEntity>> = emailAccountStore.connectedAccounts
        .map { list -> list.filter { it.emailProvider == EmailProvider.GMAIL } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val outlookAccounts: StateFlow<List<EmailAccountEntity>> = emailAccountStore.connectedAccounts
        .map { list -> list.filter { it.emailProvider == EmailProvider.OUTLOOK } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    fun saveBackendBaseUrl(url: String) {
        openAiRepository.setBackendBaseUrl(url)
        _testResult.value = "Backend URL saved."
    }

    fun saveReplyStyle(style: String) {
        openAiRepository.setReplyStyle(style)
    }

    fun saveAdditionalPrompt(prompt: String) {
        openAiRepository.setAdditionalPrompt(prompt)
    }

    fun saveDefaultReplyCount(count: Int) {
        openAiRepository.setDefaultReplyCount(count)
        _testResult.value = "Default replies set to $count."
    }

    fun testBackendConnection(
        url: String = backendBaseUrl.value,
        replyStyle: String = "Concise",
        additionalPrompt: String? = null
    ) {
        viewModelScope.launch {
            _isTesting.value = true
            _testResult.value = null
            val result = openAiRepository.testConnection(
                baseUrl = url,
                replyStyle = replyStyle,
                additionalPrompt = additionalPrompt
            )
            result.onSuccess { msg ->
                _testResult.value = msg
            }.onFailure { err ->
                val errorText = when {
                    err is java.net.SocketTimeoutException || err.message?.contains("timeout", ignoreCase = true) == true ->
                        "Connection timed out. The AI model or server took longer to respond. Please try again."
                    else ->
                        "Error: ${err.message ?: "Failed to connect to backend AI"}"
                }
                _testResult.value = errorText
            }
            _isTesting.value = false
        }
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
                _testResult.value = "Error: ${err.message ?: "Failed to connect to AI service"}"
            }
            _isTesting.value = false
        }
    }

    private fun getService(provider: EmailProvider): EmailService = when (provider) {
        EmailProvider.GMAIL -> gmailEmailService
        EmailProvider.OUTLOOK -> outlookEmailService
    }

    fun prepareEmailAuthIntent(
        provider: EmailProvider,
        onReady: (android.content.Intent) -> Unit,
        onError: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            try {
                val service = getService(provider)
                val intent = service.createAuthorizationIntent()
                onReady(intent)
            } catch (e: Exception) {
                _authStatus.value = "Error: ${e.message ?: "Failed to initialize ${provider.displayName} Sign-In"}"
                onError?.invoke()
            }
        }
    }

    fun handleEmailAuthResult(provider: EmailProvider, resultIntent: android.content.Intent) {
        viewModelScope.launch {
            _authStatus.value = "Connecting to ${provider.displayName}..."
            val service = getService(provider)
            val result = service.handleAuthorizationResult(resultIntent)
            result.onSuccess { account ->
                val namePart = if (!account.displayName.isNullOrBlank()) "${account.displayName} (${account.emailAddress})" else account.emailAddress
                _authStatus.value = "Connected ${provider.displayName}: $namePart"
            }.onFailure { ex ->
                _authStatus.value = "Error: ${ex.message ?: "Authentication failed"}"
            }
        }
    }

    fun prepareGoogleAuthIntent(onReady: (android.content.Intent) -> Unit, onError: (() -> Unit)? = null) {
        prepareEmailAuthIntent(EmailProvider.GMAIL, onReady, onError)
    }

    fun prepareOutlookAuthIntent(onReady: (android.content.Intent) -> Unit, onError: (() -> Unit)? = null) {
        prepareEmailAuthIntent(EmailProvider.OUTLOOK, onReady, onError)
    }

    fun handleGoogleAuthResult(resultIntent: android.content.Intent) {
        handleEmailAuthResult(EmailProvider.GMAIL, resultIntent)
    }

    fun handleOutlookAuthResult(resultIntent: android.content.Intent) {
        handleEmailAuthResult(EmailProvider.OUTLOOK, resultIntent)
    }

    suspend fun getGoogleAuthIntent(): android.content.Intent {
        return gmailEmailService.createAuthorizationIntent()
    }

    fun updateDisplayName(name: String) {
        emailAccountStore.updateDisplayName(name)
    }

    fun updateDisplayName(emailAddress: String, name: String) {
        emailAccountStore.updateDisplayName(emailAddress, name)
    }

    fun disconnectAccount(emailAddress: String) {
        val account = emailAccountStore.getAccount(emailAddress)
        viewModelScope.launch {
            if (account != null) {
                getService(account.emailProvider).disconnectAccount(emailAddress)
            } else {
                emailAccountStore.disconnectAccount(emailAddress)
            }
            _authStatus.value = null
        }
    }

    fun disconnectGmail() {
        viewModelScope.launch {
            gmailEmailService.disconnectAll()
            _authStatus.value = null
        }
    }

    fun disconnectOutlook() {
        viewModelScope.launch {
            outlookEmailService.disconnectAll()
            _authStatus.value = null
        }
    }

    fun clearAuthStatus() {
        _authStatus.value = null
    }

    fun updateUserProfile(profile: UserProfile) {
        viewModelScope.launch {
            authRepository.updateProfile(profile)
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
        }
    }
}
