package com.cypressit.cyvault.feature.vault.addcard.domain.usecase

import com.cypressit.cyvault.feature.vault.addcard.domain.model.Institution
import com.cypressit.cyvault.feature.vault.addcard.domain.repository.BankRepository

class GetInstitutionsUseCase(private val repository: BankRepository) {
    suspend operator fun invoke(query: String, countryCodes: List<String>): Result<List<Institution>> =
        repository.getInstitutions(query, countryCodes)
}
