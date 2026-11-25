package com.cypressit.cyvault.feature.authentication.presentation.viewmodel

sealed interface VerificationIntent {
    data class CodeChanged(val code: String) : VerificationIntent
    data object SubmitVerification : VerificationIntent
    data object BackToLogin : VerificationIntent
}
