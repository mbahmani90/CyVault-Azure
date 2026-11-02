package com.cypressit.cyvault.feature.vault.home.domain.repository

import com.cypressit.cyvault.feature.vault.home.domain.model.Balance

interface BalanceRepository {
    suspend fun getBalances(): Result<List<Balance>>

    /** Creates the balance, or replaces the one with the same ID; returns it as the server stored it. */
    suspend fun saveBalance(balance: Balance): Result<Balance>

    suspend fun deleteBalance(id: String): Result<Unit>

    /**
     * Sends [amount] from the user's card [cardId] to the card linked to [phoneNumber], and returns
     * the user's new Transfer balance. The caller picks [transferId] once per send, so a retry
     * repeats the same transfer instead of sending twice. Fails with
     * [NoCardForPhoneNumberException] or [InvalidTransferException].
     */
    suspend fun sendTransfer(
        transferId: String,
        cardId: String,
        phoneNumber: String,
        amount: Double,
        currency: String,
    ): Result<Balance>
}

/** No card is linked to the phone number the money was sent to. */
class NoCardForPhoneNumberException : Exception("No card is linked to this phone number.")

/** The API rejected the transfer: the card, the amount, or sending to the card it comes from. */
class InvalidTransferException : Exception("The transfer isn't valid.")
