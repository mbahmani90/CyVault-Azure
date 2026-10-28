package com.cypressit.cyvault.feature.vault.home.domain.usecase

import com.cypressit.cyvault.feature.vault.home.domain.model.Account
import com.cypressit.cyvault.feature.vault.home.domain.repository.AccountRepository

/** The signed-in user shown in the drawer, or null when nobody is signed in. */
class GetCurrentAccountUseCase(private val repository: AccountRepository) {
    suspend operator fun invoke(): Account? = repository.currentAccount()
}
