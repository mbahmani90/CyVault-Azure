package com.cypressit.cyvault.feature.authentication.session

import com.cypressit.cyvault.feature.authentication.domain.repository.AuthRepository
import com.cypressit.cyvault.core.session.SessionManager
import com.cypressit.cyvault.core.session.SessionUser

class SessionManagerImpl(
    private val repository: AuthRepository,
) : SessionManager {
    override suspend fun currentUser(): SessionUser? =
        repository.getCurrentUser()?.let { SessionUser(id = it.id, name = it.name, email = it.email) }

    override suspend fun accessToken(scopes: List<String>): String = repository.accessToken(scopes)

    override suspend fun signOut(): Result<Unit> = repository.signOut()
}
