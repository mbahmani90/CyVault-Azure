package com.cypressit.cyvault.feature.vault.addcard.di

import com.cypressit.cyvault.feature.vault.addcard.domain.usecase.CreateCardUseCase
import com.cypressit.cyvault.feature.vault.addcard.presentation.viewmodel.AddCardViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

internal val addCardModule = module {
    // Use cases
    factory { CreateCardUseCase(get()) }

    // ViewModel
    viewModelOf(::AddCardViewModel)
}
