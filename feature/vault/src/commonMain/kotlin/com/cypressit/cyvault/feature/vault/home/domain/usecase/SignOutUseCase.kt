package com.cypressit.cyvault.feature.vault.home.domain.usecase

import com.cypressit.cyvault.feature.vault.device.domain.repository.DeviceRepository
import com.cypressit.cyvault.feature.vault.home.domain.repository.AccountRepository

/**
 * Signs the user out of the vault. First the account's pushes stop on this phone, while the API
 * still accepts its token, so the next person to use the phone doesn't see them.
 */
class SignOutUseCase(
    private val repository: AccountRepository,
    private val devices: DeviceRepository,
) {
    suspend operator fun invoke(): Result<Unit> {
        repository.currentAccount()?.let { devices.stopPushes(it.id) }
        return repository.signOut()
    }
}
