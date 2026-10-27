package com.cypressit.cyvault.feature.vault.addcard.data.repository

import com.cypressit.cyvault.feature.vault.addcard.data.mapper.toDomain
import com.cypressit.cyvault.feature.vault.addcard.data.remote.YapilyService
import com.cypressit.cyvault.feature.vault.addcard.domain.model.BankAccount
import com.cypressit.cyvault.feature.vault.addcard.domain.model.Institution
import com.cypressit.cyvault.feature.vault.addcard.domain.repository.BankRepository

class BankRepositoryImpl(
    private val service: YapilyService,
) : BankRepository {

    override suspend fun getInstitutions(query: String, countryCodes: List<String>): Result<List<Institution>> =
        runCatching { service.getInstitutions(query, countryCodes).map { it.toDomain() } }

    override suspend fun createAccountAuthorisation(institutionId: String): Result<String> =
        runCatching { service.createAccountAuthorisation(institutionId).data.authorisationUrl }

    override suspend fun getAccounts(consentToken: String): Result<List<BankAccount>> =
        runCatching { service.getAccounts(consentToken).map { it.toDomain() } }
}
