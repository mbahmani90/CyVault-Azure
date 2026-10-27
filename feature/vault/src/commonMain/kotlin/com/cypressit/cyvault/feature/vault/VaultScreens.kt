package com.cypressit.cyvault.feature.vault

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import com.cypressit.cyvault.feature.vault.home.presentation.ui.VaultScaffold
import com.cypressit.cyvault.feature.vault.addcard.presentation.ui.AddCardScreen as AddCardContent
import com.cypressit.cyvault.feature.vault.vaultlist.presentation.ui.VaultListScreen as VaultListContent

/*
 * The vault feature's public screens. Each one wraps a screen package in the vault's shell
 * (drawer, top bar, sign-out) and only reports events; the app module owns routes and
 * navigation, so this feature doesn't depend on Navigation Compose.
 */

/** The vault's root screen: the card list, with the menu and drawer. */
@Composable
fun VaultListScreen(
    onAddCardClick: () -> Unit,
    onSignedOut: () -> Unit,
) {
    VaultScaffold(
        showMenu = true,
        onBack = {},
        onSignedOut = onSignedOut,
    ) { modifier ->
        Box(modifier) {
            VaultListContent(onAddCard = onAddCardClick)
        }
    }
}

/** Add a card through open banking, with a back arrow. */
@Composable
fun AddCardScreen(
    onBack: () -> Unit,
    onCardSaved: () -> Unit,
) {
    VaultScaffold(
        showMenu = false,
        onBack = onBack,
        onSignedOut = {},
    ) { modifier ->
        Box(modifier) {
            AddCardContent(
                onNavigateBack = onBack,
                onCardSaved = onCardSaved,
            )
        }
    }
}
