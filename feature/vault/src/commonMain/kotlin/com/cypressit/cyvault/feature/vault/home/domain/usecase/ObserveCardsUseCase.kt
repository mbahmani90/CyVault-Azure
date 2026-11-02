package com.cypressit.cyvault.feature.vault.home.domain.usecase

import com.cypressit.cyvault.feature.vault.card.domain.model.Card
import com.cypressit.cyvault.feature.vault.card.domain.repository.CardRepository
import kotlinx.coroutines.flow.StateFlow

/** The user's cards as every screen shares them; GetCardsUseCase fetches them again. */
class ObserveCardsUseCase(private val repository: CardRepository) {
    operator fun invoke(): StateFlow<List<Card>> = repository.cards
}
