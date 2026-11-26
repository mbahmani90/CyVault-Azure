package com.cypressit.cyvault.feature.authentication.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cypressit.cyvault.core.presentation.MviViewModel
import com.cypressit.cyvault.feature.authentication.domain.debug.ForgotPasswordPreview
import com.cypressit.cyvault.feature.authentication.domain.usecase.GetCurrentUserUseCase
import com.cypressit.cyvault.feature.authentication.domain.usecase.LoginUseCase
import com.cypressit.cyvault.feature.authentication.domain.usecase.SignInWithGoogleUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LoginViewModel(
    private val loginUseCase: LoginUseCase,
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val signInWithGoogleUseCase: SignInWithGoogleUseCase,
    /** Null except in debug builds: Forgot Password isn't finished. */
    forgotPasswordPreview: ForgotPasswordPreview? = null,
) : ViewModel(), MviViewModel<LoginState, LoginIntent, AuthEffect> {

    private val _state = MutableStateFlow(LoginState(canResetPassword = forgotPasswordPreview != null))
    override val state: StateFlow<LoginState> = _state.asStateFlow()

    private val _effect = Channel<AuthEffect>(Channel.BUFFERED)
    override val effect: Flow<AuthEffect> = _effect.receiveAsFlow()

    init {
        checkCurrentSession()
    }

    private fun checkCurrentSession() {
        viewModelScope.launch {
            if (getCurrentUserUseCase() != null) navigate(AuthNavEvent.Home)
        }
    }

    override fun onIntent(intent: LoginIntent) {
        when (intent) {
            is LoginIntent.EmailChanged ->
                _state.update { it.copy(email = intent.email, emailError = null) }

            is LoginIntent.PasswordChanged ->
                _state.update { it.copy(password = intent.password, passwordError = null) }

            LoginIntent.SubmitLogin -> handleLogin()
            LoginIntent.SignInWithGoogle -> handleGoogleSignIn()
            LoginIntent.OpenRegister -> viewModelScope.launch { navigate(AuthNavEvent.Register) }

            LoginIntent.OpenForgotPassword -> if (_state.value.canResetPassword) {
                viewModelScope.launch { navigate(AuthNavEvent.ForgotPassword(_state.value.email.trim())) }
            }

            LoginIntent.ResetEmailSent -> viewModelScope.launch {
                _effect.send(AuthEffect.ShowMessage("Password reset email sent. Please check your inbox."))
            }
        }
    }

    private fun handleLogin() {
        if (!validateInputs()) return
        val s = _state.value
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            loginUseCase(s.email.trim(), s.password)
                .onSuccess { navigate(it.toNavEvent()) }
                .onFailure { _effect.send(AuthEffect.ShowError(it.message ?: "Login failed. Please try again.")) }
            _state.update { it.copy(isLoading = false) }
        }
    }

    private fun handleGoogleSignIn() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            signInWithGoogleUseCase()
                .onSuccess { navigate(AuthNavEvent.Home) }
                .onFailure {
                    _effect.send(AuthEffect.ShowError(it.message ?: "Google sign-in failed. Please try again."))
                }
            _state.update { it.copy(isLoading = false) }
        }
    }

    private fun validateInputs(): Boolean {
        val s = _state.value
        val emailError = if (AuthInput.isValidEmail(s.email)) null else "Enter a valid email address"
        val passwordError = if (s.password.length < AuthInput.MIN_PASSWORD_LENGTH) {
            "Password must be at least ${AuthInput.MIN_PASSWORD_LENGTH} characters"
        } else {
            null
        }
        _state.update { it.copy(emailError = emailError, passwordError = passwordError) }
        return emailError == null && passwordError == null
    }

    private suspend fun navigate(to: AuthNavEvent) = _effect.send(AuthEffect.Navigate(to))
}
