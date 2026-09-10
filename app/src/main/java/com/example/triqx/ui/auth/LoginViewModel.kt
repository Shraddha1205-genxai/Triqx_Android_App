package com.example.triqx.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.triqx.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LoginStep {
    PHONE_INPUT,
    OTP_VERIFICATION
}

data class LoginUiState(
    val step: LoginStep = LoginStep.PHONE_INPUT,
    val countryCode: String = "+91",
    val phoneNumber: String = "",
    val otpCode: String = "",
    val countdownSeconds: Int = 30,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) {
    val fullPhoneNumber: String
        get() = "${countryCode.trim()} ${phoneNumber.trim()}"

    val isPhoneValid: Boolean
        get() = phoneNumber.trim().length in 10..12 && phoneNumber.trim().all { it.isDigit() }

    val canResendOtp: Boolean
        get() = countdownSeconds == 0 && !isLoading

    val canSubmitOtp: Boolean
        get() = otpCode.length in 5..6 && !isLoading
}

sealed interface LoginNavigationEvent {
    data class NavigateNext(val isFirstLogin: Boolean) : LoginNavigationEvent
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<LoginNavigationEvent>()
    val navigationEvent: SharedFlow<LoginNavigationEvent> = _navigationEvent.asSharedFlow()

    private var countdownJob: Job? = null

    fun onPhoneNumberChange(newPhone: String) {
        val digitsOnly = newPhone.filter { it.isDigit() }.take(12)
        _uiState.update { it.copy(phoneNumber = digitsOnly, errorMessage = null) }
    }

    fun onCountryCodeChange(newCode: String) {
        val formatted = if (newCode.startsWith("+")) newCode else "+$newCode"
        _uiState.update { it.copy(countryCode = formatted) }
    }

    fun onOtpChange(newOtp: String) {
        val digits = newOtp.filter { it.isDigit() }.take(6)
        _uiState.update { it.copy(otpCode = digits, errorMessage = null) }

        if (digits.length == 6) {
            onVerifyOtp()
        }
    }

    fun onSendOtp() {
        val state = _uiState.value
        if (!state.isPhoneValid) {
            _uiState.update { it.copy(errorMessage = "Please enter a valid 10-digit mobile number") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = authRepository.sendOtp(state.fullPhoneNumber)
            result.onSuccess {
                _uiState.update { current ->
                    current.copy(
                        step = LoginStep.OTP_VERIFICATION,
                        isLoading = false,
                        otpCode = "",
                        errorMessage = null
                    )
                }
                startCountdown()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = err.message ?: "Failed to send OTP. Please try again."
                    )
                }
            }
        }
    }

    fun onVerifyOtp() {
        val state = _uiState.value
        if (state.otpCode.length !in 5..6) {
            _uiState.update { it.copy(errorMessage = "Please enter the complete verification code") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = authRepository.verifyOtp(state.fullPhoneNumber, state.otpCode)
            result.onSuccess {
                _uiState.update { it.copy(isLoading = false, errorMessage = null) }
                val profile = authRepository.userProfile.value
                val isFirstLogin = profile?.isFirstLogin != false
                _navigationEvent.emit(LoginNavigationEvent.NavigateNext(isFirstLogin))
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = err.message ?: "Incorrect OTP. Please enter 123456."
                    )
                }
            }
        }
    }

    fun onResendOtp() {
        val state = _uiState.value
        if (!state.canResendOtp) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = authRepository.resendOtp(state.fullPhoneNumber)
            result.onSuccess {
                _uiState.update { it.copy(isLoading = false, otpCode = "", errorMessage = null) }
                startCountdown()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = err.message ?: "Failed to resend OTP"
                    )
                }
            }
        }
    }

    fun onChangePhoneNumber() {
        countdownJob?.cancel()
        _uiState.update {
            it.copy(
                step = LoginStep.PHONE_INPUT,
                otpCode = "",
                errorMessage = null
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            _uiState.update { it.copy(countdownSeconds = 30) }
            for (sec in 29 downTo 0) {
                delay(1000)
                _uiState.update { it.copy(countdownSeconds = sec) }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        countdownJob?.cancel()
    }
}
