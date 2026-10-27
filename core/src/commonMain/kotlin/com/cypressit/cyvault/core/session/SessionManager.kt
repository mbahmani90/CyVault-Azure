package com.cypressit.cyvault.core.session

interface SessionManager {
    suspend fun signOut(): Result<Unit>
}
