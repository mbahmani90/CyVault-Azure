package com.cypressit.cyvault.core.di

import android.app.Application
import android.content.Context
import com.cypressit.cyvault.core.platform.ActivityProvider
import com.cypressit.cyvault.core.platform.ResumedActivityProvider
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single { OkHttp.create() }

    // Created at startKoin (Application.onCreate), so it sees MainActivity resume.
    single<ActivityProvider>(createdAtStart = true) {
        ResumedActivityProvider(get<Context>() as Application)
    }
}
