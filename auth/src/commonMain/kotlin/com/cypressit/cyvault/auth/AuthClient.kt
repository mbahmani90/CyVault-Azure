package com.cypressit.cyvault.auth

/**
 * Sign-in and session for the app's users. Android talks to Entra External ID through MSAL
 * native auth; iOS goes through [IosAuthBridge][com.cypressit.cyvault.auth.IosAuthBridge],
 * implemented in Swift. Calls throw with a user-readable message when they fail.
 */
interface AuthClient {
    /** Signs in with email and password. */
    suspend fun signIn(email: String, password: String): AuthUser

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

data class AuthUser(
    /** Entra object ID (oid): stable per account, the same value an API sees in the token. */
    val id: String,
    val name: String,
    val email: String,
)
