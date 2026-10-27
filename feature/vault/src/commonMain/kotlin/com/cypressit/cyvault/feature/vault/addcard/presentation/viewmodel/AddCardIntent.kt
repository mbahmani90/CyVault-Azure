package com.cypressit.cyvault.feature.vault.addcard.presentation.viewmodel

import com.cypressit.cyvault.feature.vault.addcard.domain.model.BankAccount
import com.cypressit.cyvault.feature.vault.addcard.domain.model.Institution
import com.cypressit.cyvault.feature.vault.card.domain.model.CardType

sealed class AddCardIntent {
    // SelectBank
    data class CountryChanged(val value: String) : AddCardIntent()
    data class BankSearchQueryChanged(val value: String) : AddCardIntent()
    data class InstitutionSelected(val institution: Institution) : AddCardIntent()

    // Authenticating — consentToken is null when using the manual fallback button
    data class AuthenticationCompleted(val consentToken: String? = null) : AddCardIntent()
    data class AuthenticationFailed(val reason: String) : AddCardIntent()

    // SelectAccount
    data class AccountSelected(val account: BankAccount) : AddCardIntent()

    // EnterCardDetails
    data class CardNumberChanged(val value: String) : AddCardIntent()
    data class CardHolderNameChanged(val value: String) : AddCardIntent()
    data class Cvv2Changed(val value: String) : AddCardIntent()
    data class ExpiryDateChanged(val value: String) : AddCardIntent()
    data class CardTypeChanged(val value: CardType) : AddCardIntent()
    data object Submit : AddCardIntent()
}
