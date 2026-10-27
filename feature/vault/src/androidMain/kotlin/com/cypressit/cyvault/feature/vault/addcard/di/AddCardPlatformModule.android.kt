package com.cypressit.cyvault.feature.vault.addcard.di

import com.cypressit.cyvault.feature.vault.addcard.presentation.BankAppLauncher
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual val addCardPlatformModule: Module = module {
    factory { BankAppLauncher(androidContext()) }
}
