package com.cypressit.cyvault.feature.vault.addcard.presentation.viewmodel

import com.cypressit.cyvault.feature.vault.card.domain.model.Card
import com.cypressit.cyvault.feature.vault.card.domain.model.CardInput

data class AddCardState(
    val cards: List<Card> = emptyList(),
    val isCardsLoading: Boolean = false,
    val isAddCardDialogVisible: Boolean = false,
    val cardNumber: String = "",
    val phoneNumber: String = "",
    val isSavingCard: Boolean = false,
    /** Why the last Add failed, shown in the dialog; null while there's nothing to show. */
    val saveError: AddCardSaveError? = null,
) {
    // The API's own rules, so Add is only enabled for input it accepts.
    val isCardNumberValid: Boolean
        get() = CardInput.isValidCardNumber(cardNumber)

    val isPhoneNumberValid: Boolean
        get() = CardInput.normalizePhoneNumber(phoneNumber) != null

    val isAddEnabled: Boolean
        get() = isCardNumberValid && isPhoneNumberValid && !isSavingCard

    val isEmpty: Boolean
        get() = cards.isEmpty() && !isCardsLoading
}

enum class AddCardSaveError { PhoneNumberAlreadyLinked, InvalidCard, Failed }
