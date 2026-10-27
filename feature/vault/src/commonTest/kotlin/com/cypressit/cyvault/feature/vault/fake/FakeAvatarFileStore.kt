package com.cypressit.cyvault.feature.vault.fake

import com.cypressit.cyvault.feature.vault.home.data.local.AvatarFileStore

/** An in-memory photo cache. */
class FakeAvatarFileStore : AvatarFileStore {
    val files = mutableMapOf<String, ByteArray>()

    override fun read(userId: String): ByteArray? = files[userId]

    override fun write(userId: String, jpeg: ByteArray) {
        files[userId] = jpeg
    }

    override fun delete(userId: String) {
        files.remove(userId)
    }

    override fun deleteAll() = files.clear()
}
