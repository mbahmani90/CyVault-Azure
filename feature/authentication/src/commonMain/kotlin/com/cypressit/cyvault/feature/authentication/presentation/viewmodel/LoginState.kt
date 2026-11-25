package com.cypressit.cyvault.feature.authentication.presentation.viewmodel

data class LoginState(
    val isLoading: Boolean = false,
    val email: String = "",
    val password: String = "",
    val emailError: String? = null,
    val passwordError: String? = null,
    /** True in debug builds only, until resetting the password with the code is built. */
    val canResetPassword: Boolean = false,
)
