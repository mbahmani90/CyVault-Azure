package com.cypressit.cyvault.feature.authentication.domain.usecase

import com.cypressit.cyvault.feature.authentication.domain.model.User
import com.cypressit.cyvault.feature.authentication.domain.repository.AuthRepository

class LoginUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(email: String, password: String): Result<User> =
        repository.login(email, password)
}
