package com.cypressit.cyvault.feature.vault.addcard.presentation.viewmodel

sealed interface AddCardEffect {
    data object CardsLoadFailed : AddCardEffect
    data object CardAdded : AddCardEffect
    data class CardsDeleted(val count: Int) : AddCardEffect

    /** Some cards weren't deleted; they stay in the list, still selected. */
    data object CardsDeleteFailed : AddCardEffect
}
