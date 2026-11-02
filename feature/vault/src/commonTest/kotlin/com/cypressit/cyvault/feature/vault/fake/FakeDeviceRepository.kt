package com.cypressit.cyvault.feature.vault.fake

import com.cypressit.cyvault.feature.vault.device.domain.repository.ApprovalPrompt
import com.cypressit.cyvault.feature.vault.device.domain.repository.DeviceEnrollment
import com.cypressit.cyvault.feature.vault.device.domain.repository.DeviceRepository
import com.cypressit.cyvault.feature.vault.device.domain.repository.DeviceSignature

class FakeDeviceRepository(
    var result: Result<DeviceEnrollment> = Result.success(DeviceEnrollment.Enrolled("device-1")),
) : DeviceRepository {

    val enrolledUserIds = mutableListOf<String>()

    override suspend fun enroll(userId: String): Result<DeviceEnrollment> {
        enrolledUserIds += userId
        return result
    }

    /** When set, sign fails with this instead of signing. */
    var signFailure: Exception? = null

    /** The messages sign was asked to sign, and the prompts it showed, in order. */
    val signedMessages = mutableListOf<String>()
    val prompts = mutableListOf<ApprovalPrompt>()

    override suspend fun sign(userId: String, message: String, prompt: ApprovalPrompt): Result<DeviceSignature> {
        signFailure?.let { return Result.failure(it) }
        signedMessages += message
        prompts += prompt
        return Result.success(DeviceSignature(deviceId = "device-1", signature = "signature-of-$message"))
    }
}
