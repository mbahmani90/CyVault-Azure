package com.cypressit.cyvault.feature.vault.home.di

import androidx.room.Room
import com.cypressit.cyvault.core.di.IO_DISPATCHER
import com.cypressit.cyvault.feature.vault.home.data.local.AndroidAvatarFileStore
import com.cypressit.cyvault.feature.vault.home.data.local.AndroidPickedPhotoReader
import com.cypressit.cyvault.feature.vault.home.data.local.AvatarFileStore
import com.cypressit.cyvault.feature.vault.home.data.local.BALANCE_CACHE_FILE_NAME
import com.cypressit.cyvault.feature.vault.home.data.local.BalanceCacheDatabase
import com.cypressit.cyvault.feature.vault.home.data.local.PickedPhotoReader
import com.cypressit.cyvault.feature.vault.home.data.local.buildBalanceCache
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.io.File

internal actual val homePlatformModule: Module = module {
    single<AvatarFileStore> { AndroidAvatarFileStore(androidContext()) }
    single<PickedPhotoReader> { AndroidPickedPhotoReader(androidContext(), get(named(IO_DISPATCHER))) }
    single {
        val context = androidContext()
        // The no-backup directory: Android's cloud backup never copies financial data from it.
        Room.databaseBuilder<BalanceCacheDatabase>(
            context = context,
            name = File(context.noBackupFilesDir, BALANCE_CACHE_FILE_NAME).absolutePath,
        ).buildBalanceCache(get(named(IO_DISPATCHER)))
    }
}
