package com.cypressit.cyvault.feature.vault.home.presentation.viewmodel

import com.cypressit.cyvault.feature.vault.card.domain.model.Card

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
)
