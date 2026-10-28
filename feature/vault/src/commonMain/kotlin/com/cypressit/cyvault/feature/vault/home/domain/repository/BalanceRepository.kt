package com.cypressit.cyvault.feature.vault.home.domain.repository

import com.cypressit.cyvault.feature.vault.home.domain.model.Balance

interface BalanceRepository {
    suspend fun getBalances(): Result<List<Balance>>

    /** Creates the balance, or replaces the one with the same ID; returns it as the server stored it. */
    suspend fun saveBalance(balance: Balance): Result<Balance>

    suspend fun deleteBalance(id: String): Result<Unit>
}
