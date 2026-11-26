package com.cypressit.cyvault.feature.authentication.presentation.viewmodel

data class VerificationState(
    /** Where the code was sent. */
    val email: String,
    val isLoading: Boolean = false,
    val verificationCode: String = "",
    val verificationCodeError: String? = null,
)
