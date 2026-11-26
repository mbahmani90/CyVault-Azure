package com.cypressit.cyvault.feature.authentication.di

import com.cypressit.cyvault.feature.authentication.data.repository.AuthRepositoryImpl
import com.cypressit.cyvault.feature.authentication.domain.repository.AuthRepository
import com.cypressit.cyvault.feature.authentication.domain.usecase.ConfirmSignUpUseCase
import com.cypressit.cyvault.feature.authentication.domain.usecase.ForgotPasswordUseCase
import com.cypressit.cyvault.feature.authentication.domain.usecase.GetCurrentUserUseCase
import com.cypressit.cyvault.feature.authentication.domain.usecase.LoginUseCase
import com.cypressit.cyvault.feature.authentication.domain.usecase.RegisterUseCase
import com.cypressit.cyvault.feature.authentication.domain.usecase.SignInWithGoogleUseCase
import com.cypressit.cyvault.feature.authentication.domain.usecase.SignOutUseCase
import com.cypressit.cyvault.feature.authentication.domain.usecase.SubmitMfaCodeUseCase
import com.cypressit.cyvault.feature.authentication.domain.usecase.SubmitMfaPhoneNumberUseCase
import com.cypressit.cyvault.feature.authentication.presentation.viewmodel.ForgotPasswordViewModel
import com.cypressit.cyvault.feature.authentication.presentation.viewmodel.LoginViewModel
import com.cypressit.cyvault.feature.authentication.presentation.viewmodel.MfaCodeViewModel
import com.cypressit.cyvault.feature.authentication.presentation.viewmodel.MfaPhoneNumberViewModel
import com.cypressit.cyvault.feature.authentication.presentation.viewmodel.RegisterViewModel
import com.cypressit.cyvault.feature.authentication.presentation.viewmodel.VerificationViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val authModule = module {
    // Repository (AuthClient comes from :auth's authModule)
    single<AuthRepository> { AuthRepositoryImpl(get()) }

    // Use cases
    factory { LoginUseCase(get()) }
    factory { RegisterUseCase(get()) }
    factory { ConfirmSignUpUseCase(get()) }
    factory { ForgotPasswordUseCase(get()) }
    factory { SignOutUseCase(get()) }
    factory { GetCurrentUserUseCase(get()) }
    factory { SignInWithGoogleUseCase(get()) }
    factory { SubmitMfaPhoneNumberUseCase(get()) }
    factory { SubmitMfaCodeUseCase(get()) }

    // ViewModels, one per auth screen
    // Written out because ForgotPasswordPreview is optional: only debug builds provide it.
    viewModel {
        LoginViewModel(
            loginUseCase = get(),
            getCurrentUserUseCase = get(),
            signInWithGoogleUseCase = get(),
            forgotPasswordPreview = getOrNull(),
        )
    }
    viewModel { RegisterViewModel(registerUseCase = get()) }
    viewModel { (email: String) -> VerificationViewModel(email = email, confirmSignUpUseCase = get()) }
    viewModel { (email: String) -> ForgotPasswordViewModel(email = email, forgotPasswordUseCase = get()) }
    viewModel { MfaPhoneNumberViewModel(submitMfaPhoneNumberUseCase = get()) }
    viewModel { (sentTo: String, codeLength: Int) ->
        MfaCodeViewModel(sentTo = sentTo, codeLength = codeLength, submitMfaCodeUseCase = get())
    }
}
