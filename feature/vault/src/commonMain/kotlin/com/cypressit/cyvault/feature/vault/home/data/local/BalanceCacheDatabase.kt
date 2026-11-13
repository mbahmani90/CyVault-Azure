package com.cypressit.cyvault.feature.vault.home.data.local

import androidx.paging.PagingSource
import androidx.room.ConstructedBy
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow

/** The cached balances' file: only ever a copy of the API's rows, so it can always be thrown away. */
internal const val BALANCE_CACHE_FILE_NAME = "balance-cache.db"

/**
 * One cached balance. [id] is the API's ID, which starts with the date's inverted ticks, so sorting
 * by it gives the API's newest-first order.
 */
@Entity(tableName = "balances")
data class CachedBalance(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val amount: Double,
    val currency: String,
    val date: String?,
    val cardId: String?,
    val type: String,
    val counterparty: String?,
)

/** Whose balances are cached, and where the next page starts. There is only ever this one row. */
@Entity(tableName = "cache_state")
data class BalanceCacheState(
    val ownerId: String,
    /** The API's token for the next page; null before the first page, and after the last. */
    val nextPageToken: String?,
    val isEndReached: Boolean,
    @PrimaryKey val id: Int = ONLY_ROW_ID,
) {
    companion object {
        const val ONLY_ROW_ID = 0
    }
}

/** The last totals from the API, so the card can show them offline. Only ever this one row. */
@Entity(tableName = "balance_totals")
data class CachedBalanceTotals(
    /** Currency to total, as JSON. */
    val amountsJson: String,
    val oldestDate: String?,
    @PrimaryKey val id: Int = ONLY_ROW_ID,
) {
    companion object {
        const val ONLY_ROW_ID = 0
    }
}

/** Removes the signed-in user's cached balances from the device. */
interface BalanceCache {
    suspend fun clear()
}

@Dao
interface BalanceCacheDao : BalanceCache {

    @Query("SELECT * FROM balances ORDER BY id")
    fun balances(): PagingSource<Int, CachedBalance>

    /** The IDs of every balance loaded so far: what "select all" selects. */
    @Query("SELECT id FROM balances")
    fun ids(): Flow<List<String>>

    @Query("SELECT * FROM balance_totals")
    fun totals(): Flow<CachedBalanceTotals?>

    @Upsert
    suspend fun saveTotals(totals: CachedBalanceTotals)

    @Query("SELECT * FROM cache_state")
    suspend fun state(): BalanceCacheState?

    @Query("SELECT MAX(id) FROM balances")
    suspend fun lastId(): String?

    /**
     * Readies the cache for [ownerId]. Another user's balances are dropped first: sign-out clears
     * them too, but it might not have run, so one user's money can never show for another.
     */
    @Transaction
    suspend fun prepareFor(ownerId: String) {
        if (state()?.ownerId == ownerId) return
        clear()
        saveState(BalanceCacheState(ownerId, nextPageToken = null, isEndReached = false))
    }

    /** Replaces everything with the list's first page, as after a pull to refresh. */
    @Transaction
    suspend fun saveFirstPage(ownerId: String, rows: List<CachedBalance>, nextPageToken: String?) {
        deleteBalances()
        savePage(ownerId, rows, nextPageToken)
    }

    /** Adds a following page; a null [nextPageToken] means it was the last. */
    @Transaction
    suspend fun savePage(ownerId: String, rows: List<CachedBalance>, nextPageToken: String?) {
        upsert(rows)
        saveState(BalanceCacheState(ownerId, nextPageToken, isEndReached = nextPageToken == null))
    }

    /**
     * Saves one balance, removing the row [replacesId] when an edited date gave it a new ID. A row
     * older than everything loaded isn't added, as it would show after the last row with unloaded
     * balances missing before it. Paging reaches it later.
     */
    @Transaction
    suspend fun save(row: CachedBalance, replacesId: String? = null) {
        val lastId = lastId()
        val isInLoadedRange = state()?.isEndReached == true || (lastId != null && row.id <= lastId)
        if (replacesId != null && replacesId != row.id) delete(replacesId)
        if (isInLoadedRange) upsert(listOf(row))
    }

    @Query("DELETE FROM balances WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM balances WHERE id IN (:ids)")
    suspend fun deleteAll(ids: List<String>)

    @Transaction
    override suspend fun clear() {
        deleteBalances()
        deleteState()
        deleteTotals()
    }

    @Upsert
    suspend fun upsert(rows: List<CachedBalance>)

    @Upsert
    suspend fun saveState(state: BalanceCacheState)

    @Query("DELETE FROM balances")
    suspend fun deleteBalances()

    @Query("DELETE FROM cache_state")
    suspend fun deleteState()

    @Query("DELETE FROM balance_totals")
    suspend fun deleteTotals()
}

@Database(
    entities = [CachedBalance::class, BalanceCacheState::class, CachedBalanceTotals::class],
    version = 2,
    exportSchema = false,
)
@ConstructedBy(BalanceCacheDatabaseConstructor::class)
abstract class BalanceCacheDatabase : RoomDatabase() {
    abstract fun balanceCacheDao(): BalanceCacheDao
}

/** Room generates the actual object for each platform. */
@Suppress("KotlinNoActualForExpect")
expect object BalanceCacheDatabaseConstructor : RoomDatabaseConstructor<BalanceCacheDatabase> {
    override fun initialize(): BalanceCacheDatabase
}

/**
 * Finishes a platform's database builder. A schema change just drops the tables rather than
 * migrating them, since the API can always fill them again.
 */
internal fun RoomDatabase.Builder<BalanceCacheDatabase>.buildBalanceCache(
    queryDispatcher: CoroutineDispatcher,
): BalanceCacheDatabase = setDriver(BundledSQLiteDriver())
    .setQueryCoroutineContext(queryDispatcher)
    .fallbackToDestructiveMigration(dropAllTables = true)
    .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
    .build()
