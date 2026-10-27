package com.cypressit.cyvault.feature.vault.home.di

import com.cypressit.cyvault.feature.vault.home.presentation.viewmodel.VaultHomeViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

internal val homeModule = module {
    // ViewModel
    viewModelOf(::VaultHomeViewModel)
}
