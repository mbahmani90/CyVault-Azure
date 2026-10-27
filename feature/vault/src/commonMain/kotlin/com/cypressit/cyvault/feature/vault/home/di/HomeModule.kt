package com.cypressit.cyvault.feature.vault.home.di

import com.cypressit.cyvault.feature.vault.home.domain.usecase.DeleteCardUseCase
import com.cypressit.cyvault.feature.vault.home.domain.usecase.GetCardsUseCase
import com.cypressit.cyvault.feature.vault.home.presentation.viewmodel.VaultHomeViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

internal val homeModule = module {
    // Use cases
    factory { GetCardsUseCase(get()) }
    factory { DeleteCardUseCase(get()) }

    // ViewModel
    viewModelOf(::VaultHomeViewModel)
}
