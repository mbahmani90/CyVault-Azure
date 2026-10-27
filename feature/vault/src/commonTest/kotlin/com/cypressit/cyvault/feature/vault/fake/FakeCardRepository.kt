package com.cypressit.cyvault.feature.vault.fake

import com.cypressit.cyvault.feature.vault.card.domain.model.Card
import com.cypressit.cyvault.feature.vault.card.domain.model.CardType
import com.cypressit.cyvault.feature.vault.card.domain.repository.CardRepository

class FakeCardRepository(var cards: List<Card> = emptyList()) : CardRepository {

    override suspend fun getCards(): Result<List<Card>> = Result.success(cards)

    override suspend fun createCard(
        bankName: String,
        cardHolderName: String,
        cardNumber: String,
        cvv2: String,
        expiryDate: String,
        balance: Double,
        cardType: CardType,
    ): Result<Card> = Result.failure(NotImplementedError())

    override suspend fun deleteCard(id: String): Result<Unit> = Result.success(Unit)
}
