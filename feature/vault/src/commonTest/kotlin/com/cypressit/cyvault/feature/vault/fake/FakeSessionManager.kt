package com.cypressit.cyvault.feature.vault.fake

import com.cypressit.cyvault.core.session.SessionManager
import com.cypressit.cyvault.core.session.SessionUser

class FakeSessionManager(
    var user: SessionUser? = SessionUser(id = USER_ID, name = "Ada Lovelace", email = "ada@example.com"),
    var token: String = "test-token",
) : SessionManager {

    companion object {
        const val USER_ID = "2ff862e2-8362-40b4-8070-7f9778f660cf"
    }

    val requestedScopes = mutableListOf<List<String>>()
    var signOutCount = 0

    override suspend fun currentUser(): SessionUser? = user

    override suspend fun accessToken(scopes: List<String>): String {
        requestedScopes += scopes
        return token
    }

    override suspend fun signOut(): Result<Unit> {
        signOutCount++
        return Result.success(Unit)
    }
}
