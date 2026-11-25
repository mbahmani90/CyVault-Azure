package com.cypressit.cyvault.feature.authentication.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cypressit.cyvault.core.presentation.MviViewModel
import com.cypressit.cyvault.feature.authentication.domain.usecase.ConfirmSignUpUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Confirms a new account with the code emailed to [email]. */
class VerificationViewModel(
    email: String,
    private val confirmSignUpUseCase: ConfirmSignUpUseCase,
) : ViewModel(), MviViewModel<VerificationState, VerificationIntent, AuthEffect> {

    private val _state = MutableStateFlow(VerificationState(email = email))
    override val state: StateFlow<VerificationState> = _state.asStateFlow()

    private val _effect = Channel<AuthEffect>(Channel.BUFFERED)
    override val effect: Flow<AuthEffect> = _effect.receiveAsFlow()

    override fun onIntent(intent: VerificationIntent) {
        when (intent) {
            is VerificationIntent.CodeChanged ->
                _state.update { it.copy(verificationCode = intent.code, verificationCodeError = null) }

            VerificationIntent.SubmitVerification -> handleVerification()

            VerificationIntent.BackToLogin ->
                viewModelScope.launch { _effect.send(AuthEffect.Navigate(AuthNavEvent.BackToLogin())) }
        }
    }

    private fun handleVerification() {
        val s = _state.value
        if (s.verificationCode.isBlank()) {
            _state.update { it.copy(verificationCodeError = "Please enter the verification code") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            confirmSignUpUseCase(s.email, s.verificationCode.trim())
                .onSuccess { _effect.send(AuthEffect.Navigate(AuthNavEvent.Home)) }
                .onFailure {
                    _effect.send(AuthEffect.ShowError(it.message ?: "Verification failed. Please try again."))
                }
            _state.update { it.copy(isLoading = false) }
        }
    }
}
