package com.cypressit.cyvault.feature.authentication.presentation.viewmodel

sealed interface ForgotPasswordIntent {
    data class EmailChanged(val email: String) : ForgotPasswordIntent
    data object SubmitForgotPassword : ForgotPasswordIntent
    data object BackToLogin : ForgotPasswordIntent
}
