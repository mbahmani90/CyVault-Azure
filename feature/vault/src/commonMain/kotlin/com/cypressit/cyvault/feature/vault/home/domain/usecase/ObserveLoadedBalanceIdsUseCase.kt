package com.cypressit.cyvault.feature.vault.home.domain.usecase

import com.cypressit.cyvault.feature.vault.home.domain.repository.BalanceRepository
import kotlinx.coroutines.flow.Flow

/** The IDs of the balances loaded so far: what "select all" can select. */
class ObserveLoadedBalanceIdsUseCase(private val repository: BalanceRepository) {
    operator fun invoke(): Flow<Set<String>> = repository.loadedBalanceIds()
}
