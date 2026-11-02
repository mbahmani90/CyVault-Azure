package com.cypressit.cyvault.feature.authentication.domain.model

/** Where a login stands: signed in, or waiting for an SMS MFA step. */
sealed interface LoginResult {
    data class SignedIn(val user: User) : LoginResult

    /** The account's first MFA sign-in: it needs a phone number to text codes to. */
    data object PhoneNumberRequired : LoginResult

    /** A code was texted to [sentTo], a masked phone number. */
    data class CodeRequired(val sentTo: String, val codeLength: Int) : LoginResult
}
