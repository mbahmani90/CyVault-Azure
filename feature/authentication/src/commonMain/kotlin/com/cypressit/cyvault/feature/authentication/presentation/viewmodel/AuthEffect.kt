package com.cypressit.cyvault.feature.authentication.presentation.viewmodel

sealed interface AuthEffect {
    object NavigateToHome : AuthEffect
    data class ShowError(val message: String) : AuthEffect
    data class ShowMessage(val message: String) : AuthEffect

    /** An MFA step failed; [message] is Entra's, or null to show the app's own. */
    data class ShowMfaFailed(val message: String?) : AuthEffect
}
