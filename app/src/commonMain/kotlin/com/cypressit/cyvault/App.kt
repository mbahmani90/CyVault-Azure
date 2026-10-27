package com.cypressit.cyvault

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.cypressit.cyvault.feature.authentication.navigation.AuthRoute
import com.cypressit.cyvault.feature.authentication.navigation.authScreen
import com.cypressit.cyvault.feature.vault.navigation.VaultHomeRoute
import com.cypressit.cyvault.feature.vault.navigation.vaultScreen

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
                    navController.navigate(VaultHomeRoute) {
                        popUpTo(AuthRoute) { inclusive = true }
                    }
                }
            )

            vaultScreen(
                onNavigateToLogin = {
                    navController.navigate(AuthRoute) {
                        popUpTo(VaultHomeRoute) { inclusive = true }
                    }
                }
            )
        }
    }
}
