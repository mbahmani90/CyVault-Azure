package com.cypressit.cyvault.feature.authentication.presentation.viewmodel

/** One-off events every auth screen's ViewModel sends to its screen. */
sealed interface AuthEffect {
    /** Leave this screen; the app decides which route [to] maps to. */
    data class Navigate(val to: AuthNavEvent) : AuthEffect
    data class ShowError(val message: String) : AuthEffect
    data class ShowMessage(val message: String) : AuthEffect

    /** An MFA step failed; [message] is Entra's, or null to show the app's own. */
    data class ShowMfaFailed(val message: String?) : AuthEffect
}
