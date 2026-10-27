package com.cypressit.cyvault.feature.authentication.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.cypressit.cyvault.feature.authentication.presentation.ui.AuthScreen
import kotlinx.serialization.Serializable

@Serializable
data object AuthRoute

/** The authentication feature's single entry point; :app wires the callbacks. */
fun NavGraphBuilder.authScreen(onAuthSuccess: () -> Unit) {
    composable<AuthRoute> {
        AuthScreen(onAuthSuccess = onAuthSuccess)
    }
}
