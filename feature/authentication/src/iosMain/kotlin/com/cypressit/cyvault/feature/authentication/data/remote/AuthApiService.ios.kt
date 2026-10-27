package com.cypressit.cyvault.feature.authentication.data.remote

import com.cypressit.cyvault.feature.authentication.domain.model.User
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

actual class AuthApiService actual constructor() {

    private val bridge: IosAuthBridge
        get() = IosAuthBridgeHolder.bridge
            ?: error("IosAuthBridge not set. Call IosAuthBridgeHolder.bridge = ... before using auth.")

    actual suspend fun login(email: String, password: String): User =
        suspendCancellableCoroutine { continuation ->
            bridge.signIn(email, password) { userId, name, error ->
                if (error != null) {
                    continuation.resumeWithException(Exception(error))
                } else {
                    continuation.resume(User(id = userId ?: "", name = name ?: "", email = email, token = ""))
                }
            }
        }

    actual suspend fun register(name: String, email: String, password: String) =
        suspendCancellableCoroutine { continuation ->
            bridge.register(name, email, password) { error ->
                if (error != null) continuation.resumeWithException(Exception(error))
                else continuation.resume(Unit)
            }
        }

    actual suspend fun confirmSignUp(email: String, code: String) =
        suspendCancellableCoroutine { continuation ->
            bridge.confirmSignUp(email, code) { error ->
                if (error != null) continuation.resumeWithException(Exception(error))
                else continuation.resume(Unit)
            }
        }

    actual suspend fun forgotPassword(email: String) =
        suspendCancellableCoroutine { continuation ->
            bridge.forgotPassword(email) { error ->
                if (error != null) continuation.resumeWithException(Exception(error))
                else continuation.resume(Unit)
            }
        }

    actual suspend fun signOut() =
        suspendCancellableCoroutine { continuation ->
            bridge.signOut { error ->
                if (error != null) continuation.resumeWithException(Exception(error))
                else continuation.resume(Unit)
            }
        }

    actual suspend fun getCurrentUser(): User? =
        suspendCancellableCoroutine { continuation ->
            bridge.getCurrentUser { userId, name, email, error ->
                if (error != null || userId == null) continuation.resume(null)
                else continuation.resume(User(id = userId, name = name ?: "", email = email ?: "", token = ""))
            }
        }

    actual suspend fun signInWithGoogle(): User =
        suspendCancellableCoroutine { continuation ->
            bridge.signInWithGoogle { userId, name, email, error ->
                if (error != null) {
                    continuation.resumeWithException(Exception(error))
                } else {
                    continuation.resume(User(id = userId ?: "", name = name ?: "", email = email ?: "", token = ""))
                }
            }
        }
}
