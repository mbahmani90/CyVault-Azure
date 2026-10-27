package com.cypressit.cyvault.feature.vault.addcard.presentation.viewmodel

sealed class AddCardEffect {
    data class LaunchBankApp(val bankUrl: String) : AddCardEffect()
    data object CardSaved : AddCardEffect()
    data class ShowError(val message: String) : AddCardEffect()
}
