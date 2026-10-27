package com.cypressit.cyvault.feature.authentication.di

import android.app.Application
import android.content.Context
import com.cypressit.cyvault.feature.authentication.data.remote.ResumedActivityTracker
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual val authPlatformModule: Module = module {
    // Created at startKoin (Application.onCreate), so the tracker sees MainActivity resume.
    single(createdAtStart = true) {
        ResumedActivityTracker.also { it.register(get<Context>() as Application) }
    }
}
