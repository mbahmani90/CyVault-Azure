package com.cypressit.cyvault.feature.authentication.data.repository

import com.cypressit.cyvault.auth.AuthClient
import com.cypressit.cyvault.feature.authentication.data.mapper.toDomain
import com.cypressit.cyvault.feature.authentication.domain.model.User
import com.cypressit.cyvault.feature.authentication.domain.repository.AuthRepository

class AuthRepositoryImpl(
    private val authClient: AuthClient,
) : AuthRepository {

    override suspend fun login(email: String, password: String): Result<User> =
        runCatching { authClient.signIn(email, password).toDomain() }

    override suspend fun register(name: String, email: String, password: String): Result<Unit> =
        runCatching { authClient.signUp(name, email, password) }

    override suspend fun confirmSignUp(email: String, code: String): Result<Unit> =
        runCatching { authClient.confirmSignUp(email, code) }

    override suspend fun forgotPassword(email: String): Result<Unit> =
        runCatching { authClient.resetPassword(email) }

    override suspend fun signOut(): Result<Unit> =
        runCatching { authClient.signOut() }

    override suspend fun getCurrentUser(): User? =
        authClient.currentUser()?.toDomain()

    override suspend fun signInWithGoogle(): Result<User> =
        runCatching { authClient.signInWithGoogle().toDomain() }
}
