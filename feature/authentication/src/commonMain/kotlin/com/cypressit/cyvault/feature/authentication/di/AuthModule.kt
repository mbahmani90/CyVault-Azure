package com.cypressit.cyvault.feature.authentication.di

import com.cypressit.cyvault.feature.authentication.data.remote.AuthApiService
import com.cypressit.cyvault.feature.authentication.data.repository.AuthRepositoryImpl
import com.cypressit.cyvault.feature.authentication.domain.repository.AuthRepository
import com.cypressit.cyvault.feature.authentication.domain.usecase.ConfirmSignUpUseCase
import com.cypressit.cyvault.feature.authentication.domain.usecase.ForgotPasswordUseCase
import com.cypressit.cyvault.feature.authentication.domain.usecase.GetCurrentUserUseCase
import com.cypressit.cyvault.feature.authentication.domain.usecase.LoginUseCase
import com.cypressit.cyvault.feature.authentication.domain.usecase.RegisterUseCase
import com.cypressit.cyvault.feature.authentication.domain.usecase.SignInWithGoogleUseCase
import com.cypressit.cyvault.feature.authentication.domain.usecase.SignOutUseCase
import com.cypressit.cyvault.feature.authentication.presentation.viewmodel.AuthViewModel
import com.cypressit.cyvault.feature.authentication.session.SessionManagerImpl
import com.cypressit.cyvault.core.session.SessionManager
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val authModule = module {
    // Remote
    single { AuthApiService() }

    // Repository
    single<AuthRepository> { AuthRepositoryImpl(get()) }

    // Session
    single<SessionManager> { SessionManagerImpl(get()) }

    // Use cases
    factory { LoginUseCase(get()) }
    factory { RegisterUseCase(get()) }
    factory { ConfirmSignUpUseCase(get()) }
    factory { ForgotPasswordUseCase(get()) }
    factory { SignOutUseCase(get()) }
    factory { GetCurrentUserUseCase(get()) }
    factory { SignInWithGoogleUseCase(get()) }

    // ViewModel
    viewModelOf(::AuthViewModel)
}
