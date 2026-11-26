package com.cypressit.cyvault.navigation

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.toRoute
import com.cypressit.cyvault.feature.authentication.presentation.ui.ForgotPasswordScreen
import com.cypressit.cyvault.feature.authentication.presentation.ui.LoginScreen
import com.cypressit.cyvault.feature.authentication.presentation.ui.MfaCodeScreen
import com.cypressit.cyvault.feature.authentication.presentation.ui.MfaPhoneNumberScreen
import com.cypressit.cyvault.feature.authentication.presentation.ui.RegisterScreen
import com.cypressit.cyvault.feature.authentication.presentation.ui.VerificationScreen
import com.cypressit.cyvault.feature.authentication.presentation.viewmodel.AuthNavEvent

/** Set on the sign-in entry when Forgot Password sent the reset email, so sign-in can say so. */
private const val RESET_EMAIL_SENT = "resetEmailSent"

/**
 * The sign-in flow. Sign-in stays at the bottom of it: every other auth screen goes back there,
 * and signing in clears the whole flow so Back can't return to it.
 */
internal fun NavGraphBuilder.authGraph(navController: NavController) {
    val onNavigate: (AuthNavEvent) -> Unit = { navController.navigateAuth(it) }

    navigation<AuthGraphRoute>(startDestination = LoginRoute) {
        composable<LoginRoute> { entry ->
            val resetEmailSent by entry.savedStateHandle
                .getStateFlow(RESET_EMAIL_SENT, false)
                .collectAsState()
            LoginScreen(
                resetEmailSent = resetEmailSent,
                onResetEmailSentShown = { entry.savedStateHandle[RESET_EMAIL_SENT] = false },
                onNavigate = onNavigate,
            )
        }

        composable<RegisterRoute> {
            RegisterScreen(onNavigate = onNavigate)
        }

        composable<VerificationRoute> { entry ->
            VerificationScreen(email = entry.toRoute<VerificationRoute>().email, onNavigate = onNavigate)
        }

        composable<ForgotPasswordRoute> { entry ->
            ForgotPasswordScreen(email = entry.toRoute<ForgotPasswordRoute>().email, onNavigate = onNavigate)
        }

        composable<MfaPhoneNumberRoute> {
            MfaPhoneNumberScreen(onNavigate = onNavigate)
        }

        composable<MfaCodeRoute> { entry ->
            val route = entry.toRoute<MfaCodeRoute>()
            MfaCodeScreen(sentTo = route.sentTo, codeLength = route.codeLength, onNavigate = onNavigate)
        }
    }
}

internal fun NavController.navigateAuth(event: AuthNavEvent) {
    when (event) {
        AuthNavEvent.Home -> navigateClearingBackStack(VaultHomeRoute)
        AuthNavEvent.Register -> navigate(RegisterRoute) { launchSingleTop = true }
        is AuthNavEvent.ForgotPassword -> navigate(ForgotPasswordRoute(event.email)) { launchSingleTop = true }
        AuthNavEvent.MfaPhoneNumber -> navigate(MfaPhoneNumberRoute) { launchSingleTop = true }

        // Each takes the place of the step before it, so Back from it goes to sign-in:
        // the account already exists, and Entra has moved past the phone number.
        is AuthNavEvent.Verification -> navigate(VerificationRoute(event.email)) { popUpTo<LoginRoute>() }
        is AuthNavEvent.MfaCode ->
            navigate(MfaCodeRoute(event.sentTo, event.codeLength)) { popUpTo<LoginRoute>() }

        is AuthNavEvent.BackToLogin -> {
            popBackStack<LoginRoute>(inclusive = false)
            if (event.resetEmailSent) currentBackStackEntry?.savedStateHandle?.set(RESET_EMAIL_SENT, true)
        }
    }
}
