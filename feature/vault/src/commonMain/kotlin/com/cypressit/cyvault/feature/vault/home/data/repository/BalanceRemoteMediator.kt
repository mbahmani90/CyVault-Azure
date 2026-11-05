package com.cypressit.cyvault.feature.vault.home.data.repository

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import com.cypressit.cyvault.feature.vault.home.data.local.BalanceCacheDao
import com.cypressit.cyvault.feature.vault.home.data.local.CachedBalance
import com.cypressit.cyvault.feature.vault.home.data.mapper.toCached
import com.cypressit.cyvault.feature.vault.home.data.mapper.toDomain
import com.cypressit.cyvault.feature.vault.home.data.remote.BalanceApiService
import kotlinx.coroutines.CancellationException

/**
 * Fills the Room cache from the API, one page at a time. The list only ever reads from Room, so
 * when a load fails here, what's already cached stays on screen.
 */
@OptIn(ExperimentalPagingApi::class)
internal class BalanceRemoteMediator(
    private val apiService: BalanceApiService,
    private val dao: BalanceCacheDao,
    private val pageSize: Int,
    private val currentUserId: suspend () -> String?,
) : RemoteMediator<Int, CachedBalance>() {

    // Runs before the list's first read, so another user's rows are gone before anything shows.
    override suspend fun initialize(): InitializeAction {
        currentUserId()?.let { dao.prepareFor(it) }
        return InitializeAction.LAUNCH_INITIAL_REFRESH
    }

    override suspend fun load(loadType: LoadType, state: PagingState<Int, CachedBalance>): MediatorResult {
        // Newest first: nothing can come before the first page.
        if (loadType == LoadType.PREPEND) return MediatorResult.Success(endOfPaginationReached = true)

        return try {
            val ownerId = currentUserId() ?: throw IllegalStateException("No signed-in user")
            val startToken = when (loadType) {
                LoadType.APPEND -> {
                    val cacheState = dao.state()
                    if (cacheState?.isEndReached == true) return MediatorResult.Success(endOfPaginationReached = true)
                    // No token yet: the first page hasn't arrived, and it brings one.
                    cacheState?.nextPageToken ?: return MediatorResult.Success(endOfPaginationReached = false)
                }
                else -> null
            }

            val (rows, nextToken) = fetchPage(startToken)
            if (loadType == LoadType.REFRESH) {
                dao.saveFirstPage(ownerId, rows, nextToken)
            } else {
                dao.savePage(ownerId, rows, nextToken)
            }
            MediatorResult.Success(endOfPaginationReached = nextToken == null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            MediatorResult.Error(e)
        }
    }

    // Table Storage can return an empty page that still has a token. Nothing new would reach Room
    // to trigger another load, so this keeps going until there are rows or no more pages.
    private suspend fun fetchPage(startToken: String?): Pair<List<CachedBalance>, String?> {
        var token = startToken
        while (true) {
            val page = apiService.getBalancesPage(pageSize, token)
            if (page.items.isNotEmpty() || page.continuationToken == null) {
                return page.items.map { it.toDomain().toCached() } to page.continuationToken
            }
            token = page.continuationToken
        }
    }
}
