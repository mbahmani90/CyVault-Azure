package com.cypressit.cyvault.feature.vault.device.domain.repository

/** This device's enrollment with the API, which lets it sign the user's transfers. */
interface DeviceRepository {
    /**
     * Makes sure the device has a signing key for the account and that the API knows its public
     * key. Does nothing when both are already done, so it's cheap to call on every start.
     */
    suspend fun enroll(userId: String): Result<DeviceEnrollment>
}

sealed interface DeviceEnrollment {
    /** The device can sign; [deviceId] goes with each signature. */
    data class Enrolled(val deviceId: String) : DeviceEnrollment

    /** No fingerprint or face unlock is set up, so there is no key yet. It's tried again next start. */
    data object BiometricsUnavailable : DeviceEnrollment
}
