package com.cypressit.cyvault.feature.vault.home.domain.usecase

import com.cypressit.cyvault.feature.vault.home.domain.model.Balance
import com.cypressit.cyvault.feature.vault.home.domain.repository.BalanceRepository

/** Sends money to the card linked to a phone number; returns the user's new Transfer balance. */
class SendTransferUseCase(private val repository: BalanceRepository) {
    suspend operator fun invoke(
        transferId: String,
        cardId: String,
        phoneNumber: String,
        amount: Double,
        currency: String,
    ): Result<Balance> = repository.sendTransfer(
        transferId = transferId,
        cardId = cardId,
        phoneNumber = phoneNumber,
        amount = amount,
        currency = currency,
    )
}
