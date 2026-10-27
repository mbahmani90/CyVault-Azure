package com.cypressit.cyvault.feature.vault.addcard.di

import com.cypressit.cyvault.feature.vault.addcard.data.remote.YapilyService
import com.cypressit.cyvault.feature.vault.addcard.data.repository.BankRepositoryImpl
import com.cypressit.cyvault.feature.vault.addcard.domain.repository.BankRepository
import com.cypressit.cyvault.feature.vault.addcard.domain.usecase.CreateAccountAuthorisationUseCase
import com.cypressit.cyvault.feature.vault.addcard.domain.usecase.CreateCardUseCase
import com.cypressit.cyvault.feature.vault.addcard.domain.usecase.GetAccountsUseCase
import com.cypressit.cyvault.feature.vault.addcard.domain.usecase.GetInstitutionsUseCase
import com.cypressit.cyvault.feature.vault.addcard.presentation.viewmodel.AddCardViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

internal val addCardModule = module {
    includes(addCardPlatformModule)

    // Remote
    single { YapilyService(get()) }

    // Repository
    single<BankRepository> { BankRepositoryImpl(get()) }

    // Use cases
    factory { GetInstitutionsUseCase(get()) }
    factory { CreateAccountAuthorisationUseCase(get()) }
    factory { GetAccountsUseCase(get()) }
    factory { CreateCardUseCase(get()) }

    // ViewModel
    viewModelOf(::AddCardViewModel)
}
