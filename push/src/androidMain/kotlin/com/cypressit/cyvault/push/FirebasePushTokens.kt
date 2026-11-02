package com.cypressit.cyvault.push

import com.google.android.gms.tasks.Task
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** The Firebase Cloud Messaging token of this app install. */
internal class FirebasePushTokens : PushTokens {

    override suspend fun current(): String? =
        try {
            FirebaseMessaging.getInstance().token.await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // No Google Play services, or offline: the phone just gets no pushes this time.
            null
        }

    override suspend fun delete() {
        FirebaseMessaging.getInstance().deleteToken().await()
    }
}

private suspend fun <T> Task<T>.await(): T =
    suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            val error = task.exception
            when {
                error != null -> continuation.resumeWithException(error)
                task.isCanceled -> continuation.cancel()
                else -> continuation.resume(task.result)
            }
        }
    }
