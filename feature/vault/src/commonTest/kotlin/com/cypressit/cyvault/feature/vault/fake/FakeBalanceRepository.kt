package com.cypressit.cyvault.feature.vault.fake

import com.cypressit.cyvault.feature.vault.home.domain.model.Balance
import com.cypressit.cyvault.feature.vault.home.domain.repository.BalanceRepository
import kotlinx.coroutines.CompletableDeferred

/** Saves echo the balance back the way the API does: trimmed name, upper-case currency. */
class FakeBalanceRepository(var balances: List<Balance> = emptyList()) : BalanceRepository {

    companion object {
        const val NOW = "2026-10-28T12:00:00Z"
    }

    var failure: Exception? = null

    /** When set, getBalances waits for it, so a test can see the loading state. */
    var gate: CompletableDeferred<Unit>? = null
    val saved = mutableListOf<Balance>()
    val deletedIds = mutableListOf<String>()

    override suspend fun getBalances(): Result<List<Balance>> {
        gate?.await()
        return failure?.let { Result.failure(it) } ?: Result.success(balances)
    }

    override suspend fun saveBalance(balance: Balance): Result<Balance> {
        failure?.let { return Result.failure(it) }
        saved += balance
        // Like the API, a new balance without a date gets one.
        return Result.success(
            balance.copy(
                name = balance.name.trim(),
                currency = balance.currency.uppercase(),
                date = balance.date ?: NOW,
            ),
        )
    }

    override suspend fun deleteBalance(id: String): Result<Unit> {
        failure?.let { return Result.failure(it) }
        deletedIds += id
        return Result.success(Unit)
    }
}
