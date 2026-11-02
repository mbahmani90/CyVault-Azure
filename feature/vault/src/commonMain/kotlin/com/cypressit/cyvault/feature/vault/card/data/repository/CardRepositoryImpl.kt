package com.cypressit.cyvault.feature.vault.card.data.repository

import com.cypressit.cyvault.core.coroutines.runSuspendCatching
import com.cypressit.cyvault.feature.vault.card.data.mapper.toDomain
import com.cypressit.cyvault.feature.vault.card.data.remote.CardApiService
import com.cypressit.cyvault.feature.vault.card.data.remote.dto.CardRequestDto
import com.cypressit.cyvault.feature.vault.card.domain.model.Card
import com.cypressit.cyvault.feature.vault.card.domain.repository.CardRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class CardRepositoryImpl(
    private val apiService: CardApiService,
) : CardRepository {

    private val _cards = MutableStateFlow<List<Card>>(emptyList())
    override val cards: StateFlow<List<Card>> = _cards.asStateFlow()

    override suspend fun getCards(): Result<List<Card>> =
        runSuspendCatching { apiService.getCards().map { it.toDomain() } }
            .onSuccess { _cards.value = it }

    override suspend fun createCard(
        id: String,
        cardNumber: String,
        phoneNumber: String,
        bankName: String,
    ): Result<Card> = runSuspendCatching {
        apiService.putCard(
            id,
            CardRequestDto(cardNumber = cardNumber, phoneNumber = phoneNumber, bankName = bankName),
        ).toDomain()
    }.onSuccess { card ->
        // Newest first; a retry with the same ID replaces the card instead of adding it twice.
        _cards.update { cards -> listOf(card) + cards.filterNot { it.id == card.id } }
    }

    override suspend fun deleteCard(id: String): Result<Unit> =
        runSuspendCatching { apiService.deleteCard(id) }
            .onSuccess { _cards.update { cards -> cards.filterNot { it.id == id } } }
}
