package com.cypressit.cyvault.feature.vault.fake

import com.cypressit.cyvault.feature.vault.card.domain.model.Card
import com.cypressit.cyvault.feature.vault.card.domain.repository.CardRepository

class FakeCardRepository(var cards: List<Card> = emptyList()) : CardRepository {

    /** When set, createCard fails with this instead of saving. */
    var createFailure: Throwable? = null

    /** The IDs createCard was called with, in order. */
    val createdIds = mutableListOf<String>()

    /** The bank names createCard was called with, in order. */
    val createdBankNames = mutableListOf<String>()

    /** IDs whose delete fails; the rest are deleted. */
    var failingDeleteIds: Set<String> = emptySet()

    /** The IDs deleteCard was called with, in order. */
    val deletedIds = mutableListOf<String>()

    override suspend fun getCards(): Result<List<Card>> = Result.success(cards)

    override suspend fun createCard(
        id: String,
        cardNumber: String,
        phoneNumber: String,
        bankName: String,
    ): Result<Card> {
        createdIds += id
        createdBankNames += bankName
        createFailure?.let { return Result.failure(it) }
        val card = Card(
            id = id,
            last4 = cardNumber.takeLast(4),
            brand = "Visa",
            bankName = bankName,
            phoneNumber = phoneNumber,
            createdAt = "",
        )
        cards = listOf(card) + cards
        return Result.success(card)
    }

    override suspend fun deleteCard(id: String): Result<Unit> {
        deletedIds += id
        if (id in failingDeleteIds) return Result.failure(IllegalStateException())
        cards = cards.filterNot { it.id == id }
        return Result.success(Unit)
    }
}
