package com.cypressit.cyvault.feature.vault.home.data.repository

import com.cypressit.cyvault.core.coroutines.runSuspendCatching
import com.cypressit.cyvault.feature.vault.home.data.mapper.toDomain
import com.cypressit.cyvault.feature.vault.home.data.mapper.toRequest
import com.cypressit.cyvault.feature.vault.home.data.remote.BalanceApiService
import com.cypressit.cyvault.feature.vault.home.domain.model.Balance
import com.cypressit.cyvault.feature.vault.home.domain.repository.BalanceRepository

class BalanceRepositoryImpl(
    private val apiService: BalanceApiService,
) : BalanceRepository {

    override suspend fun getBalances(): Result<List<Balance>> =
        runSuspendCatching { apiService.getBalances().map { it.toDomain() } }

    override suspend fun saveBalance(balance: Balance): Result<Balance> =
        runSuspendCatching { apiService.putBalance(balance.id, balance.toRequest()).toDomain() }

    override suspend fun deleteBalance(id: String): Result<Unit> =
        runSuspendCatching { apiService.deleteBalance(id) }
}
