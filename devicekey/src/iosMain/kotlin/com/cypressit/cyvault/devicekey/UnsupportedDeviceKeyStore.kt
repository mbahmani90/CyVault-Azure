package com.cypressit.cyvault.devicekey

/**
 * No Secure Enclave key yet: iOS has nothing to sign with until it calls an API that checks
 * signatures. Until then no key is ever made, as if the device had no biometric.
 */
class UnsupportedDeviceKeyStore : DeviceKeyStore {
    override val deviceName: String = "iPhone"

    override fun publicKey(userId: String): ByteArray? = null

    override fun createKey(userId: String): ByteArray =
        throw BiometricsUnavailableException(UnsupportedOperationException("Device keys aren't supported on iOS yet"))
}
