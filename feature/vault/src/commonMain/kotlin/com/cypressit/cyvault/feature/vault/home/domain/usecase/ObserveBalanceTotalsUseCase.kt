package com.cypressit.cyvault.feature.vault.home.domain.usecase

import com.cypressit.cyvault.feature.vault.home.domain.model.BalanceTotals
import com.cypressit.cyvault.feature.vault.home.domain.repository.BalanceRepository
import kotlinx.coroutines.flow.Flow

/** The last totals fetched, which also show offline. */
class ObserveBalanceTotalsUseCase(private val repository: BalanceRepository) {
    operator fun invoke(): Flow<BalanceTotals> = repository.totals()
}
