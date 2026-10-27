package com.cypressit.cyvault.feature.authentication.data.remote

import com.cypressit.cyvault.feature.authentication.domain.model.User

expect class AuthApiService() {
    suspend fun login(email: String, password: String): User
    suspend fun register(name: String, email: String, password: String)
    suspend fun confirmSignUp(email: String, code: String)
    suspend fun forgotPassword(email: String)
    suspend fun signOut()
    suspend fun getCurrentUser(): User?
    suspend fun signInWithGoogle(): User
    suspend fun accessToken(scopes: List<String>): String
}
