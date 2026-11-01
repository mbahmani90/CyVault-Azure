package com.cypressit.cyvault.feature.vault.addcard.domain.usecase

import com.cypressit.cyvault.feature.vault.card.domain.repository.CardRepository

/**
 * Deletes one or more cards, one request each since the API deletes one at a time. A failure
 * doesn't stop the rest; returns the IDs that were deleted, which may be fewer than [ids].
 */
class DeleteCardsUseCase(private val repository: CardRepository) {
    suspend operator fun invoke(ids: Collection<String>): Set<String> =
        ids.filterTo(mutableSetOf()) { repository.deleteCard(it).isSuccess }
}
