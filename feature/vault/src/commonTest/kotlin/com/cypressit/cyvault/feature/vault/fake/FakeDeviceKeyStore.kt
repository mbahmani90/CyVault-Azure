package com.cypressit.cyvault.feature.vault.fake

import com.cypressit.cyvault.devicekey.BiometricCancelledException
import com.cypressit.cyvault.devicekey.BiometricsUnavailableException
import com.cypressit.cyvault.devicekey.DeviceKeyStore
import com.cypressit.cyvault.devicekey.KeyInvalidatedException
import com.cypressit.cyvault.devicekey.SigningPrompt

/** Keys are made-up bytes, numbered in the order they're created; a signature names its key. */
class FakeDeviceKeyStore(var hasBiometrics: Boolean = true) : DeviceKeyStore {

    override val deviceName: String = "Pixel Test"

    val keys = mutableMapOf<String, ByteArray>()
    var createdKeyCount = 0

    /** Keys a new fingerprint or face disabled: signing with them fails until a new key is made. */
    val invalidatedKeys = mutableSetOf<String>()

    /** When true, the user backs out of the prompt. */
    var cancelsPrompt = false

    val prompts = mutableListOf<SigningPrompt>()

    override fun publicKey(userId: String): ByteArray? = keys[userId]

    override fun createKey(userId: String): ByteArray {
        if (!hasBiometrics) throw BiometricsUnavailableException()
        createdKeyCount++
        invalidatedKeys -= userId
        return byteArrayOf(createdKeyCount.toByte()).also { keys[userId] = it }
    }

    override suspend fun sign(userId: String, message: ByteArray, prompt: SigningPrompt): ByteArray {
        if (!hasBiometrics) throw BiometricsUnavailableException()
        val key = keys[userId] ?: throw KeyInvalidatedException()
        if (userId in invalidatedKeys) throw KeyInvalidatedException()
        prompts += prompt
        if (cancelsPrompt) throw BiometricCancelledException()
        return key + message
    }
}
