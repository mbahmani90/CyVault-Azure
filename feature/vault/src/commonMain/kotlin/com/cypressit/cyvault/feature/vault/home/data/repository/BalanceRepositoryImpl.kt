package com.cypressit.cyvault.feature.vault.home.data.repository

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.cypressit.cyvault.core.coroutines.runSuspendCatching
import com.cypressit.cyvault.feature.vault.home.data.local.BalanceCacheDao
import com.cypressit.cyvault.feature.vault.home.data.local.CachedBalanceTotals
import com.cypressit.cyvault.feature.vault.home.data.mapper.toCached
import com.cypressit.cyvault.feature.vault.home.data.mapper.toDomain
import com.cypressit.cyvault.feature.vault.home.data.mapper.toRequest
import com.cypressit.cyvault.feature.vault.home.data.remote.BalanceApiService
import com.cypressit.cyvault.feature.vault.home.data.remote.dto.TransferRequestDto
import com.cypressit.cyvault.feature.vault.home.domain.model.Balance
import com.cypressit.cyvault.feature.vault.home.domain.model.BalanceTotals
import com.cypressit.cyvault.feature.vault.home.domain.model.TransferApproval
import com.cypressit.cyvault.feature.vault.home.domain.model.TransferChallenge
import com.cypressit.cyvault.feature.vault.home.domain.repository.BalanceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/**
 * Reads come from the Room cache. Writes go to the API first, and reach the cache only if the API
 * took them, so the cache never shows something the server doesn't have.
 */
class BalanceRepositoryImpl(
    private val apiService: BalanceApiService,
    private val dao: BalanceCacheDao,
    private val currentUserId: suspend () -> String?,
) : BalanceRepository {

    @OptIn(ExperimentalPagingApi::class)
    override fun balances(): Flow<PagingData<Balance>> = Pager(
        // No placeholders: the list only knows about the balances loaded so far.
        config = PagingConfig(pageSize = PAGE_SIZE, initialLoadSize = PAGE_SIZE, enablePlaceholders = false),
        remoteMediator = BalanceRemoteMediator(apiService, dao, PAGE_SIZE, currentUserId),
        pagingSourceFactory = { dao.balances() },
    ).flow.map { page -> page.map { it.toDomain() } }

    override fun loadedBalanceIds(): Flow<Set<String>> = flow {
        prepareCache()
        emitAll(dao.ids().map { it.toSet() })
    }

    override fun totals(): Flow<BalanceTotals> = flow {
        prepareCache()
        emitAll(
            dao.totals().map { cached ->
                cached?.let {
                    BalanceTotals(
                        amounts = Json.decodeFromString<Map<String, Double>>(it.amountsJson).toList(),
                        oldestDate = it.oldestDate,
                    )
                } ?: BalanceTotals()
            },
        )
    }

    override suspend fun refreshTotals(): Result<Unit> = runSuspendCatching {
        val totals = apiService.getTotals()
        prepareCache()
        dao.saveTotals(
            CachedBalanceTotals(
                amountsJson = Json.encodeToString(totals.totals.associate { it.currency to it.total }),
                oldestDate = totals.oldestDate,
            ),
        )
    }

    override suspend fun createBalance(balance: Balance): Result<Balance> = runSuspendCatching {
        val saved = apiService.postBalance(balance.toRequest().copy(id = balance.id)).toDomain()
        dao.save(saved.toCached())
        saved
    }

    override suspend fun updateBalance(balance: Balance): Result<Balance> = runSuspendCatching {
        val saved = apiService.putBalance(balance.id, balance.toRequest()).toDomain()
        dao.save(saved.toCached(), replacesId = balance.id)
        saved
    }

    override suspend fun deleteBalance(id: String): Result<Unit> = runSuspendCatching {
        apiService.deleteBalance(id)
        dao.delete(id)
    }

    override suspend fun deleteBalances(ids: List<String>): Result<Unit> = runSuspendCatching {
        apiService.deleteBalances(ids)
        dao.deleteAll(ids)
    }

    override val maxDeletesPerRequest = BalanceApiService.MAX_DELETES_PER_REQUEST

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
        val sent = apiService.postTransfer(
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
        // The sender's Transfer row; the receiver's appears in their own list.
        dao.save(sent.toCached())
        sent
    }

    // Runs before anything is read, so another user's rows are gone before anything shows.
    private suspend fun prepareCache() {
        currentUserId()?.let { dao.prepareFor(it) }
    }

    private companion object {
        const val PAGE_SIZE = 20
    }
}
