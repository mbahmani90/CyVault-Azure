package com.cypressit.cyvault

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cypressit.cyvault.feature.authentication.presentation.ui.AuthScreen
import com.cypressit.cyvault.feature.vault.AddCardScreen
import com.cypressit.cyvault.feature.vault.VaultListScreen
import com.cypressit.cyvault.navigation.AddCardRoute
import com.cypressit.cyvault.navigation.AuthRoute
import com.cypressit.cyvault.navigation.VaultListRoute

/**
 * The app's only NavHost. Features expose plain screens that report events through
 * callbacks; every route and every navigation decision lives here.
 */
@Composable
fun App() {
    MaterialTheme {
        val navController = rememberNavController()

        NavHost(
            navController = navController,
            startDestination = AuthRoute,
        ) {
            composable<AuthRoute> {
                AuthScreen(
                    onAuthSuccess = {
                        navController.navigate(VaultListRoute) {
                            popUpTo(AuthRoute) { inclusive = true }
                        }
                    }
                )
            }

            composable<VaultListRoute> {
                VaultListScreen(
                    onAddCardClick = { navController.navigate(AddCardRoute) },
                    onSignedOut = {
                        navController.navigate(AuthRoute) {
                            popUpTo(VaultListRoute) { inclusive = true }
                        }
                    },
                )
            }

            composable<AddCardRoute> {
                AddCardScreen(
                    onBack = { navController.popBackStack() },
                    onCardSaved = { navController.popBackStack() },
                )
            }
        }
    }
}
