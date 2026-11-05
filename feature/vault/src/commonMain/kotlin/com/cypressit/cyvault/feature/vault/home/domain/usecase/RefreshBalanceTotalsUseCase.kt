package com.cypressit.cyvault.feature.vault.home.domain.usecase

import com.cypressit.cyvault.feature.vault.home.domain.repository.BalanceRepository

/** Fetches the totals from the API. */
class RefreshBalanceTotalsUseCase(private val repository: BalanceRepository) {
    suspend operator fun invoke(): Result<Unit> = repository.refreshTotals()
}
