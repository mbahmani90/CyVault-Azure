package com.cypressit.cyvault.feature.authentication.domain.usecase

import com.cypressit.cyvault.feature.authentication.domain.model.LoginResult
import com.cypressit.cyvault.feature.authentication.domain.repository.AuthRepository

class LoginUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(email: String, password: String): Result<LoginResult> =
        repository.login(email, password)
}
