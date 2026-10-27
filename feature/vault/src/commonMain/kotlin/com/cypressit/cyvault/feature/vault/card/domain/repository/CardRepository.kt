package com.cypressit.cyvault.feature.vault.card.domain.repository

import com.cypressit.cyvault.feature.vault.card.domain.model.Card
import com.cypressit.cyvault.feature.vault.card.domain.model.CardType

interface CardRepository {
    suspend fun getCards(): Result<List<Card>>
    suspend fun createCard(
        bankName: String,
        cardHolderName: String,
        cardNumber: String,
        cvv2: String,
        expiryDate: String,
        balance: Double,
        cardType: CardType,
    ): Result<Card>
    suspend fun deleteCard(id: String): Result<Unit>
}
