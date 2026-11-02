package com.cypressit.cyvault.devicekey.di

import com.cypressit.cyvault.devicekey.AndroidDeviceKeyStore
import com.cypressit.cyvault.devicekey.DeviceKeyStore
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual val deviceKeyPlatformModule: Module = module {
    single<DeviceKeyStore> { AndroidDeviceKeyStore() }
}
