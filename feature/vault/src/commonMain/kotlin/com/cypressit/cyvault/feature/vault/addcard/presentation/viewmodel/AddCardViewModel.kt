package com.cypressit.cyvault.feature.vault.addcard.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cypressit.cyvault.feature.vault.addcard.domain.usecase.CreateAccountAuthorisationUseCase
import com.cypressit.cyvault.feature.vault.addcard.domain.usecase.CreateCardUseCase
import com.cypressit.cyvault.feature.vault.addcard.domain.usecase.GetAccountsUseCase
import com.cypressit.cyvault.feature.vault.addcard.domain.usecase.GetInstitutionsUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AddCardViewModel(
    private val getInstitutions: GetInstitutionsUseCase,
    private val createAccountAuthorisation: CreateAccountAuthorisationUseCase,
    private val getAccounts: GetAccountsUseCase,
    private val createCard: CreateCardUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(AddCardState())
    val state: StateFlow<AddCardState> = _state.asStateFlow()

    private val _effect = Channel<AddCardEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    private var searchJob: Job? = null

    fun onIntent(intent: AddCardIntent) {
        when (intent) {
            is AddCardIntent.CountryChanged ->
                _state.update { it.copy(country = intent.value, institutions = emptyList()) }

            is AddCardIntent.BankSearchQueryChanged -> {
                _state.update { it.copy(bankSearchQuery = intent.value) }
                searchJob?.cancel()
                if (intent.value.length >= 2) {
                    searchJob = viewModelScope.launch { fetchInstitutions(intent.value) }
                } else {
                    _state.update { it.copy(institutions = emptyList()) }
                }
            }

            is AddCardIntent.InstitutionSelected -> {
                _state.update { it.copy(selectedInstitution = intent.institution) }
                viewModelScope.launch { openYapilyAuth(intent.institution.id) }
            }

            is AddCardIntent.AuthenticationCompleted ->
                viewModelScope.launch { onAuthCompleted(intent.consentToken) }

            is AddCardIntent.AuthenticationFailed -> {
                _state.update { it.copy(step = AddCardStep.SelectBank) }
                viewModelScope.launch {
                    _effect.send(AddCardEffect.ShowError(intent.reason))
                }
            }

            is AddCardIntent.AccountSelected ->
                _state.update {
                    it.copy(
                        selectedAccount = intent.account,
                        bankName = it.selectedInstitution?.name ?: "",
                        balance = intent.account.balance.toString(),
                        currency = intent.account.currency,
                        cardHolderName = intent.account.ownerName ?: "",
                        step = AddCardStep.EnterCardDetails,
                    )
                }

            is AddCardIntent.CardNumberChanged -> _state.update { it.copy(cardNumber = intent.value) }
            is AddCardIntent.CardHolderNameChanged -> _state.update { it.copy(cardHolderName = intent.value) }
            is AddCardIntent.Cvv2Changed -> _state.update { it.copy(cvv2 = intent.value) }
            is AddCardIntent.ExpiryDateChanged -> _state.update { it.copy(expiryDate = intent.value) }
            is AddCardIntent.CardTypeChanged -> _state.update { it.copy(cardType = intent.value) }
            AddCardIntent.Submit -> submit()
        }
    }

    private suspend fun fetchInstitutions(query: String) {
        _state.update { it.copy(isInstitutionsLoading = true) }
        val countryCodes = _state.value.country
            .split(",")
            .map { it.trim().uppercase() }
            .filter { it.length == 2 }
            .ifEmpty { listOf("PT") }
        getInstitutions(query, countryCodes)
            .onSuccess { list ->
                _state.update { it.copy(isInstitutionsLoading = false, institutions = list) }
            }
            .onFailure { err ->
                _state.update { it.copy(isInstitutionsLoading = false) }
                _effect.send(AddCardEffect.ShowError(err.message ?: "Failed to load banks"))
            }
    }

    private suspend fun openYapilyAuth(institutionId: String) {
        _state.update { it.copy(isLoading = true) }
        createAccountAuthorisation(institutionId)
            .onSuccess { authorisationUrl ->
                _state.update { it.copy(isLoading = false, step = AddCardStep.Authenticating) }
                _effect.send(AddCardEffect.LaunchBankApp(authorisationUrl))
            }
            .onFailure { err ->
                _state.update { it.copy(isLoading = false) }
                _effect.send(AddCardEffect.ShowError(err.message ?: "Failed to connect to bank"))
            }
    }

    private suspend fun onAuthCompleted(consentToken: String?) {
        _state.update { it.copy(isLoading = true) }

        if (consentToken == null) {
            _state.update { it.copy(isLoading = false, step = AddCardStep.EnterCardDetails) }
            return
        }

        _state.update { it.copy(consentToken = consentToken) }

        getAccounts(consentToken)
            .onSuccess { accounts ->
                _state.update { it.copy(isLoading = false, accounts = accounts) }
                if (accounts.size == 1) {
                    onIntent(AddCardIntent.AccountSelected(accounts.first()))
                } else {
                    _state.update { it.copy(step = AddCardStep.SelectAccount) }
                }
            }
            .onFailure { err ->
                _state.update { it.copy(isLoading = false) }
                _effect.send(AddCardEffect.ShowError(err.message ?: "Failed to load accounts"))
            }
    }

    private fun submit() {
        val s = _state.value
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            createCard(
                bankName = s.bankName,
                cardHolderName = s.cardHolderName,
                cardNumber = s.cardNumber,
                cvv2 = s.cvv2,
                expiryDate = s.expiryDate,
                balance = s.balance.toDoubleOrNull() ?: 0.0,
                cardType = s.cardType,
            )
                .onSuccess { _effect.send(AddCardEffect.CardSaved) }
                .onFailure { err ->
                    _state.update { it.copy(isLoading = false) }
                    _effect.send(AddCardEffect.ShowError(err.message ?: "Failed to save card"))
                }
        }
    }
}
