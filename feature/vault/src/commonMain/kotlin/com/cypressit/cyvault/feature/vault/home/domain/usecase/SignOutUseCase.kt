package com.cypressit.cyvault.feature.vault.home.domain.usecase

import com.cypressit.cyvault.feature.vault.home.domain.repository.AccountRepository

/** Signs the user out of the vault. */
class SignOutUseCase(private val repository: AccountRepository) {
    suspend operator fun invoke(): Result<Unit> = repository.signOut()
}
