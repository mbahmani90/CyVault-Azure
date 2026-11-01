package com.cypressit.cyvault.feature.vault.addcard.presentation.viewmodel

sealed interface AddCardIntent {
    data object AddCardClicked : AddCardIntent
    data object AddCardDialogDismissed : AddCardIntent
    data class CardNumberChanged(val value: String) : AddCardIntent
    data class PhoneNumberChanged(val value: String) : AddCardIntent
}
