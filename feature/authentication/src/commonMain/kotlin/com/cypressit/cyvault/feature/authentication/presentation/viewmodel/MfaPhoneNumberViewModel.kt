package com.cypressit.cyvault.feature.authentication.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cypressit.cyvault.core.presentation.MviViewModel
import com.cypressit.cyvault.feature.authentication.domain.model.LoginResult
import com.cypressit.cyvault.feature.authentication.domain.usecase.SubmitMfaPhoneNumberUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The account's first MFA sign-in: asks for the phone number codes go to. */
class MfaPhoneNumberViewModel(
    private val submitMfaPhoneNumberUseCase: SubmitMfaPhoneNumberUseCase,
) : ViewModel(), MviViewModel<MfaPhoneNumberState, MfaPhoneNumberIntent, AuthEffect> {

    private val _state = MutableStateFlow(MfaPhoneNumberState())
    override val state: StateFlow<MfaPhoneNumberState> = _state.asStateFlow()

    private val _effect = Channel<AuthEffect>(Channel.BUFFERED)
    override val effect: Flow<AuthEffect> = _effect.receiveAsFlow()

    override fun onIntent(intent: MfaPhoneNumberIntent) {
        when (intent) {
            is MfaPhoneNumberIntent.PhoneNumberChanged -> _state.update {
                val typed = intent.phoneNumber.filter { c -> c.isDigit() || c == '+' || c == ' ' }
                it.copy(phoneNumber = typed.take(MAX_PHONE_NUMBER_INPUT), phoneNumberError = null)
            }

            MfaPhoneNumberIntent.SubmitPhoneNumber -> handlePhoneNumber()

            MfaPhoneNumberIntent.BackToLogin ->
                viewModelScope.launch { _effect.send(AuthEffect.Navigate(AuthNavEvent.BackToLogin())) }
        }
    }

    private fun handlePhoneNumber() {
        // Entra wants the international format: +, country code, number, no spaces.
        val phoneNumber = _state.value.phoneNumber.filterNot { it == ' ' }
        if (!PHONE_NUMBER.matches(phoneNumber)) {
            _state.update { it.copy(phoneNumberError = MfaInputError.InvalidPhoneNumber) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            submitMfaPhoneNumberUseCase(phoneNumber)
                .onSuccess { result ->
                    // Asked for the number again: stay here rather than open this screen twice.
                    if (result != LoginResult.PhoneNumberRequired) {
                        _effect.send(AuthEffect.Navigate(result.toNavEvent()))
                    }
                }
                .onFailure { _effect.send(AuthEffect.ShowMfaFailed(it.message)) }
            _state.update { it.copy(isLoading = false) }
        }
    }

    private companion object {
        /** E.164: +, a country code that doesn't start with 0, 8 to 15 digits in all. */
        val PHONE_NUMBER = Regex("""\+[1-9]\d{7,14}""")

        /** E.164's 16 characters plus room for the spaces people type between groups. */
        const val MAX_PHONE_NUMBER_INPUT = 20
    }
}
