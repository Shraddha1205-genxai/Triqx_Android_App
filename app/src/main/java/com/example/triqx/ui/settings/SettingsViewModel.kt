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
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val prefs = context.getSharedPreferences("triqx_settings_prefs", Context.MODE_PRIVATE)

    val apiKey: StateFlow<String> = openAiRepository.apiKey
    val selectedModel: StateFlow<String> = openAiRepository.selectedModel

    private val _showDebugMenu = MutableStateFlow(prefs.getBoolean("show_debug_menu", false))
    val showDebugMenu: StateFlow<Boolean> = _showDebugMenu.asStateFlow()

    private val _isTesting = MutableStateFlow(false)
    val isTesting: StateFlow<Boolean> = _isTesting.asStateFlow()

    private val _testResult = MutableStateFlow<String?>(null)
    val testResult: StateFlow<String?> = _testResult.asStateFlow()

    fun setShowDebugMenu(enabled: Boolean) {
        prefs.edit().putBoolean("show_debug_menu", enabled).apply()
        _showDebugMenu.value = enabled
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
}
