package com.cypressit.cyvault.feature.vault.home.domain.usecase

import com.cypressit.cyvault.feature.vault.device.domain.repository.ApprovalPrompt
import com.cypressit.cyvault.feature.vault.device.domain.repository.DeviceRepository
import com.cypressit.cyvault.feature.vault.home.domain.model.Balance
import com.cypressit.cyvault.feature.vault.home.domain.model.TransferApproval
import com.cypressit.cyvault.feature.vault.home.domain.repository.BalanceRepository

/**
 * Sends money to the card linked to a phone number; returns the user's new Transfer balance. The
 * API issues a one-time challenge for exactly this transfer, the user approves it with a
 * fingerprint or face, which signs it with this device's key, and only then is it sent.
 */
class SendTransferUseCase(
    private val balances: BalanceRepository,
    private val devices: DeviceRepository,
) {
    suspend operator fun invoke(
        userId: String,
        transferId: String,
        cardId: String,
        phoneNumber: String,
        amount: Double,
        currency: String,
        prompt: ApprovalPrompt,
    ): Result<Balance> {
        val challenge = balances.requestTransferChallenge(transferId, cardId, phoneNumber, amount, currency)
            .getOrElse { return Result.failure(it) }
        val signed = devices.sign(userId, challenge.payload, prompt)
            .getOrElse { return Result.failure(it) }
        return balances.sendTransfer(
            transferId = transferId,
            cardId = cardId,
            phoneNumber = phoneNumber,
            amount = amount,
            currency = currency,
            approval = TransferApproval(
                deviceId = signed.deviceId,
                nonce = challenge.nonce,
                signature = signed.signature,
            ),
        )
    }
}
