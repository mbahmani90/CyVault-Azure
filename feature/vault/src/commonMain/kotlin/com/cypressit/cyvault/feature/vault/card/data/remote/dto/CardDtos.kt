package com.cypressit.cyvault.feature.vault.card.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CardResponseDto(
    @SerialName("id") val id: String,
    @SerialName("last4") val last4: String,
    @SerialName("brand") val brand: String = "",
    @SerialName("bankName") val bankName: String = "",
    @SerialName("phoneNumber") val phoneNumber: String,
    /** ISO 8601: when the card was added. */
    @SerialName("createdAt") val createdAt: String,
)

@Serializable
data class CardRequestDto(
    /** Sent once to add the card; the API keeps only its last four digits. */
    @SerialName("cardNumber") val cardNumber: String,
    @SerialName("phoneNumber") val phoneNumber: String,
    /** Required, at most 50 characters. */
    @SerialName("bankName") val bankName: String,
)
