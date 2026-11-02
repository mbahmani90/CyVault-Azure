package com.cypressit.cyvault.feature.vault.fake

import com.cypressit.cyvault.feature.vault.home.domain.model.Balance
import com.cypressit.cyvault.feature.vault.home.domain.model.BalanceType
import com.cypressit.cyvault.feature.vault.home.domain.model.TransferApproval
import com.cypressit.cyvault.feature.vault.home.domain.model.TransferChallenge
import com.cypressit.cyvault.feature.vault.home.domain.repository.BalanceRepository
import kotlinx.coroutines.CompletableDeferred

/** Saves echo the balance back the way the API does: trimmed name, upper-case currency. */
class FakeBalanceRepository(var balances: List<Balance> = emptyList()) : BalanceRepository {

    companion object {
        const val NOW = "2026-10-28T12:00:00Z"
    }

    var failure: Exception? = null

    /** Deleting one of these fails, while the others succeed. */
    var failingDeleteIds: Set<String> = emptySet()

    /** When set, getBalances waits for it, so a test can see the loading state. */
    var gate: CompletableDeferred<Unit>? = null
    val saved = mutableListOf<Balance>()
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
        if (id in failingDeleteIds) return Result.failure(Exception("delete failed"))
        deletedIds += id
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
        return Result.success(
            Balance(
                id = "$transferId-sent",
                name = "",
                amount = amount,
                currency = currency,
                date = NOW,
                cardId = cardId,
                type = BalanceType.Transfer,
                counterparty = phoneNumber,
            ),
        )
    }
}
