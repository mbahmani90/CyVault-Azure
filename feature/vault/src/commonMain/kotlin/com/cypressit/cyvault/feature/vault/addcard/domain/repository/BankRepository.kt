package com.cypressit.cyvault.feature.vault.addcard.domain.repository

import com.cypressit.cyvault.feature.vault.addcard.domain.model.BankAccount
import com.cypressit.cyvault.feature.vault.addcard.domain.model.Institution

interface BankRepository {
    suspend fun getInstitutions(query: String, countryCodes: List<String>): Result<List<Institution>>

    /** Returns the authorisation URL the user opens to give consent at their bank. */
    suspend fun createAccountAuthorisation(institutionId: String): Result<String>

    suspend fun getAccounts(consentToken: String): Result<List<BankAccount>>
}
