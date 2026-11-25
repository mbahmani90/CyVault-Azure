package com.cypressit.cyvault.feature.authentication.presentation.viewmodel

sealed interface RegisterIntent {
    data class NameChanged(val name: String) : RegisterIntent
    data class EmailChanged(val email: String) : RegisterIntent
    data class PasswordChanged(val password: String) : RegisterIntent
    data class ConfirmPasswordChanged(val confirmPassword: String) : RegisterIntent
    data object SubmitRegister : RegisterIntent
    data object BackToLogin : RegisterIntent
}
