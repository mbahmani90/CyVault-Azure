package com.cypressit.cyvault.feature.vault.home.data.local

import com.cypressit.cyvault.feature.vault.home.domain.model.PickedPhoto

/** Reads a photo the user picked and prepares it for upload. */
interface PickedPhotoReader {
    /**
     * The photo turned upright and scaled down to a JPEG small enough to upload.
     * Throws when it can't be read or isn't an image.
     */
    suspend fun readJpeg(photo: PickedPhoto): ByteArray
}
