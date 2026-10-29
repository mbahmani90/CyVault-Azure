package com.cypressit.cyvault.auth

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** [AuthClient] on whatever the Swift side implements in [IosAuthBridge]. */
internal class BridgeAuthClient : AuthClient {

    private val bridge: IosAuthBridge
        get() = IosAuthBridgeHolder.bridge
            ?: error("IosAuthBridge not set. Call IosAuthBridgeHolder.bridge = ... before using auth.")

    override suspend fun signIn(email: String, password: String): AuthUser =
        suspendCancellableCoroutine { continuation ->
            bridge.signIn(email, password) { userId, name, error ->
                if (error != null) {
                    continuation.resumeWithException(Exception(error))
                } else {
                    continuation.resume(AuthUser(id = userId ?: "", name = name ?: "", email = email))
                }
            }
        }

    override suspend fun signUp(name: String, email: String, password: String) =
        suspendCancellableCoroutine { continuation ->
            bridge.register(name, email, password) { error ->
                if (error != null) {
                    continuation.resumeWithException(Exception(error))
                } else {
                    continuation.resume(Unit)
                }
            }
        }

    override suspend fun confirmSignUp(email: String, code: String) =
        suspendCancellableCoroutine { continuation ->
            bridge.confirmSignUp(email, code) { error ->
                if (error != null) {
                    continuation.resumeWithException(Exception(error))
                } else {
                    continuation.resume(Unit)
                }
            }
        }

    override suspend fun resetPassword(email: String) =
        suspendCancellableCoroutine { continuation ->
            bridge.forgotPassword(email) { error ->
                if (error != null) {
                    continuation.resumeWithException(Exception(error))
                } else {
                    continuation.resume(Unit)
                }
            }
        }

    override suspend fun signOut() =
        suspendCancellableCoroutine { continuation ->
            bridge.signOut { error ->
                if (error != null) {
                    continuation.resumeWithException(Exception(error))
                } else {
                    continuation.resume(Unit)
                }
            }
        }

    override suspend fun currentUser(): AuthUser? =
        suspendCancellableCoroutine { continuation ->
            bridge.getCurrentUser { userId, name, email, error ->
                if (error != null || userId == null) {
                    continuation.resume(null)
                } else {
                    continuation.resume(AuthUser(id = userId, name = name ?: "", email = email ?: ""))
                }
            }
        }

    override suspend fun signInWithGoogle(): AuthUser =
        suspendCancellableCoroutine { continuation ->
            bridge.signInWithGoogle { userId, name, email, error ->
                if (error != null) {
                    continuation.resumeWithException(Exception(error))
                } else {
                    continuation.resume(AuthUser(id = userId ?: "", name = name ?: "", email = email ?: ""))
                }
            }
        }

    // The bridge has no tokens yet: CyVault's iOS app still signs in with Cognito, whose
    // tokens the Azure backend doesn't accept.
    override suspend fun accessToken(scopes: List<String>): String =
        throw UnsupportedOperationException("Access tokens aren't supported by the iOS bridge yet")
}
