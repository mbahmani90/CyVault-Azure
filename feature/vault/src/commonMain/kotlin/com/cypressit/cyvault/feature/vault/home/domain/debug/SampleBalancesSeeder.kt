package com.cypressit.cyvault.feature.vault.home.domain.debug

/**
 * Adds a batch of sample balances for trying the app. Only :feature:vault-debug implements it,
 * and only debug builds load that module; without it, the drawer hides the sample-items action.
 */
fun interface SampleBalancesSeeder {
    /** Returns how many balances were saved. Fails with [NoCardsForSamplesException] without a card. */
    suspend fun addSampleBalances(): Result<Int>
}

/** A balance needs a card, and the user has none. */
class NoCardsForSamplesException : Exception("Sample balances need a card.")
