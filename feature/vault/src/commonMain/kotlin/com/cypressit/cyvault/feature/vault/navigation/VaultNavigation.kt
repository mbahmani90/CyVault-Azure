package com.cypressit.cyvault.feature.vault.navigation

import androidx.compose.foundation.layout.Box
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.cypressit.cyvault.feature.vault.addcard.presentation.ui.AddCardScreen
import com.cypressit.cyvault.feature.vault.home.presentation.ui.VaultScaffold
import com.cypressit.cyvault.feature.vault.vaultlist.presentation.ui.VaultListScreen

/*
 * The vault's destinations in app's NavHost. They only report events; app decides
 * where each one leads, so the feature never navigates by itself.
 */

fun NavGraphBuilder.vaultListScreen(
    onAddCardClick: () -> Unit,
    onSignedOut: () -> Unit,
) {
    composable<VaultListRoute> {
        VaultScaffold(
            showMenu = true,
            onBack = {},
            onSignedOut = onSignedOut,
        ) { modifier ->
            Box(modifier) {
                VaultListScreen(onAddCard = onAddCardClick)
            }
        }
    }
}

fun NavGraphBuilder.addCardScreen(
    onBack: () -> Unit,
    onCardSaved: () -> Unit,
) {
    composable<AddCardRoute> {
        VaultScaffold(
            showMenu = false,
            onBack = onBack,
            onSignedOut = {},
        ) { modifier ->
            Box(modifier) {
                AddCardScreen(
                    onNavigateBack = onBack,
                    onCardSaved = onCardSaved,
                )
            }
        }
    }
}
