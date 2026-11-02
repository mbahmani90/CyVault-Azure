package com.cypressit.cyvault.push.di

import com.cypressit.cyvault.push.FirebasePushTokens
import com.cypressit.cyvault.push.PushTokens
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual val pushPlatformModule: Module = module {
    single<PushTokens> { FirebasePushTokens() }
}
