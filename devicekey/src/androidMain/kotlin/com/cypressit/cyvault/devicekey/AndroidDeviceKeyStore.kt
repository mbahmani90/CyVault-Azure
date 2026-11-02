package com.cypressit.cyvault.devicekey

import android.annotation.TargetApi
import android.app.Activity
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import kotlinx.coroutines.suspendCancellableCoroutine
import java.security.InvalidAlgorithmParameterException
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.ProviderException
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Keeps each account's key in the Android Keystore, in StrongBox when the device has it. The key
 * signs only right after a strong biometric check (no time window), and adding a fingerprint or
 * face invalidates it, so someone who adds their own biometric can't use it.
 *
 * Needs Android 9 (API 28), the first with the system BiometricPrompt: below it no key is made, as
 * if the device had no biometric.
 */
internal class AndroidDeviceKeyStore(private val activityProvider: ActivityProvider) : DeviceKeyStore {

    private val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

    override val deviceName: String = Build.MODEL.orEmpty().take(MAX_DEVICE_NAME_LENGTH)

    override fun publicKey(userId: String): ByteArray? =
        keyStore.getCertificate(aliasFor(userId))?.publicKey?.encoded

    override fun createKey(userId: String): ByteArray {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) throw BiometricsUnavailableException()
        val alias = aliasFor(userId)
        keyStore.deleteEntry(alias)

        return try {
            generate(alias, strongBox = true)
        } catch (e: InvalidAlgorithmParameterException) {
            // Thrown when no biometric is enrolled: a key that needs one can't be made.
            throw BiometricsUnavailableException(e)
        } catch (e: IllegalStateException) {
            throw BiometricsUnavailableException(e)
        }
    }

    override suspend fun sign(userId: String, message: ByteArray, prompt: SigningPrompt): ByteArray {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) throw BiometricsUnavailableException()
        val privateKey = keyStore.getKey(aliasFor(userId), null) as? PrivateKey ?: throw KeyInvalidatedException()

        // Before the prompt: a key a new biometric disabled fails here, without asking the user.
        val signature = Signature.getInstance(SIGNATURE_ALGORITHM)
        try {
            signature.initSign(privateKey)
        } catch (e: KeyPermanentlyInvalidatedException) {
            throw KeyInvalidatedException(e)
        }

        // No screen to show the prompt on (the app went to the background): nothing is signed.
        val activity = activityProvider.currentActivity ?: throw BiometricCancelledException()
        val authorized = authenticate(activity, signature, prompt)
        authorized.update(message)
        return authorized.sign()
    }

    /** Shows the prompt; returns [signature] once the Keystore has unlocked it for one use. */
    @TargetApi(Build.VERSION_CODES.P)
    private suspend fun authenticate(activity: Activity, signature: Signature, prompt: SigningPrompt): Signature =
        suspendCancellableCoroutine { continuation ->
            val cancellation = CancellationSignal()
            continuation.invokeOnCancellation { cancellation.cancel() }

            fun fail(error: Exception) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }

            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    // The Signature passed in, now unlocked for one use; never missing in practice.
                    val unlocked = result.cryptoObject?.signature ?: return fail(BiometricCancelledException())
                    if (continuation.isActive) continuation.resume(unlocked)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                    fail(
                        when (errorCode) {
                            BiometricPrompt.BIOMETRIC_ERROR_NO_BIOMETRICS,
                            BiometricPrompt.BIOMETRIC_ERROR_HW_NOT_PRESENT,
                            BiometricPrompt.BIOMETRIC_ERROR_HW_UNAVAILABLE,
                            -> BiometricsUnavailableException()
                            // Cancelled, timed out, or locked out after too many tries: nothing signed.
                            else -> BiometricCancelledException()
                        },
                    )
                }

                // A finger or face that didn't match: the prompt says so and lets the user try again.
                override fun onAuthenticationFailed() = Unit
            }

            activity.runOnUiThread {
                val builder = BiometricPrompt.Builder(activity)
                    .setTitle(prompt.title)
                    .setDescription(prompt.description)
                    .setNegativeButton(prompt.cancelLabel, activity.mainExecutor) { _, _ ->
                        fail(BiometricCancelledException())
                    }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                }
                builder.build().authenticate(
                    BiometricPrompt.CryptoObject(signature),
                    cancellation,
                    activity.mainExecutor,
                    callback,
                )
            }
        }

    @TargetApi(Build.VERSION_CODES.P)
    private fun generate(alias: String, strongBox: Boolean): ByteArray {
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .setIsStrongBoxBacked(strongBox)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                } else {
                    // -1: every use needs its own biometric check, the same as a timeout of 0 above.
                    @Suppress("DEPRECATION")
                    setUserAuthenticationValidityDurationSeconds(-1)
                }
            }
            .build()

        return try {
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEY_STORE)
                .apply { initialize(spec) }
                .generateKeyPair()
                .public
                .encoded
        } catch (e: ProviderException) {
            // The device has no StrongBox chip: the Keystore's TEE still keeps the key in hardware.
            if (strongBox && e is StrongBoxUnavailableException) generate(alias, strongBox = false) else throw e
        }
    }

    private fun aliasFor(userId: String) = "device-key-${requireUserId(userId)}"

    /** The ID goes into a Keystore alias: letters, digits and '-' only. */
    private fun requireUserId(userId: String): String {
        require(userId.isNotEmpty() && userId.all { it.isLetterOrDigit() || it == '-' }) { "Invalid user ID" }
        return userId
    }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"

        // What the API verifies: ECDSA over SHA-256, DER-encoded.
        const val SIGNATURE_ALGORITHM = "SHA256withECDSA"

        // The API's limit on a device name.
        const val MAX_DEVICE_NAME_LENGTH = 50
    }
}
