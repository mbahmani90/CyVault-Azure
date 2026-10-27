package com.cypressit.cyvault.feature.vault.vaultlist.presentation.viewmodel

import com.cypressit.cyvault.feature.vault.card.domain.model.Card

data class VaultListState(
    val isLoading: Boolean = false,
    val cards: List<Card> = emptyList(),
    val totalBalance: Double = 0.0,
    val error: String? = null,
)
