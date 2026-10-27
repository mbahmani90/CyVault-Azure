package com.cypressit.cyvault.feature.vault.home.di

import com.cypressit.cyvault.core.di.IO_DISPATCHER
import com.cypressit.cyvault.feature.vault.home.data.local.AndroidAvatarFileStore
import com.cypressit.cyvault.feature.vault.home.data.local.AndroidPickedPhotoReader
import com.cypressit.cyvault.feature.vault.home.data.local.AvatarFileStore
import com.cypressit.cyvault.feature.vault.home.data.local.PickedPhotoReader
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

internal actual val homePlatformModule: Module = module {
    single<AvatarFileStore> { AndroidAvatarFileStore(androidContext()) }
    single<PickedPhotoReader> { AndroidPickedPhotoReader(androidContext(), get(named(IO_DISPATCHER))) }
}
