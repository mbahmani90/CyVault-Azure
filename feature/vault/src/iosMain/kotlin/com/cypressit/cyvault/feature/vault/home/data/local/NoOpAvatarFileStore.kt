package com.cypressit.cyvault.feature.vault.home.data.local

/**
 * Caches nothing: iOS still signs in with Cognito, so it can't reach the Azure photo API and
 * there is no photo to cache. The drawer keeps showing the initials avatar.
 */
class NoOpAvatarFileStore : AvatarFileStore {
    override fun read(userId: String): ByteArray? = null
    override fun write(userId: String, jpeg: ByteArray) = Unit
    override fun delete(userId: String) = Unit
    override fun deleteAll() = Unit
}
