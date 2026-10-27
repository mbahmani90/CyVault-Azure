package com.cypressit.cyvault.feature.vault.home.domain.usecase

import com.cypressit.cyvault.feature.vault.card.domain.model.Card
import com.cypressit.cyvault.feature.vault.card.domain.repository.CardRepository

class GetCardsUseCase(private val repository: CardRepository) {
    suspend operator fun invoke(): Result<List<Card>> = repository.getCards()
}
