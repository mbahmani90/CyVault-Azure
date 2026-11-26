package com.cypressit.cyvault.feature.authentication.fake

import com.cypressit.cyvault.feature.authentication.data.repository.AuthRepositoryImpl
import com.cypressit.cyvault.feature.authentication.domain.debug.ForgotPasswordPreview
import com.cypressit.cyvault.feature.authentication.domain.usecase.ConfirmSignUpUseCase
import com.cypressit.cyvault.feature.authentication.domain.usecase.ForgotPasswordUseCase
import com.cypressit.cyvault.feature.authentication.domain.usecase.GetCurrentUserUseCase
import com.cypressit.cyvault.feature.authentication.domain.usecase.LoginUseCase
import com.cypressit.cyvault.feature.authentication.domain.usecase.RegisterUseCase
import com.cypressit.cyvault.feature.authentication.domain.usecase.SignInWithGoogleUseCase
import com.cypressit.cyvault.feature.authentication.domain.usecase.SubmitMfaCodeUseCase
import com.cypressit.cyvault.feature.authentication.domain.usecase.SubmitMfaPhoneNumberUseCase
import com.cypressit.cyvault.feature.authentication.presentation.viewmodel.ForgotPasswordViewModel
import com.cypressit.cyvault.feature.authentication.presentation.viewmodel.LoginViewModel
import com.cypressit.cyvault.feature.authentication.presentation.viewmodel.MfaCodeViewModel
import com.cypressit.cyvault.feature.authentication.presentation.viewmodel.MfaPhoneNumberViewModel
import com.cypressit.cyvault.feature.authentication.presentation.viewmodel.RegisterViewModel
import com.cypressit.cyvault.feature.authentication.presentation.viewmodel.VerificationViewModel

/** The auth screens' ViewModels on top of [client], built without Koin. */
class AuthTestViewModels(val client: FakeAuthClient = FakeAuthClient()) {
    private val repository = AuthRepositoryImpl(client)

    fun login(debugBuild: Boolean = false) = LoginViewModel(
        loginUseCase = LoginUseCase(repository),
        getCurrentUserUseCase = GetCurrentUserUseCase(repository),
        signInWithGoogleUseCase = SignInWithGoogleUseCase(repository),
        forgotPasswordPreview = if (debugBuild) ForgotPasswordPreview else null,
    )

    fun register() = RegisterViewModel(RegisterUseCase(repository))

    fun verification(email: String = "ada@example.com") =
        VerificationViewModel(email, ConfirmSignUpUseCase(repository))

    fun forgotPassword(email: String = "") = ForgotPasswordViewModel(email, ForgotPasswordUseCase(repository))

    fun mfaPhoneNumber() = MfaPhoneNumberViewModel(SubmitMfaPhoneNumberUseCase(repository))

    fun mfaCode(sentTo: String = "+351*****678", codeLength: Int = 6) =
        MfaCodeViewModel(sentTo, codeLength, SubmitMfaCodeUseCase(repository))
}
