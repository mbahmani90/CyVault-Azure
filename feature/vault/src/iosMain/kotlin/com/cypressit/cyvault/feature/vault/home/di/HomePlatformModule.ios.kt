package com.cypressit.cyvault.feature.vault.home.di

import androidx.room.Room
import com.cypressit.cyvault.core.di.IO_DISPATCHER
import com.cypressit.cyvault.feature.vault.home.data.local.AvatarFileStore
import com.cypressit.cyvault.feature.vault.home.data.local.BALANCE_CACHE_FILE_NAME
import com.cypressit.cyvault.feature.vault.home.data.local.BalanceCacheDatabase
import com.cypressit.cyvault.feature.vault.home.data.local.NoOpAvatarFileStore
import com.cypressit.cyvault.feature.vault.home.data.local.PickedPhotoReader
import com.cypressit.cyvault.feature.vault.home.data.local.UnsupportedPickedPhotoReader
import com.cypressit.cyvault.feature.vault.home.data.local.buildBalanceCache
import kotlinx.cinterop.ExperimentalForeignApi
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

internal actual val homePlatformModule: Module = module {
    single<AvatarFileStore> { NoOpAvatarFileStore() }
    single<PickedPhotoReader> { UnsupportedPickedPhotoReader() }
    single {
        Room.databaseBuilder<BalanceCacheDatabase>(name = "${cachesDirectory()}/$BALANCE_CACHE_FILE_NAME")
            .buildBalanceCache(get(named(IO_DISPATCHER)))
    }
}

// The caches directory: iOS doesn't back it up, and the API can always fill the cache again.
@OptIn(ExperimentalForeignApi::class) // NSFileManager's URLForDirectory
private fun cachesDirectory(): String {
    val url = NSFileManager.defaultManager.URLForDirectory(
        directory = NSCachesDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    )
    return requireNotNull(url?.path) { "No caches directory" }
}
