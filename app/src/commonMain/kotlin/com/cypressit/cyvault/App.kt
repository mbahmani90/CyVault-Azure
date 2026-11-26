package com.cypressit.cyvault

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cypressit.cyvault.feature.vault.AddCardScreen
import com.cypressit.cyvault.feature.vault.VaultHomeScreen
import com.cypressit.cyvault.navigation.AddCardRoute
import com.cypressit.cyvault.navigation.AuthGraphRoute
import com.cypressit.cyvault.navigation.VaultHomeRoute
import com.cypressit.cyvault.navigation.authGraph
import com.cypressit.cyvault.navigation.navigateClearingBackStack
import com.cypressit.cyvault.systemdesign.components.exposeTestTags
import com.cypressit.cyvault.systemdesign.theme.CyTheme

/**
 * The app's only NavHost. Features expose plain screens that report events through
 * callbacks; every route and every navigation decision lives here and in [com.cypressit.cyvault.navigation].
 */
@Composable
fun App() {
    CyTheme {
        val navController = rememberNavController()

        NavHost(
            navController = navController,
            startDestination = AuthGraphRoute,
            // Lets the :e2e tests find screens' test tags
            modifier = Modifier.exposeTestTags(),
        ) {
            authGraph(navController)

            composable<VaultHomeRoute> {
                VaultHomeScreen(
                    onAddCardClick = { navController.navigate(AddCardRoute) },
                    onSignedOut = { navController.navigateClearingBackStack(AuthGraphRoute) },
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
