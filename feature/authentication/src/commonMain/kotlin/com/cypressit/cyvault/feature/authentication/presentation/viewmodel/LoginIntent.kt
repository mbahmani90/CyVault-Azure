package com.cypressit.cyvault.feature.authentication.presentation.viewmodel

sealed interface LoginIntent {
    data class EmailChanged(val email: String) : LoginIntent
    data class PasswordChanged(val password: String) : LoginIntent
    data object SubmitLogin : LoginIntent
    data object SignInWithGoogle : LoginIntent
    data object OpenRegister : LoginIntent
    data object OpenForgotPassword : LoginIntent

    /** Forgot Password sent the reset email and came back here. */
    data object ResetEmailSent : LoginIntent
}
