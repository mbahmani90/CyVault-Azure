package com.cypressit.cyvault.auth.di

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Provides [AuthClient][com.cypressit.cyvault.auth.AuthClient]. On Android the app must also
 * provide an [MsalConfig][com.cypressit.cyvault.auth.MsalConfig] and `androidContext`.
 */
val authClientModule: Module = module {
    includes(authPlatformModule)
}

internal expect val authPlatformModule: Module
