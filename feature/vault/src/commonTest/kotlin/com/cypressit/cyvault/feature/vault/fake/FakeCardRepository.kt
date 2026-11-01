package com.cypressit.cyvault.feature.vault.fake

import com.cypressit.cyvault.feature.vault.card.domain.model.Card
import com.cypressit.cyvault.feature.vault.card.domain.repository.CardRepository

class FakeCardRepository(var cards: List<Card> = emptyList()) : CardRepository {

    /** When set, createCard fails with this instead of saving. */
    var createFailure: Throwable? = null

    /** The IDs createCard was called with, in order. */
    val createdIds = mutableListOf<String>()

    override suspend fun getCards(): Result<List<Card>> = Result.success(cards)

    override suspend fun createCard(id: String, cardNumber: String, phoneNumber: String): Result<Card> {
        createdIds += id
        createFailure?.let { return Result.failure(it) }
        val card = Card(id = id, last4 = cardNumber.takeLast(4), phoneNumber = phoneNumber, createdAt = "")
        cards = listOf(card) + cards
        return Result.success(card)
    }

    override suspend fun deleteCard(id: String): Result<Unit> = Result.success(Unit)
}
