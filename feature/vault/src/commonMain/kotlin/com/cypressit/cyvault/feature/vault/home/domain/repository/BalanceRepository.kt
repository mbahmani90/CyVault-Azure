package com.cypressit.cyvault.feature.vault.home.domain.repository

import androidx.paging.PagingData
import com.cypressit.cyvault.feature.vault.home.domain.model.Balance
import com.cypressit.cyvault.feature.vault.home.domain.model.BalanceTotals
import com.cypressit.cyvault.feature.vault.home.domain.model.TransferApproval
import com.cypressit.cyvault.feature.vault.home.domain.model.TransferChallenge
import kotlinx.coroutines.flow.Flow

interface BalanceRepository {
    /** The balances, newest first: what's on the device, with more fetched as the user scrolls. */
    fun balances(): Flow<PagingData<Balance>>

    /** The IDs of the balances on the device so far. */
    fun loadedBalanceIds(): Flow<Set<String>>

    /** The last totals fetched, which also show offline. */
    fun totals(): Flow<BalanceTotals>

    /** Fetches the totals, which update [totals]. */
    suspend fun refreshTotals(): Result<Unit>

    /**
     * Creates the balance, which needs a date; returns it as the server stored it, under the ID the
     * server built. The caller picks [Balance.id] once per new balance, so a retry after a lost
     * response replaces the row instead of adding another.
     */
    suspend fun createBalance(balance: Balance): Result<Balance>

    /** Replaces the balance [Balance.id]; returns it as stored, under a new ID when its date changed. */
    suspend fun updateBalance(balance: Balance): Result<Balance>

    suspend fun deleteBalance(id: String): Result<Unit>

    /**
     * The one-time challenge that approves sending [amount] from the user's card [cardId] to the
     * card linked to [phoneNumber]. The API checks the transfer first, so it fails like
     * [sendTransfer] does, before the user is asked to approve it.
     */
    suspend fun requestTransferChallenge(
        transferId: String,
        cardId: String,
        phoneNumber: String,
        amount: Double,
        currency: String,
    ): Result<TransferChallenge>

    /**
     * Sends [amount] from the user's card [cardId] to the card linked to [phoneNumber], approved by
     * [approval], and returns the user's new Transfer balance. The caller picks [transferId] once
     * per send, so a retry repeats the same transfer instead of sending twice. Fails with
     * [NoCardForPhoneNumberException], [InvalidTransferException],
     * [TransferChallengeExpiredException] or [SigningLockedException].
     */
    suspend fun sendTransfer(
        transferId: String,
        cardId: String,
        phoneNumber: String,
        amount: Double,
        currency: String,
        approval: TransferApproval,
    ): Result<Balance>
}

/** No card is linked to the phone number the money was sent to. */
class NoCardForPhoneNumberException : Exception("No card is linked to this phone number.")

/** The API rejected the transfer: the card, the amount, or sending to the card it comes from. */
class InvalidTransferException : Exception("The transfer isn't valid.")

/** The approval came after its challenge expired: a new one has to be asked for and approved. */
class TransferChallengeExpiredException : Exception("The approval took too long.")

/** Too many wrong signatures in a row: the API blocks transfers for a while (PSD2). */
class SigningLockedException : Exception("Transfers are blocked for a while after too many failed approvals.")
