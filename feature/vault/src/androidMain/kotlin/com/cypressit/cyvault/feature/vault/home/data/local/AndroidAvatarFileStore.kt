package com.cypressit.cyvault.feature.vault.home.data.local

import android.content.Context
import java.io.File

/** Keeps the photos in the app's private `filesDir`, which other apps can't read. */
class AndroidAvatarFileStore(context: Context) : AvatarFileStore {

    private val directory = File(context.filesDir, "avatars")

    override fun read(userId: String): ByteArray? =
        fileFor(userId).takeIf { it.isFile }?.readBytes()

    override fun write(userId: String, jpeg: ByteArray) {
        directory.mkdirs()
        // Write next to the target and rename, so a crash never leaves a half-written photo.
        val temp = File(directory, "${requireObjectId(userId)}.tmp")
        temp.writeBytes(jpeg)
        if (!temp.renameTo(fileFor(userId))) {
            temp.delete()
            throw IllegalStateException("Couldn't save the profile photo")
        }
    }

    override fun delete(userId: String) {
        fileFor(userId).delete()
    }

    override fun deleteAll() {
        directory.deleteRecursively()
    }

    private fun fileFor(userId: String) = File(directory, "${requireObjectId(userId)}.jpg")
}
