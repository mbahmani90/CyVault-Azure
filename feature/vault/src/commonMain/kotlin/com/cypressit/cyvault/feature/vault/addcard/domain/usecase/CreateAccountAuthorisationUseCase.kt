package com.cypressit.cyvault.feature.vault.addcard.domain.usecase

import com.cypressit.cyvault.feature.vault.addcard.domain.repository.BankRepository

class CreateAccountAuthorisationUseCase(private val repository: BankRepository) {
    // Returns the authorisationUrl to open in Custom Tabs
    suspend operator fun invoke(institutionId: String): Result<String> =
        repository.createAccountAuthorisation(institutionId)
}
