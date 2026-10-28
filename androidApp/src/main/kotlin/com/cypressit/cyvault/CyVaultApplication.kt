package com.cypressit.cyvault

import android.app.Application
import com.cypressit.cyvault.auth.MsalConfig
import com.cypressit.cyvault.di.appModules
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext.startKoin
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

class CyVaultApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidContext(this@CyVaultApplication)
            // The app's Entra registration (client ID, tenant, redirect URI) for :auth.
            modules(module { single { MsalConfig(R.raw.auth_config_native_auth) } })
        }
    }
}
fun initKoin(configuration: KoinAppDeclaration? = null){
    startKoin{
        configuration?.invoke(this)
        modules(appModules)
    }
}