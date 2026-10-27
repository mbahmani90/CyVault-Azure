package com.cypressit.cyvault.di

import com.cypressit.cyvault.core.di.networkModule
import com.cypressit.cyvault.core.di.platformModule
import com.cypressit.cyvault.feature.authentication.di.authModule
import com.cypressit.cyvault.feature.vault.di.vaultFeatureModule
import org.koin.core.module.Module

/**
 * The single list passed to startKoin on every platform.
 * Add every new feature module here.
 */
val appModules: List<Module>
    get() = listOf(
        platformModule,
        networkModule,
        authModule,
        vaultFeatureModule,
    )
