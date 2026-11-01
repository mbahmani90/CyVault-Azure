package com.cypressit.cyvault.feature.vault.addcard.presentation.viewmodel

sealed interface AddCardEffect {
    data object CardsLoadFailed : AddCardEffect
    data object CardAdded : AddCardEffect
}
