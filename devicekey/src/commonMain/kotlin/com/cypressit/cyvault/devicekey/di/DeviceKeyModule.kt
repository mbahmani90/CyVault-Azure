package com.cypressit.cyvault.devicekey.di

import org.koin.core.module.Module
import org.koin.dsl.module

/** Provides [DeviceKeyStore][com.cypressit.cyvault.devicekey.DeviceKeyStore]. */
val deviceKeyModule: Module = module {
    includes(deviceKeyPlatformModule)
}

internal expect val deviceKeyPlatformModule: Module
