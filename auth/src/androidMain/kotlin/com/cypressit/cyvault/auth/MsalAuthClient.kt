package com.cypressit.cyvault.auth

import com.microsoft.identity.client.AcquireTokenParameters
import com.microsoft.identity.client.AuthenticationCallback
import com.microsoft.identity.client.IAccount
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.exception.MsalDeclinedScopeException
import com.microsoft.identity.client.exception.MsalException
import com.microsoft.identity.nativeauth.AuthMethod
import com.microsoft.identity.nativeauth.UserAttributes
import com.microsoft.identity.nativeauth.parameters.NativeAuthChallengeAuthMethodParameters
import com.microsoft.identity.nativeauth.parameters.NativeAuthGetAccessTokenParameters
import com.microsoft.identity.nativeauth.parameters.NativeAuthResetPasswordParameters
import com.microsoft.identity.nativeauth.parameters.NativeAuthSignInParameters
import com.microsoft.identity.nativeauth.parameters.NativeAuthSignUpParameters
import com.microsoft.identity.nativeauth.statemachine.errors.Error
import com.microsoft.identity.nativeauth.statemachine.results.GetAccessTokenResult
import com.microsoft.identity.nativeauth.statemachine.results.GetAccountResult
import com.microsoft.identity.nativeauth.statemachine.results.MFARequiredResult
import com.microsoft.identity.nativeauth.statemachine.results.RegisterStrongAuthChallengeResult
import com.microsoft.identity.nativeauth.statemachine.results.ResetPasswordStartResult
import com.microsoft.identity.nativeauth.statemachine.results.SignInResult
import com.microsoft.identity.nativeauth.statemachine.results.SignOutResult
import com.microsoft.identity.nativeauth.statemachine.results.SignUpResult
import com.microsoft.identity.nativeauth.statemachine.states.AccountState
import com.microsoft.identity.nativeauth.statemachine.states.AwaitingMFAState
import com.microsoft.identity.nativeauth.statemachine.states.RegisterStrongAuthState
import com.microsoft.identity.nativeauth.statemachine.states.ResetPasswordCodeRequiredState
import com.microsoft.identity.nativeauth.statemachine.states.SignUpCodeRequiredState
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** [AuthClient] on Microsoft Entra External ID through MSAL native auth. */
internal class MsalAuthClient(
    private val clientProvider: MsalClientProvider,
    private val activityProvider: ActivityProvider,
) : AuthClient {

    // Native auth is a state machine: the state returned by one step is needed for the next,
    // e.g. signUp() returns the state that confirmSignUp() submits the code to.
    private var pendingSignUp: SignUpCodeRequiredState? = null
    private var pendingPasswordReset: ResetPasswordCodeRequiredState? = null

    // MFA: the account's first MFA sign-in registers a phone (RegisterStrongAuthState), later
    // ones only send a code. Either way the code goes to a different state's submitChallenge(),
    // so the step that sent it keeps that call here.
    private var pendingPhoneRegistration: Pair<RegisterStrongAuthState, AuthMethod>? = null
    private var pendingMfaCode: (suspend (String) -> Any)? = null

    override suspend fun signIn(email: String, password: String): SignInStep {
        val parameters = NativeAuthSignInParameters(email).apply {
            this.password = password.toCharArray()
            clientProvider.signInScopes.takeIf { it.isNotEmpty() }?.let { scopes = it }
        }
        pendingPhoneRegistration = null
        pendingMfaCode = null
        return when (val result = clientProvider.get().signIn(parameters)) {
            is SignInResult.Complete -> SignInStep.SignedIn(result.resultValue.toAuthUser())
            is SignInResult.StrongAuthMethodRegistrationRequired -> {
                pendingPhoneRegistration = result.nextState to result.authMethods.sms()
                SignInStep.PhoneNumberRequired
            }
            is SignInResult.MFARequired -> sendMfaCode(result.nextState, result.authMethods.sms())
            else -> fail(result, "Sign in failed")
        }
    }

    override suspend fun submitMfaPhoneNumber(phoneNumber: String): SignInStep {
        val (state, authMethod) = pendingPhoneRegistration ?: throw sessionExpired()
        val parameters = NativeAuthChallengeAuthMethodParameters(authMethod, phoneNumber)
        return when (val result = state.challengeAuthMethod(parameters)) {
            is RegisterStrongAuthChallengeResult.VerificationRequired -> {
                pendingPhoneRegistration = null
                val sent = result.result
                pendingMfaCode = { code -> sent.getNextState().submitChallenge(code) }
                SignInStep.CodeRequired(sentTo = sent.getSentTo(), codeLength = sent.getCodeLength())
            }
            is SignInResult.Complete -> {
                pendingPhoneRegistration = null
                SignInStep.SignedIn(result.resultValue.toAuthUser())
            }
            // The registration state is kept, so the user can correct the number and retry.
            else -> fail(result, "Couldn't send a code to this number")
        }
    }

    override suspend fun submitMfaCode(code: String): AuthUser {
        val submit = pendingMfaCode ?: throw sessionExpired()
        return when (val result = submit(code)) {
            is SignInResult.Complete -> {
                pendingMfaCode = null
                result.resultValue.toAuthUser()
            }
            else -> fail(result, "Verification failed")
        }
    }

    private suspend fun sendMfaCode(state: AwaitingMFAState, authMethod: AuthMethod): SignInStep =
        when (val result = state.requestChallenge(authMethod)) {
            is MFARequiredResult.VerificationRequired -> {
                pendingMfaCode = { code -> result.nextState.submitChallenge(code) }
                SignInStep.CodeRequired(sentTo = result.sentTo, codeLength = result.codeLength)
            }
            else -> fail(result, "Couldn't send the code")
        }

    /** The SMS method among those Entra offers; it's the only MFA method the app supports. */
    private fun List<AuthMethod>.sms(): AuthMethod =
        firstOrNull { it.challengeChannel.equals("sms", ignoreCase = true) }
            ?: throw Exception("Sign in needs a verification method this app doesn't support")

    private fun sessionExpired() = Exception("Sign in session expired. Please sign in again.")

    override suspend fun signUp(name: String, email: String, password: String) {
        val parameters = NativeAuthSignUpParameters(email).apply {
            this.password = password.toCharArray()
            attributes = UserAttributes.Builder().displayName(name).build()
        }
        when (val result = clientProvider.get().signUp(parameters)) {
            is SignUpResult.CodeRequired -> pendingSignUp = result.nextState
            else -> fail(result, "Sign up failed")
        }
    }

    override suspend fun confirmSignUp(email: String, code: String) {
        val state = pendingSignUp ?: throw Exception("Sign up session expired. Please register again.")
        when (val result = state.submitCode(code)) {
            is SignUpResult.Complete -> pendingSignUp = null
            else -> fail(result, "Verification failed")
        }
    }

    override suspend fun resetPassword(email: String) {
        val parameters = NativeAuthResetPasswordParameters(email)
        when (val result = clientProvider.get().resetPassword(parameters)) {
            is ResetPasswordStartResult.CodeRequired -> pendingPasswordReset = result.nextState
            else -> fail(result, "Password reset failed")
        }
    }

    override suspend fun signInWithGoogle(): AuthUser {
        // Native auth only covers in-app email flows. Google goes through MSAL's browser flow:
        // Entra's sign-in page opens in a browser tab and hands the user over to Google
        // (the Google identity provider set up in the Entra external tenant).
        val activity = activityProvider.currentActivity
            ?: throw Exception("Google sign-in failed. Please try again.")
        val client = clientProvider.get()
        val account = try {
            suspendCancellableCoroutine<IAuthenticationResult> { continuation ->
                val parameters = AcquireTokenParameters.Builder()
                    .startAuthorizationFromActivity(activity)
                    // The API scope makes Entra run the API's Conditional Access (MFA) here, on
                    // its own pages in this browser tab: native auth's in-app MFA screens only
                    // cover email sign-in.
                    .withScopes(listOf("openid", "offline_access") + clientProvider.signInScopes)
                    // Skips Entra's provider picker and goes straight to Google.
                    .withDomainHint("google.com")
                    .withCallback(object : AuthenticationCallback {
                        override fun onSuccess(result: IAuthenticationResult) = continuation.resume(result)
                        override fun onError(exception: MsalException) = continuation.resumeWithException(exception)
                        override fun onCancel() = continuation.resumeWithException(
                            Exception("Google sign-in was cancelled"),
                        )
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
        // currentUser() and signOut() keep working through the native auth account.
        return currentAccount()?.toAuthUser() ?: account.toAuthUser()
    }

    override suspend fun currentUser(): AuthUser? = try {
        currentAccount()?.toAuthUser()
    } catch (e: Exception) {
        null
    }

    override suspend fun accessToken(scopes: List<String>): String {
        val account = currentAccount() ?: throw Exception("Not signed in")
        val parameters = NativeAuthGetAccessTokenParameters().apply { this.scopes = scopes }
        // Served from MSAL's cache while valid; refreshed silently with the refresh token after.
        // Google accounts work too, since the browser flow shares the same cache.
        return when (val result = account.getAccessToken(parameters)) {
            is GetAccessTokenResult.Complete -> result.resultValue.accessToken
            else -> fail(result, "Couldn't get an access token")
        }
    }

    override suspend fun signOut() {
        val account = currentAccount() ?: return
        when (val result = account.signOut()) {
            is SignOutResult.Complete -> Unit
            else -> fail(result, "Sign out failed")
        }
    }

    private suspend fun currentAccount(): AccountState? =
        when (val result = clientProvider.get().getCurrentAccount()) {
            is GetAccountResult.AccountFound -> result.resultValue
            else -> null
        }

    private fun AccountState.toAuthUser(): AuthUser {
        val claims = getClaims().orEmpty()
        val account = getAccount()
        return AuthUser(
            id = claims["oid"] as? String ?: account.id,
            name = claims["name"] as? String ?: "",
            email = claims["email"] as? String ?: account.username,
        )
    }

    private fun IAccount.toAuthUser(): AuthUser {
        val claims = claims.orEmpty()
        return AuthUser(
            id = claims["oid"] as? String ?: id,
            name = claims["name"] as? String ?: "",
            email = claims["email"] as? String ?: username,
        )
    }

    private fun fail(result: Any, fallbackMessage: String): Nothing {
        val message = (result as? Error)?.errorMessage
        throw Exception(message ?: fallbackMessage)
    }
}
