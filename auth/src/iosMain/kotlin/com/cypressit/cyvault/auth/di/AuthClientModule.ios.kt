package com.cypressit.cyvault.auth.di

import com.cypressit.cyvault.auth.AuthClient
import com.cypressit.cyvault.auth.BridgeAuthClient
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual val authPlatformModule: Module = module {
    single<AuthClient> { BridgeAuthClient() }
}
