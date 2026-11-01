package com.cypressit.cyvault.feature.vault.addcard.presentation.viewmodel

import com.cypressit.cyvault.feature.vault.card.domain.model.Card

sealed interface AddCardIntent {
    data object AddCardClicked : AddCardIntent
    data object AddCardDialogDismissed : AddCardIntent
    data class CardNumberChanged(val value: String) : AddCardIntent
    data class PhoneNumberChanged(val value: String) : AddCardIntent
    data class BankNameChanged(val value: String) : AddCardIntent

    /** The dialog's Add button: saves the card. */
    data object AddCardSubmitted : AddCardIntent

    /** A tap on a card: shows its details, or toggles it in selection mode. */
    data class ClickCard(val card: Card) : AddCardIntent

    /** A long press on a card: starts selection mode, or toggles the card in it. */
    data class LongClickCard(val card: Card) : AddCardIntent
    data object CardDetailsDismissed : AddCardIntent

    /** The details dialog's Delete: asks to confirm deleting the card shown. */
    data object RequestDeleteViewingCard : AddCardIntent
    data object ConfirmDeleteCard : AddCardIntent
    data object DismissDeleteCard : AddCardIntent

    data object ClearCardSelection : AddCardIntent
    data object SelectAllCards : AddCardIntent

    /** The selection bar's delete: asks to confirm deleting the selected cards. */
    data object RequestDeleteSelectedCards : AddCardIntent
    data object ConfirmDeleteSelectedCards : AddCardIntent
    data object DismissDeleteSelectedCards : AddCardIntent
}
