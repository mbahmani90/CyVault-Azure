package com.cypressit.cyvault.feature.vault.home.data.local

/**
 * The profile photo cached on the device, in app-private storage. There is one file per
 * account, named by its Entra object ID, so another account signed in on the same device
 * never sees it. Calls block: callers switch to the I/O dispatcher.
 */
interface AvatarFileStore {
    fun read(userId: String): ByteArray?
    fun write(userId: String, jpeg: ByteArray)
    fun delete(userId: String)

    /** Removes every cached photo, e.g. on sign-out. */
    fun deleteAll()
}

/** An Entra object ID is a GUID; anything else could escape the avatar folder as a file name. */
internal fun requireObjectId(userId: String): String {
    require(userId.isNotEmpty() && userId.all { it.isLetterOrDigit() || it == '-' }) { "Invalid user ID" }
    return userId
}
