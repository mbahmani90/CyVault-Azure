package com.cypressit.cyvault.devicekey

/**
 * The device's signing key, one per account. The private key is made inside the platform's secure
 * hardware (Android Keystore, iOS Secure Enclave) and never leaves it; it can only sign after the
 * user passes a biometric check. [publicKey] and [createKey] block: callers switch to the I/O
 * dispatcher.
 */
interface DeviceKeyStore {
    /** A label for the API's device list, such as "Pixel 8". */
    val deviceName: String

    /** The account's public key as DER SubjectPublicKeyInfo, or null when it has none yet. */
    fun publicKey(userId: String): ByteArray?

    /**
     * Makes a new EC P-256 key pair for the account, replacing any old one, and returns the public
     * key as DER SubjectPublicKeyInfo. Throws [BiometricsUnavailableException] when the device has
     * no biometric to protect the key with.
     */
    fun createKey(userId: String): ByteArray

    /**
     * Shows the biometric prompt with [prompt]'s text and, once the user passes it, signs
     * [message] with the account's key: ECDSA with SHA-256, the signature DER-encoded. Throws
     * [BiometricCancelledException] when the user backs out, [KeyInvalidatedException] when the key
     * is gone or a new biometric made it unusable, and [BiometricsUnavailableException] when the
     * device can't show the prompt.
     */
    suspend fun sign(userId: String, message: ByteArray, prompt: SigningPrompt): ByteArray
}

/** The biometric prompt's text: what the user approves by passing it. */
data class SigningPrompt(val title: String, val description: String, val cancelLabel: String)

/** No strong biometric (fingerprint or face) is set up, or the device has none: no key can be made. */
class BiometricsUnavailableException(cause: Throwable? = null) :
    Exception("No fingerprint or face unlock is set up to protect the device key.", cause)

/** The user closed the biometric prompt without passing it: nothing was signed. */
class BiometricCancelledException : Exception("The biometric check was cancelled.")

/**
 * The account has no key, or a fingerprint or face was added since it was made, which disables it
 * for good. A new key has to be made and enrolled.
 */
class KeyInvalidatedException(cause: Throwable? = null) :
    Exception("The device key can't be used any more.", cause)
