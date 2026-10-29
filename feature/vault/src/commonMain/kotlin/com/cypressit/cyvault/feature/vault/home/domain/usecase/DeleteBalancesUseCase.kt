package com.cypressit.cyvault.feature.vault.home.domain.usecase

import com.cypressit.cyvault.feature.vault.home.domain.repository.BalanceRepository

/**
 * Deletes several balances, one request each since the API deletes one at a time. A failure
 * doesn't stop the rest; returns the IDs that were deleted, which may be fewer than [ids].
 */
class DeleteBalancesUseCase(private val repository: BalanceRepository) {
    suspend operator fun invoke(ids: Collection<String>): Set<String> =
        ids.filterTo(mutableSetOf()) { repository.deleteBalance(it).isSuccess }
}
