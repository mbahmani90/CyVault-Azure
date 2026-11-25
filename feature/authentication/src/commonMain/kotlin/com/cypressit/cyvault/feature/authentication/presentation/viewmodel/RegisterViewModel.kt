package com.cypressit.cyvault.feature.authentication.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cypressit.cyvault.core.presentation.MviViewModel
import com.cypressit.cyvault.feature.authentication.domain.usecase.RegisterUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RegisterViewModel(
    private val registerUseCase: RegisterUseCase,
) : ViewModel(), MviViewModel<RegisterState, RegisterIntent, AuthEffect> {

    private val _state = MutableStateFlow(RegisterState())
    override val state: StateFlow<RegisterState> = _state.asStateFlow()

    private val _effect = Channel<AuthEffect>(Channel.BUFFERED)
    override val effect: Flow<AuthEffect> = _effect.receiveAsFlow()

    override fun onIntent(intent: RegisterIntent) {
        when (intent) {
            is RegisterIntent.NameChanged ->
                _state.update { it.copy(name = intent.name, nameError = null) }

            is RegisterIntent.EmailChanged ->
                _state.update { it.copy(email = intent.email, emailError = null) }

            is RegisterIntent.PasswordChanged ->
                _state.update { it.copy(password = intent.password, passwordError = null) }

            is RegisterIntent.ConfirmPasswordChanged ->
                _state.update { it.copy(confirmPassword = intent.confirmPassword, confirmPasswordError = null) }

            RegisterIntent.SubmitRegister -> handleRegister()

            RegisterIntent.BackToLogin ->
                viewModelScope.launch { _effect.send(AuthEffect.Navigate(AuthNavEvent.BackToLogin())) }
        }
    }

    private fun handleRegister() {
        if (!validateInputs()) return
        val s = _state.value
        val email = s.email.trim()
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            registerUseCase(s.name.trim(), email, s.password)
                .onSuccess { _effect.send(AuthEffect.Navigate(AuthNavEvent.Verification(email))) }
                .onFailure {
                    _effect.send(AuthEffect.ShowError(it.message ?: "Registration failed. Please try again."))
                }
            _state.update { it.copy(isLoading = false) }
        }
    }

    private fun validateInputs(): Boolean {
        val s = _state.value
        val nameError = if (s.name.isBlank()) "Name is required" else null
        val emailError = if (AuthInput.isValidEmail(s.email)) null else "Enter a valid email address"
        val passwordError = if (s.password.length < AuthInput.MIN_PASSWORD_LENGTH) {
            "Password must be at least ${AuthInput.MIN_PASSWORD_LENGTH} characters"
        } else {
            null
        }
        val confirmPasswordError = if (s.confirmPassword != s.password) "Passwords do not match" else null
        _state.update {
            it.copy(
                nameError = nameError,
                emailError = emailError,
                passwordError = passwordError,
                confirmPasswordError = confirmPasswordError,
            )
        }
        return listOf(nameError, emailError, passwordError, confirmPasswordError).all { it == null }
    }
}
