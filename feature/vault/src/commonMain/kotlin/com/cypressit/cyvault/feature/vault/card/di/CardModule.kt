package com.cypressit.cyvault.feature.vault.card.di

import com.cypressit.cyvault.feature.vault.card.data.remote.CardApiService
import com.cypressit.cyvault.feature.vault.card.data.repository.CardRepositoryImpl
import com.cypressit.cyvault.feature.vault.card.domain.repository.CardRepository
import org.koin.dsl.module

internal val cardModule = module {
    // Remote
    single { CardApiService(get()) }

    // Repository
    single<CardRepository> { CardRepositoryImpl(get()) }
}
