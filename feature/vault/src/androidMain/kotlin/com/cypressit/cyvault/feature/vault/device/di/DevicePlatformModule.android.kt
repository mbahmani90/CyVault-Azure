package com.cypressit.cyvault.feature.vault.device.di

import com.cypressit.cyvault.feature.vault.device.data.local.deviceIdDataStore
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual val devicePlatformModule: Module = module {
    // filesDir is app-private: other apps can't read it.
    single { deviceIdDataStore(androidContext().filesDir.absolutePath) }
}
