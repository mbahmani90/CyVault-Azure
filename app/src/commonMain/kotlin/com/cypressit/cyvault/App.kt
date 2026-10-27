package com.cypressit.cyvault

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.cypressit.cyvault.feature.authentication.navigation.AuthRoute
import com.cypressit.cyvault.feature.authentication.navigation.authScreen
import com.cypressit.cyvault.feature.vault.navigation.AddCardRoute
import com.cypressit.cyvault.feature.vault.navigation.VaultListRoute
import com.cypressit.cyvault.feature.vault.navigation.addCardScreen
import com.cypressit.cyvault.feature.vault.navigation.vaultListScreen

/**
 * The app's only NavHost. Features report events through callbacks;
 * every navigation decision is made here.
 */
@Composable
fun App() {
    MaterialTheme {
        val navController = rememberNavController()

        NavHost(
            navController = navController,
            startDestination = AuthRoute,
        ) {
            authScreen(
                onAuthSuccess = {
                    navController.navigate(VaultListRoute) {
                        popUpTo(AuthRoute) { inclusive = true }
                    }
                }
            )

            vaultListScreen(
                onAddCardClick = { navController.navigate(AddCardRoute) },
                onSignedOut = {
                    navController.navigate(AuthRoute) {
                        popUpTo(VaultListRoute) { inclusive = true }
                    }
                },
            )

            addCardScreen(
                onBack = { navController.popBackStack() },
                onCardSaved = { navController.popBackStack() },
            )
        }
    }
}
