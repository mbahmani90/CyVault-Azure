package com.cypressit.cyvault.feature.vault.fake

import com.cypressit.cyvault.feature.vault.card.domain.model.Card
import com.cypressit.cyvault.feature.vault.card.domain.repository.CardRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Like CardRepositoryImpl, with [serverCards] in place of the API. */
class FakeCardRepository(cards: List<Card> = emptyList()) : CardRepository {

    /** What the API would return. */
    var serverCards: List<Card> = cards

    private val _cards = MutableStateFlow<List<Card>>(emptyList())
    override val cards: StateFlow<List<Card>> = _cards.asStateFlow()

    /** How many times getCards was called. */
    var getCardsCalls = 0

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

    override suspend fun getCards(): Result<List<Card>> {
        getCardsCalls++
        _cards.value = serverCards
        return Result.success(serverCards)
    }

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
        serverCards = listOf(card) + serverCards.filterNot { it.id == id }
        _cards.update { cards -> listOf(card) + cards.filterNot { it.id == id } }
        return Result.success(card)
    }

    override suspend fun deleteCard(id: String): Result<Unit> {
        deletedIds += id
        if (id in failingDeleteIds) return Result.failure(IllegalStateException())
        serverCards = serverCards.filterNot { it.id == id }
        _cards.update { cards -> cards.filterNot { it.id == id } }
        return Result.success(Unit)
    }
}
