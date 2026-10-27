package com.cypressit.cyvault.feature.vault.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.cypressit.cyvault.feature.vault.addcard.presentation.ui.AddCardScreen
import com.cypressit.cyvault.feature.vault.home.presentation.ui.VaultHomeScreen
import com.cypressit.cyvault.feature.vault.vaultlist.presentation.ui.VaultListScreen

/** The vault feature's single entry point; :app wires the callbacks. */
fun NavGraphBuilder.vaultScreen(onNavigateToLogin: () -> Unit) {
    composable<VaultHomeRoute> {
        VaultNavHost(onNavigateToLogin = onNavigateToLogin)
    }
}

/**
 * Wires the vault's screens together, so the screen packages never import each other:
 * home only gets a content slot and navigation callbacks.
 */
@Composable
private fun VaultNavHost(onNavigateToLogin: () -> Unit) {
    val navController = rememberNavController()
    val currentBackStack by navController.currentBackStackEntryAsState()
    val isOnListScreen = currentBackStack?.destination?.route
        ?.contains(VaultRoute.List::class.simpleName ?: "") != false

    VaultHomeScreen(
        isOnListScreen = isOnListScreen,
        onNavigateToLogin = onNavigateToLogin,
        onNavigateBack = { navController.popBackStack() },
    ) { modifier ->
        NavHost(
            navController = navController,
            startDestination = VaultRoute.List,
            modifier = modifier,
        ) {
            composable<VaultRoute.List> {
                VaultListScreen(onAddCard = { navController.navigate(VaultRoute.AddCard) })
            }
            composable<VaultRoute.AddCard> {
                AddCardScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onCardSaved = { navController.popBackStack() },
                )
            }
        }
    }
}
