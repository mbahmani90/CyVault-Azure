package com.cypressit.cyvault.push.di

import org.koin.core.module.Module
import org.koin.dsl.module

/** Provides [PushTokens][com.cypressit.cyvault.push.PushTokens]. */
val pushModule: Module = module {
    includes(pushPlatformModule)
}

internal expect val pushPlatformModule: Module
