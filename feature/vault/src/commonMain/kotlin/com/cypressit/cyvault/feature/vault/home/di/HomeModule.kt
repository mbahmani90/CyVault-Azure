package com.cypressit.cyvault.feature.vault.home.di

import com.cypressit.cyvault.core.di.IO_DISPATCHER
import com.cypressit.cyvault.feature.vault.di.UserSession
import com.cypressit.cyvault.feature.vault.home.data.remote.BalanceApiService
import com.cypressit.cyvault.feature.vault.home.data.remote.CyVaultApiConfig
import com.cypressit.cyvault.feature.vault.home.data.remote.ProfilePhotoApiService
import com.cypressit.cyvault.feature.vault.home.data.remote.SessionApiService
import com.cypressit.cyvault.feature.vault.home.data.repository.AccountRepositoryImpl
import com.cypressit.cyvault.feature.vault.home.data.repository.BalanceRepositoryImpl
import com.cypressit.cyvault.feature.vault.home.data.repository.ProfilePhotoRepositoryImpl
import com.cypressit.cyvault.feature.vault.home.domain.repository.AccountRepository
import com.cypressit.cyvault.feature.vault.home.domain.repository.BalanceRepository
import com.cypressit.cyvault.feature.vault.home.domain.repository.ProfilePhotoRepository
import com.cypressit.cyvault.feature.vault.home.domain.usecase.ChangeProfilePhotoUseCase
import com.cypressit.cyvault.feature.vault.home.domain.usecase.ClearProfilePhotoCacheUseCase
import com.cypressit.cyvault.feature.vault.home.domain.usecase.DeleteBalanceUseCase
import com.cypressit.cyvault.feature.vault.home.domain.usecase.DeleteBalancesUseCase
import com.cypressit.cyvault.feature.vault.home.domain.usecase.DeleteCardUseCase
import com.cypressit.cyvault.feature.vault.home.domain.usecase.GetBalancesUseCase
import com.cypressit.cyvault.feature.vault.home.domain.usecase.GetCardsUseCase
import com.cypressit.cyvault.feature.vault.home.domain.usecase.GetCurrentAccountUseCase
import com.cypressit.cyvault.feature.vault.home.domain.usecase.GetProfilePhotoUseCase
import com.cypressit.cyvault.feature.vault.home.domain.usecase.ObserveCardsUseCase
import com.cypressit.cyvault.feature.vault.home.domain.usecase.SaveBalanceUseCase
import com.cypressit.cyvault.feature.vault.home.domain.usecase.SendTransferUseCase
import com.cypressit.cyvault.feature.vault.home.domain.usecase.SignOutUseCase
import com.cypressit.cyvault.feature.vault.home.presentation.viewmodel.ResourceTransferPromptTexts
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
    single { SessionApiService(get(), get(), apiConfig()) }

    // Repository (AuthClient comes from :auth's authClientModule). The account is the auth
    // client's, so it stays a singleton.
    single<AccountRepository> { AccountRepositoryImpl(get(), get()) }

    // Everything holding the user's data, and what's built on it, lives in their session.
    scope(UserSession.qualifier) {
        scoped<BalanceRepository> { BalanceRepositoryImpl(get()) }
        scoped<ProfilePhotoRepository> {
            ProfilePhotoRepositoryImpl(
                apiService = get(),
                fileStore = get(),
                photoReader = get(),
                ioDispatcher = get(named(IO_DISPATCHER)),
            )
        }

        // Use cases
        factory { GetCardsUseCase(get()) }
        factory { ObserveCardsUseCase(get()) }
        factory { DeleteCardUseCase(get()) }
        factory { GetCurrentAccountUseCase(get()) }
        factory { SignOutUseCase(get(), get()) }
        factory { GetProfilePhotoUseCase(get()) }
        factory { ChangeProfilePhotoUseCase(get()) }
        factory { ClearProfilePhotoCacheUseCase(get()) }
        factory { GetBalancesUseCase(get()) }
        factory { SaveBalanceUseCase(get()) }
        factory { DeleteBalanceUseCase(get()) }
        factory { DeleteBalancesUseCase(get()) }
        factory { SendTransferUseCase(get(), get()) }

        // ViewModel
        // Written out because the sample-balances seeder is optional: only debug builds load
        // :feature:vault-debug, which provides it.
        viewModel {
            VaultHomeViewModel(
                getCards = get(),
                observeCards = get(),
                deleteCard = get(),
                getCurrentAccount = get(),
                signOutUser = get(),
                getProfilePhoto = get(),
                changeProfilePhoto = get(),
                clearProfilePhotoCache = get(),
                getBalances = get(),
                saveBalance = get(),
                deleteBalance = get(),
                deleteBalances = get(),
                sendTransfer = get(),
                enrollDevice = get(),
                transferPromptTexts = ResourceTransferPromptTexts(),
                sampleBalancesSeeder = getOrNull(),
            )
        }
    }
}

/** Android debug builds provide the debug environment's API; everything else uses production. */
private fun Scope.apiConfig(): CyVaultApiConfig = getOrNull() ?: CyVaultApiConfig.Production
