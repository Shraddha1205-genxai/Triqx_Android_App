package com.example.triqx.ui.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AuthViewModel : ViewModel() {
    var email by mutableStateOf("")
    var password by mutableStateOf("")
    var name by mutableStateOf("")
    var isLoading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var isAuthenticated by mutableStateOf(false)

    fun onLoginClick(onSuccess: () -> Unit) {
        viewModelScope.launch {
            isLoading = true
            error = null
            // Simulate network delay
            delay(1500)
            if (email.contains("@") && password.length >= 6) {
                isAuthenticated = true
                onSuccess()
            } else {
                error = "Invalid email or password"
            }
            isLoading = false
        }
    }

    fun onRegisterClick(onSuccess: () -> Unit) {
        viewModelScope.launch {
            isLoading = true
            error = null
            // Simulate network delay
            delay(1500)
            if (name.isNotEmpty() && email.contains("@") && password.length >= 6) {
                isAuthenticated = true
                onSuccess()
            } else {
                error = "Please fill all fields correctly"
            }
            isLoading = false
        }
    }
}
