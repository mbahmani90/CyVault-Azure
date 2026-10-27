package com.cypressit.cyvault.feature.vault.home.domain.repository

import com.cypressit.cyvault.feature.vault.home.domain.model.PickedPhoto
import kotlinx.coroutines.flow.Flow

interface ProfilePhotoRepository {
    /**
     * The user's photo as JPEG bytes: the copy cached on the device first, if there is one,
     * then the server's copy. Null means the user has no photo.
     */
    fun getPhoto(userId: String): Flow<ByteArray?>

    /** Uploads [photo] as the user's new photo and caches the server's version, which it returns. */
    suspend fun changePhoto(userId: String, photo: PickedPhoto): Result<ByteArray>

    /** Deletes every photo cached on the device. */
    suspend fun clearCache()
}
