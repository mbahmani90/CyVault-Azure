package com.cypressit.cyvault.feature.authentication.presentation.viewmodel

data class AuthState(
    val isLoading: Boolean = false,
    val email: String = "",
    val password: String = "",
    val name: String = "",
    val confirmPassword: String = "",
    val verificationCode: String = "",
    val isRegisterMode: Boolean = false,
    val isForgotPasswordMode: Boolean = false,
    val isVerificationMode: Boolean = false,
    val nameError: String? = null,
    val emailError: String? = null,
    val passwordError: String? = null,
    val confirmPasswordError: String? = null,
    val verificationCodeError: String? = null,
)
