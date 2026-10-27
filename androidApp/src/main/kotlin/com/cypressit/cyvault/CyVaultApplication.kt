package com.cypressit.cyvault

import android.app.Application
import org.koin.android.ext.koin.androidContext
import com.cypressit.cyvault.di.appModules
import org.koin.core.context.GlobalContext.startKoin
import org.koin.dsl.KoinAppDeclaration

class CyVaultApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidContext(this@CyVaultApplication)
        }
    }
}
fun initKoin(configuration: KoinAppDeclaration? = null){
    startKoin{
        configuration?.invoke(this)
        modules(appModules)
    }
}