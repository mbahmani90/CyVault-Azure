package com.cypressit.cyvault.feature.authentication.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cypressit.cyvault.core.presentation.MviViewModel
import com.cypressit.cyvault.feature.authentication.domain.usecase.ForgotPasswordUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Sends a password reset email; [email] is what was typed on the sign-in screen. */
class ForgotPasswordViewModel(
    email: String,
    private val forgotPasswordUseCase: ForgotPasswordUseCase,
) : ViewModel(), MviViewModel<ForgotPasswordState, ForgotPasswordIntent, AuthEffect> {

    private val _state = MutableStateFlow(ForgotPasswordState(email = email))
    override val state: StateFlow<ForgotPasswordState> = _state.asStateFlow()

    private val _effect = Channel<AuthEffect>(Channel.BUFFERED)
    override val effect: Flow<AuthEffect> = _effect.receiveAsFlow()

    override fun onIntent(intent: ForgotPasswordIntent) {
        when (intent) {
            is ForgotPasswordIntent.EmailChanged ->
                _state.update { it.copy(email = intent.email, emailError = null) }

            ForgotPasswordIntent.SubmitForgotPassword -> handleForgotPassword()

            ForgotPasswordIntent.BackToLogin ->
                viewModelScope.launch { _effect.send(AuthEffect.Navigate(AuthNavEvent.BackToLogin())) }
        }
    }

    private fun handleForgotPassword() {
        val s = _state.value
        if (!AuthInput.isValidEmail(s.email)) {
            _state.update { it.copy(emailError = "Enter a valid email address") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            forgotPasswordUseCase(s.email.trim())
                .onSuccess { _effect.send(AuthEffect.Navigate(AuthNavEvent.BackToLogin(resetEmailSent = true))) }
                .onFailure {
                    _effect.send(AuthEffect.ShowError(it.message ?: "Failed to send reset email. Please try again."))
                }
            _state.update { it.copy(isLoading = false) }
        }
    }
}
