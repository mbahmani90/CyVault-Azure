package com.cypressit.cyvault.feature.authentication.presentation.viewmodel

data class MfaPhoneNumberState(
    val isLoading: Boolean = false,
    val phoneNumber: String = "",
    val phoneNumberError: MfaInputError? = null,
)

enum class MfaInputError { InvalidPhoneNumber, IncompleteCode }
