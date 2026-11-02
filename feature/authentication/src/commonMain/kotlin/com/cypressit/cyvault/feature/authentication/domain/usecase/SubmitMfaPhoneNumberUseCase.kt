package com.cypressit.cyvault.feature.authentication.domain.usecase

import com.cypressit.cyvault.feature.authentication.domain.model.LoginResult
import com.cypressit.cyvault.feature.authentication.domain.repository.AuthRepository

/** Registers the phone number SMS codes go to, after [LoginResult.PhoneNumberRequired]. */
class SubmitMfaPhoneNumberUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(phoneNumber: String): Result<LoginResult> =
        repository.submitMfaPhoneNumber(phoneNumber)
}
