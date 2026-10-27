package com.cypressit.cyvault.feature.vault.card.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreateCardRequestDto(
    @SerialName("bank_name") val bankName: String,
    @SerialName("card_holder_name") val cardHolderName: String,
    @SerialName("card_number") val cardNumber: String,
    @SerialName("cvv2") val cvv2: String,
    @SerialName("expiry_date") val expiryDate: String,
    @SerialName("balance") val balance: Double,
    @SerialName("card_type") val cardType: String,
)
