package com.cypressit.cyvault.feature.vault.home.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BalanceResponseDto(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("description") val description: String = "",
    @SerialName("amount") val amount: Double,
    @SerialName("currency") val currency: String,
    /** ISO 8601: when the purchase or income happened. */
    @SerialName("date") val date: String? = null,
    @SerialName("updatedAt") val updatedAt: String? = null,
)

@Serializable
data class BalanceRequestDto(
    @SerialName("name") val name: String,
    @SerialName("description") val description: String,
    @SerialName("amount") val amount: Double,
    @SerialName("currency") val currency: String,
    /** ISO 8601, or null to let the API date a new balance now and keep an edited one's date. */
    @SerialName("date") val date: String? = null,
)
