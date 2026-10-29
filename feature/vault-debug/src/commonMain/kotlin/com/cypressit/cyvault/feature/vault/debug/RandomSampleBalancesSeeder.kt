package com.cypressit.cyvault.feature.vault.debug

import com.cypressit.cyvault.feature.vault.home.domain.debug.SampleBalancesSeeder
import com.cypressit.cyvault.feature.vault.home.domain.model.Balance
import com.cypressit.cyvault.feature.vault.home.domain.repository.BalanceRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.math.roundToLong
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Saves [count] random purchases and incomes, dated over the last 90 days, so the home screen
 * can be tried with a long list. Fails if any save fails, though the ones saved before it stay.
 */
class RandomSampleBalancesSeeder(
    private val repository: BalanceRepository,
    private val count: Int = DEFAULT_COUNT,
    private val random: Random = Random.Default,
    private val clock: Clock = Clock.System,
) : SampleBalancesSeeder {

    companion object {
        const val DEFAULT_COUNT = 50

        // Saves run a few at a time: one by one is slow, all at once floods the API.
        private const val PARALLEL_SAVES = 10

        private val purchases = listOf(
            "Lidl" to "Groceries", "Aldi" to "Groceries", "Rewe" to "Weekly shop", "Edeka" to "Fruit",
            "Bakery" to "Bread", "Cafe" to "Coffee", "Pharmacy" to "Medicine", "Amazon" to "Books",
            "Shell" to "Fuel", "Deutsche Bahn" to "Train ticket", "Netflix" to "Subscription",
            "Restaurant" to "Dinner", "IKEA" to "Furniture", "Zara" to "Clothes", "Gym" to "Membership",
        )
        private val incomes = listOf(
            "Salary" to "Monthly salary", "Freelance" to "Invoice paid", "Refund" to "Returned order",
            "Gift" to "Birthday", "Interest" to "Savings interest",
        )
    }

    override suspend fun addSampleBalances(): Result<Int> {
        val samples = List(count) { sample() }
        val results = coroutineScope {
            samples.chunked(PARALLEL_SAVES).flatMap { chunk ->
                chunk.map { async { repository.saveBalance(it) } }.awaitAll()
            }
        }
        return results.firstOrNull { it.isFailure }?.let { Result.failure(it.exceptionOrNull()!!) }
            ?: Result.success(results.size)
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun sample(): Balance {
        // About one in eight is income, like a real account.
        val isIncome = random.nextInt(8) == 0
        val (name, description) = (if (isIncome) incomes else purchases).random(random)
        val amount = if (isIncome) random.nextDouble(50.0, 3000.0) else -random.nextDouble(1.0, 150.0)
        val date = clock.now() - random.nextLong(90.days.inWholeMinutes).minutes
        return Balance(
            id = Uuid.random().toHexString(),
            name = name,
            description = description,
            amount = (amount * 100).roundToLong() / 100.0,
            currency = "EUR",
            date = date.toString(),
        )
    }
}
