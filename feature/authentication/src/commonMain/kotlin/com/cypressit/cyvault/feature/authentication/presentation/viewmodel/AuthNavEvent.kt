package com.cypressit.cyvault.feature.authentication.presentation.viewmodel

import com.cypressit.cyvault.feature.authentication.domain.model.LoginResult

/**
 * Where an auth screen asks to go next. The feature doesn't know the app's routes:
 * the app maps each event to one and decides how the back stack changes.
 */
sealed interface AuthNavEvent {
    /** Signed in: leave the auth flow. */
    data object Home : AuthNavEvent
    data object Register : AuthNavEvent
    data class ForgotPassword(val email: String) : AuthNavEvent

    /** The account was created; [email] gets the code that confirms it. */
    data class Verification(val email: String) : AuthNavEvent

    /** The account's first MFA sign-in: ask for the phone number codes go to. */
    data object MfaPhoneNumber : AuthNavEvent

    /** A code was texted to [sentTo] (masked). */
    data class MfaCode(val sentTo: String, val codeLength: Int) : AuthNavEvent

    /** Back to the sign-in screen; [resetEmailSent] tells it to say the reset email went out. */
    data class BackToLogin(val resetEmailSent: Boolean = false) : AuthNavEvent
}

/** The screen a sign-in step leads to. */
internal fun LoginResult.toNavEvent(): AuthNavEvent = when (this) {
    is LoginResult.SignedIn -> AuthNavEvent.Home
    LoginResult.PhoneNumberRequired -> AuthNavEvent.MfaPhoneNumber
    is LoginResult.CodeRequired -> AuthNavEvent.MfaCode(
        sentTo = sentTo,
        codeLength = codeLength.takeIf { it > 0 } ?: MfaCodeState.DEFAULT_CODE_LENGTH,
    )
}
