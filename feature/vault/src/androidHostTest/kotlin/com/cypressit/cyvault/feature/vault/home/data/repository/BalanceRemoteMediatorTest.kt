package com.cypressit.cyvault.feature.vault.home.data.repository

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingConfig
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import com.cypressit.cyvault.feature.vault.home.data.local.CachedBalance
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The mediator against the real queries, with the API's responses scripted. */
@OptIn(ExperimentalPagingApi::class)
@RunWith(RobolectricTestRunner::class)
class BalanceRemoteMediatorTest {

    private val database = inMemoryBalanceCache()
    private val dao = database.balanceCacheDao()
    private val requests = mutableListOf<HttpRequestData>()
    private var userId: String? = "user-1"

    @After fun tearDown() = database.close()

    private fun mediator(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        BalanceRemoteMediator(
            apiService = balanceApi { request ->
                requests += request
                handler(request)
            },
            dao = dao,
            pageSize = 20,
            currentUserId = { userId },
        )

    private val pagingState = PagingState<Int, CachedBalance>(
        pages = emptyList(),
        anchorPosition = null,
        config = PagingConfig(pageSize = 20),
        leadingPlaceholderCount = 0,
    )

    private fun RemoteMediator.MediatorResult.endReached() =
        assertIs<RemoteMediator.MediatorResult.Success>(this).endOfPaginationReached

    @Test
    fun initialize_dropsAnotherUsersRowsBeforeAnythingShows() = runTest {
        dao.savePage("user-2", listOf(cachedBalance("theirs")), nextPageToken = null)

        val action = mediator { respondJson(pageJson()) }.initialize()

        assertEquals(RemoteMediator.InitializeAction.LAUNCH_INITIAL_REFRESH, action)
        assertEquals(emptyList(), dao.cachedIds())
        assertEquals("user-1", dao.state()?.ownerId)
    }

    @Test
    fun refresh_asksForTheFirstPageAndReplacesTheCache() = runTest {
        dao.savePage("user-1", listOf(cachedBalance("old")), nextPageToken = "stale")

        val result = mediator { respondJson(pageJson("1", "2", token = "next")) }
            .load(LoadType.REFRESH, pagingState)

        assertFalse(result.endReached())
        assertEquals(listOf("1", "2"), dao.cachedIds())
        assertEquals("next", dao.state()?.nextPageToken)
        val url = requests.single().url
        assertEquals("20", url.parameters["pageSize"])
        assertNull(url.parameters["continuationToken"])
    }

    @Test
    fun refresh_ofAnEmptyAccount_isTheEnd() = runTest {
        val result = mediator { respondJson(pageJson()) }.load(LoadType.REFRESH, pagingState)

        assertTrue(result.endReached())
        assertEquals(true, dao.state()?.isEndReached)
    }

    @Test
    fun append_usesTheStoredTokenAndAddsToTheCache() = runTest {
        dao.savePage("user-1", listOf(cachedBalance("1")), nextPageToken = "a token/with spaces")

        val result = mediator { respondJson(pageJson("2", token = "next")) }.load(LoadType.APPEND, pagingState)

        assertFalse(result.endReached())
        assertEquals(listOf("1", "2"), dao.cachedIds())
        assertEquals("a token/with spaces", requests.single().url.parameters["continuationToken"])
        assertEquals("next", dao.state()?.nextPageToken)
    }

    @Test
    fun append_ofTheLastPage_isTheEnd() = runTest {
        dao.savePage("user-1", listOf(cachedBalance("1")), nextPageToken = "last")

        val result = mediator { respondJson(pageJson("2")) }.load(LoadType.APPEND, pagingState)

        assertTrue(result.endReached())
        assertEquals(true, dao.state()?.isEndReached)
    }

    @Test
    fun append_afterTheEnd_doesntCallTheApi() = runTest {
        dao.savePage("user-1", listOf(cachedBalance("1")), nextPageToken = null)

        val result = mediator { respondJson(pageJson()) }.load(LoadType.APPEND, pagingState)

        assertTrue(result.endReached())
        assertEquals(emptyList(), requests)
    }

    @Test
    fun append_beforeTheFirstPage_waitsForIt() = runTest {
        dao.prepareFor("user-1")

        val result = mediator { respondJson(pageJson()) }.load(LoadType.APPEND, pagingState)

        assertFalse(result.endReached())
        assertEquals(emptyList(), requests)
    }

    @Test
    fun prepend_isAlwaysTheEnd() = runTest {
        val result = mediator { respondJson(pageJson()) }.load(LoadType.PREPEND, pagingState)

        assertTrue(result.endReached())
        assertEquals(emptyList(), requests)
    }

    @Test
    fun anEmptyPageWithAToken_keepsGoingUntilThereAreRows() = runTest {
        val mediator = mediator { request ->
            when (request.url.parameters["continuationToken"]) {
                null -> respondJson(pageJson(token = "second"))
                "second" -> respondJson(pageJson(token = "third"))
                else -> respondJson(pageJson("1"))
            }
        }

        val result = mediator.load(LoadType.REFRESH, pagingState)

        assertTrue(result.endReached())
        assertEquals(listOf("1"), dao.cachedIds())
        assertEquals(3, requests.size)
    }

    @Test
    fun aFailedRefresh_leavesTheCacheAsItWas() = runTest {
        dao.savePage("user-1", listOf(cachedBalance("1")), nextPageToken = "next")

        val result = mediator { respond("", HttpStatusCode.ServiceUnavailable) }.load(LoadType.REFRESH, pagingState)

        assertIs<RemoteMediator.MediatorResult.Error>(result)
        assertEquals(listOf("1"), dao.cachedIds())
        assertEquals("next", dao.state()?.nextPageToken)
    }

    @Test
    fun aFailedAppend_keepsTheTokenToRetry() = runTest {
        dao.savePage("user-1", listOf(cachedBalance("1")), nextPageToken = "next")

        val result = mediator { error("offline") }.load(LoadType.APPEND, pagingState)

        assertIs<RemoteMediator.MediatorResult.Error>(result)
        assertEquals("next", dao.state()?.nextPageToken)
    }

    @Test
    fun withoutASignedInUser_itFailsWithoutCallingTheApi() = runTest {
        userId = null

        val result = mediator { respondJson(pageJson("1")) }.load(LoadType.REFRESH, pagingState)

        assertIs<RemoteMediator.MediatorResult.Error>(result)
        assertEquals(emptyList(), requests)
        assertEquals(emptyList(), dao.cachedIds())
    }
}
