package com.example.triqx.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.triqx.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileSetupUiState(
    val firstName: String = "",
    val lastName: String = "",
    val primaryPhoneNumber: String = "",
    val additionalPhoneNumbers: List<String> = emptyList(),
    val emails: List<String> = emptyList(),
    val aboutMe: String = "",
    val professionalDetails: String = "",
    val newPhoneInput: String = "",
    val newEmailInput: String = "",
    val isSaving: Boolean = false,
    val firstNameError: String? = null
) {
    val allPhoneNumbers: List<String>
        get() = if (primaryPhoneNumber.isNotBlank()) {
            listOf(primaryPhoneNumber) + additionalPhoneNumbers
        } else {
            additionalPhoneNumbers
        }
}

sealed interface ProfileSetupEvent {
    data object SetupComplete : ProfileSetupEvent
}

@HiltViewModel
class ProfileSetupViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileSetupUiState())
    val uiState: StateFlow<ProfileSetupUiState> = _uiState.asStateFlow()

    private val _event = MutableSharedFlow<ProfileSetupEvent>()
    val event: SharedFlow<ProfileSetupEvent> = _event.asSharedFlow()

    init {
        val currentProfile = authRepository.userProfile.value
        val primaryPhone = currentProfile?.primaryPhone.orEmpty()
        val otherPhones = currentProfile?.phoneNumbers?.drop(1) ?: emptyList()

        _uiState.update {
            it.copy(
                firstName = currentProfile?.firstName.orEmpty(),
                lastName = currentProfile?.lastName.orEmpty(),
                primaryPhoneNumber = primaryPhone,
                additionalPhoneNumbers = otherPhones,
                emails = currentProfile?.emails ?: emptyList(),
                aboutMe = currentProfile?.aboutMe.orEmpty(),
                professionalDetails = currentProfile?.professionalDetails.orEmpty()
            )
        }
    }

    fun onFirstNameChange(name: String) {
        _uiState.update { it.copy(firstName = name, firstNameError = null) }
    }

    fun onLastNameChange(name: String) {
        _uiState.update { it.copy(lastName = name) }
    }

    fun onAboutMeChange(about: String) {
        _uiState.update { it.copy(aboutMe = about) }
    }

    fun onProfessionalDetailsChange(details: String) {
        _uiState.update { it.copy(professionalDetails = details) }
    }

    fun onNewPhoneInputChange(phone: String) {
        val digits = phone.filter { it.isDigit() || it == '+' || it == ' ' }.take(15)
        _uiState.update { it.copy(newPhoneInput = digits) }
    }

    fun onAddPhoneNumber() {
        val phone = _uiState.value.newPhoneInput.trim()
        if (phone.length >= 8 && !_uiState.value.allPhoneNumbers.contains(phone)) {
            _uiState.update {
                it.copy(
                    additionalPhoneNumbers = it.additionalPhoneNumbers + phone,
                    newPhoneInput = ""
                )
            }
        }
    }

    fun onRemoveAdditionalPhone(phone: String) {
        _uiState.update {
            it.copy(additionalPhoneNumbers = it.additionalPhoneNumbers.filter { p -> p != phone })
        }
    }

    fun onNewEmailInputChange(email: String) {
        _uiState.update { it.copy(newEmailInput = email.trim()) }
    }

    fun onAddEmail() {
        val email = _uiState.value.newEmailInput.trim()
        if (email.contains("@") && email.contains(".") && !_uiState.value.emails.contains(email)) {
            _uiState.update {
                it.copy(
                    emails = it.emails + email,
                    newEmailInput = ""
                )
            }
        }
    }

    fun onRemoveEmail(email: String) {
        _uiState.update {
            it.copy(emails = it.emails.filter { e -> e != email })
        }
    }

    fun onSaveProfile() {
        val state = _uiState.value
        if (state.firstName.trim().isBlank()) {
            _uiState.update { it.copy(firstNameError = "First name is mandatory") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            authRepository.completeProfile(
                firstName = state.firstName,
                lastName = state.lastName,
                phoneNumbers = state.allPhoneNumbers,
                emails = state.emails,
                aboutMe = state.aboutMe,
                professionalDetails = state.professionalDetails
            )
            _uiState.update { it.copy(isSaving = false) }
            _event.emit(ProfileSetupEvent.SetupComplete)
        }
    }
}
