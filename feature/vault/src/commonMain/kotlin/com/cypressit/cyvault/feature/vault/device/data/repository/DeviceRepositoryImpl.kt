package com.cypressit.cyvault.feature.vault.device.data.repository

import com.cypressit.cyvault.core.coroutines.runSuspendCatching
import com.cypressit.cyvault.devicekey.BiometricCancelledException
import com.cypressit.cyvault.devicekey.BiometricsUnavailableException
import com.cypressit.cyvault.devicekey.DeviceKeyStore
import com.cypressit.cyvault.devicekey.KeyInvalidatedException
import com.cypressit.cyvault.devicekey.SigningPrompt
import com.cypressit.cyvault.feature.vault.device.data.local.DeviceIdStore
import com.cypressit.cyvault.feature.vault.device.data.remote.DeviceApiService
import com.cypressit.cyvault.feature.vault.device.data.remote.DeviceKeyRevokedException
import com.cypressit.cyvault.feature.vault.device.data.remote.dto.DeviceRequestDto
import com.cypressit.cyvault.feature.vault.device.domain.repository.ApprovalPrompt
import com.cypressit.cyvault.feature.vault.device.domain.repository.DeviceEnrollment
import com.cypressit.cyvault.feature.vault.device.domain.repository.DeviceRepository
import com.cypressit.cyvault.feature.vault.device.domain.repository.DeviceSignature
import com.cypressit.cyvault.feature.vault.device.domain.repository.SigningCancelledException
import com.cypressit.cyvault.feature.vault.device.domain.repository.SigningUnavailableException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlin.io.encoding.Base64

class DeviceRepositoryImpl(
    private val apiService: DeviceApiService,
    private val keyStore: DeviceKeyStore,
    private val idStore: DeviceIdStore,
    private val ioDispatcher: CoroutineDispatcher,
) : DeviceRepository {

    override suspend fun enroll(userId: String): Result<DeviceEnrollment> = runSuspendCatching {
        try {
            DeviceEnrollment.Enrolled(enrolledDeviceId(userId))
        } catch (_: BiometricsUnavailableException) {
            DeviceEnrollment.BiometricsUnavailable
        }
    }

    override suspend fun sign(userId: String, message: String, prompt: ApprovalPrompt): Result<DeviceSignature> =
        runSuspendCatching {
            try {
                val deviceId = enrolledDeviceId(userId)
                try {
                    signWith(deviceId, userId, message, prompt)
                } catch (_: KeyInvalidatedException) {
                    // A fingerprint or face was added since the key was made, which disabled it:
                    // enroll a new key and ask again. The old device stays on the API, unused.
                    signWith(enrollNewKey(userId), userId, message, prompt)
                }
            } catch (_: BiometricCancelledException) {
                throw SigningCancelledException()
            } catch (_: BiometricsUnavailableException) {
                throw SigningUnavailableException()
            } catch (_: KeyInvalidatedException) {
                throw SigningUnavailableException()
            }
        }

    private suspend fun enrolledDeviceId(userId: String): String {
        val existingKey = withContext(ioDispatcher) { keyStore.publicKey(userId) }
        if (existingKey != null) {
            idStore.deviceId(userId)?.let { return it }
        }

        // A key without a device ID is one whose enrollment response was lost: sending it again
        // returns the same device. Only a key the user removed has to be replaced.
        val publicKey = existingKey ?: createKey(userId)
        val deviceId = try {
            send(publicKey)
        } catch (_: DeviceKeyRevokedException) {
            send(createKey(userId))
        }
        idStore.save(userId, deviceId)
        return deviceId
    }

    private suspend fun enrollNewKey(userId: String): String {
        val deviceId = send(createKey(userId))
        idStore.save(userId, deviceId)
        return deviceId
    }

    private suspend fun signWith(
        deviceId: String,
        userId: String,
        message: String,
        prompt: ApprovalPrompt,
    ): DeviceSignature {
        val signature = keyStore.sign(
            userId,
            message.encodeToByteArray(),
            SigningPrompt(prompt.title, prompt.description, prompt.cancelLabel),
        )
        return DeviceSignature(deviceId, Base64.encode(signature))
    }

    /** A new key has no device ID until the API gives it one: the old key's ID is dropped first. */
    private suspend fun createKey(userId: String): ByteArray {
        idStore.clear(userId)
        return withContext(ioDispatcher) { keyStore.createKey(userId) }
    }

    private suspend fun send(publicKey: ByteArray): String =
        apiService.enroll(DeviceRequestDto(Base64.encode(publicKey), keyStore.deviceName)).id
}
