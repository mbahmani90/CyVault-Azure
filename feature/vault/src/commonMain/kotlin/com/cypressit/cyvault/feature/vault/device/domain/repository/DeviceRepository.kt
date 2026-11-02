package com.cypressit.cyvault.feature.vault.device.domain.repository

/** This device's enrollment with the API, which lets it sign the user's transfers. */
interface DeviceRepository {
    /**
     * Makes sure the device has a signing key for the account and that the API knows its public
     * key. Does nothing when both are already done, so it's cheap to call on every start.
     */
    suspend fun enroll(userId: String): Result<DeviceEnrollment>

    /**
     * Shows the biometric prompt with [prompt]'s text and signs [message] with this device's key.
     * Enrolls the device first when it isn't yet, and replaces a key that a new fingerprint or face
     * disabled. Fails with [SigningCancelledException] or [SigningUnavailableException].
     */
    suspend fun sign(userId: String, message: String, prompt: ApprovalPrompt): Result<DeviceSignature>
}

sealed interface DeviceEnrollment {
    /** The device can sign; [deviceId] goes with each signature. */
    data class Enrolled(val deviceId: String) : DeviceEnrollment

    /** No fingerprint or face unlock is set up, so there is no key yet. It's tried again next start. */
    data object BiometricsUnavailable : DeviceEnrollment
}

/** What the biometric prompt shows: the user approves exactly this. */
data class ApprovalPrompt(val title: String, val description: String, val cancelLabel: String)

/** [signature] (base64 DER) was made by the key the API knows as [deviceId]. */
data class DeviceSignature(val deviceId: String, val signature: String)

/** The user closed the biometric prompt: nothing was signed or sent. */
class SigningCancelledException : Exception("The approval was cancelled.")

/** This phone can't sign: no fingerprint or face unlock is set up, or it's older than Android 9. */
class SigningUnavailableException : Exception("This phone can't approve transfers.")
