package com.cypressit.cyvault.feature.authentication.data.remote

import com.cypressit.cyvault.core.platform.ActivityProvider
import com.cypressit.cyvault.feature.authentication.domain.model.User
import com.microsoft.identity.client.AcquireTokenParameters
import com.microsoft.identity.client.AuthenticationCallback
import com.microsoft.identity.client.IAccount
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.exception.MsalDeclinedScopeException
import com.microsoft.identity.client.exception.MsalException
import com.microsoft.identity.nativeauth.UserAttributes
import com.microsoft.identity.nativeauth.parameters.NativeAuthGetAccessTokenParameters
import com.microsoft.identity.nativeauth.parameters.NativeAuthResetPasswordParameters
import com.microsoft.identity.nativeauth.parameters.NativeAuthSignInParameters
import com.microsoft.identity.nativeauth.parameters.NativeAuthSignUpParameters
import com.microsoft.identity.nativeauth.statemachine.errors.Error
import com.microsoft.identity.nativeauth.statemachine.results.GetAccessTokenResult
import com.microsoft.identity.nativeauth.statemachine.results.GetAccountResult
import com.microsoft.identity.nativeauth.statemachine.results.ResetPasswordStartResult
import com.microsoft.identity.nativeauth.statemachine.results.SignInResult
import com.microsoft.identity.nativeauth.statemachine.results.SignOutResult
import com.microsoft.identity.nativeauth.statemachine.results.SignUpResult
import com.microsoft.identity.nativeauth.statemachine.states.AccountState
import com.microsoft.identity.nativeauth.statemachine.states.ResetPasswordCodeRequiredState
import com.microsoft.identity.nativeauth.statemachine.states.SignUpCodeRequiredState
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import org.koin.mp.KoinPlatform

actual class AuthApiService actual constructor() {

    // The constructor is shared with iOS, so Android-only dependencies come from Koin,
    // like the Context in MsalClientProvider.
    private val activityProvider: ActivityProvider by lazy { KoinPlatform.getKoin().get() }

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
        // Native auth only covers in-app email flows. Google goes through MSAL's browser flow:
        // Entra's sign-in page opens in a browser tab and hands the user over to Google
        // (the Google identity provider set up in the Entra external tenant).
        val activity = activityProvider.currentActivity
            ?: throw Exception("Google sign-in failed. Please try again.")
        val client = MsalClientProvider.get()
        val account = try {
            suspendCancellableCoroutine<IAuthenticationResult> { continuation ->
                val parameters = AcquireTokenParameters.Builder()
                    .startAuthorizationFromActivity(activity)
                    // MSAL rejects an empty list. No API scopes are needed, so ask only for the
                    // sign-in scopes the app registration already grants.
                    .withScopes(listOf("openid", "offline_access"))
                    // Skips Entra's provider picker and goes straight to Google.
                    .withDomainHint("google.com")
                    .withCallback(object : AuthenticationCallback {
                        override fun onSuccess(result: IAuthenticationResult) = continuation.resume(result)
                        override fun onError(exception: MsalException) = continuation.resumeWithException(exception)
                        override fun onCancel() = continuation.resumeWithException(Exception("Google sign-in was cancelled"))
                    })
                    .build()
                client.acquireToken(parameters)
            }.account
        } catch (e: MsalDeclinedScopeException) {
            // Entra granted fewer scopes than requested, but the user is signed in and the
            // account (with its ID token) is already cached. Only the account is needed here.
            e.silentParametersForGrantedScopes.account ?: throw e
        }
        // The browser flow stores the account in the same MSAL cache as native auth, so
        // getCurrentUser() and signOut() keep working through the native auth account.
        return currentAccount()?.toUser() ?: account.toUser()
    }

    actual suspend fun accessToken(scopes: List<String>): String {
        val account = currentAccount() ?: throw Exception("Not signed in")
        val parameters = NativeAuthGetAccessTokenParameters().apply { this.scopes = scopes }
        // Served from MSAL's cache while valid; refreshed silently with the refresh token after.
        // Google accounts work too, since the browser flow shares the same cache.
        return when (val result = account.getAccessToken(parameters)) {
            is GetAccessTokenResult.Complete -> result.resultValue.accessToken
            else -> fail(result, "Couldn't get an access token")
        }
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

    private fun IAccount.toUser(): User {
        val claims = claims.orEmpty()
        return User(
            id = claims["oid"] as? String ?: id,
            name = claims["name"] as? String ?: "",
            email = claims["email"] as? String ?: username,
            token = idToken.orEmpty(),
        )
    }

    private fun fail(result: Any, fallbackMessage: String): Nothing {
        val message = (result as? Error)?.errorMessage
        throw Exception(message ?: fallbackMessage)
    }
}
