package com.cypressit.cyvault.feature.vault.debug

import androidx.paging.PagingData
import com.cypressit.cyvault.feature.vault.card.domain.model.Card
import com.cypressit.cyvault.feature.vault.card.domain.repository.CardRepository
import com.cypressit.cyvault.feature.vault.home.domain.debug.NoCardsForSamplesException
import com.cypressit.cyvault.feature.vault.home.domain.model.Balance
import com.cypressit.cyvault.feature.vault.home.domain.model.BalanceTotals
import com.cypressit.cyvault.feature.vault.home.domain.model.TransferApproval
import com.cypressit.cyvault.feature.vault.home.domain.model.TransferChallenge
import com.cypressit.cyvault.feature.vault.home.domain.repository.BalanceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.math.round
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class RandomSampleBalancesSeederTest {

    private class FakeBalanceRepository : BalanceRepository {
        var failure: Exception? = null
        val saved = mutableListOf<Balance>()

        override fun balances(): Flow<PagingData<Balance>> = error("Not used")

        override fun loadedBalanceIds(): Flow<Set<String>> = error("Not used")

        override fun totals(): Flow<BalanceTotals> = error("Not used")

        override suspend fun refreshTotals(): Result<Unit> = error("Not used")

        override suspend fun createBalance(balance: Balance): Result<Balance> {
            failure?.let { return Result.failure(it) }
            saved += balance
            return Result.success(balance)
        }

        override suspend fun updateBalance(balance: Balance): Result<Balance> = error("Not used")

        override suspend fun deleteBalance(id: String): Result<Unit> = Result.success(Unit)

        override suspend fun deleteBalances(ids: List<String>): Result<Unit> = Result.success(Unit)

        override val maxDeletesPerRequest = 99

        override suspend fun requestTransferChallenge(
            transferId: String,
            cardId: String,
            phoneNumber: String,
            amount: Double,
            currency: String,
        ): Result<TransferChallenge> = error("Not used")

        override suspend fun sendTransfer(
            transferId: String,
            cardId: String,
            phoneNumber: String,
            amount: Double,
            currency: String,
            approval: TransferApproval,
        ): Result<Balance> = error("Not used")
    }

    private class FakeCardRepository(var serverCards: List<Card>) : CardRepository {
        override val cards = MutableStateFlow(serverCards)

        override suspend fun getCards(): Result<List<Card>> = Result.success(serverCards)

        override suspend fun createCard(
            id: String,
            cardNumber: String,
            phoneNumber: String,
            bankName: String,
        ): Result<Card> = error("Not used")

        override suspend fun deleteCard(id: String): Result<Unit> = error("Not used")
    }

    private fun card(id: String) = Card(id, "1111", "Visa", "CGD", "+351912345678", "2026-10-01T00:00:00Z")

    private val now = Instant.parse("2026-10-28T12:00:00Z")
    private val clock = object : Clock {
        override fun now() = now
    }
    private val repository = FakeBalanceRepository()
    private val cards = FakeCardRepository(listOf(card("card-1"), card("card-2")))

    private fun seeder(count: Int) = RandomSampleBalancesSeeder(repository, cards, count, Random(42), clock)

    @Test
    fun savesTheRequestedNumberOfDistinctBalances() = runTest {
        val result = seeder(count = 25).addSampleBalances()

        assertEquals(25, result.getOrNull())
        assertEquals(25, repository.saved.size)
        assertEquals(25, repository.saved.map { it.id }.toSet().size)
    }

    @Test
    fun samplesAreEurosInTheLast90DaysWithTwoDecimals() = runTest {
        seeder(count = 100).addSampleBalances()

        repository.saved.forEach { balance ->
            assertEquals("EUR", balance.currency)
            assertTrue(balance.name.isNotBlank())
            assertEquals(balance.amount, round(balance.amount * 100) / 100)
            val date = Instant.parse(balance.date!!)
            assertTrue(date <= now && date >= now - 90.days, "date $date")
        }
        // Mostly purchases, with some income.
        assertTrue(repository.saved.any { it.amount > 0 })
        assertTrue(repository.saved.count { it.amount < 0 } > 50)
    }

    @Test
    fun everySampleGoesOnOneOfTheUsersCards() = runTest {
        seeder(count = 50).addSampleBalances()

        assertEquals(setOf("card-1", "card-2"), repository.saved.map { it.cardId }.toSet())
    }

    @Test
    fun withoutCards_failsAndSavesNothing() = runTest {
        cards.serverCards = emptyList()

        assertIs<NoCardsForSamplesException>(seeder(count = 5).addSampleBalances().exceptionOrNull())
        assertTrue(repository.saved.isEmpty())
    }

    @Test
    fun whenASaveFails_reportsTheFailure() = runTest {
        repository.failure = Exception("offline")

        assertTrue(seeder(count = 5).addSampleBalances().isFailure)
    }
}
