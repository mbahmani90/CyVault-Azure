package com.cypressit.cyvault.feature.vault.home.domain.usecase

import androidx.paging.PagingData
import com.cypressit.cyvault.feature.vault.home.domain.model.Balance
import com.cypressit.cyvault.feature.vault.home.domain.repository.BalanceRepository
import kotlinx.coroutines.flow.Flow

/** The balances, newest first, a page at a time. */
class GetBalancesUseCase(private val repository: BalanceRepository) {
    operator fun invoke(): Flow<PagingData<Balance>> = repository.balances()
}
