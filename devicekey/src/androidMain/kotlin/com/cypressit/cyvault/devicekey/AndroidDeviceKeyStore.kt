package com.cypressit.cyvault.devicekey

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.security.InvalidAlgorithmParameterException
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.ProviderException
import java.security.spec.ECGenParameterSpec

/**
 * Keeps each account's key in the Android Keystore, in StrongBox when the device has it. The key
 * signs only right after a strong biometric check (no time window), and adding a fingerprint or
 * face invalidates it, so someone who adds their own biometric can't use it.
 */
class AndroidDeviceKeyStore : DeviceKeyStore {

    private val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

    override val deviceName: String = Build.MODEL.orEmpty().take(MAX_DEVICE_NAME_LENGTH)

    override fun publicKey(userId: String): ByteArray? =
        keyStore.getCertificate(aliasFor(userId))?.publicKey?.encoded

    override fun createKey(userId: String): ByteArray {
        val alias = aliasFor(userId)
        keyStore.deleteEntry(alias)

        return try {
            generate(alias, strongBox = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
        } catch (e: InvalidAlgorithmParameterException) {
            // Thrown when no biometric is enrolled: a key that needs one can't be made.
            throw BiometricsUnavailableException(e)
        } catch (e: IllegalStateException) {
            throw BiometricsUnavailableException(e)
        }
    }

    private fun generate(alias: String, strongBox: Boolean): ByteArray {
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                } else {
                    // -1: every use needs its own biometric check, the same as a timeout of 0 above.
                    @Suppress("DEPRECATION")
                    setUserAuthenticationValidityDurationSeconds(-1)
                }
                if (strongBox) setIsStrongBoxBacked(true)
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
            // Checked by type here because StrongBoxUnavailableException only exists from API 28.
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

        // The API's limit on a device name.
        const val MAX_DEVICE_NAME_LENGTH = 50
    }
}
