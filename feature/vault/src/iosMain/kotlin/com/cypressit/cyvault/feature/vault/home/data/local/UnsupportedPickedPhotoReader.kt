package com.cypressit.cyvault.feature.vault.home.data.local

import com.cypressit.cyvault.feature.vault.home.domain.model.PickedPhoto

/** iOS has no photo picker yet (see [NoOpAvatarFileStore]), so there is never a photo to read. */
class UnsupportedPickedPhotoReader : PickedPhotoReader {
    override suspend fun readJpeg(photo: PickedPhoto): ByteArray =
        throw UnsupportedOperationException("Profile photos aren't supported on iOS yet")
}
