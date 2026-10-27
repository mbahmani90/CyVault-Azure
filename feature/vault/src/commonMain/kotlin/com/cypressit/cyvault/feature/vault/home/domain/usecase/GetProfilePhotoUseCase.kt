package com.cypressit.cyvault.feature.vault.home.domain.usecase

import com.cypressit.cyvault.feature.vault.home.domain.repository.ProfilePhotoRepository
import kotlinx.coroutines.flow.Flow

/** The user's profile photo: the device's copy at once, then the server's when it differs. */
class GetProfilePhotoUseCase(private val repository: ProfilePhotoRepository) {
    operator fun invoke(userId: String): Flow<ByteArray?> = repository.getPhoto(userId)
}
