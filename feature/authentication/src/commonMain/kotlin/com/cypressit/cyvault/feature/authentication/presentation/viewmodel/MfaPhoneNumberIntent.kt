package com.cypressit.cyvault.feature.authentication.presentation.viewmodel

sealed interface MfaPhoneNumberIntent {
    data class PhoneNumberChanged(val phoneNumber: String) : MfaPhoneNumberIntent
    data object SubmitPhoneNumber : MfaPhoneNumberIntent
    data object BackToLogin : MfaPhoneNumberIntent
}
