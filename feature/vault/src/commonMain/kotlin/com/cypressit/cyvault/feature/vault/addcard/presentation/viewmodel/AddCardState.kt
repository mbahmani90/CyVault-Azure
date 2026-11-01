package com.cypressit.cyvault.feature.vault.addcard.presentation.viewmodel

import com.cypressit.cyvault.feature.vault.card.domain.model.Card

data class AddCardState(
    val cards: List<Card> = emptyList(),
    val isCardsLoading: Boolean = false,
    val isAddCardDialogVisible: Boolean = false,
    val cardNumber: String = "",
    val phoneNumber: String = "",
) {
    // Digits only, so spaces or a "+351" prefix typed by the user don't break the count.
    val isCardNumberValid: Boolean
        get() = cardNumber.count { it.isDigit() } >= MIN_CARD_NUMBER_DIGITS

    val isPhoneNumberValid: Boolean
        get() = phoneNumber.count { it.isDigit() } >= MIN_PHONE_NUMBER_DIGITS

    val isAddEnabled: Boolean
        get() = isCardNumberValid && isPhoneNumberValid

    val isEmpty: Boolean
        get() = cards.isEmpty() && !isCardsLoading

    private companion object {
        const val MIN_CARD_NUMBER_DIGITS = 16
        const val MIN_PHONE_NUMBER_DIGITS = 9
    }
}
