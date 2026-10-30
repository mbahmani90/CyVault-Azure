package com.cypressit.cyvault.feature.vault.home.domain.usecase

import com.cypressit.cyvault.feature.vault.home.domain.model.Balance
import com.cypressit.cyvault.feature.vault.home.domain.repository.BalanceRepository

class GetBalancesUseCase(private val repository: BalanceRepository) {
    suspend operator fun invoke(): Result<List<Balance>> = repository.getBalances()
}
