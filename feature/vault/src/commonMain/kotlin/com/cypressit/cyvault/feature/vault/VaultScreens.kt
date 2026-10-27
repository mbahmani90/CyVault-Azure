package com.cypressit.cyvault.feature.vault

import androidx.compose.runtime.Composable
import com.cypressit.cyvault.feature.vault.addcard.presentation.ui.AddCardScreen as AddCardContent
import com.cypressit.cyvault.feature.vault.home.presentation.ui.VaultHomeScreen as VaultHomeContent

/*
 * The vault feature's public screens. They only report events; the app module owns routes
 * and navigation, so this feature doesn't depend on Navigation Compose.
 */

/** The vault's home: card list, drawer with sign-out, and an add-card button. */
@Composable
fun VaultHomeScreen(
    onAddCardClick: () -> Unit,
    onSignedOut: () -> Unit,
) {
    VaultHomeContent(
        onAddCardClick = onAddCardClick,
        onSignedOut = onSignedOut,
    )
}

/** Add a card through open banking; it has its own top bar with a back arrow. */
@Composable
fun AddCardScreen(
    onBack: () -> Unit,
    onCardSaved: () -> Unit,
) {
    AddCardContent(
        onNavigateBack = onBack,
        onCardSaved = onCardSaved,
    )
}
