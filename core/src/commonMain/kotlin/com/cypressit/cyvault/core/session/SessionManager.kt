package com.cypressit.cyvault.core.session

interface SessionManager {
    /** The signed-in user, or null when nobody is signed in. */
    suspend fun currentUser(): SessionUser?

    /**
     * An access token for the signed-in user with the given API [scopes], for an
     * `Authorization: Bearer` header. Comes from the token cache when possible and is refreshed
     * when it has expired. Throws when nobody is signed in or the token can't be obtained.
     */
    suspend fun accessToken(scopes: List<String>): String

    suspend fun signOut(): Result<Unit>
}

/** What other features may know about the signed-in user, without depending on authentication. */
data class SessionUser(
    /** Entra object ID (oid): stable per account, the same value the backend sees in the token. */
    val id: String,
    val name: String,
    val email: String,
)
