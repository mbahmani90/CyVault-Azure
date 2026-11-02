package com.cypressit.cyvault.feature.authentication.domain.usecase

import com.cypressit.cyvault.feature.authentication.domain.model.User
import com.cypressit.cyvault.feature.authentication.domain.repository.AuthRepository

/** Completes the login with the texted code. */
class SubmitMfaCodeUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(code: String): Result<User> =
        repository.submitMfaCode(code)
}
