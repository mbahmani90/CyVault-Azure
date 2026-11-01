package com.cypressit.cyvault.feature.vault.card.data.repository

import com.cypressit.cyvault.core.coroutines.runSuspendCatching
import com.cypressit.cyvault.feature.vault.card.data.mapper.toDomain
import com.cypressit.cyvault.feature.vault.card.data.remote.CardApiService
import com.cypressit.cyvault.feature.vault.card.data.remote.dto.CardRequestDto
import com.cypressit.cyvault.feature.vault.card.domain.model.Card
import com.cypressit.cyvault.feature.vault.card.domain.repository.CardRepository

class CardRepositoryImpl(
    private val apiService: CardApiService,
) : CardRepository {

    override suspend fun getCards(): Result<List<Card>> =
        runSuspendCatching { apiService.getCards().map { it.toDomain() } }

    override suspend fun createCard(id: String, cardNumber: String, phoneNumber: String): Result<Card> =
        runSuspendCatching {
            apiService.putCard(id, CardRequestDto(cardNumber = cardNumber, phoneNumber = phoneNumber)).toDomain()
        }

    override suspend fun deleteCard(id: String): Result<Unit> =
        runSuspendCatching { apiService.deleteCard(id) }
}
