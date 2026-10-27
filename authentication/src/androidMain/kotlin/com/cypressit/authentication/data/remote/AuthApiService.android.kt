package com.cypressit.authentication.data.remote

import com.cypressit.authentication.domain.model.User
import com.microsoft.identity.nativeauth.UserAttributes
import com.microsoft.identity.nativeauth.parameters.NativeAuthResetPasswordParameters
import com.microsoft.identity.nativeauth.parameters.NativeAuthSignInParameters
import com.microsoft.identity.nativeauth.parameters.NativeAuthSignUpParameters
import com.microsoft.identity.nativeauth.statemachine.errors.Error
import com.microsoft.identity.nativeauth.statemachine.results.GetAccountResult
import com.microsoft.identity.nativeauth.statemachine.results.ResetPasswordStartResult
import com.microsoft.identity.nativeauth.statemachine.results.SignInResult
import com.microsoft.identity.nativeauth.statemachine.results.SignOutResult
import com.microsoft.identity.nativeauth.statemachine.results.SignUpResult
import com.microsoft.identity.nativeauth.statemachine.states.AccountState
import com.microsoft.identity.nativeauth.statemachine.states.ResetPasswordCodeRequiredState
import com.microsoft.identity.nativeauth.statemachine.states.SignUpCodeRequiredState

actual class AuthApiService actual constructor() {

    // Native auth is a state machine: the state returned by one step is needed for the next,
    // e.g. register() returns the state that confirmSignUp() submits the code to.
    private var pendingSignUp: SignUpCodeRequiredState? = null
    private var pendingPasswordReset: ResetPasswordCodeRequiredState? = null

    actual suspend fun login(email: String, password: String): User {
        val parameters = NativeAuthSignInParameters(email).apply {
            this.password = password.toCharArray()
        }
        return when (val result = MsalClientProvider.get().signIn(parameters)) {
            is SignInResult.Complete -> result.resultValue.toUser()
            else -> fail(result, "Sign in failed")
        }
    }

    actual suspend fun register(name: String, email: String, password: String) {
        val parameters = NativeAuthSignUpParameters(email).apply {
            this.password = password.toCharArray()
            attributes = UserAttributes.Builder().displayName(name).build()
        }
        when (val result = MsalClientProvider.get().signUp(parameters)) {
            is SignUpResult.CodeRequired -> pendingSignUp = result.nextState
            else -> fail(result, "Sign up failed")
        }
    }

    actual suspend fun confirmSignUp(email: String, code: String) {
        val state = pendingSignUp ?: throw Exception("Sign up session expired. Please register again.")
        when (val result = state.submitCode(code)) {
            is SignUpResult.Complete -> pendingSignUp = null
            else -> fail(result, "Verification failed")
        }
    }

    actual suspend fun forgotPassword(email: String) {
        val parameters = NativeAuthResetPasswordParameters(email)
        when (val result = MsalClientProvider.get().resetPassword(parameters)) {
            is ResetPasswordStartResult.CodeRequired -> pendingPasswordReset = result.nextState
            else -> fail(result, "Password reset failed")
        }
    }

    actual suspend fun signOut() {
        val account = currentAccount() ?: return
        when (val result = account.signOut()) {
            is SignOutResult.Complete -> Unit
            else -> fail(result, "Sign out failed")
        }
    }

    actual suspend fun getCurrentUser(): User? = try {
        currentAccount()?.toUser()
    } catch (e: Exception) {
        null
    }

    actual suspend fun signInWithGoogle(): User {
        // Native auth only covers in-app email flows; Google needs MSAL's browser flow
        // and the Google identity provider set up in the Entra external tenant.
        throw Exception("Google sign-in is not available yet")
    }

    private suspend fun currentAccount(): AccountState? =
        when (val result = MsalClientProvider.get().getCurrentAccount()) {
            is GetAccountResult.AccountFound -> result.resultValue
            else -> null
        }

    private fun AccountState.toUser(): User {
        val claims = getClaims().orEmpty()
        val account = getAccount()
        return User(
            id = claims["oid"] as? String ?: account.id,
            name = claims["name"] as? String ?: "",
            email = claims["email"] as? String ?: account.username,
            token = getIdToken().orEmpty(),
        )
    }

    private fun fail(result: Any, fallbackMessage: String): Nothing {
        val message = (result as? Error)?.errorMessage
        throw Exception(message ?: fallbackMessage)
    }
}
