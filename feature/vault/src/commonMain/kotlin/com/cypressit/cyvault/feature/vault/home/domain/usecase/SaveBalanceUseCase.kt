package com.cypressit.cyvault.feature.vault.home.domain.usecase

import com.cypressit.cyvault.feature.vault.home.domain.model.Balance
import com.cypressit.cyvault.feature.vault.home.domain.repository.BalanceRepository

/** Creates or replaces a balance; returns it as the server stored it, whose ID may differ. */
class SaveBalanceUseCase(private val repository: BalanceRepository) {
    suspend operator fun invoke(balance: Balance, isNew: Boolean): Result<Balance> =
        if (isNew) repository.createBalance(balance) else repository.updateBalance(balance)
}
