package com.cypressit.cyvault.feature.vault.home.data.repository

import com.cypressit.cyvault.core.coroutines.runSuspendCatching
import com.cypressit.cyvault.feature.vault.home.data.local.AvatarFileStore
import com.cypressit.cyvault.feature.vault.home.data.local.PickedPhotoReader
import com.cypressit.cyvault.feature.vault.home.data.remote.ProfilePhotoApiService
import com.cypressit.cyvault.feature.vault.home.domain.model.PickedPhoto
import com.cypressit.cyvault.feature.vault.home.domain.repository.ProfilePhotoRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

class ProfilePhotoRepositoryImpl(
    private val apiService: ProfilePhotoApiService,
    private val fileStore: AvatarFileStore,
    private val photoReader: PickedPhotoReader,
    private val ioDispatcher: CoroutineDispatcher,
) : ProfilePhotoRepository {

    override fun getPhoto(userId: String): Flow<ByteArray?> = flow {
        val cached = withContext(ioDispatcher) { fileStore.read(userId) }
        if (cached != null) emit(cached)

        // The server is the source of truth; offline, the cached copy simply stays.
        val remote = runSuspendCatching { apiService.getPhoto() }.getOrElse { return@flow }
        withContext(ioDispatcher) {
            if (remote == null) fileStore.delete(userId) else fileStore.write(userId, remote)
        }
        if (cached == null || remote == null || !remote.contentEquals(cached)) emit(remote)
    }

    override suspend fun changePhoto(userId: String, photo: PickedPhoto): Result<ByteArray> = runSuspendCatching {
        apiService.putPhoto(photoReader.readJpeg(photo))
        // The server re-encodes the upload; cache its version so the device matches it.
        val stored = apiService.getPhoto() ?: error("The uploaded photo wasn't found")
        withContext(ioDispatcher) { fileStore.write(userId, stored) }
        stored
    }

    override suspend fun clearCache() = withContext(ioDispatcher) { fileStore.deleteAll() }
}
