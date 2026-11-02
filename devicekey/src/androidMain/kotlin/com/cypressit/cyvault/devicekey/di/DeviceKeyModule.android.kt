package com.cypressit.cyvault.devicekey.di

import android.app.Application
import com.cypressit.cyvault.devicekey.ActivityProvider
import com.cypressit.cyvault.devicekey.AndroidDeviceKeyStore
import com.cypressit.cyvault.devicekey.DeviceKeyStore
import com.cypressit.cyvault.devicekey.ResumedActivityProvider
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual val deviceKeyPlatformModule: Module = module {
    // Created at startKoin (Application.onCreate), so it sees the first activity resume.
    single<ActivityProvider>(createdAtStart = true) {
        ResumedActivityProvider(androidContext() as Application)
    }
    single<DeviceKeyStore> { AndroidDeviceKeyStore(activityProvider = get()) }
}
