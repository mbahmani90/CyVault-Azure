package com.cypressit.cyvault.feature.vault.home.domain.usecase

import com.cypressit.cyvault.feature.vault.home.domain.repository.BalanceRepository

/**
 * Deletes several balances, as many per request as the API takes; each request deletes all of its
 * balances or none. A failed request doesn't stop the rest; returns the IDs that were deleted,
 * which may be fewer than [ids].
 */
class DeleteBalancesUseCase(private val repository: BalanceRepository) {
    suspend operator fun invoke(ids: Collection<String>): Set<String> =
        ids.distinct()
            .chunked(repository.maxDeletesPerRequest)
            .filter { repository.deleteBalances(it).isSuccess }
            .flatten()
            .toSet()
}
