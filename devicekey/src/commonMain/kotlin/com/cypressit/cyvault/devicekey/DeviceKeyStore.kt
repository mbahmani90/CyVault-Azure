package com.cypressit.cyvault.devicekey

/**
 * The device's signing key, one per account. The private key is made inside the platform's secure hardware (Android Keystore, iOS Secure Enclave)
 * and never leaves it; it can only sign after the user passes a biometric check. Calls block:
 * callers switch to the I/O dispatcher.
 */
interface DeviceKeyStore {
    /** A label for the API's device list, such as "Pixel 8". */
    val deviceName: String

    /** The account's public key as DER SubjectPublicKeyInfo, or null when it has none yet. */
    fun publicKey(userId: String): ByteArray?

    /**
     * Makes a new EC P-256 key pair for the account, replacing any old one, and returns the public key as DER SubjectPublicKeyInfo. Throws
     * [BiometricsUnavailableException] when the device has no biometric to protect the key with.
     */
    fun createKey(userId: String): ByteArray
}

/** No strong biometric (fingerprint or face) is set up, or the device has none: no key can be made. */
class BiometricsUnavailableException(cause: Throwable? = null) :
    Exception("No fingerprint or face unlock is set up to protect the device key.", cause)
