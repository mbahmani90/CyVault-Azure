package com.cypressit.cyvault.feature.vault.card.data.repository

import com.cypressit.cyvault.feature.vault.card.data.remote.dto.CreateCardRequestDto
import com.cypressit.cyvault.feature.vault.card.data.mapper.toDomain
import com.cypressit.cyvault.feature.vault.card.data.remote.CardApiService
import com.cypressit.cyvault.feature.vault.card.domain.model.Card
import com.cypressit.cyvault.feature.vault.card.domain.model.CardType
import com.cypressit.cyvault.feature.vault.card.domain.repository.CardRepository

class CardRepositoryImpl(
    private val apiService: CardApiService,
) : CardRepository {

    override suspend fun getCards(): Result<List<Card>> =
        runCatching { apiService.getCards().map { it.toDomain() } }

    override suspend fun createCard(
        bankName: String,
        cardHolderName: String,
        cardNumber: String,
        cvv2: String,
        expiryDate: String,
        balance: Double,
        cardType: CardType,
    ): Result<Card> = runCatching {
        apiService.createCard(
            CreateCardRequestDto(
                bankName = bankName,
                cardHolderName = cardHolderName,
                cardNumber = cardNumber,
                cvv2 = cvv2,
                expiryDate = expiryDate,
                balance = balance,
                cardType = cardType.name,
            )
        ).toDomain()
    }

    override suspend fun deleteCard(id: String): Result<Unit> =
        runCatching { apiService.deleteCard(id) }
}
