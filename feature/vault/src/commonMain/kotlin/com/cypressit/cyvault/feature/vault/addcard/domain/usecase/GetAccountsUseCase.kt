package com.cypressit.cyvault.feature.vault.addcard.domain.usecase

import com.cypressit.cyvault.feature.vault.addcard.domain.model.BankAccount
import com.cypressit.cyvault.feature.vault.addcard.domain.repository.BankRepository

class GetAccountsUseCase(private val repository: BankRepository) {
    suspend operator fun invoke(consentToken: String): Result<List<BankAccount>> =
        repository.getAccounts(consentToken)
}
