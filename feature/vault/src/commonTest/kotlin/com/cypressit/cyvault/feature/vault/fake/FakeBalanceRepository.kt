package com.cypressit.cyvault.feature.vault.fake

import androidx.paging.PagingData
import com.cypressit.cyvault.feature.vault.home.domain.model.Balance
import com.cypressit.cyvault.feature.vault.home.domain.model.BalanceTotals
import com.cypressit.cyvault.feature.vault.home.domain.model.BalanceType
import com.cypressit.cyvault.feature.vault.home.domain.model.TransferApproval
import com.cypressit.cyvault.feature.vault.home.domain.model.TransferChallenge
import com.cypressit.cyvault.feature.vault.home.domain.repository.BalanceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Saves echo the balance back the way the API does: trimmed name, upper-case currency. */
class FakeBalanceRepository(balances: List<Balance> = emptyList()) : BalanceRepository {

    private val cached = MutableStateFlow(balances)

    /** What the cache holds: what's on the device so far. */
    var balances: List<Balance>
        get() = cached.value
        set(value) {
            cached.value = value
        }

    val totals = MutableStateFlow(BalanceTotals())
    var totalsRefreshCount = 0

    companion object {
        const val NOW = "2026-10-28T12:00:00Z"
    }

    var failure: Exception? = null

    /** Deleting one of these fails, while the others succeed. */
    var failingDeleteIds: Set<String> = emptySet()

    val saved = mutableListOf<Balance>()

    /** The IDs creating and updating were called with, in order, including the ones that failed. */
    val attemptedIds = mutableListOf<String>()

    /** When set, an update returns this ID, as the API does when the date changes. */
    var idAfterUpdate: String? = null
    val deletedIds = mutableListOf<String>()

    /** When set, sendTransfer fails with this instead of sending. */
    var transferFailure: Exception? = null

    /** The transfer IDs sendTransfer was called with, in order. */
    val transferIds = mutableListOf<String>()

    /** When set, requestTransferChallenge fails with this, before anything is signed. */
    var challengeFailure: Exception? = null

    /** The approvals sendTransfer was called with, in order. */
    val approvals = mutableListOf<TransferApproval>()

    // Like the API: one nonce and payload per transfer ID.
    override suspend fun requestTransferChallenge(
        transferId: String,
        cardId: String,
        phoneNumber: String,
        amount: Double,
        currency: String,
    ): Result<TransferChallenge> {
        challengeFailure?.let { return Result.failure(it) }
        return Result.success(TransferChallenge(nonce = "nonce-$transferId", payload = "payload-$transferId"))
    }

    override fun balances(): Flow<PagingData<Balance>> = flowOf(PagingData.from(balances))

    override fun loadedBalanceIds(): Flow<Set<String>> =
        cached.map { list -> list.mapTo(mutableSetOf()) { it.id } }

    override fun totals(): Flow<BalanceTotals> = totals

    override suspend fun refreshTotals(): Result<Unit> {
        totalsRefreshCount++
        return failure?.let { Result.failure(it) } ?: Result.success(Unit)
    }

    override suspend fun createBalance(balance: Balance): Result<Balance> = save(balance).onSuccess { cache(it) }

    override suspend fun updateBalance(balance: Balance): Result<Balance> =
        save(balance).map { it.copy(id = idAfterUpdate ?: it.id) }.onSuccess { cache(it, replacesId = balance.id) }

    private fun cache(balance: Balance, replacesId: String? = null) {
        balances = balances.filterNot { it.id == balance.id || it.id == replacesId } + balance
    }

    private fun save(balance: Balance): Result<Balance> {
        attemptedIds += balance.id
        failure?.let { return Result.failure(it) }
        saved += balance
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
        if (id in failingDeleteIds) return Result.failure(Exception("delete failed"))
        deletedIds += id
        balances = balances.filterNot { it.id == id }
        return Result.success(Unit)
    }

    // Like the API: the sender's Transfer row, keyed by the transfer ID.
    override suspend fun sendTransfer(
        transferId: String,
        cardId: String,
        phoneNumber: String,
        amount: Double,
        currency: String,
        approval: TransferApproval,
    ): Result<Balance> {
        transferIds += transferId
        approvals += approval
        transferFailure?.let { return Result.failure(it) }
        val sent = Balance(
            id = "$transferId-sent",
            name = "",
            amount = amount,
            currency = currency,
            date = NOW,
            cardId = cardId,
            type = BalanceType.Transfer,
            counterparty = phoneNumber,
        )
        cache(sent)
        return Result.success(sent)
    }
}
