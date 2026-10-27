package com.cypressit.cyvault.feature.vault.fake

import com.cypressit.cyvault.feature.vault.home.data.local.PickedPhotoReader
import com.cypressit.cyvault.feature.vault.home.domain.model.PickedPhoto

/** Returns [jpeg] for any photo, or throws [failure] when it's set. */
class FakePickedPhotoReader(
    var jpeg: ByteArray = byteArrayOf(5, 5),
    var failure: Throwable? = null,
) : PickedPhotoReader {
    val read = mutableListOf<PickedPhoto>()

    override suspend fun readJpeg(photo: PickedPhoto): ByteArray {
        read += photo
        failure?.let { throw it }
        return jpeg
    }
}
