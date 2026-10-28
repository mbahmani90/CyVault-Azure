package com.cypressit.cyvault.auth.di

import android.app.Application
import com.cypressit.cyvault.auth.ActivityProvider
import com.cypressit.cyvault.auth.AuthClient
import com.cypressit.cyvault.auth.MsalAuthClient
import com.cypressit.cyvault.auth.MsalClientProvider
import com.cypressit.cyvault.auth.ResumedActivityProvider
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual val authPlatformModule: Module = module {
    // Created at startKoin (Application.onCreate), so it sees the first activity resume.
    single<ActivityProvider>(createdAtStart = true) {
        ResumedActivityProvider(androidContext() as Application)
    }
    single { MsalClientProvider(androidContext(), config = get()) }
    single<AuthClient> { MsalAuthClient(clientProvider = get(), activityProvider = get()) }
}
