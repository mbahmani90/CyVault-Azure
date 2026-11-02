package com.cypressit.cyvault.feature.authentication.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cypressit.cyvault.core.presentation.MviViewModel
import com.cypressit.cyvault.feature.authentication.domain.debug.ForgotPasswordPreview
import com.cypressit.cyvault.feature.authentication.domain.model.LoginResult
import com.cypressit.cyvault.feature.authentication.domain.usecase.ConfirmSignUpUseCase
import com.cypressit.cyvault.feature.authentication.domain.usecase.ForgotPasswordUseCase
import com.cypressit.cyvault.feature.authentication.domain.usecase.GetCurrentUserUseCase
import com.cypressit.cyvault.feature.authentication.domain.usecase.LoginUseCase
import com.cypressit.cyvault.feature.authentication.domain.usecase.RegisterUseCase
import com.cypressit.cyvault.feature.authentication.domain.usecase.SignInWithGoogleUseCase
import com.cypressit.cyvault.feature.authentication.domain.usecase.SignOutUseCase
import com.cypressit.cyvault.feature.authentication.domain.usecase.SubmitMfaCodeUseCase
import com.cypressit.cyvault.feature.authentication.domain.usecase.SubmitMfaPhoneNumberUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AuthViewModel(
    private val loginUseCase: LoginUseCase,
    private val registerUseCase: RegisterUseCase,
    private val confirmSignUpUseCase: ConfirmSignUpUseCase,
    private val forgotPasswordUseCase: ForgotPasswordUseCase,
    private val signOutUseCase: SignOutUseCase,
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val signInWithGoogleUseCase: SignInWithGoogleUseCase,
    private val submitMfaPhoneNumberUseCase: SubmitMfaPhoneNumberUseCase,
    private val submitMfaCodeUseCase: SubmitMfaCodeUseCase,
    /** Null except in debug builds: Forgot Password isn't finished. */
    forgotPasswordPreview: ForgotPasswordPreview? = null,
) : ViewModel(), MviViewModel<AuthState, AuthIntent, AuthEffect> {

    private val _state = MutableStateFlow(AuthState(canResetPassword = forgotPasswordPreview != null))
    override val state: StateFlow<AuthState> = _state.asStateFlow()

    private val _effect = Channel<AuthEffect>(Channel.BUFFERED)
    override val effect: Flow<AuthEffect> = _effect.receiveAsFlow()

    init {
        checkCurrentSession()
    }

    private fun checkCurrentSession() {
        viewModelScope.launch {
            val user = getCurrentUserUseCase()
            if (user != null) {
                _effect.send(AuthEffect.NavigateToHome)
            }
        }
    }

    override fun onIntent(intent: AuthIntent) {
        when (intent) {
            is AuthIntent.NameChanged ->
                _state.update { it.copy(name = intent.name, nameError = null) }

            is AuthIntent.EmailChanged ->
                _state.update { it.copy(email = intent.email, emailError = null) }

            is AuthIntent.PasswordChanged ->
                _state.update { it.copy(password = intent.password, passwordError = null) }

            is AuthIntent.ConfirmPasswordChanged ->
                _state.update { it.copy(confirmPassword = intent.confirmPassword, confirmPasswordError = null) }

            is AuthIntent.VerificationCodeChanged ->
                _state.update { it.copy(verificationCode = intent.code, verificationCodeError = null) }

            AuthIntent.ToggleMode ->
                _state.update { it.copy(isRegisterMode = !it.isRegisterMode, isForgotPasswordMode = false) }

            AuthIntent.ShowForgotPassword -> if (_state.value.canResetPassword) {
                _state.update { it.copy(isForgotPasswordMode = true, isRegisterMode = false, emailError = null) }
            }

            AuthIntent.BackToLogin ->
                _state.update {
                    it.copy(
                        isForgotPasswordMode = false,
                        isRegisterMode = false,
                        isVerificationMode = false,
                        emailError = null,
                        mfaStep = null,
                        mfaPhoneNumber = "",
                        mfaCode = "",
                        mfaPhoneNumberError = null,
                        mfaCodeError = null,
                    )
                }

            is AuthIntent.MfaPhoneNumberChanged -> _state.update {
                val typed = intent.phoneNumber.filter { c -> c.isDigit() || c == '+' || c == ' ' }
                it.copy(mfaPhoneNumber = typed.take(MAX_PHONE_NUMBER_INPUT), mfaPhoneNumberError = null)
            }

            is AuthIntent.MfaCodeChanged -> _state.update {
                it.copy(mfaCode = intent.code.filter(Char::isDigit).take(it.mfaCodeLength), mfaCodeError = null)
            }

            AuthIntent.SubmitMfaPhoneNumber -> handleMfaPhoneNumber()
            AuthIntent.SubmitMfaCode -> handleMfaCode()
            AuthIntent.SubmitLogin -> handleLogin()
            AuthIntent.SubmitRegister -> handleRegister()
            AuthIntent.SubmitVerification -> handleVerification()
            AuthIntent.SubmitForgotPassword -> handleForgotPassword()
            AuthIntent.SignInWithGoogle -> handleGoogleSignIn()
        }
    }

    private fun handleLogin() {
        if (!validateLoginInputs()) return
        val s = _state.value
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            loginUseCase(s.email.trim(), s.password)
                .onSuccess { onLoginResult(it) }
                .onFailure { _effect.send(AuthEffect.ShowError(it.message ?: "Login failed. Please try again.")) }
            _state.update { it.copy(isLoading = false) }
        }
    }

    private suspend fun onLoginResult(result: LoginResult) {
        when (result) {
            is LoginResult.SignedIn -> _effect.send(AuthEffect.NavigateToHome)
            LoginResult.PhoneNumberRequired -> _state.update {
                it.copy(mfaStep = MfaStep.PhoneNumber, mfaPhoneNumber = "", mfaPhoneNumberError = null)
            }
            is LoginResult.CodeRequired -> _state.update {
                it.copy(
                    mfaStep = MfaStep.Code,
                    mfaSentTo = result.sentTo,
                    mfaCodeLength = result.codeLength.takeIf { length -> length > 0 }
                        ?: AuthState.DEFAULT_MFA_CODE_LENGTH,
                    mfaCode = "",
                    mfaCodeError = null,
                )
            }
        }
    }

    private fun handleMfaPhoneNumber() {
        // Entra wants the international format: +, country code, number, no spaces.
        val phoneNumber = _state.value.mfaPhoneNumber.filterNot { it == ' ' }
        if (!PHONE_NUMBER.matches(phoneNumber)) {
            _state.update { it.copy(mfaPhoneNumberError = MfaInputError.InvalidPhoneNumber) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            submitMfaPhoneNumberUseCase(phoneNumber)
                .onSuccess { onLoginResult(it) }
                .onFailure { _effect.send(AuthEffect.ShowMfaFailed(it.message)) }
            _state.update { it.copy(isLoading = false) }
        }
    }

    private fun handleMfaCode() {
        val s = _state.value
        if (s.mfaCode.length != s.mfaCodeLength) {
            _state.update { it.copy(mfaCodeError = MfaInputError.IncompleteCode) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            submitMfaCodeUseCase(s.mfaCode)
                .onSuccess { _effect.send(AuthEffect.NavigateToHome) }
                .onFailure {
                    _state.update { it.copy(mfaCode = "") }
                    _effect.send(AuthEffect.ShowMfaFailed(it.message))
                }
            _state.update { it.copy(isLoading = false) }
        }
    }

    private fun handleRegister() {
        if (!validateRegisterInputs()) return
        val s = _state.value
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            registerUseCase(s.name.trim(), s.email.trim(), s.password)
                .onSuccess {
                    _state.update { it.copy(isVerificationMode = true, isRegisterMode = false) }
                }
                .onFailure {
                    _effect.send(AuthEffect.ShowError(it.message ?: "Registration failed. Please try again."))
                }
            _state.update { it.copy(isLoading = false) }
        }
    }

    private fun handleVerification() {
        val s = _state.value
        if (s.verificationCode.isBlank()) {
            _state.update { it.copy(verificationCodeError = "Please enter the verification code") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            confirmSignUpUseCase(s.email.trim(), s.verificationCode.trim())
                .onSuccess { _effect.send(AuthEffect.NavigateToHome) }
                .onFailure {
                    _effect.send(AuthEffect.ShowError(it.message ?: "Verification failed. Please try again."))
                }
            _state.update { it.copy(isLoading = false) }
        }
    }

    private fun handleGoogleSignIn() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            signInWithGoogleUseCase()
                .onSuccess { _effect.send(AuthEffect.NavigateToHome) }
                .onFailure {
                    _effect.send(AuthEffect.ShowError(it.message ?: "Google sign-in failed. Please try again."))
                }
            _state.update { it.copy(isLoading = false) }
        }
    }

    private fun handleForgotPassword() {
        val s = _state.value
        if (s.email.isBlank() || !s.email.contains('@')) {
            _state.update { it.copy(emailError = "Enter a valid email address") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            forgotPasswordUseCase(s.email.trim())
                .onSuccess {
                    _effect.send(AuthEffect.ShowMessage("Password reset email sent. Please check your inbox."))
                    _state.update { it.copy(isForgotPasswordMode = false) }
                }
                .onFailure {
                    _effect.send(
                        AuthEffect.ShowError(
                            it.message ?: "Failed to send reset email. Please try again.",
                        ),
                    )
                }
            _state.update { it.copy(isLoading = false) }
        }
    }

    private fun validateLoginInputs(): Boolean {
        var valid = true
        val s = _state.value
        if (s.email.isBlank() || !s.email.contains('@')) {
            _state.update { it.copy(emailError = "Enter a valid email address") }
            valid = false
        }
        if (s.password.length < 6) {
            _state.update { it.copy(passwordError = "Password must be at least 6 characters") }
            valid = false
        }
        return valid
    }

    private companion object {
        /** E.164: +, a country code that doesn't start with 0, 8 to 15 digits in all. */
        val PHONE_NUMBER = Regex("""\+[1-9]\d{7,14}""")

        /** E.164's 16 characters plus room for the spaces people type between groups. */
        const val MAX_PHONE_NUMBER_INPUT = 20
    }

    private fun validateRegisterInputs(): Boolean {
        var valid = validateLoginInputs()
        val s = _state.value
        if (s.name.isBlank()) {
            _state.update { it.copy(nameError = "Name is required") }
            valid = false
        }
        if (s.confirmPassword != s.password) {
            _state.update { it.copy(confirmPasswordError = "Passwords do not match") }
            valid = false
        }
        return valid
    }
}
