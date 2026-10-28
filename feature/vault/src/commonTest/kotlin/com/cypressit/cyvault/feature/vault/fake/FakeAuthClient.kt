package com.cypressit.cyvault.feature.vault.fake

import com.cypressit.cyvault.auth.AuthClient
import com.cypressit.cyvault.auth.AuthUser

/** The vault only reads the user, gets tokens and signs out; sign-in flows aren't faked. */
class FakeAuthClient(
    var user: AuthUser? = AuthUser(id = USER_ID, name = "Ada Lovelace", email = "ada@example.com"),
    var token: String = "test-token",
) : AuthClient {

    companion object {
        const val USER_ID = "2ff862e2-8362-40b4-8070-7f9778f660cf"
    }

    val requestedScopes = mutableListOf<List<String>>()
    var signOutCount = 0

    override suspend fun currentUser(): AuthUser? = user

    override suspend fun accessToken(scopes: List<String>): String {
        requestedScopes += scopes
        return token
    }

    override suspend fun signOut() {
        signOutCount++
    }

    override suspend fun signIn(email: String, password: String): AuthUser = throw NotImplementedError()
    override suspend fun signUp(name: String, email: String, password: String) = throw NotImplementedError()
    override suspend fun confirmSignUp(email: String, code: String) = throw NotImplementedError()
    override suspend fun resetPassword(email: String) = throw NotImplementedError()
    override suspend fun signInWithGoogle(): AuthUser = throw NotImplementedError()
}
