package com.cypressit.cyvault.feature.vault.card.di

import com.cypressit.cyvault.feature.vault.card.data.remote.CardApiService
import com.cypressit.cyvault.feature.vault.card.data.repository.CardRepositoryImpl
import com.cypressit.cyvault.feature.vault.card.domain.repository.CardRepository
import com.cypressit.cyvault.feature.vault.home.data.remote.CyVaultApiConfig
import org.koin.core.scope.Scope
import org.koin.dsl.module

internal val cardModule = module {
    // Remote (AuthClient comes from :auth's authClientModule)
    single { CardApiService(get(), get(), apiConfig()) }

    // Repository
    single<CardRepository> { CardRepositoryImpl(get()) }
}

/** The app registers its build's config; without one, the production API. */
private fun Scope.apiConfig(): CyVaultApiConfig = getOrNull() ?: CyVaultApiConfig.Production
