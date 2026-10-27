package com.cypressit.cyvault.feature.authentication.domain.repository

import com.cypressit.cyvault.feature.authentication.domain.model.User

interface AuthRepository {
    suspend fun login(email: String, password: String): Result<User>
    suspend fun register(name: String, email: String, password: String): Result<Unit>
    suspend fun confirmSignUp(email: String, code: String): Result<Unit>
    suspend fun forgotPassword(email: String): Result<Unit>
    suspend fun signOut(): Result<Unit>
    suspend fun getCurrentUser(): User?
    suspend fun signInWithGoogle(): Result<User>
    suspend fun accessToken(scopes: List<String>): String
}
