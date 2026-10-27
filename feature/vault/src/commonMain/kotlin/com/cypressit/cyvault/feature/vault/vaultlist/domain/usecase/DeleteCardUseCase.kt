package com.cypressit.cyvault.feature.vault.vaultlist.domain.usecase

import com.cypressit.cyvault.feature.vault.card.domain.repository.CardRepository

class DeleteCardUseCase(private val repository: CardRepository) {
    suspend operator fun invoke(id: String): Result<Unit> = repository.deleteCard(id)
}
