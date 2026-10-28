package com.cypressit.cyvault.feature.vault.home.domain.usecase

import com.cypressit.cyvault.feature.vault.home.domain.repository.BalanceRepository

class DeleteBalanceUseCase(private val repository: BalanceRepository) {
    suspend operator fun invoke(id: String): Result<Unit> = repository.deleteBalance(id)
}
