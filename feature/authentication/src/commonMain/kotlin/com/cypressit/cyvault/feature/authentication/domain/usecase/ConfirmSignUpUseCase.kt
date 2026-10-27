package com.cypressit.cyvault.feature.authentication.domain.usecase

import com.cypressit.cyvault.feature.authentication.domain.repository.AuthRepository

class ConfirmSignUpUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(email: String, code: String): Result<Unit> =
        repository.confirmSignUp(email, code)
}
