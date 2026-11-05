package com.cypressit.cyvault.feature.vault.home.domain.model

/** What all the user's balances add up to, loaded or not. */
data class BalanceTotals(
    /** Currency to total, one per currency since euros and pounds can't be summed together. */
    val amounts: List<Pair<String, Double>> = emptyList(),
    /** ISO 8601: when the oldest balance happened; null when there are none. */
    val oldestDate: String? = null,
)
