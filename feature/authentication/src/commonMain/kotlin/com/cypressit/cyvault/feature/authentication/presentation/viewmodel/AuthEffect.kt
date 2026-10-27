package com.cypressit.cyvault.feature.authentication.presentation.viewmodel

sealed class AuthEffect {
    object NavigateToHome : AuthEffect()
    data class ShowError(val message: String) : AuthEffect()
    data class ShowMessage(val message: String) : AuthEffect()
}
