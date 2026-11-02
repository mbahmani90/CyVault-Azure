package com.cypressit.cyvault.push.di

import com.cypressit.cyvault.push.PushTokens
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual val pushPlatformModule: Module = module {
    single<PushTokens> { UnsupportedPushTokens() }
}

// Until APNs is set up, an iOS phone has no token, so it registers none and gets no pushes.
private class UnsupportedPushTokens : PushTokens {
    override suspend fun current(): String? = null

    override suspend fun delete() = Unit
}
