package com.cypressit.cyvault.feature.vault.home.data.local

import androidx.paging.PagingSource
import androidx.room.Room
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** Runs the real queries on an in-memory database, on Robolectric's SQLite. */
@RunWith(RobolectricTestRunner::class)
class BalanceCacheDaoTest {

    private val database = Room.inMemoryDatabaseBuilder(
        RuntimeEnvironment.getApplication(),
        BalanceCacheDatabase::class.java,
    ).allowMainThreadQueries().build()
    private val dao = database.balanceCacheDao()

    @After fun tearDown() = database.close()

    private fun row(id: String, name: String = id) = CachedBalance(
        id = id,
        name = name,
        description = "",
        amount = 1.5,
        currency = "EUR",
        date = "2026-10-28T09:00:00Z",
        cardId = "card-1",
        type = "Income",
        counterparty = null,
    )

    private suspend fun cachedIds(loadSize: Int = 100): List<String> {
        val result = dao.balances().load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = loadSize, placeholdersEnabled = false),
        )
        return assertIs<PagingSource.LoadResult.Page<Int, CachedBalance>>(result).data.map { it.id }
    }

    @Test
    fun balances_comeBackSortedById_whichIsNewestFirst() = runTest {
        dao.upsert(listOf(row("2516_oldest-is-highest"), row("2514_newest"), row("2515_middle")))

        assertEquals(listOf("2514_newest", "2515_middle", "2516_oldest-is-highest"), cachedIds())
    }

    @Test
    fun balances_loadOnlyWhatIsAskedFor() = runTest {
        dao.upsert(listOf(row("1"), row("2"), row("3")))

        assertEquals(listOf("1", "2"), cachedIds(loadSize = 2))
    }

    @Test
    fun state_beforeAnything_isNull() = runTest {
        assertNull(dao.state())
    }

    @Test
    fun prepareFor_aNewUser_startsAtTheFirstPage() = runTest {
        dao.prepareFor("user-1")

        assertEquals(BalanceCacheState("user-1", nextPageToken = null, isEndReached = false), dao.state())
    }

    @Test
    fun prepareFor_theSameUser_keepsTheirCache() = runTest {
        dao.prepareFor("user-1")
        dao.savePage("user-1", listOf(row("1")), nextPageToken = "next")

        dao.prepareFor("user-1")

        assertEquals(listOf("1"), cachedIds())
        assertEquals("next", dao.state()?.nextPageToken)
    }

    @Test
    fun prepareFor_anotherUser_dropsTheFirstUsersBalances() = runTest {
        dao.prepareFor("user-1")
        dao.savePage("user-1", listOf(row("1")), nextPageToken = "next")

        dao.prepareFor("user-2")

        assertEquals(emptyList(), cachedIds())
        assertEquals(BalanceCacheState("user-2", nextPageToken = null, isEndReached = false), dao.state())
    }

    @Test
    fun savePage_addsToWhatIsCached_andKeepsTheToken() = runTest {
        dao.savePage("user-1", listOf(row("1")), nextPageToken = "page-2")
        dao.savePage("user-1", listOf(row("2")), nextPageToken = "page-3")

        assertEquals(listOf("1", "2"), cachedIds())
        assertEquals(BalanceCacheState("user-1", "page-3", isEndReached = false), dao.state())
    }

    @Test
    fun savePage_withoutAToken_isTheEnd() = runTest {
        dao.savePage("user-1", listOf(row("1")), nextPageToken = null)

        assertEquals(BalanceCacheState("user-1", null, isEndReached = true), dao.state())
    }

    @Test
    fun saveFirstPage_replacesEverything() = runTest {
        dao.savePage("user-1", listOf(row("1"), row("2")), nextPageToken = null)

        dao.saveFirstPage("user-1", listOf(row("3")), nextPageToken = "page-2")

        assertEquals(listOf("3"), cachedIds())
        assertEquals(BalanceCacheState("user-1", "page-2", isEndReached = false), dao.state())
    }

    @Test
    fun save_anEditWithANewId_replacesTheOldRow() = runTest {
        dao.upsert(listOf(row("2516_x"), row("2517_y")))

        dao.save(row("2515_x"), replacesId = "2516_x")

        assertEquals(listOf("2515_x", "2517_y"), cachedIds())
    }

    @Test
    fun save_anEditKeepingItsId_updatesTheRow() = runTest {
        dao.upsert(listOf(row("1", name = "before")))

        dao.save(row("1", name = "after"), replacesId = "1")

        assertEquals(listOf("1"), cachedIds())
        val page = dao.balances().load(PagingSource.LoadParams.Refresh(null, 10, false))
        assertEquals("after", assertIs<PagingSource.LoadResult.Page<Int, CachedBalance>>(page).data.single().name)
    }

    @Test
    fun delete_removesOnlyThatRow() = runTest {
        dao.upsert(listOf(row("1"), row("2")))

        dao.delete("1")

        assertEquals(listOf("2"), cachedIds())
    }

    @Test
    fun clear_removesBalancesAndState() = runTest {
        dao.savePage("user-1", listOf(row("1")), nextPageToken = "next")

        dao.clear()

        assertEquals(emptyList(), cachedIds())
        assertNull(dao.state())
    }
}
