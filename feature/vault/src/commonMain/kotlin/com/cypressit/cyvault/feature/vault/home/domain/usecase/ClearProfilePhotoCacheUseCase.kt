package com.cypressit.cyvault.feature.vault.home.domain.usecase

import com.cypressit.cyvault.feature.vault.home.domain.repository.ProfilePhotoRepository

/** Removes cached profile photos from the device, so none stays behind after sign-out. */
class ClearProfilePhotoCacheUseCase(private val repository: ProfilePhotoRepository) {
    suspend operator fun invoke() = repository.clearCache()
}
