package com.cypressit.cyvault.feature.vault.fake

import com.cypressit.cyvault.feature.vault.home.domain.model.PickedPhoto
import com.cypressit.cyvault.feature.vault.home.domain.repository.ProfilePhotoRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakeProfilePhotoRepository(
    var photo: Flow<ByteArray?> = flowOf(null),
    var changeResult: Result<ByteArray> = Result.success(byteArrayOf(7)),
) : ProfilePhotoRepository {

    val photoRequests = mutableListOf<String>()
    val changes = mutableListOf<Pair<String, PickedPhoto>>()
    var clearCount = 0

    /** When set, changePhoto waits for it, so a test can look at the state mid-upload. */
    var uploadGate: CompletableDeferred<Unit>? = null

    override fun getPhoto(userId: String): Flow<ByteArray?> {
        photoRequests += userId
        return photo
    }

    override suspend fun changePhoto(userId: String, photo: PickedPhoto): Result<ByteArray> {
        changes += userId to photo
        uploadGate?.await()
        return changeResult
    }

    override suspend fun clearCache() {
        clearCount++
    }
}
