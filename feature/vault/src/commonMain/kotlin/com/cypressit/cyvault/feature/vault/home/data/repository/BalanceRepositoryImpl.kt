package com.cypressit.cyvault.feature.vault.home.data.repository

import com.cypressit.cyvault.core.coroutines.runSuspendCatching
import com.cypressit.cyvault.feature.vault.home.data.mapper.toDomain
import com.cypressit.cyvault.feature.vault.home.data.mapper.toRequest
import com.cypressit.cyvault.feature.vault.home.data.remote.BalanceApiService
import com.cypressit.cyvault.feature.vault.home.data.remote.dto.TransferRequestDto
import com.cypressit.cyvault.feature.vault.home.domain.model.Balance
import com.cypressit.cyvault.feature.vault.home.domain.model.TransferApproval
import com.cypressit.cyvault.feature.vault.home.domain.model.TransferChallenge
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

    override suspend fun requestTransferChallenge(
        transferId: String,
        cardId: String,
        phoneNumber: String,
        amount: Double,
        currency: String,
    ): Result<TransferChallenge> = runSuspendCatching {
        val challenge = apiService.postTransferChallenge(
            TransferRequestDto(
                transferId = transferId,
                cardId = cardId,
                phoneNumber = phoneNumber,
                amount = amount,
                currency = currency,
            ),
        )
        TransferChallenge(nonce = challenge.nonce, payload = challenge.payload)
    }

    override suspend fun sendTransfer(
        transferId: String,
        cardId: String,
        phoneNumber: String,
        amount: Double,
        currency: String,
        approval: TransferApproval,
    ): Result<Balance> = runSuspendCatching {
        apiService.postTransfer(
            TransferRequestDto(
                transferId = transferId,
                cardId = cardId,
                phoneNumber = phoneNumber,
                amount = amount,
                currency = currency,
                deviceId = approval.deviceId,
                nonce = approval.nonce,
                signature = approval.signature,
            ),
        ).toDomain()
    }
}
