package com.cypressit.cyvault.feature.vault.vaultlist.di

import com.cypressit.cyvault.feature.vault.vaultlist.domain.usecase.DeleteCardUseCase
import com.cypressit.cyvault.feature.vault.vaultlist.domain.usecase.GetCardsUseCase
import com.cypressit.cyvault.feature.vault.vaultlist.presentation.viewmodel.VaultListViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

internal val vaultListModule = module {
    // Use cases
    factory { GetCardsUseCase(get()) }
    factory { DeleteCardUseCase(get()) }

    // ViewModel
    viewModelOf(::VaultListViewModel)
}
