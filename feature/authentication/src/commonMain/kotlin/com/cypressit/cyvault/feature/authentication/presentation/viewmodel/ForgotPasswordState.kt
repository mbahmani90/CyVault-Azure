package com.cypressit.cyvault.feature.authentication.presentation.viewmodel

data class ForgotPasswordState(
    val isLoading: Boolean = false,
    val email: String = "",
    val emailError: String? = null,
)
