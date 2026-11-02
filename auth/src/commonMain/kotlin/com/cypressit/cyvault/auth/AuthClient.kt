package com.cypressit.cyvault.auth

/**
 * Sign-in and session for the app's users. Android talks to Entra External ID through MSAL
 * native auth; iOS goes through [IosAuthBridge][com.cypressit.cyvault.auth.IosAuthBridge],
 * implemented in Swift. Calls throw with a user-readable message when they fail.
 */
interface AuthClient {
    /**
     * Signs in with email and password. When the tenant requires MFA, the user isn't signed in
     * yet: the returned [SignInStep] says what to ask for next.
     */
    suspend fun signIn(email: String, password: String): SignInStep

    /**
     * Registers [phoneNumber] (international format, e.g. +351912345678) as the account's MFA
     * phone, after [SignInStep.PhoneNumberRequired]. A code is texted to it for [submitMfaCode].
     */
    suspend fun submitMfaPhoneNumber(phoneNumber: String): SignInStep

    /** Completes the sign-in with the code from [SignInStep.CodeRequired]. */
    suspend fun submitMfaCode(code: String): AuthUser

    /** Starts sign-up; a verification code is sent to [email] for [confirmSignUp]. */
    suspend fun signUp(name: String, email: String, password: String)

    /** Completes the sign-up started by [signUp] with the emailed code. */
    suspend fun confirmSignUp(email: String, code: String)

    /** Sends a password reset code to [email]. */
    suspend fun resetPassword(email: String)

    /** Signs in through Google, in a browser tab. */
    suspend fun signInWithGoogle(): AuthUser

    /** The signed-in user, or null when nobody is signed in. */
    suspend fun currentUser(): AuthUser?

    /**
     * An access token for the signed-in user with the given API [scopes], for an
     * `Authorization: Bearer` header. Comes from the token cache when possible and is refreshed
     * when it has expired. Throws when nobody is signed in or the token can't be obtained.
     */
    suspend fun accessToken(scopes: List<String>): String

    suspend fun signOut()
}

/** Where an email and password sign-in stands. */
sealed interface SignInStep {
    data class SignedIn(val user: AuthUser) : SignInStep

    /** MFA is required and the account has no phone yet (its first sign-in since MFA). */
    data object PhoneNumberRequired : SignInStep

    /** MFA is required and a code was texted to [sentTo], a masked phone number. */
    data class CodeRequired(val sentTo: String, val codeLength: Int) : SignInStep
}

data class AuthUser(
    /** Entra object ID (oid): stable per account, the same value an API sees in the token. */
    val id: String,
    val name: String,
    val email: String,
)
