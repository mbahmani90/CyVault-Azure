package com.cypressit.cyvault.feature.vault.addcard.domain.usecase

import com.cypressit.cyvault.feature.vault.card.domain.model.Card
import com.cypressit.cyvault.feature.vault.card.domain.repository.CardRepository

class CreateCardUseCase(private val repository: CardRepository) {
    suspend operator fun invoke(
        id: String,
        cardNumber: String,
        phoneNumber: String,
        bankName: String,
    ): Result<Card> =
        repository.createCard(id = id, cardNumber = cardNumber, phoneNumber = phoneNumber, bankName = bankName)
}
