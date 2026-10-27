package com.cypressit.cyvault.feature.vault.addcard.presentation.viewmodel

import com.cypressit.cyvault.feature.vault.addcard.domain.model.BankAccount
import com.cypressit.cyvault.feature.vault.addcard.domain.model.Institution
import com.cypressit.cyvault.feature.vault.card.domain.model.CardType

enum class AddCardStep { SelectBank, Authenticating, SelectAccount, EnterCardDetails }

data class AddCardState(
    val step: AddCardStep = AddCardStep.SelectBank,

    // SelectBank step
    val country: String = "PT",
    val bankSearchQuery: String = "",
    val institutions: List<Institution> = emptyList(),
    val isInstitutionsLoading: Boolean = false,
    val selectedInstitution: Institution? = null,

    // Authenticating step
    val consentToken: String? = null,

    // SelectAccount step
    val accounts: List<BankAccount> = emptyList(),
    val selectedAccount: BankAccount? = null,

    // EnterCardDetails step
    val bankName: String = "",
    val balance: String = "",
    val currency: String = "EUR",
    val cardNumber: String = "",
    val cardHolderName: String = "",
    val cvv2: String = "",
    val expiryDate: String = "",
    val cardType: CardType = CardType.OTHER,
    val isLoading: Boolean = false,
)
