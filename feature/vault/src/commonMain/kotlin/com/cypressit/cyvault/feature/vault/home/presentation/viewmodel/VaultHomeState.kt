package com.cypressit.cyvault.feature.vault.home.presentation.viewmodel

import com.cypressit.cyvault.feature.vault.card.domain.model.Card
import com.cypressit.cyvault.feature.vault.home.domain.model.Balance

data class VaultHomeState(
    val isLoading: Boolean = false,
    val cards: List<Card> = emptyList(),
    val totalBalance: Double = 0.0,
    val error: String? = null,
    val userName: String = "",
    val userEmail: String = "",
    /** JPEG bytes of the user's profile photo, or null to show the initials avatar. */
    val profilePhoto: ByteArray? = null,
    val isUpdatingPhoto: Boolean = false,
    val isAddItemDialogVisible: Boolean = false,
    val balances: List<Balance> = emptyList(),
    /** True while the balances are being fetched, so the list doesn't claim there are none. */
    val isLoadingBalances: Boolean = false,
    /** The balance being edited in the add-item dialog, or null when adding a new one. */
    val editingBalance: Balance? = null,
    val isSavingBalance: Boolean = false,
) {
    /**
     * The balances added up per currency, since euros and pounds can't be summed together.
     * Each currency appears once, in the order it first appears in [balances].
     */
    val balanceTotals: List<Pair<String, Double>>
        get() = balances
            .groupBy { it.currency }
            .map { (currency, items) -> currency to items.sumOf { it.amount } }
}
