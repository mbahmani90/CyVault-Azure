package com.cypressit.cyvault.feature.vault.fake

import com.cypressit.cyvault.devicekey.BiometricsUnavailableException
import com.cypressit.cyvault.devicekey.DeviceKeyStore

/** Keys are made-up bytes, numbered in the order they're created. */
class FakeDeviceKeyStore(var hasBiometrics: Boolean = true) : DeviceKeyStore {

    override val deviceName: String = "Pixel Test"

    val keys = mutableMapOf<String, ByteArray>()
    var createdKeyCount = 0

    override fun publicKey(userId: String): ByteArray? = keys[userId]

    override fun createKey(userId: String): ByteArray {
        if (!hasBiometrics) throw BiometricsUnavailableException()
        createdKeyCount++
        return byteArrayOf(createdKeyCount.toByte()).also { keys[userId] = it }
    }
}
