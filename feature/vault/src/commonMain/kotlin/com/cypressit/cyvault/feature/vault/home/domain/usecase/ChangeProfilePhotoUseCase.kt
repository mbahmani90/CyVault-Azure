package com.cypressit.cyvault.feature.vault.home.domain.usecase

import com.cypressit.cyvault.feature.vault.home.domain.model.PickedPhoto
import com.cypressit.cyvault.feature.vault.home.domain.repository.ProfilePhotoRepository

/** Makes a picked photo the user's profile photo; returns the photo as the server stored it. */
class ChangeProfilePhotoUseCase(private val repository: ProfilePhotoRepository) {
    suspend operator fun invoke(userId: String, photo: PickedPhoto): Result<ByteArray> =
        repository.changePhoto(userId, photo)
}
