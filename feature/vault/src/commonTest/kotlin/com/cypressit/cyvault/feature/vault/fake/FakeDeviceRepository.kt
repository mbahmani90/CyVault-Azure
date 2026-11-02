package com.cypressit.cyvault.feature.vault.fake

import com.cypressit.cyvault.feature.vault.device.domain.repository.DeviceEnrollment
import com.cypressit.cyvault.feature.vault.device.domain.repository.DeviceRepository

class FakeDeviceRepository(
    var result: Result<DeviceEnrollment> = Result.success(DeviceEnrollment.Enrolled("device-1")),
) : DeviceRepository {

    val enrolledUserIds = mutableListOf<String>()

    override suspend fun enroll(userId: String): Result<DeviceEnrollment> {
        enrolledUserIds += userId
        return result
    }
}
