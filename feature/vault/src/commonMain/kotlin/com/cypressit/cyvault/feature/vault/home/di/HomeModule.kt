package com.cypressit.cyvault.feature.vault.home.di

import com.cypressit.cyvault.core.di.IO_DISPATCHER
import com.cypressit.cyvault.feature.vault.home.data.remote.BalanceApiService
import com.cypressit.cyvault.feature.vault.home.data.remote.CyVaultApiConfig
import com.cypressit.cyvault.feature.vault.home.data.remote.ProfilePhotoApiService
import com.cypressit.cyvault.feature.vault.home.data.repository.AccountRepositoryImpl
import com.cypressit.cyvault.feature.vault.home.data.repository.BalanceRepositoryImpl
import com.cypressit.cyvault.feature.vault.home.data.repository.ProfilePhotoRepositoryImpl
import com.cypressit.cyvault.feature.vault.home.domain.repository.AccountRepository
import com.cypressit.cyvault.feature.vault.home.domain.repository.BalanceRepository
import com.cypressit.cyvault.feature.vault.home.domain.repository.ProfilePhotoRepository
import com.cypressit.cyvault.feature.vault.home.domain.usecase.ChangeProfilePhotoUseCase
import com.cypressit.cyvault.feature.vault.home.domain.usecase.ClearProfilePhotoCacheUseCase
import com.cypressit.cyvault.feature.vault.home.domain.usecase.DeleteBalanceUseCase
import com.cypressit.cyvault.feature.vault.home.domain.usecase.DeleteCardUseCase
import com.cypressit.cyvault.feature.vault.home.domain.usecase.GetBalancesUseCase
import com.cypressit.cyvault.feature.vault.home.domain.usecase.GetCardsUseCase
import com.cypressit.cyvault.feature.vault.home.domain.usecase.GetCurrentAccountUseCase
import com.cypressit.cyvault.feature.vault.home.domain.usecase.GetProfilePhotoUseCase
import com.cypressit.cyvault.feature.vault.home.domain.usecase.SaveBalanceUseCase
import com.cypressit.cyvault.feature.vault.home.domain.usecase.SignOutUseCase
import com.cypressit.cyvault.feature.vault.home.presentation.viewmodel.VaultHomeViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.dsl.module

internal val homeModule = module {
    includes(homePlatformModule)

    // Remote
    single { ProfilePhotoApiService(get(), get(), apiConfig()) }
    single { BalanceApiService(get(), get(), apiConfig()) }

    // Repository (AuthClient comes from :auth's authClientModule)
    single<AccountRepository> { AccountRepositoryImpl(get()) }
    single<BalanceRepository> { BalanceRepositoryImpl(get()) }
    single<ProfilePhotoRepository> {
        ProfilePhotoRepositoryImpl(
            apiService = get(),
            fileStore = get(),
            photoReader = get(),
            ioDispatcher = get(named(IO_DISPATCHER)),
        )
    }

    // Use cases
    factory { GetCardsUseCase(get()) }
    factory { DeleteCardUseCase(get()) }
    factory { GetCurrentAccountUseCase(get()) }
    factory { SignOutUseCase(get()) }
    factory { GetProfilePhotoUseCase(get()) }
    factory { ChangeProfilePhotoUseCase(get()) }
    factory { ClearProfilePhotoCacheUseCase(get()) }
    factory { GetBalancesUseCase(get()) }
    factory { SaveBalanceUseCase(get()) }
    factory { DeleteBalanceUseCase(get()) }

    // ViewModel
    // Written out because the sample-balances seeder is optional: only debug builds load
    // :feature:vault-debug, which provides it.
    viewModel {
        VaultHomeViewModel(
            getCards = get(),
            deleteCard = get(),
            getCurrentAccount = get(),
            signOutUser = get(),
            getProfilePhoto = get(),
            changeProfilePhoto = get(),
            clearProfilePhotoCache = get(),
            getBalances = get(),
            saveBalance = get(),
            deleteBalance = get(),
            sampleBalancesSeeder = getOrNull(),
        )
    }
}

/** Android debug builds provide the debug environment's API; everything else uses production. */
private fun Scope.apiConfig(): CyVaultApiConfig = getOrNull() ?: CyVaultApiConfig.Production
