package com.cypressit.cyvault.feature.authentication.presentation.viewmodel

sealed interface MfaCodeIntent {
    data class CodeChanged(val code: String) : MfaCodeIntent
    data object SubmitCode : MfaCodeIntent
    data object BackToLogin : MfaCodeIntent
}
