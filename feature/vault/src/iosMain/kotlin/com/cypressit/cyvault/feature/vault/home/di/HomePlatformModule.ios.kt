package com.cypressit.cyvault.feature.vault.home.di

import com.cypressit.cyvault.feature.vault.home.data.local.AvatarFileStore
import com.cypressit.cyvault.feature.vault.home.data.local.NoOpAvatarFileStore
import com.cypressit.cyvault.feature.vault.home.data.local.PickedPhotoReader
import com.cypressit.cyvault.feature.vault.home.data.local.UnsupportedPickedPhotoReader
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual val homePlatformModule: Module = module {
    single<AvatarFileStore> { NoOpAvatarFileStore() }
    single<PickedPhotoReader> { UnsupportedPickedPhotoReader() }
}
