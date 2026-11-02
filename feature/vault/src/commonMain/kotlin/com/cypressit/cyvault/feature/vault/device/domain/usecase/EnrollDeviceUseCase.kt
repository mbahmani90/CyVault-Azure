package com.cypressit.cyvault.feature.vault.device.domain.usecase

import com.cypressit.cyvault.feature.vault.device.domain.repository.DeviceEnrollment
import com.cypressit.cyvault.feature.vault.device.domain.repository.DeviceRepository

/** Enrolls this device for the signed-in user, once; later calls find it already enrolled. */
class EnrollDeviceUseCase(private val repository: DeviceRepository) {
    suspend operator fun invoke(userId: String): Result<DeviceEnrollment> = repository.enroll(userId)
}
