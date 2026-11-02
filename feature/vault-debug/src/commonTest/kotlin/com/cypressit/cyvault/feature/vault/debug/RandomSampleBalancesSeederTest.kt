package com.cypressit.cyvault.feature.vault.debug

import com.cypressit.cyvault.feature.vault.card.domain.model.Card
import com.cypressit.cyvault.feature.vault.card.domain.repository.CardRepository
import com.cypressit.cyvault.feature.vault.home.domain.model.Balance
import com.cypressit.cyvault.feature.vault.home.domain.repository.BalanceRepository
import kotlinx.coroutines.test.runTest
import kotlin.math.round
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class RandomSampleBalancesSeederTest {

    private class FakeBalanceRepository : BalanceRepository {
        var failure: Exception? = null
        val saved = mutableListOf<Balance>()

        override suspend fun getBalances(): Result<List<Balance>> = Result.success(saved)

        override suspend fun saveBalance(balance: Balance): Result<Balance> {
            failure?.let { return Result.failure(it) }
            saved += balance
            return Result.success(balance)
        }

        override suspend fun deleteBalance(id: String): Result<Unit> = Result.success(Unit)
    }

    private class FakeCardRepository(var cards: List<Card>) : CardRepository {
        override suspend fun getCards(): Result<List<Card>> = Result.success(cards)

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
        cards.cards = emptyList()

        assertTrue(seeder(count = 5).addSampleBalances().isFailure)
        assertTrue(repository.saved.isEmpty())
    }

    @Test
    fun whenASaveFails_reportsTheFailure() = runTest {
        repository.failure = Exception("offline")

        assertTrue(seeder(count = 5).addSampleBalances().isFailure)
    }
}
