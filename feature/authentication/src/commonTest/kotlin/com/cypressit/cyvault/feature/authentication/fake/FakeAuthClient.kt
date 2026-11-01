package com.cypressit.cyvault.feature.authentication.fake

import com.cypressit.cyvault.auth.AuthClient
import com.cypressit.cyvault.auth.AuthUser

/** Nobody is signed in; the flows the tests don't use aren't faked. */
class FakeAuthClient : AuthClient {

    val resetPasswordEmails = mutableListOf<String>()

    override suspend fun currentUser(): AuthUser? = null

    override suspend fun resetPassword(email: String) {
        resetPasswordEmails += email
    }

    override suspend fun signIn(email: String, password: String): AuthUser = throw NotImplementedError()

    override suspend fun signUp(name: String, email: String, password: String) = throw NotImplementedError()

    override suspend fun confirmSignUp(email: String, code: String) = throw NotImplementedError()

    override suspend fun signInWithGoogle(): AuthUser = throw NotImplementedError()

    override suspend fun accessToken(scopes: List<String>): String = throw NotImplementedError()

    override suspend fun signOut() = throw NotImplementedError()
}
