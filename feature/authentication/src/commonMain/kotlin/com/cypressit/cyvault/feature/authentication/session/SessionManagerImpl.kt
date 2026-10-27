package com.cypressit.cyvault.feature.authentication.session

import com.cypressit.cyvault.feature.authentication.domain.repository.AuthRepository
import com.cypressit.cyvault.core.session.SessionManager

class SessionManagerImpl(
    private val repository: AuthRepository,
) : SessionManager {
    override suspend fun signOut(): Result<Unit> = repository.signOut()
}
