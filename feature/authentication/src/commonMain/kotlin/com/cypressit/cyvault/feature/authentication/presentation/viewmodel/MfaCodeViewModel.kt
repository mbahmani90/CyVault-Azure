package com.cypressit.cyvault.feature.authentication.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cypressit.cyvault.core.presentation.MviViewModel
import com.cypressit.cyvault.feature.authentication.domain.usecase.SubmitMfaCodeUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The SMS code, [codeLength] digits sent to [sentTo], that completes an MFA sign-in. */
class MfaCodeViewModel(
    sentTo: String,
    codeLength: Int,
    private val submitMfaCodeUseCase: SubmitMfaCodeUseCase,
) : ViewModel(), MviViewModel<MfaCodeState, MfaCodeIntent, AuthEffect> {

    private val _state = MutableStateFlow(
        MfaCodeState(
            sentTo = sentTo,
            codeLength = codeLength.takeIf { it > 0 } ?: MfaCodeState.DEFAULT_CODE_LENGTH,
        ),
    )
    override val state: StateFlow<MfaCodeState> = _state.asStateFlow()

    private val _effect = Channel<AuthEffect>(Channel.BUFFERED)
    override val effect: Flow<AuthEffect> = _effect.receiveAsFlow()

    override fun onIntent(intent: MfaCodeIntent) {
        when (intent) {
            is MfaCodeIntent.CodeChanged -> _state.update {
                it.copy(code = intent.code.filter(Char::isDigit).take(it.codeLength), codeError = null)
            }

            MfaCodeIntent.SubmitCode -> handleCode()

            MfaCodeIntent.BackToLogin ->
                viewModelScope.launch { _effect.send(AuthEffect.Navigate(AuthNavEvent.BackToLogin())) }
        }
    }

    private fun handleCode() {
        val s = _state.value
        if (s.code.length != s.codeLength) {
            _state.update { it.copy(codeError = MfaInputError.IncompleteCode) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            submitMfaCodeUseCase(s.code)
                .onSuccess { _effect.send(AuthEffect.Navigate(AuthNavEvent.Home)) }
                .onFailure {
                    _state.update { it.copy(code = "") }
                    _effect.send(AuthEffect.ShowMfaFailed(it.message))
                }
            _state.update { it.copy(isLoading = false) }
        }
    }
}
