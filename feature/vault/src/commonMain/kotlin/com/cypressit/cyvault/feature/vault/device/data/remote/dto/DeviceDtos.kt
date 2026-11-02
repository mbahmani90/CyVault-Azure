package com.cypressit.cyvault.feature.vault.device.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeviceRequestDto(
    /** The EC P-256 public key, base64 of its DER SubjectPublicKeyInfo. */
    @SerialName("publicKey") val publicKey: String,
    /** At most 50 characters. */
    @SerialName("deviceName") val deviceName: String,
)

@Serializable
data class DeviceResponseDto(
    /** The device ID the API picked, a GUID; the app sends it with every signature. */
    @SerialName("id") val id: String,
    @SerialName("algorithm") val algorithm: String = "",
    @SerialName("deviceName") val deviceName: String = "",
    /** ISO 8601: when the device was enrolled. */
    @SerialName("createdAt") val createdAt: String,
)
