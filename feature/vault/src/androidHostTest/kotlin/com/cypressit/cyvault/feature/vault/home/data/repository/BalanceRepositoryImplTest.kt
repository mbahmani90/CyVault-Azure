package com.cypressit.cyvault.feature.vault.home.data.repository

import com.cypressit.cyvault.feature.vault.home.domain.model.Balance
import com.cypressit.cyvault.feature.vault.home.domain.model.BalanceTotals
import com.cypressit.cyvault.feature.vault.home.domain.model.BalanceType
import com.cypressit.cyvault.feature.vault.home.domain.model.TransferApproval
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The repository against the real queries, with the API's responses scripted. */
@RunWith(RobolectricTestRunner::class)
class BalanceRepositoryImplTest {

    private val database = inMemoryBalanceCache()
    private val dao = database.balanceCacheDao()

    @After fun tearDown() = database.close()

    private fun repository(
        userId: String = "user-1",
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) = BalanceRepositoryImpl(balanceApi(handler), dao, currentUserId = { userId })

    private fun failing() = repository { respond("", HttpStatusCode.ServiceUnavailable) }

    private val balance =
        Balance(id = "uuid", name = "Lidl", amount = 1.5, currency = "EUR", date = "2026-10-28T09:00:00Z")

    private val approval = TransferApproval(deviceId = "device", nonce = "nonce", signature = "signature")

    @Test
    fun create_inTheLoadedRange_returnsItAsStoredAndCachesIt() = runTest {
        dao.savePage("user-1", listOf(cachedBalance("2520_older")), nextPageToken = "next")

        val saved = repository { respondJson(balanceJson("2510_uuid", name = "Stored")) }.createBalance(balance)

        assertEquals("2510_uuid", saved.getOrThrow().id)
        assertEquals("Stored", saved.getOrThrow().name)
        assertEquals(listOf("2510_uuid", "2520_older"), dao.cachedIds())
    }

    @Test
    fun create_olderThanEverythingLoaded_isLeftForPagingToReach() = runTest {
        dao.savePage("user-1", listOf(cachedBalance("2510_newer")), nextPageToken = "next")

        repository { respondJson(balanceJson("2520_uuid")) }.createBalance(balance).getOrThrow()

        assertEquals(listOf("2510_newer"), dao.cachedIds())
    }

    @Test
    fun create_olderThanEverything_afterTheLastPage_isCached() = runTest {
        dao.savePage("user-1", listOf(cachedBalance("2510_newer")), nextPageToken = null)

        repository { respondJson(balanceJson("2520_uuid")) }.createBalance(balance).getOrThrow()

        assertEquals(listOf("2510_newer", "2520_uuid"), dao.cachedIds())
    }

    @Test
    fun create_whenTheApiFails_leavesTheCacheAlone() = runTest {
        dao.savePage("user-1", emptyList(), nextPageToken = null)

        assertTrue(failing().createBalance(balance).isFailure)

        assertEquals(emptyList(), dao.cachedIds())
    }

    @Test
    fun update_withANewId_replacesTheOldRow() = runTest {
        dao.savePage("user-1", listOf(cachedBalance("2510_uuid"), cachedBalance("2520_other")), nextPageToken = "next")

        repository { respondJson(balanceJson("2515_uuid")) }.updateBalance(balance.copy(id = "2510_uuid")).getOrThrow()

        assertEquals(listOf("2515_uuid", "2520_other"), dao.cachedIds())
    }

    @Test
    fun update_movedOlderThanEverythingLoaded_leavesTheListUntilPagingReachesIt() = runTest {
        dao.savePage("user-1", listOf(cachedBalance("2510_uuid"), cachedBalance("2520_other")), nextPageToken = "next")

        repository { respondJson(balanceJson("2530_uuid")) }.updateBalance(balance.copy(id = "2510_uuid")).getOrThrow()

        assertEquals(listOf("2520_other"), dao.cachedIds())
    }

    @Test
    fun update_ofTheLastLoadedRow_keepingItsId_staysInTheList() = runTest {
        dao.savePage("user-1", listOf(cachedBalance("2510_a"), cachedBalance("2520_b", name = "Before")), "next")

        repository { respondJson(balanceJson("2520_b", name = "After")) }
            .updateBalance(balance.copy(id = "2520_b"))
            .getOrThrow()

        assertEquals("After", dao.cachedRows().last().name)
    }

    @Test
    fun update_whenTheApiFails_leavesTheCacheAlone() = runTest {
        dao.savePage("user-1", listOf(cachedBalance("2510_uuid")), nextPageToken = null)

        assertTrue(failing().updateBalance(balance.copy(id = "2510_uuid")).isFailure)

        assertEquals(listOf("2510_uuid"), dao.cachedIds())
    }

    @Test
    fun delete_removesTheRow() = runTest {
        dao.savePage("user-1", listOf(cachedBalance("1"), cachedBalance("2")), nextPageToken = null)

        repository { respond("", HttpStatusCode.NoContent) }.deleteBalance("1").getOrThrow()

        assertEquals(listOf("2"), dao.cachedIds())
    }

    @Test
    fun delete_whenTheApiFails_keepsTheRow() = runTest {
        dao.savePage("user-1", listOf(cachedBalance("1")), nextPageToken = null)

        assertTrue(failing().deleteBalance("1").isFailure)

        assertEquals(listOf("1"), dao.cachedIds())
    }

    @Test
    fun sendTransfer_cachesTheSentRow() = runTest {
        dao.savePage("user-1", emptyList(), nextPageToken = null)
        val json = """{"id":"2510_t-sent","name":"","amount":20.0,"currency":"EUR","type":"Transfer"}"""

        val sent = repository { respondJson(json) }
            .sendTransfer("t", "card-1", "+351912345678", 20.0, "EUR", approval)

        assertEquals(BalanceType.Transfer, sent.getOrThrow().type)
        assertEquals(listOf("2510_t-sent"), dao.cachedIds())
    }

    @Test
    fun sendTransfer_whenTheApiFails_cachesNothing() = runTest {
        dao.savePage("user-1", emptyList(), nextPageToken = null)

        assertTrue(failing().sendTransfer("t", "card-1", "+351912345678", 20.0, "EUR", approval).isFailure)

        assertEquals(emptyList(), dao.cachedIds())
    }

    @Test
    fun totals_beforeAnyFetch_areEmpty() = runTest {
        assertEquals(BalanceTotals(), repository { respondJson("{}") }.totals().first())
    }

    @Test
    fun refreshTotals_updatesTheTotals() = runTest {
        val amounts = """[{"currency":"EUR","total":1250.5},{"currency":"USD","total":-40.0}]"""
        val json = """{"oldestDate":"2026-08-08T16:58:51Z","totals":$amounts}"""
        val repository = repository { respondJson(json) }

        repository.refreshTotals().getOrThrow()

        assertEquals(
            BalanceTotals(listOf("EUR" to 1250.5, "USD" to -40.0), "2026-08-08T16:58:51Z"),
            repository.totals().first(),
        )
    }

    @Test
    fun refreshTotals_whenItFails_keepsTheLastOnesForOffline() = runTest {
        repository { respondJson("""{"totals":[{"currency":"EUR","total":10.0}]}""") }.refreshTotals().getOrThrow()

        val offline = failing()

        assertTrue(offline.refreshTotals().isFailure)
        assertEquals(listOf("EUR" to 10.0), offline.totals().first().amounts)
    }

    @Test
    fun anotherUser_seesNoneOfTheFirstUsersTotalsOrBalances() = runTest {
        dao.savePage("user-1", listOf(cachedBalance("1")), nextPageToken = null)
        repository { respondJson("""{"totals":[{"currency":"EUR","total":10.0}]}""") }.refreshTotals().getOrThrow()

        val next = repository(userId = "user-2") { respondJson("{}") }

        assertEquals(BalanceTotals(), next.totals().first())
        assertEquals(emptySet(), next.loadedBalanceIds().first())
    }

    @Test
    fun loadedBalanceIds_followTheCache() = runTest {
        dao.savePage("user-1", listOf(cachedBalance("1"), cachedBalance("2")), nextPageToken = null)

        assertEquals(setOf("1", "2"), repository { respondJson("{}") }.loadedBalanceIds().first())
    }
}
