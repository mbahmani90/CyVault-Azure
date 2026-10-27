package com.cypressit.cyvault.core.session

interface SessionManager {
    /** The signed-in user, or null when nobody is signed in. */
    suspend fun currentUser(): SessionUser?

    suspend fun signOut(): Result<Unit>
}

/** What other features may know about the signed-in user, without depending on authentication. */
data class SessionUser(
    val name: String,
    val email: String,
)
